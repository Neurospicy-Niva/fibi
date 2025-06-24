package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.application.calendar.CalendarConfigValidator
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.CalendarRegistrationActivityFinished
import icu.neurospicy.fibi.domain.repository.*
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.http.UnvalidatedCalendarConfiguration
import icu.neurospicy.fibi.outgoing.ollama.ExpectedField
import icu.neurospicy.fibi.outgoing.ollama.ExtractionSpec
import icu.neurospicy.fibi.outgoing.ollama.InformationExtractor
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toSet
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.util.regex.Pattern
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage

@Component
class RegisterCalendarSubtaskHandler(
    friendshipLedger: FriendshipLedger,
    private val calendarConfigurationRepository: CalendarConfigurationRepository,
    private val calendarConfigValidator: CalendarConfigValidator,
    private val llmClient: LlmClient,
    private val informationExtractor: InformationExtractor,
    val eventPublisher: ApplicationEventPublisher,
    val calendarActivityRepository: CalendarActivityRepository,
) : CrudSubtaskHandler<CalendarConfigurations, CalendarConfiguration>(
    intent = CalendarIntents.Register,
    entityHandler = object : CrudEntityHandler<CalendarConfigurations, CalendarConfiguration> {
        override suspend fun identifyEntityId(
            allEntities: List<CalendarConfiguration>,
            rawText: String,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): IdResolutionResult = NoActionResolutionResult()

        override suspend fun extractEntityData(
            rawText: String,
            previousData: CalendarConfigurations?,
            clarificationQuestion: String?,
            answer: String?,
            friendshipId: FriendshipId,
            timezone: ZoneId,
            messageTime: Instant,
            messageId: MessageId?,
            channel: Channel?,
        ): ExtractionResult<CalendarConfigurations> {
            val textToProcess = listOfNotNull(rawText, clarificationQuestion, answer).joinToString("\n")
            val configs = extractCalendarUrls(friendshipId, textToProcess).configs

            if (answer == null && messageId != null) {
                calendarActivityRepository.startRegistration(
                    CalendarRegistration.started(
                        friendshipId = friendshipId, source = MessageSource(messageId, channel ?: Channel.SIGNAL)
                    )
                )
            }

            if (configs.isEmpty()) {
                // Try to interpret if the user asks for help
                val supportPrompt =
                    if (answer == null) buildStartRegistrationPrompt(rawText) else buildFollowUpHelpPrompt(textToProcess)

                val helpMessage = llmClient.promptReceivingText(
                    listOf(AiUserMessage(supportPrompt)),
                    OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.3).topP(0.8).build(),
                    timezone,
                    messageTime
                )
                    ?: "Could you let me know which calendar service you're using (e.g., Nextcloud, Google Calendar)? I can help you find the (caldav) URL."

                return ExtractionResult(clarifyingQuestion = helpMessage)
            }

            val invalidUrls = mutableSetOf<String>()
            val savedConfigs = configs.asFlow().mapNotNull { unvalidatedConfig ->
                val result = calendarConfigValidator.isValid(unvalidatedConfig)
                if (result.isValid) {
                    result.config
                } else {
                    invalidUrls.add(unvalidatedConfig.url)
                    null
                }
            }.toSet().let { CalendarConfigurations(it) }
                .apply { calendarConfigurationRepository.save(friendshipId, this) }
                .apply {
                    eventPublisher.publishEvent(
                        CalendarRegistrationActivityFinished(
                            this.javaClass,
                            friendshipId,
                            this.configurations.toSet(),
                            channel,
                            messageId
                        )
                    )
                }

            return when {
                invalidUrls.isEmpty() -> ExtractionResult(
                    data = savedConfigs,
                    responseMessage = "Your${if (savedConfigs.configurations.size > 1) " calendars are" else " calendar is"} successfully added."
                )

                savedConfigs.configurations.isNotEmpty() -> {
                    val invalidText = invalidUrls.joinToString("\n") { "- $it" }
                    ExtractionResult(
                        data = savedConfigs,
                        clarifyingQuestion = "❗ The following calendars could not be added:\n$invalidText\nPlease verify URL and credentials.",
                        responseMessage = "${if (savedConfigs.configurations.size > 1) "${savedConfigs.configurations.size} calendars are" else "One calendar is"} successfully added."
                    )
                }

                else -> ExtractionResult(
                    clarifyingQuestion = "⚠️ No calendar could be added. Please verify URL(s) and credentials."
                )
            }
        }

        private suspend fun extractCalendarUrls(
            friendshipId: FriendshipId,
            message: String,
        ): CalendarUrlExtractionResult {
            val urlPattern = Pattern.compile(
                "\\b((?:(?:https?|dav(?:s|webcal)?):\\/\\/[a-zA-Z0-9+&@#\\/%?=~_\\-|!:,.;]*[a-zA-Z0-9+&@#\\/%=~_|]))"
            )
            val matcher = urlPattern.matcher(message)
            val configs: MutableSet<UnvalidatedCalendarConfiguration> = mutableSetOf()
            while (matcher.find()) {
                matcher.group().let { possibleCalendarUrl ->
                    informationExtractor.extract(
                        message, ExtractionSpec(
                            ExtractedCredential::class.java,
                            """
Your task is to extract credentials that are used to access a calendar url.
The credentials either consist of username and password or an API key. The calendar url is "${possibleCalendarUrl}".

### Example 1
**Message:**
Url: http://example.com Username: kira Password: runforestrun123
**Result:**
{
"username": "kira",
"password": "runforestrun123"
}

**Message:**
My calendar URL is: http://example.com/mikes/calendar
User: "Mike"
Pw: "kn28%njs§l:22"
**Result:**
{### Example 2

"username": "Mike",
"password": "kn28%njs§l:22"
}

### Example 3
**Message:**
http://example.com/calendar
apikey: nkj23b8gfev9wub2398tfb29ef3g
**Result:**
{
"apiKey": "nkj23b8gfev9wub2398tfb29ef3g"
}

### Example 4
**Message:**
webdav://example.com/calendar
**Result:**
{}
                            """.trimIndent(),
                            listOf(
                                ExpectedField("username", "String", required = false),
                                ExpectedField("password", "String", required = false),
                                ExpectedField("apiKey", "String", required = false)
                            )
                        ), friendshipId
                    ).let { credential ->
                        configs.add(
                            when {
                                credential.username?.isNotBlank() == true && credential.password?.isNotBlank() == true -> UnvalidatedCalendarConfiguration(
                                    friendshipId,
                                    possibleCalendarUrl,
                                    UsernamePasswordCredential(credential.username, credential.password)
                                )

                                credential.apiKey?.isNotBlank() == true -> UnvalidatedCalendarConfiguration(
                                    friendshipId, possibleCalendarUrl, ApiKeyCredential(credential.apiKey)
                                )

                                else -> UnvalidatedCalendarConfiguration(friendshipId, possibleCalendarUrl)
                            }
                        )
                    }
                }
            }
            return CalendarUrlExtractionResult(configs)
        }

        private fun buildFollowUpHelpPrompt(textToProcess: String): String = """
                    The user was asked to provide a calendar URL but instead responded with this:

                    "$textToProcess"

                    Please respond with helpful instructions for how they can find their calendar URL. Mention, that it must be a CalDAV URL. 
                    If they mention a calendar type (e.g., Google, Nextcloud), give step-by-step instructions. 
                    If unclear, ask them which service they use.

                    Keep the answer short, clear, and supportive.
                """.trimIndent()

        private fun buildStartRegistrationPrompt(textToProcess: String): String = """
                    The user asked to add their calendar, but did not provide a caldav URL:

                    "$textToProcess"

                    Please respond and ask for a calendar (caldav) URL and credentials if needed. 
                    If they mention a calendar type (e.g., Google, Nextcloud), give step-by-step instructions. 
                    If unclear, ask them which service they use.

                    Keep the answer short, clear, and supportive.
                """.trimIndent()


    },
    friendshipLedger
) {
    override suspend fun loadEntities(friendshipId: FriendshipId): List<CalendarConfiguration> {
        return emptyList()
    }

    override suspend fun applyUpdate(
        friendshipId: FriendshipId,
        id: String?,
        entity: CalendarConfigurations,
    ) {
        //applied changes in extractCalendarUrls already
    }

    override fun getDefaultDataQuestion(): String = "Could you share the calendar URL you want to add?"


    data class CalendarUrlExtractionResult(
        val configs: Set<UnvalidatedCalendarConfiguration>,
    )

    data class ExtractedCredential(
        val username: String?, val password: String?, val apiKey: String?,
    )
}

