package icu.neurospicy.fibi.outgoing.signal

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.LedgerEntry
import icu.neurospicy.fibi.domain.model.RelationStatus.Friend
import icu.neurospicy.fibi.domain.model.SignalId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate
import java.time.Instant.now
import java.util.*
import kotlin.random.Random.Default.nextInt
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@ExtendWith(MockKExtension::class)
internal class SignalMessageSenderTest {
    private val objectMapper = ObjectMapper()

    @MockK
    val restTemplate = mockk<RestTemplate>()

    @MockK
    val friendshipLedger = mockk<FriendshipLedger>()

    @BeforeEach
    fun postForEntitySendsRelaxedMock() {
        every {
            restTemplate.postForEntity(
                any<String>(),
                any<HttpEntity<String>>(),
                String::class.java
            )

        } returns mockk(relaxed = true)
    }

    @Test
    fun `sends message with style on event with markdown`() {
        //given
        val signalMessageSender =
            SignalMessageSender(friendshipLedger, restTemplate, objectMapper, "http://example.com")
        val friendshipId = FriendshipId()
        val signalNumber = "+${nextInt(99999999, 999999999)}"
        every { friendshipLedger.findBy(any<FriendshipId>()) } returns ledgerEntry(friendshipId, signalNumber)
        //when
        signalMessageSender.sendMessageToUser(
            friendshipId, "This is a **great** message."
        )
        //then
        //{"jsonrpc":"2.0","method":"send","params":{"recipient":"+123456","message":"Text","targetTimestamps":[1738407011]},"id":"5"}
        verify {
            restTemplate.postForEntity(any<String>(), withArg<HttpEntity<String>> {
                assertTrue(it.body!!.contains("send"))
                assertTrue(it.body!!.contains(signalNumber))
                assertTrue(it.body!!.contains("textStyles"))
            }, String::class.java)
        }
    }


    @Test
    fun `sends receipt confirmation on event`() {
        //given
        val signalMessageSender =
            SignalMessageSender(friendshipLedger, restTemplate, objectMapper, "http://example.com")
        val timestamp = now().epochSecond
        val friendshipId = FriendshipId()
        val signalNumber = "+${nextInt(99999999, 999999999)}"
        every { friendshipLedger.findBy(any<FriendshipId>()) } returns ledgerEntry(friendshipId, signalNumber)
        //when
        signalMessageSender.sendReceivedConfirmation(
            ConfirmSignalMessageReceived(
                friendshipId, SignalMessageId(
                    timestamp
                )
            )
        )
        //then
        //{"jsonrpc":"2.0","method":"sendReceipt","params":{"recipient":"+123456","targetTimestamps":[1738407011]},"id":"5"}
        verify {
            restTemplate.postForEntity(any<String>(), withArg<HttpEntity<String>> {
                assertTrue(it.body!!.contains("sendReceipt"))
                assertTrue(it.body!!.contains(signalNumber))
                assertTrue(it.body!!.contains(timestamp.toString()))
            }, String::class.java)
        }
    }

    @Test
    fun `sends typing`() {
        //given
        val signalMessageSender =
            SignalMessageSender(friendshipLedger, restTemplate, objectMapper, "http://example.com")
        val friendshipId = FriendshipId()
        val signalNumber = "+${nextInt(99999999, 999999999)}"
        every { friendshipLedger.findBy(any<FriendshipId>()) } returns ledgerEntry(friendshipId, signalNumber)
        //when
        signalMessageSender.sendTyping(friendshipId = friendshipId)
        //then
        //{"jsonrpc":"2.0","method":"sendTyping","params":{"recipients":["+123456"]},"id":"5"}
        verify {
            restTemplate.postForEntity(any<String>(), withArg<HttpEntity<String>> {
                val json = ObjectMapper().readTree(it.body)
                assertEquals("sendTyping", json.path("method").asText())
                assertContains(json.path("params").path("recipients").map { n -> n.asText() }, signalNumber)
                assertFalse(json.path("params").get("stop")?.asBoolean() ?: false)
            }, String::class.java)
        }
    }

