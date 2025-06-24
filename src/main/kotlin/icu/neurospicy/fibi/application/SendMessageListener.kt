package icu.neurospicy.fibi.application

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.MessageGenerationFinished
import icu.neurospicy.fibi.domain.model.events.MessageGenerationStarted
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.ConversationRepository
import icu.neurospicy.fibi.outgoing.SchedulerService
import icu.neurospicy.fibi.outgoing.mongodb.ChatRepositoryUsingMongodb
import icu.neurospicy.fibi.outgoing.ollama.ResponseGenerator
import icu.neurospicy.fibi.outgoing.signal.SignalMessageSender
import kotlinx.coroutines.runBlocking
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.Instant.now

@Component
class SendMessageListener(
    private val signalMessageSender: SignalMessageSender,
    private val responseGenerator: ResponseGenerator,
    private val chatRepository: ChatRepositoryUsingMongodb,
    private val conversationRepository: ConversationRepository,
    private val schedulerService: SchedulerService
) {
    @EventListener
    @Async
    fun onMessageSendRequested(event: SendMessageCmd) = runBlocking {
        val message = event.outgoingMessage
        val responseText: String = when (message) {
            is OutgoingAdaptedTextMessage -> responseGenerator.generateResponseWith(
                event.friendshipId, message, event.answerToMessageId
            )

            is OutgoingGeneratedMessage -> responseGenerator.generateResponseWith(
                event.friendshipId, message, event.answerToMessageId
            )

            is OutgoingTextMessage -> message.text
        }

        when (message.channel) {
            Channel.SIGNAL -> signalMessageSender.sendMessageToUser(event.friendshipId, responseText)
        }
        val sentAt = now()
        chatRepository.add(event.friendshipId, message, responseText, sentAt)
        conversationRepository.addFibisResponse(
            event.friendshipId, FibiMessage(
                FibiMessageId(), sentAt, responseText, Channel.SIGNAL, event.outgoingMessage.toolCalls
            )
        )
    }

    @EventListener
    @Async
    fun onMessageGenerationStarted(event: MessageGenerationStarted) {
        schedulerService.scheduleGeneratingMessage(event.friendshipId, event.channel)
    }

    @EventListener
    @Async
    fun onMessageGenerationStopped(event: MessageGenerationFinished) {
        schedulerService.removeGeneratingMessageScheduler(event.friendshipId, event.channel)
    }
}
