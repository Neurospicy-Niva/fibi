package icu.neurospicy.fibi.incoming.signal

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.*
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.RelationStatus.*
import icu.neurospicy.fibi.domain.repository.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.codec.ServerSentEvent
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class SignalEventHandlerTest {
    @MockK
    private lateinit var objectMapper: ObjectMapper

    @MockK(relaxed = true)
    private lateinit var signalReactionHandler: SignalReactionHandler

    @MockK(relaxed = true)
    private lateinit var signalMessageHandler: SignalMessageHandler

    private lateinit var signalEventHandler: SignalEventHandler

    @BeforeEach
    fun setup() {
        signalEventHandler = SignalEventHandler(
            objectMapper,
            signalMessageHandler,
            signalReactionHandler
        )
    }

    @Test
    fun `do nothing when event data is invalid json`() {
        // Given
        val invalidJson = "{invalid: json}"
        val event: ServerSentEvent<*> = mockk {
            every { data() } returns invalidJson
        }
        every { objectMapper.readValue(invalidJson, SignalRpcEvent::class.java) } throws Exception("JSON parse error")
        // When
        signalEventHandler.process(event)
        // Then
        verify { objectMapper.readValue(invalidJson, SignalRpcEvent::class.java) }
        verify { listOf(signalMessageHandler, signalReactionHandler) wasNot Called }
    }

    @Test
    fun `do nothing on null input`() {
        // When
        signalEventHandler.process(null)
        // Then
        verify { listOf(signalMessageHandler, signalReactionHandler) wasNot Called }
    }

    @ParameterizedTest
    @MethodSource("non processable events")
    fun `do nothing on corrupt input`(eventRetval: Any?, rawDataString: String?, response: SignalRpcEvent?) {
        // Given
        val event = mockk<ServerSentEvent<*>> { every { data() } returns eventRetval }
        every { objectMapper.readValue(rawDataString, SignalRpcEvent::class.java) } returns response
        // When
        signalEventHandler.process(event)
        // Then
        verify { listOf(signalMessageHandler, signalReactionHandler) wasNot Called }
    }

    @Test
    fun `forwards message to message handler`() {
        // Given
        val signalId = SignalId(UUID.randomUUID())
        val rawDataString =
            """{"jsonrpc": "2.0","method": "receive","params": {"envelope": {"source": "$signalId","dataMessage": {"message": "Hello","timestamp": 1738056239736}}}"""
        val event: ServerSentEvent<*> = mockk {
            every { data() } returns rawDataString
        }
        every { objectMapper.readValue(rawDataString, SignalRpcEvent::class.java) } returns SignalRpcEvent(
            jsonrpc = "2.0",
            method = "receive",
            envelope = EnvelopeData(
                source = signalId.toString(),
                timestamp = 1738056239736,
                dataMessage = DataMessage(message = "Hello")
            )
        )
        // When
        signalEventHandler.process(event)
        // Then
        verify {
            signalMessageHandler.process(
                withArg {
                    assertEquals(signalId.toString(), it.source)
                    assertEquals(1738056239736, it.timestamp)
                    assertEquals(DataMessage(message = "Hello"), it.dataMessage)
                }, "Hello", rawDataString
            )
        }
    }

    @Test
    fun `forwards reaction to reaction handler`() {
        // Given
        val signalId = SignalId(UUID.randomUUID())
        val rawDataString =
            """{"jsonrpc": "2.0","method": "receive","params": {"envelope": {"source": "$signalId","reaction": {"emoji": "👍","targetAuthor": "+1337","targetSentTimestamp": 1738056239736,"isRemove": false},"timestamp": 1738056240000}}}"""
        val event: ServerSentEvent<*> = mockk {
            every { data() } returns rawDataString
        }
        every { objectMapper.readValue(rawDataString, SignalRpcEvent::class.java) } returns SignalRpcEvent(
            jsonrpc = "2.0",
            method = "receive",
            envelope = EnvelopeData(
                source = signalId.toString(),
                timestamp = 1738056240000,
                reaction = Reaction(
                    emoji = "👍",
                    targetAuthor = "+1337",
                    targetSentTimestamp = 1738056239736,
                    isRemove = false
                )
            )
        )
        // When
        signalEventHandler.process(event)
        // Then
        verify {
            signalReactionHandler.process(
                "$signalId", eq(
                    Reaction(
                        emoji = "👍",
                        targetAuthor = "+1337",
                        targetSentTimestamp = 1738056239736,
                        isRemove = false
                    )
                )
            )
        }
    }

    companion object {
        @JvmStatic
        fun `non processable events`() = listOf(
            Arguments.of(
                """{"jsonrpc": "2.0","method": "receive","params": {"envelope": {"source": null,"dataMessage": null}}}""",
                """{"jsonrpc": "2.0","method": "receive","params": {"envelope": {"source": null,"dataMessage": null}}}""",
                SignalRpcEvent(
                    jsonrpc = "2.0",
                    method = "receive",
                    envelope = EnvelopeData(
                        source = null,
                        dataMessage = null
                    )
                )
            ),
            Arguments.of(
                listOf(1, 2, 3),
                null,
                null
            ),
            Arguments.of(
                null,
                null,
                null
            )
        )
    }
}