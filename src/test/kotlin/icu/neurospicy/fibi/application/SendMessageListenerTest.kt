package icu.neurospicy.fibi.application

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.outgoing.mongodb.ChatRepositoryUsingMongodb
import icu.neurospicy.fibi.outgoing.ollama.ResponseGenerator
import icu.neurospicy.fibi.outgoing.signal.SignalMessageSender
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
internal class SendMessageListenerTest {

    @MockK(relaxed = true)
    lateinit var signalMessageSender: SignalMessageSender

    @MockK(relaxed = true)
    lateinit var responseGenerator: ResponseGenerator

    @MockK(relaxed = true)
    lateinit var chatRepository: ChatRepositoryUsingMongodb

    @Test
    fun `send to friend on text message`() {
        //given
        val sendMessageListener =
            SendMessageListener(
                signalMessageSender,
                responseGenerator,
                chatRepository,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        val text = "The text to send"
        val friendshipId = FriendshipId()
        //when
        sendMessageListener.onMessageSendRequested(
            SendMessageCmd(
                this.javaClass,
                friendshipId,
                OutgoingTextMessage(SIGNAL, text),
            )
        )
        //then
        verify(exactly = 1) { signalMessageSender.sendMessageToUser(friendshipId, text) }
    }

    @Test
    fun `adds history on text message`() {
        //given
        val sendMessageListener =
            SendMessageListener(
                signalMessageSender,
                responseGenerator,
                chatRepository,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        val text = "The text to send"
        val friendshipId = FriendshipId()
        val messageId = FibiMessageId()
        //when
        sendMessageListener.onMessageSendRequested(
            SendMessageCmd(
                this.javaClass,
                friendshipId,
                OutgoingTextMessage(SIGNAL, text, messageId = messageId),
            )
        )
        //then
        verify(exactly = 1) {
            chatRepository.add(
                friendshipId,
                OutgoingTextMessage(SIGNAL, text, messageId = messageId),
                text,
                any()
            )
        }
    }


    @Test
    fun `sends message on adapted text message`() {
        //given
        val sendMessageListener =
            SendMessageListener(
                signalMessageSender,
                responseGenerator,
                chatRepository,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        val message = OutgoingAdaptedTextMessage(SIGNAL, "description", "chat")
        val messageFromGenerator = "generated text"
        val friendshipId = FriendshipId()
        coEvery {
            responseGenerator.generateResponseWith(
                any(),
                any(OutgoingAdaptedTextMessage::class)
            )
        } returns messageFromGenerator
        //when
        sendMessageListener.onMessageSendRequested(
            SendMessageCmd(
                this.javaClass,
                friendshipId,
                message,
            )
        )
        //then
        verify(exactly = 1) { signalMessageSender.sendMessageToUser(friendshipId, messageFromGenerator) }
    }

    @Test
    fun `adds history on adapted text message`() {
        //given
        val sendMessageListener =
            SendMessageListener(
                signalMessageSender,
                responseGenerator,
                chatRepository,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        val message = OutgoingAdaptedTextMessage(SIGNAL, "description", "chat")
        val messageFromGenerator = "generated text"
        val friendshipId = FriendshipId()
        coEvery {
            responseGenerator.generateResponseWith(
                any(),
                any(OutgoingAdaptedTextMessage::class)
            )
        } returns messageFromGenerator
        //when
        sendMessageListener.onMessageSendRequested(
            SendMessageCmd(
                this.javaClass,
                friendshipId,
                message,
            )
        )
        //then
        verify(exactly = 1) { chatRepository.add(friendshipId, message, messageFromGenerator, any()) }
    }

    @Test
    fun `sends message on generated message`() {
        //given
        val sendMessageListener =
            SendMessageListener(
                signalMessageSender,
                responseGenerator,
                chatRepository,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        val message =
            OutgoingGeneratedMessage(SIGNAL, "description", useTaskActions = true, useFriendSettingActions = false)
        val messageFromGenerator = "generated text"
        val friendshipId = FriendshipId()
        coEvery {
            responseGenerator.generateResponseWith(
                any(),
                any(OutgoingGeneratedMessage::class)
            )
        } returns messageFromGenerator
        //when
        sendMessageListener.onMessageSendRequested(
            SendMessageCmd(
                this.javaClass,
                friendshipId,
                message,
            )
        )
        //then
        verify(exactly = 1) { signalMessageSender.sendMessageToUser(friendshipId, messageFromGenerator) }
    }

    @Test
    fun `adds history on generated message`() {
        //given
        val sendMessageListener =
            SendMessageListener(
                signalMessageSender,
                responseGenerator,
                chatRepository,
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        val message =
            OutgoingGeneratedMessage(SIGNAL, "description", useTaskActions = true, useFriendSettingActions = false)
        val messageFromGenerator = "generated text"
        val friendshipId = FriendshipId()
        coEvery {
            responseGenerator.generateResponseWith(
                any(),
                any(OutgoingGeneratedMessage::class)
            )
        } returns messageFromGenerator
        //when
        sendMessageListener.onMessageSendRequested(
            SendMessageCmd(
                this.javaClass,
                friendshipId,
                message,
            )
        )
        //then
        verify(exactly = 1) { chatRepository.add(friendshipId, message, messageFromGenerator, any()) }
    }
}