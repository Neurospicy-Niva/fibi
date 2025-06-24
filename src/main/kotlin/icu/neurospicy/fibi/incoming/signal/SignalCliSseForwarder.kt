package icu.neurospicy.fibi.incoming.signal

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class SignalCliSseForwarder(
    private val signalEventHandler: SignalEventHandler
) {
    @Value("\${signal-cli.api-url}")
    private lateinit var signalApiUrl: String

    @PostConstruct
    private fun init() {
        val client = WebClient.create(signalApiUrl)
        val eventStream = client.get()
            .uri("/events")
            .retrieve()
            .bodyToFlux(ServerSentEvent::class.java)

        eventStream.subscribe(
            { event -> processNewMessage(event) },
            { error -> LOG.error("Error receiving SSE: $error", error) }
        )
        LOG.info("Connected to signal-cli SSE endpoint")
    }

    private fun processNewMessage(event: ServerSentEvent<*>?) {
        LOG.debug("Received new signal event: {}", event)
        signalEventHandler.process(event)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SignalCliSseForwarder::class.java)
    }
}