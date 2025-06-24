package icu.neurospicy.fibi.domain.service.onboarding

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.IncomingAcquaintanceMessageReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.ollama.IntentRecognitionFailed
import icu.neurospicy.fibi.outgoing.ollama.IntentRecognizer
import icu.neurospicy.fibi.outgoing.ollama.PossibleIntent
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant.now
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.temporal.ChronoUnit

private const val TOS_NAME = "fibi - Terms of Service.v0"

@Service
class AcquaintanceInteraction(
    private val intentRecognizer: IntentRecognizer,
    private val chatRepository: ChatRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val friendshipLedger: FriendshipLedger,
    private val promptsConfiguration: PromptsConfiguration
) {
    @EventListener
    @Async
    fun onMessageSendRequested(event: IncomingAcquaintanceMessageReceived) = runBlocking {
        val history =
            chatRepository.findHistory(event.friendshipId).timeline.filter { it.channel == event.message.channel }
        val otherIntent = PossibleIntent(
            "Other/Functionality",
            """None of the others, e.g., "It's cold outside", "I am angry, help me.", "Add a 12 minute timer for my pizza"."""
        )
        val intentToCallable = mapOf(
            PossibleIntent(
                "Confirms TOS", """The user accepts the terms of service, e.g., "Yes", "✓" or "Fine".""", "🧚"
            ) to {
                welcome(
                    event.friendshipId, event.message.messageId, event.message.channel, history.plus(
                        UserMessage(
                            FibiMessageId(), event.message.receivedAt, event.message.text, event.message.channel
                        )
                    )
                )
            },
            PossibleIntent(
                "Likely confirms TOS",
                """The message indicates confirmation, e.g., "Just continue", "Let's start" or a "Sounds acceptable".""",
                "🧚"
            ) to {
                welcomeBasedOnLikelyConfirmation(
                    event.friendshipId, event.message.messageId, event.message.channel, history.plus(
                        UserMessage(
                            FibiMessageId(), event.message.receivedAt, event.message.text, event.message.channel
                        )
                    )
                )
            },
            PossibleIntent(
                "Inform about TOS", "The user wants more information about the terms of service."
            ) to { answerTosQuestion(event.friendshipId, event.message.messageId, event.message.channel) },
            PossibleIntent(
                "Inform about fibi", "The user wants more information about fibi."
            ) to { answerQuestionAboutFibi(event.friendshipId, event.message.messageId, event.message.channel) },
            PossibleIntent(
                "Inform about features", "The user wants more information about the features fibi provides."
            ) to { answerFeatureQuestion(event.friendshipId, event.message.messageId, event.message.channel) },
            PossibleIntent(
                "Denies TOS", "The user CLEARLY denies the TOS, e.g., with a 'No', 'thumb down' or a '❌'", "👋"
            ) to { tellDataIsDeleted(event.friendshipId, event.message.messageId, event.message.channel) },
            otherIntent to { tellToConfirmTos(event.friendshipId, event.message.messageId, event.message.channel) })
        try {
            intentRecognizer.recognize(
                event.friendshipId, event.message, intentToCallable.keys, history, true
            ).intent
        } catch (e: IntentRecognitionFailed) {
            otherIntent
        }.let {
            intentToCallable[it]?.invoke() ?: tellToConfirmTos(
                event.friendshipId, event.message.messageId, event.message.channel
            )
        }
    }

    private fun welcome(
        friendshipId: FriendshipId, requestId: MessageId, channel: Channel, chatHistory: List<Message>
    ) {
        LOG.debug("$friendshipId is now a friend. Sending welcome message.")
        updateFriendLedger(friendshipId, chatHistory)
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass, friendshipId, OutgoingTextMessage(
                    channel, promptsConfiguration.acquaintanceWelcomeMessage
                ), requestId
            )
        )
    }

    private fun welcomeBasedOnLikelyConfirmation(
        friendshipId: FriendshipId, requestId: MessageId, channel: Channel, chatHistory: List<Message>
    ) {
        LOG.debug("$friendshipId is now a friend based on likely confirmation. Sending welcome message.")
        updateFriendLedger(friendshipId, chatHistory)
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                friendshipId,
                OutgoingTextMessage(
                    channel, promptsConfiguration.acquaintanceWelcomeLikelyMessage.trimIndent()
                ),
                requestId,
            )
        )

    }

    private fun updateFriendLedger(friendshipId: FriendshipId, chatHistory: List<Message>) {
        friendshipLedger.acceptTermsOfUse(friendshipId, AcceptedAgreement(TOS_NAME, now(), chatHistory.map {
            when (it) {
                is FibiMessage -> "${it.sentAt.atZone(UTC).format(ISO_DATE_TIME)} fibi: ${it.text}\n"
                is UserMessage -> "${it.receivedAt.atZone(UTC).format(ISO_DATE_TIME)} user: ${it.text}\n"
            }
        }.joinToString { "\n" }))
    }

    private fun answerTosQuestion(friendshipId: FriendshipId, requestId: MessageId, channel: Channel) {
        LOG.debug("Answering TOS question of $friendshipId")
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                friendshipId,
                OutgoingGeneratedMessage(
                    channel,
                    promptsConfiguration.acquaintanceTosQuestionTemplate,
                    useTaskActions = true,
                    useFriendSettingActions = false
                ),
                requestId,
            )
        )
    }

    private fun answerFeatureQuestion(friendshipId: FriendshipId, requestId: MessageId, channel: Channel) {
        LOG.debug("Answering feature question of $friendshipId")
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                friendshipId,
                OutgoingGeneratedMessage(
                    channel,
                    promptsConfiguration.acquaintanceFeatureQuestionTemplate,
                    useTaskActions = true,
                    useFriendSettingActions = false
                ),
                requestId,
            )
        )
    }

    private fun answerQuestionAboutFibi(friendshipId: FriendshipId, requestId: MessageId, channel: Channel) {
        LOG.debug("Answering question about fibi of $friendshipId")
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                friendshipId,
                OutgoingGeneratedMessage(
                    channel,
                    promptsConfiguration.acquaintanceFibiBackgroundTemplate,
                    useTaskActions = true,
                    useFriendSettingActions = false
                ),
                requestId,
            )
        )

    }

    private fun tellDataIsDeleted(friendshipId: FriendshipId, requestId: MessageId, channel: Channel) {
        LOG.debug("Confirming data deletion to $friendshipId")
        friendshipLedger.deniedTermsOfUse(friendshipId)
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                friendshipId,
                OutgoingTextMessage(
                    channel, promptsConfiguration.acquaintanceDataDeletedMessage
                ),
                requestId,
            )
        )
        chatRepository.applyDeletionRequest(friendshipId)
    }

    private fun tellToConfirmTos(friendshipId: FriendshipId, requestId: MessageId, channel: Channel) {
        LOG.debug("Telling $friendshipId to confirm TOS before chatting")
        val isLastMessageOlderThanADay = isLastMessageOlderThanADay(friendshipId)
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                _source = this.javaClass,
                friendshipId,
                OutgoingGeneratedMessage(
                    channel, promptsConfiguration.acquaintanceTosConfirmationTemplate.replace(
                        "\${tosLink}",
                        if (isLastMessageOlderThanADay) "Mention the link to the tos: https://neurospicy.icu/tos" else ""
                    ), useTaskActions = false, useFriendSettingActions = false
                ),
                requestId,
            )
        )
    }

    private fun isLastMessageOlderThanADay(friendshipId: FriendshipId): Boolean {
        return chatRepository.findHistory(friendshipId).timeline.let {
            //remove current message and last response, to access the last message send by the user
            it.removeLastOrNull()
            it.removeLastOrNull()
            it.lastOrNull()
        }?.takeIf { it is UserMessage }?.let { it as UserMessage }?.receivedAt?.plus(1, ChronoUnit.DAYS)
            ?.isBefore(now()) ?: false
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}