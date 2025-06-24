package icu.neurospicy.fibi.domain.service.friends.morningroutine

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.MessageForActivityReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.model.events.SetUpMorningRoutineActivityFinished
import icu.neurospicy.fibi.domain.model.events.SetUpMorningRoutineActivityStarted
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.RoutineConfigurationRepository
import icu.neurospicy.fibi.domain.service.ConversationContextService
import icu.neurospicy.fibi.outgoing.ollama.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val ACTIVITY_NAME = "SET_UP_MORNING_ROUTINE"

@Service
class SetUpMorningRoutineActivity(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val friendshipLedger: FriendshipLedger,
    private val chatRepository: ChatRepository,
    private val routineConfigurationRepository: RoutineConfigurationRepository,
    private val intentRecognizer: IntentRecognizer,
    private val informationExtractor: InformationExtractor,
    private val conversationContextService: ConversationContextService,
    private val promptsConfiguration: PromptsConfiguration,
) {

    suspend fun startProcess(friendshipId: FriendshipId, message: UserMessage) {
        friendshipLedger.startActivity(friendshipId, ACTIVITY_NAME)
        applicationEventPublisher.publishEvent(
            SetUpMorningRoutineActivityStarted(
                SetUpMorningRoutineActivity.javaClass, friendshipId
            )
        )

        // Try to extract wake-up time from the starting message
        val lastMessage = chatRepository.find(friendshipId, message.messageId)
        val extractionResult =
            lastMessage?.takeIf { it.byUser() }?.let { extractWakeUpTime((it as UserMessage).text) }

        if (extractionResult?.wakeUpTime == null) {
            // No wake-up time found, ask user to provide it.
            LOG.debug("Friend $friendshipId did not provide a wake-up time. Requesting wake-up time.")
            sendRequestToProvideWakeUpTime(friendshipId, message.channel, message.messageId)
            return
        }

        // Process: user already provided a wake-up time (and optionally routine activities)
        LOG.debug(
            "Friend {} provided wake-up time {}. Finishing routine setup.",
            friendshipId,
            extractionResult.wakeUpTime
        )

        // Create a Routine configuration with the extracted wake-up time and optional additional activities.
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: UTC
        val routineConfig = RoutineConfiguration(
            friendshipId = friendshipId,
            name = "Morning routine",
            description = "A routine starting after wake-up.",
            trigger = LocalTimeBasedTrigger(
                localTime = extractionResult.wakeUpTime,
                timezone = timezone
            ),
            // Optional: add user-specified activities from extractionResult.activities,
            // here we add a predetermined activity "Show today's schedule".
            activities = extractionResult.activities.toMutableList().plus(
                RoutineActivityConfiguration(
                    name = "Plan the day",
                    order = extractionResult.activities.size + 1,
                )
            )
        )

        // Save the configuration
        routineConfigurationRepository.save(routineConfig)

        // Finish the activity.
        finishActivity(friendshipId, extractionResult.wakeUpTime, message.channel, message.messageId)
    }

    @EventListener(condition = "event.activity.equals('$ACTIVITY_NAME')")
    fun handleMessage(event: MessageForActivityReceived) = runBlocking {
        // Check if the user wants to cancel the routine registration.
        if (try {
                intentRecognizer.recognize(
                    event.friendshipId, event.message, setOf(
                        PossibleIntent("cancel morning routine", "User wants to cancel morning routine setup."),
                        PossibleIntent("Other", "None of the previous.")
                    ), useTools = true
                ).intent.name == "cancel morning routine"
            } catch (e: IntentRecognitionFailed) {
                false
            }
        ) {
            cancelActivityByUser(event.friendshipId, event.message)
            return@runBlocking
        }

        // Try extracting wake-up time from subsequent messages.
        val extractionResult = extractWakeUpTime(event.message.text)
        if (extractionResult?.wakeUpTime == null) {
            // If still no wake-up time, provide help message.
            sendHelpToProvideWakeUpTime(event.friendshipId, event.message)
            return@runBlocking
        }

        // If wake-up time is now provided, finalize the routine configuration.
        LOG.debug(
            "Friend {} provided wake-up time {} via follow-up message.",
            event.friendshipId,
            extractionResult.wakeUpTime
        )

        // Create and save the routine configuration as before.
        val timezone = friendshipLedger.findBy(event.friendshipId)?.timeZone ?: UTC
        val routineConfig = RoutineConfiguration(
            friendshipId = event.friendshipId,
            name = "Morning routine",
            description = "A routine starting after wake-up.",
            trigger = LocalTimeBasedTrigger(localTime = extractionResult.wakeUpTime, timezone = timezone),
            activities = extractionResult.activities.plus(
                RoutineActivityConfiguration(
                    name = "Plan the day",
                    order = extractionResult.activities.size + 1,
                )
            )
        )
        routineConfigurationRepository.save(routineConfig)
        finishActivity(event.friendshipId, extractionResult.wakeUpTime, event.message.channel, event.message.messageId)
    }

    private fun sendRequestToProvideWakeUpTime(friendshipId: FriendshipId, channel: Channel, messageId: MessageId) {
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingAdaptedTextMessage(
                    channel,
                    "Adapt the message to the request by the user. Keep the word \"when\" in your answer.",
                    "A morning routine is a great idea. When do you usually wake-up?",
                    useTaskActions = false
                ), messageId
            )
        )
    }

    private fun sendHelpToProvideWakeUpTime(friendshipId: FriendshipId, message: UserMessage) {
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingGeneratedMessage(
                    message.channel,
                    promptsConfiguration.morningRoutineHelpPromptTemplate,
                    useTaskActions = false,
                    useFriendSettingActions = false
                ), message.messageId
            )
        )
    }

    private fun cancelActivityByUser(friendshipId: FriendshipId, message: UserMessage) {
        friendshipLedger.finishActivity(friendshipId, ACTIVITY_NAME)
        routineConfigurationRepository.cancelRegistration(friendshipId)
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingTextMessage(
                    message.channel,
                    "No worries! I won't set up your morning routine. If you change your mind later, just let me know."
                ), message.messageId
            )
        )
    }

    /**
     * Extracts the wake-up time (and optionally any additional routine activities) from a message.
     */
    private suspend fun extractWakeUpTime(message: String): MorningRoutineExtractionResult? {
        return try {
            val extractionResult = informationExtractor.extract(
                message, ExtractionSpec(
                    MorningRoutineLlmExtractionResult::class.java,
                    """Your task is to extract the wake-up time of the user and activities of their morning routine.
                    |1. The time may be in the format H:mm, just H or written in words.
                    |2. The activities are optional. If there are activities described by the user, offer their names in their chronological order. 
                    |
                    |### Example 1
                    |**Message:**
                    |I usually wake up at 7.
                    |**Result:**
                    |{
                    |"wakeUpTime": "7:00",
                    |"activities": []
                    |}
                    |
                    |### Example 2
                    |**Message:**
                    |I want my morning routine at 8:00 o'clock. I usually first drink a cup of coffee.
                    |**Result:**
                    |{
                    |"wakeUpTime": "8:00",
                    |"activities": ["Drink coffee"]
                    |}
                    |
                    |### Example 3
                    |**Message:**
                    |I am having my breakfast at eight in the morning, right after getting out of the bed.
                    |**Result:**
                    |{
                    |"wakeUpTime": "8:00",
                    |"activities": ["Have breakfast"]
                    |}
                    |
                    |### Example 4
                    |**Message:**
                    |Puh. Good question!
                    |**Result:**
                    |{}
                    """
                )
            )
            var index = 1
            MorningRoutineExtractionResult(
                LocalTime.parse(if (extractionResult.wakeUpTime.length == 4) "0" + extractionResult.wakeUpTime else extractionResult.wakeUpTime),
                extractionResult.activities?.map {
                    RoutineActivityConfiguration(
                        name = it,
                        order = index++,
                    )
                } ?: emptyList()
            )
        } catch (e: Exception) {
            LOG.debug("Failed to extract wake-up time and activities.")
            null
        }
    }

    private fun finishActivity(
        friendshipId: FriendshipId,
        wakeUpTime: LocalTime,
        channel: Channel,
        messageId: MessageId
    ) {
        friendshipLedger.finishActivity(friendshipId, ACTIVITY_NAME)
        val routineId = routineConfigurationRepository.finishRegistration(friendshipId)?.routineId ?: return
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: UTC
        val now = Instant.now()
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingTextMessage(
                    channel,
                    "Thanks, I've set your wake-up time to ${
                        wakeUpTime.format(
                            DateTimeFormatter.ofPattern(
                                "HH:mm"
                            )
                        )
                    }. Your morning routine is now configured and I will be there for you next morning.\nI assume your current time is ${
                        now.minusSeconds(((now.truncatedTo(ChronoUnit.MINUTES).epochSecond / 60) % 5) * 60)
                            .atZone(timezone).format(
                                DateTimeFormatter.ofPattern(
                                    "HH:mm"
                                )
                            )
                    }. Is that right?"
                ),
                messageId
            )
        )
        applicationEventPublisher.publishEvent(
            SetUpMorningRoutineActivityFinished(
                SetUpMorningRoutineActivity.javaClass, friendshipId, routineId
            )
        )
        conversationContextService.endConversation(friendshipId)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SetUpMorningRoutineActivity::class.java)
    }
}

/**
 * Represents the result of extracting the wake-up time (and optionally additional routine activities) from a user's message.
 */
data class MorningRoutineExtractionResult(
    val wakeUpTime: LocalTime,
    val activities: List<RoutineActivityConfiguration>
)

data class MorningRoutineLlmExtractionResult(
    val wakeUpTime: String,
    val activities: List<String>?
)