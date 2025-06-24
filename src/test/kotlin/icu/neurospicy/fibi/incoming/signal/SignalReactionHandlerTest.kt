package icu.neurospicy.fibi.incoming.signal

import icu.neurospicy.fibi.domain.model.SignalId
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.util.*

@ExtendWith(MockKExtension::class)
internal class SignalReactionHandlerTest {

    @Test
    fun reactToMessage_whenReactionIsReceived() {
        // Given
        val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val signalReactionHandler = SignalReactionHandler(applicationEventPublisher)
        val signalId = SignalId(UUID.randomUUID())
        val reaction = Reaction(
            emoji = "👍",
            targetAuthor = "+1337",
            targetSentTimestamp = 1738056239736,
            isRemove = false
        )

        // When
        signalReactionHandler.process("$signalId", reaction)
        // Then
        verify { applicationEventPublisher.publishEvent(any<Reaction>()) }
    }
}