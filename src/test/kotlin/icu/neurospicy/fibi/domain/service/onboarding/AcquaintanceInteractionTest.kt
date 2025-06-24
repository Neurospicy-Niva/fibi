package icu.neurospicy.fibi.domain.service.onboarding

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.events.IncomingAcquaintanceMessageReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.ollama.IntentRecognizer
import icu.neurospicy.fibi.outgoing.ollama.PossibleIntent
import icu.neurospicy.fibi.outgoing.ollama.Result
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant.now
import java.util.*


@ExtendWith(MockKExtension::class)
@SpringBootTest(
    classes = [
        PromptsConfiguration::class,
    ]
)
internal class AcquaintanceInteractionTest {

    @MockK(relaxed = true)
    lateinit var intentRecognizer: IntentRecognizer

    @MockK(relaxed = true)
    lateinit var chatRepository: ChatRepository

    @MockK(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockK(relaxed = true)
    lateinit var friendshipLedger: FriendshipLedger

    @Autowired
    lateinit var promptsConfiguration: PromptsConfiguration

    @ParameterizedTest(name = "sends {1} when {0} is recognized")
    @MethodSource("intent to message")
    fun `sends {message} when {intent} is recognized`(intent: String, message: String) = runBlocking {
        //given
        val acquaintanceInteraction = AcquaintanceInteraction(
            intentRecognizer, chatRepository, applicationEventPublisher, friendshipLedger, promptsConfiguration
        )
        val friendshipId = FriendshipId()
        val incomingMessage = UserMessage(
            text = "Yes or No or Maybe", messageId = SignalMessageId(now().epochSecond), channel = SIGNAL
        )
        every { chatRepository.findHistory(friendshipId) } returns ChatHistory(null, friendshipId, Stack())
        coEvery {
            intentRecognizer.recognize(
                any(), incomingMessage, any(), any(), true
            )
        } answers { Result(possibleIntentsFromCall(it).find { pi -> pi.name == intent } ?: fail()) }
        //when
        acquaintanceInteraction.onMessageSendRequested(
            IncomingAcquaintanceMessageReceived(
                friendshipId, incomingMessage
            )
        )
        //then
        verify {
            applicationEventPublisher.publishEvent(withArg<SendMessageCmd> {
                assertEquals(friendshipId, it.friendshipId)
                assertTrue(it.outgoingMessage is OutgoingTextMessage)
            })
        }
    }

    @ParameterizedTest(name = "sends message generated with {1} when {0} is recognized")
    @MethodSource("intent to description")
    fun `sends message generated with {description} when {intent} is recognized`(intent: String, description: String) =
        runBlocking {
            //given
            val acquaintanceInteraction = AcquaintanceInteraction(
                intentRecognizer, chatRepository, applicationEventPublisher, friendshipLedger, promptsConfiguration
            )
            val friendshipId = FriendshipId()
            val incomingMessage =
                UserMessage(text = "Some question", messageId = SignalMessageId(now().epochSecond), channel = SIGNAL)
            every { chatRepository.findHistory(friendshipId) } returns ChatHistory(null, friendshipId, Stack())
            coEvery {
                intentRecognizer.recognize(
                    any(), incomingMessage, any<Set<PossibleIntent>>(), any(), true
                )
            } answers { Result(possibleIntentsFromCall(it).find { pi -> pi.name == intent } ?: fail()) }
            //when
            acquaintanceInteraction.onMessageSendRequested(
                IncomingAcquaintanceMessageReceived(
                    friendshipId, incomingMessage
                )
            )
            //then
            verify {
                applicationEventPublisher.publishEvent(withArg<SendMessageCmd> {
                    assertEquals(friendshipId, it.friendshipId)
                    assertTrue(it.outgoingMessage is OutgoingGeneratedMessage)
                })
            }
        }

    @ParameterizedTest(name = "intent: {0}")
    @MethodSource("confirming tos intents")
    fun `sets as friend when confirmed TOS`(confirmingIntent: String) = runBlocking {
        //given
        val acquaintanceInteraction = AcquaintanceInteraction(
            intentRecognizer, chatRepository, applicationEventPublisher, friendshipLedger, promptsConfiguration
        )
        val friendshipId = FriendshipId()
        val incomingMessage =
            UserMessage(text = "Yes", messageId = SignalMessageId(now().epochSecond), channel = SIGNAL)
        every { chatRepository.findHistory(friendshipId) } returns ChatHistory(null, friendshipId, Stack())
        coEvery { intentRecognizer.recognize(any(), incomingMessage, any(), any(), true) } answers {
            Result(possibleIntentsFromCall(it).find { pi -> pi.name == confirmingIntent } ?: fail())
        }
        //when
        acquaintanceInteraction.onMessageSendRequested(
            IncomingAcquaintanceMessageReceived(
                friendshipId, incomingMessage
            )
        )
        //then
        verify { friendshipLedger.acceptTermsOfUse(friendshipId, any()) }
    }

    companion object {
        @JvmStatic
        fun `intent to message`() = listOf(
            Arguments.of("Confirms TOS", "Fantastic! I’m excited to help you."),
            Arguments.of("Likely confirms TOS", "Fantastic! I’m excited to help you."),
            Arguments.of("Denies TOS", "I won't store any of your personal data")
        )

        @JvmStatic
        fun `intent to description`() = listOf(
            Arguments.of(
                "Inform about features", "Ask the user to confirm the terms of service (TOS), e.g., by saying 'Yes'"
            ), Arguments.of(
                "Inform about TOS", "Ask the user to confirm the terms of service (TOS), e.g., by saying 'Yes'"
            ), Arguments.of(
                "Inform about fibi", "Ask the user to confirm the terms of service (TOS), e.g., by saying 'Yes'"
            ),
            Arguments.of(
                "Other/Functionality",
                "Ask the user to confirm the terms of service (TOS), e.g., by saying 'Yes'"
            )
        )

        @JvmStatic
        fun `confirming tos intents`() = listOf(
            Arguments.of("Confirms TOS"), Arguments.of("Likely confirms TOS")
        )
    }

    @Test
    fun `deletes history and flags user when denying TOS`() = runBlocking {
        //given
        val acquaintanceInteraction = AcquaintanceInteraction(
            intentRecognizer, chatRepository, applicationEventPublisher, friendshipLedger, mockk(relaxed = true)
        )
        val friendshipId = FriendshipId()
        val incomingMessage =
            UserMessage(text = "Yes", messageId = SignalMessageId(now().epochSecond), channel = SIGNAL)
        every { chatRepository.findHistory(friendshipId) } returns ChatHistory(null, friendshipId, Stack())
        coEvery { intentRecognizer.recognize(any(), incomingMessage, any(), any(), true) } answers {
            Result(possibleIntentsFromCall(it).find { pi -> pi.name == "Denies TOS" } ?: fail())
        }
        //when
        acquaintanceInteraction.onMessageSendRequested(
            IncomingAcquaintanceMessageReceived(
                friendshipId, incomingMessage
            )
        )
        //then
        verify { friendshipLedger.deniedTermsOfUse(friendshipId) }
        verify { chatRepository.applyDeletionRequest(friendshipId) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("intent to description")
    fun `stays acquaintance when asking questions`(intent: String) = runBlocking {
        //given
        val acquaintanceInteraction = AcquaintanceInteraction(
            intentRecognizer, chatRepository, applicationEventPublisher, friendshipLedger, mockk(relaxed = true)
        )
        val friendshipId = FriendshipId()
        val incomingMessage =
            UserMessage(text = "Some question", messageId = SignalMessageId(now().epochSecond), channel = SIGNAL)
        every { chatRepository.findHistory(friendshipId) } returns ChatHistory(null, friendshipId, Stack())
        coEvery {
            intentRecognizer.recognize(
                any(), incomingMessage, any(), any(), true
            )
        } answers { Result(possibleIntentsFromCall(it).find { pi -> pi.name == intent } ?: fail()) }
        //when
        acquaintanceInteraction.onMessageSendRequested(
            IncomingAcquaintanceMessageReceived(
                friendshipId, incomingMessage
            )
        )
        //then
        verify { friendshipLedger wasNot Called }
    }

    private fun possibleIntentsFromCall(it: Call): List<PossibleIntent> =
        (it.invocation.args[2] as Set<*>).filterIsInstance<PossibleIntent>()
}