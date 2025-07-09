package icu.neurospicy.fibi.outgoing.ollama

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.events.MessageGenerationFinished
import icu.neurospicy.fibi.domain.model.events.MessageGenerationStarted
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant.now
import java.util.*

@ExtendWith(MockKExtension::class)
internal class ResponseGeneratorTest {

    @MockK(relaxed = true)
    lateinit var llmClient: LlmClient

    @MockK
    lateinit var chatRepository: ChatRepository

    @MockK
    lateinit var friendshipLedger: FriendshipLedger

    @MockK
    lateinit var applicationEventPublisher: ApplicationEventPublisher
    private var friendshipId: FriendshipId = FriendshipId()

    @BeforeEach
    fun setup() {
        friendshipId = FriendshipId()
        every { chatRepository.find(friendshipId, any()) } returns UserMessage(
            SignalMessageId(234234),
            now(),
            "Hi",
            SIGNAL,
            ""
        )
        every { chatRepository.findHistory(any()) } returns ChatHistory(
            UUID.randomUUID().toString(),
            friendshipId,
            Stack()
        )
        coEvery { llmClient.promptReceivingText(any(), any(), any(), any(), tools = any()) } returns "Generated message"
    }

    @Test
    fun `publishes message generation events when adapting message`() = runBlocking {
        val responseGenerator = ResponseGenerator(
            llmClient, chatRepository, applicationEventPublisher, friendshipLedger,
            mockk(), mockk(), mockk(relaxed = true), mockk(), mockk(relaxed = true), "fibi64"
        )
        val message = OutgoingGeneratedMessage(SIGNAL, "Answer like an elephant talking to a 5 year old hot dog")
        every { applicationEventPublisher.publishEvent(any<MessageGenerationStarted>()) } just runs
        every { applicationEventPublisher.publishEvent(any<MessageGenerationFinished>()) } just runs
        every { friendshipLedger.findBy(friendshipId) } returns mockk(relaxed = true)
        val messageId = SignalMessageId(now().epochSecond)
        //when
        responseGenerator.generateResponseWith(friendshipId, message, messageId)
        //then
        verify {
            applicationEventPublisher.publishEvent(
                MessageGenerationStarted(
                    responseGenerator.javaClass,
                    friendshipId,
                    SIGNAL,
                    messageId
                )
            )
        }
        verify {
            applicationEventPublisher.publishEvent(
                MessageGenerationFinished(
                    responseGenerator.javaClass,
                    friendshipId,
                    SIGNAL,
                    messageId
                )
            )
        }
    }

    @Test
    fun `publishes message generation events when generating message`() = runBlocking {
        val responseGenerator =
            ResponseGenerator(
                llmClient,
                chatRepository,
                applicationEventPublisher,
                friendshipLedger,
                mockk(),
                mockk(),
                mockk(relaxed = true),
                mockk(),
                mockk(relaxed = true), "fibi64"
            )
        val message =
            OutgoingAdaptedTextMessage(SIGNAL, "Don't be shy. Shout the message out like a dinosaur!", "Hi")
        every { applicationEventPublisher.publishEvent(any<MessageGenerationStarted>()) } just runs
        every { applicationEventPublisher.publishEvent(any<MessageGenerationFinished>()) } just runs
        every { friendshipLedger.findBy(friendshipId) } returns mockk(relaxed = true)
        val messageId = SignalMessageId(now().epochSecond)
        //when
        responseGenerator.generateResponseWith(friendshipId, message, messageId)
        //then
        verify {
            applicationEventPublisher.publishEvent(
                MessageGenerationStarted(
                    responseGenerator.javaClass,
                    friendshipId,
                    SIGNAL,
                    messageId
                )
            )
        }
        verify {
            applicationEventPublisher.publishEvent(
                MessageGenerationFinished(
                    responseGenerator.javaClass,
                    friendshipId,
                    SIGNAL,
                    messageId
                )
            )
        }
    }
}