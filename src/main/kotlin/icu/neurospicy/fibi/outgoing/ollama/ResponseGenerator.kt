package icu.neurospicy.fibi.outgoing.ollama

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.MessageGenerationFinished
import icu.neurospicy.fibi.domain.model.events.MessageGenerationStarted
import icu.neurospicy.fibi.domain.repository.*
import icu.neurospicy.fibi.domain.service.friends.tools.*
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

private val DEFAULT_MODEL = "[MODEL_NAME]"

@Service
class ResponseGenerator(
    private val llmClient: LlmClient,
    private val chatRepository: ChatRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val friendshipLedger: FriendshipLedger,
    private val calendarRepository: CalendarRepository,
    private val taskRepository: TaskRepository,
    private val conversationRepository: ConversationRepository,
    private val calendarConfigurationRepository: CalendarConfigurationRepository,
    private val promptsConfiguration: PromptsConfiguration

) {
    suspend fun generateResponseWith(
        friendshipId: FriendshipId,
        message: OutgoingGeneratedMessage,
        requestId: MessageId? = null,
    ): String {
        LOG.debug("Generating response for message '{}'.", message)
        return generateMessageWithRetries(friendshipId, message, requestId)
    }


    suspend fun generateResponseWith(
        friendshipId: FriendshipId,
        message: OutgoingAdaptedTextMessage,
        requestId: MessageId? = null,
    ): String {
        LOG.debug("Adapting text of message '{}'.", message)
        return generateMessageWithRetries(friendshipId, message, requestId)
    }

    private suspend fun generateMessageWithRetries(
        friendshipId: FriendshipId, message: OutgoingMessageNeedsGenerator, requestId: MessageId?
    ): String {
        return generateMessage(friendshipId, message, requestId) ?: throw Exception("Failed hard to generate message")
    }

    private suspend fun generateMessage(
        friendshipId: FriendshipId, message: OutgoingMessageNeedsGenerator, requestId: MessageId?
    ): String? {
        val friendshipLedgerEntry = friendshipLedger.findBy(friendshipId)
        val zone = friendshipLedgerEntry?.timeZone ?: UTC
        val sendingTimeAtUserZone = ZonedDateTime.now(zone)
        val name = friendshipLedgerEntry?.signalName
        val messageRespondingTo =
            requestId?.let { chatRepository.find(friendshipId, messageId = requestId) }?.takeIf { it.byUser() }
                ?.let { it as UserMessage }

        applicationEventPublisher.publishEvent(
            MessageGenerationStarted(
                _source = this.javaClass, friendshipId, message.channel, requestId
            )
        )

        val tools = createListOfTools(message, friendshipId)
        val response = llmClient.promptReceivingText(
            createMessagePrompt(
                when (message) {
                    is OutgoingGeneratedMessage -> createDescriptionForMessage(message)
                    is OutgoingAdaptedTextMessage -> createDescriptionForMessage(message)
                }, friendshipId, name, sendingTimeAtUserZone, messageRespondingTo, message.useHistory
            ),
            OllamaOptions.builder().model(DEFAULT_MODEL).temperature(0.3).build(),
            zone,
            chatRepository.find(friendshipId, message.messageId).takeIf { it is UserMessage }
                ?.let { it as UserMessage }?.receivedAt ?: Instant.now(),
            tools = tools
        )

        applicationEventPublisher.publishEvent(
            MessageGenerationFinished(
                _source = this.javaClass, friendshipId, message.channel, requestId
            )
        )

        return response
    }

    private fun createListOfTools(
        message: OutgoingMessageNeedsGenerator, friendshipId: FriendshipId
    ): MutableSet<Any> = mutableSetOf<Any>().apply {
        if (message.useRetrievalTools) plus(
            listOf(
                CalendarTools(calendarRepository, calendarConfigurationRepository, friendshipLedger, friendshipId),
                ChatHistoryTools(friendshipLedger, chatRepository, friendshipId),
                TaskTools(friendshipId, taskRepository)
            )
        )
        if (message.useRetrievalTools) plus(
            listOf(TaskActionTools(friendshipId, taskRepository))
        )
        if (message.useFriendSettingActions) plus(
            listOf(FriendSettingsTools(friendshipLedger, friendshipId, applicationEventPublisher))
        )
    }

    private fun createMessagePrompt(
        messageDescription: Message,
        friendshipId: FriendshipId,
        name: String?,
        sendingTimeAtUserZone: ZonedDateTime,
        messageRespondingTo: UserMessage?,
        useHistory: Boolean
    ): List<Message> {
        return (conversationRepository.findByFriendshipId(friendshipId)?.messages?.map { it.toLlmMessage() }
            .takeIf { useHistory } ?: emptyList()).plus(
            SystemMessage(
                fibiSystemMessage(
                    name,
                    sendingTimeAtUserZone,
                    messageRespondingTo
                )
            )
        ).plus(messageDescription)
    }

    private fun createDescriptionForMessage(message: OutgoingGeneratedMessage): org.springframework.ai.chat.messages.UserMessage {
        return org.springframework.ai.chat.messages.UserMessage(
            promptsConfiguration.generatedMessagePromptTemplate.replace(
                "\${message.messageDescription}",
                message.messageDescription
            )
        )
    }

    private fun createDescriptionForMessage(message: OutgoingAdaptedTextMessage): org.springframework.ai.chat.messages.UserMessage {
        return org.springframework.ai.chat.messages.UserMessage(
            promptsConfiguration.adaptedTextMessagePromptTemplate.replace(
                "\${message.messageDescription}",
                message.messageDescription
            ).replace("\${message.text}", message.text)
        )
    }

    private fun fibiSystemMessage(
        name: String?, zonedDateTime: ZonedDateTime, messageRespondingTo: UserMessage?
    ): String {
        return promptsConfiguration.fibiSystemPromptTemplate.replace("\${zonedDateTime}", zonedDateTime.toString())
            .replace(
                "\${messageReceivedTime}", messageRespondingTo?.receivedAt?.atZone(zonedDateTime.zone)?.toString() ?: ""
            ).replace("\${nameInfo}", if (name != null) "The friend in this conversation is called \"$name\"." else "")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ResponseGenerator::class.java)
    }
}