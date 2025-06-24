package icu.neurospicy.fibi.outgoing.signal

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import kotlin.random.Random

/**
 * Responsible for sending messages out to the signal-cli JSON-RPC endpoint.
 * "Technical" concern: how to format the JSON, which URL, etc.
 */
@Service
class SignalMessageSender(
    private val friendshipLedger: FriendshipLedger,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,

    @Value("\${signal-cli.api-url}")
    private val signalApiUrl: String
) {


    fun sendMessageToUser(friendshipId: FriendshipId, text: String) {
        LOG.info("Sending message '{}' to '{}'", text, friendshipId)

        val friend = friendshipLedger.findBy(friendshipId)!!
        val recipient = friend.signalNumber ?: friend.signalId.toString()
        val signalMessage = SignalMessage.from(text)
        val params = mutableMapOf<String, Any>(
            "recipient" to recipient,
            "message" to signalMessage.text,
        )
        if (signalMessage.textStyles.isNotEmpty()) params["textStyles"] = signalMessage.textStyles
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "send",
            "params" to params,
            "id" to 1
        )

        sendJsonRpc(jsonRpcBody)
    }

    @EventListener
    @Async
    fun sendReceivedConfirmation(event: ConfirmSignalMessageReceived) {
        LOG.debug("Sending receive confirmation for message '{}' of friend '{}'", event.messageId, event.friendshipId)
        val friend = friendshipLedger.findBy(event.friendshipId)!!
        val recipient = friend.signalNumber ?: friend.signalId.toString()
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "sendReceipt",
            "params" to mapOf(
                "recipient" to recipient,
                "targetTimestamps" to listOf(event.messageId.toLong())
            ),
            "id" to 1
        )

        sendJsonRpc(jsonRpcBody)
    }


    fun sendTyping(friendshipId: FriendshipId) {
        LOG.debug("Sending typing notification to friend '{}'", friendshipId)
        val friend = friendshipLedger.findBy(friendshipId)!!
        val recipient = friend.signalNumber ?: friend.signalId.toString()
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "sendTyping",
            "params" to mapOf(
                "recipients" to listOf(recipient),
            ),
            "id" to 1
        )

        sendJsonRpc(jsonRpcBody)
    }

    fun sendStoppedTyping(friendshipId: FriendshipId) {
        LOG.debug("Sending typing stopped notification to friend '{}'", friendshipId)
        val friend = friendshipLedger.findBy(friendshipId)!!
        val recipient = friend.signalNumber ?: friend.signalId.toString()
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "sendTyping",
            "params" to mapOf(
                "recipients" to listOf(recipient),
                "stop" to true
            ),
            "id" to 1
        )

        sendJsonRpc(jsonRpcBody)
    }

    fun sendProfileUpdate(givenName: String, familyName: String, description: String, avatar: String?) {
        LOG.info("Sending profile update.")
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "updateProfile",
            "params" to mapOf(
                "givenName" to givenName,
                "familyName" to familyName,
                "description" to description,
                if (avatar.isNullOrBlank()) "removeAvatar" to true else "avatar" to avatar
            ),
            "id" to Random.nextInt(9999999)
        )

        sendJsonRpc(jsonRpcBody)
    }

    private fun sendJsonRpc(jsonRpcBody: Map<String, Any>) {
        val requestBody = objectMapper.writeValueAsString(jsonRpcBody)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val request = HttpEntity<String>(requestBody, headers)

        // Usually, /json-rpc is the endpoint
        val url = "$signalApiUrl/rpc"
        val response = restTemplate.postForEntity(url, request, String::class.java)
        if (response.statusCode != HttpStatus.OK) {
            LOG.debug("Response from signal-cli: status={} body={}", response.statusCode, response.body)
        }
    }

    fun markWithEmoji(friendshipId: FriendshipId, signalMessageId: SignalMessageId, emoji: String) {
        LOG.info("Sending to mark message {} with emoji {}.", signalMessageId, emoji)
        val friend = friendshipLedger.findBy(friendshipId)!!
        val recipient = friend.signalNumber ?: friend.signalId.toString()
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "sendReaction",
            "params" to mapOf(
                "recipients" to listOf(recipient),
                "targetAuthor" to recipient,
                "targetTimestamp" to signalMessageId.toLong(),
                "emoji" to emoji,
                "remove" to false
            ),
            "id" to Random.nextInt(9999999)
        )

        sendJsonRpc(jsonRpcBody)
    }

    fun removeEmojiMarkFrom(friendshipId: FriendshipId, signalMessageId: SignalMessageId, emoji: String) {
        LOG.debug("Sending to mark message {} with emoji {}.", signalMessageId, emoji)
        val friend = friendshipLedger.findBy(friendshipId)!!
        val recipient = friend.signalNumber ?: friend.signalId.toString()
        val jsonRpcBody = mapOf(
            "jsonrpc" to "2.0",
            "method" to "sendReaction",
            "params" to mapOf(
                "recipients" to listOf(recipient),
                "targetAuthor" to recipient,
                "targetTimestamp" to signalMessageId.toLong(),
                "emoji" to emoji,
                "remove" to true
            ),
            "id" to Random.nextInt(9999999)
        )

        sendJsonRpc(jsonRpcBody)

    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SignalMessageSender::class.java)
    }
}