    @Test
    fun `sends typing stopped`() {
        //given
        val signalMessageSender =
            SignalMessageSender(friendshipLedger, restTemplate, objectMapper, "http://example.com")
        val friendshipId = FriendshipId()
        val signalNumber = "+${nextInt(99999999, 999999999)}"
        every { friendshipLedger.findBy(any<FriendshipId>()) } returns ledgerEntry(friendshipId, signalNumber)
        //when
        signalMessageSender.sendStoppedTyping(friendshipId = friendshipId)
        //then
        //{"jsonrpc":"2.0","method":"sendTyping","params":{"recipients":["+123456"]},"id":"5"}
        verify {
            restTemplate.postForEntity(any<String>(), withArg<HttpEntity<String>> {
                val json = ObjectMapper().readTree(it.body)
                assertEquals("sendTyping", json.path("method").asText())
                assertContains(json.path("params").path("recipients").map { n -> n.asText() }, signalNumber)
                assertTrue(json.path("params").path("stop").asBoolean())
            }, String::class.java)
        }
    }

    @Test
    fun `send to mark emoji`() {
        //given
        val signalMessageSender =
            SignalMessageSender(friendshipLedger, restTemplate, objectMapper, "http://example.com")
        val friendshipId = FriendshipId()
        val signalNumber = "+${nextInt(99999999, 999999999)}"
        val emoji = "🧚"
        val messageId = SignalMessageId(now().epochSecond)
        every { friendshipLedger.findBy(any<FriendshipId>()) } returns ledgerEntry(friendshipId, signalNumber)
        //when
        signalMessageSender.markWithEmoji(friendshipId, messageId, emoji)
        //then
        //{"jsonrpc":"2.0","method":"sendReaction","params":{"recipients":["+123456"],"targetAuthor":"+3883838","targetTimestamp":8493981239,"emoji":"🧚"},"id":"5"}
        verify {
            restTemplate.postForEntity(any<String>(), withArg<HttpEntity<String>> {
                val json = ObjectMapper().readTree(it.body)
                assertEquals("sendReaction", json.path("method").asText())
                assertContains(json.path("params").path("recipients").map { n -> n.asText() }, signalNumber)
                assertEquals(emoji, json.path("params").path("emoji").asText())
                assertEquals(signalNumber, json.path("params").path("targetAuthor").asText())
                assertEquals(messageId.toLong(), json.path("params").path("targetTimestamp").asLong())
                assertFalse(json.path("params").get("remove")?.asBoolean() ?: false)
            }, String::class.java)
        }
    }

    @Test
    fun `sends to remove emoji mark`() {
        //given
        val signalMessageSender =
            SignalMessageSender(friendshipLedger, restTemplate, objectMapper, "http://example.com")
        val friendshipId = FriendshipId()
        val signalNumber = "+${nextInt(99999999, 999999999)}"
        every { friendshipLedger.findBy(any<FriendshipId>()) } returns ledgerEntry(friendshipId, signalNumber)
        val emoji = "🧚"
        val messageId = SignalMessageId(now().epochSecond)
        //when
        signalMessageSender.removeEmojiMarkFrom(friendshipId, messageId, emoji)
        //then
        //{"jsonrpc":"2.0","method":"sendReaction","params":{"recipients":["+123456"],"targetAuthor":"+3883838","targetTimestamp":8493981239,"emoji":"🧚","remove":true},"id":"5"}
        verify {
            restTemplate.postForEntity(any<String>(), withArg<HttpEntity<String>> {
                val json = ObjectMapper().readTree(it.body)
                assertEquals("sendReaction", json.path("method").asText())
                assertContains(json.path("params").path("recipients").map { n -> n.asText() }, signalNumber)
                assertEquals(emoji, json.path("params").path("emoji").asText())
                assertEquals(signalNumber, json.path("params").path("targetAuthor").asText())
                assertEquals(messageId.toLong(), json.path("params").path("targetTimestamp").asLong())
                assertTrue(json.path("params").path("remove").asBoolean())
            }, String::class.java)
        }
    }

    private fun ledgerEntry(
        friendshipId: FriendshipId,
        signalNumber: String
    ) = LedgerEntry(
        friendshipId = friendshipId,
        signalId = SignalId(UUID.randomUUID()),
        signalName = "Oscar",
        signalNumber = signalNumber,
        relationStatus = Friend
    )
}