package icu.neurospicy.fibi.outgoing.ollama

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.model.events.IntentRecognitionFinished
import icu.neurospicy.fibi.domain.model.events.IntentRecognitionStarted
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant.now

@ExtendWith(MockKExtension::class)
internal class IntentRecognizerTest {

    @MockK
    lateinit var llmClient: LlmClient

    @MockK
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    private val possibleIntents = setOf(
        PossibleIntent("Greet", "The user greets"),
        PossibleIntent("Say goodbye", "The user says goodbye"),
        PossibleIntent("Other", "Nothing of the others.")
    )

    @BeforeEach
    fun setup() {
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns """{"intent":"Greet"}"""
    }

    @Test
    fun test() = runBlocking {
        val intentRecognizer = IntentRecognizer(
            llmClient,
            applicationEventPublisher,
            mockk(relaxed = true),
            ObjectMapper().registerModule(KotlinModule.Builder().build()),
            mockk(relaxed = true),
            mockk(relaxed = true), "fibi64"
        )
        every { applicationEventPublisher.publishEvent(any<IntentRecognitionStarted>()) } returns Unit
        every { applicationEventPublisher.publishEvent(any<IntentRecognitionFinished>()) } returns Unit
        val friendshipId = FriendshipId()
        //when
        val messageId = SignalMessageId(now().epochSecond)
        intentRecognizer.recognize(
            friendshipId, UserMessage(messageId, text = "Howdy", channel = SIGNAL), possibleIntents, useTools = true
        )
        //then
        verify {
            applicationEventPublisher.publishEvent(
                IntentRecognitionStarted(
                    intentRecognizer.javaClass, friendshipId, SIGNAL, messageId
                )
            )
        }
        verify {
            applicationEventPublisher.publishEvent(
                IntentRecognitionFinished(
                    intentRecognizer.javaClass,
                    friendshipId,
                    SIGNAL,
                    messageId,
                    possibleIntents.first { it.name == "Greet" }, possibleIntents.first { it.name == "Greet" }.emoji
                )
            )
        }
    }
}