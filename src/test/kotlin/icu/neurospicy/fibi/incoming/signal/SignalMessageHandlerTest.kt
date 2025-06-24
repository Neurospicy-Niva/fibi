package icu.neurospicy.fibi.incoming.signal

import icu.neurospicy.fibi.application.IncomingMessageMediator
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.RelationStatus.Friend
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.signal.ConfirmSignalMessageReceived
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.Instant.now
import java.util.*
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
internal class SignalMessageHandlerTest {
    @MockK(relaxed = true)
    lateinit var friendshipLedger: FriendshipLedger

    @MockK(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher
    lateinit var signalMessageHandler: SignalMessageHandler

    @BeforeEach
    fun setup() {
        signalMessageHandler = SignalMessageHandler(
            friendshipLedger, applicationEventPublisher
        )
    }

    @Test
    fun `publishes message when receiving friends message`() {
        // Given
        val signalId = SignalId(UUID.randomUUID())
        val friendshipId = FriendshipId()
        every { friendshipLedger.findBy(signalId) } returns LedgerEntry(
            "mongoid",
            friendshipId,
            signalId,
            relationStatus = Friend
        )
        val rawMessage = "raw message"
        // When
        signalMessageHandler.process(
            EnvelopeData(
                source = signalId.toString(),
                timestamp = 1738056239736,
                dataMessage = DataMessage(message = "Hello")
            ), "Hello", rawMessage
        )
        // Then
        verify { applicationEventPublisher.publishEvent(any<UserMessage>()) }
    }

    @Test
    fun `confirms receipt on new message`() {
        // Given
        val signalId = SignalId(UUID.randomUUID())
        val friendshipId = FriendshipId()
        every { friendshipLedger.findBy(signalId) } returns LedgerEntry(
            "mongoid",
            friendshipId,
            signalId,
            relationStatus = Friend
        )
        val rawMessage = "raw message"
        // When
        val timestamp = now().epochSecond
        signalMessageHandler.process(
            EnvelopeData(
                source = signalId.toString(),
                timestamp = timestamp,
                dataMessage = DataMessage(message = "Hello")
            ), "Hello", rawMessage
        )
        // Then
        verify {
            applicationEventPublisher.publishEvent(
                ConfirmSignalMessageReceived(
                    friendshipId,
                    SignalMessageId(timestamp)
                )
            )
        }
    }
}