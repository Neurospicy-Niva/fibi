package icu.neurospicy.fibi.domain.service.friends.interaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.model.events.IncomingFriendMessageReceived
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class IntentClassifierTest {

    @MockK
    private lateinit var llmClient: LlmClient

    @MockK
    private lateinit var intentRegistry: IntentRegistry

    @MockK
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        objectMapper = jacksonObjectMapper()

        intentClassifier = IntentClassifier(llmClient, intentRegistry, objectMapper, "fibi64", "fibi64")
    }

    @Test
    fun `should classify single intent with high confidence`() = runBlocking {
        // Given
        val message = "Please add 'buy milk' to my tasks"
        val expectedIntent = Intent("AddTask")
        val expectedJson = """[{"intent": "AddTask", "confidence": 0.95}]"""

        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns expectedJson
        coEvery { llmClient.promptReceivingText(any(), any(), any(), any()) } returns "no"

        every { intentRegistry.contains(expectedIntent) } returns true
        every { intentRegistry.getDescriptions() } returns mapOf(
            expectedIntent to "Add a new task to the user's task list"
        )
        every { intentRegistry.getAll() } returns listOf(expectedIntent)

        // When
        val result = intentClassifier.classifyIntent(createIncomingMessage(message))

        // Then
        assertEquals(result.size, 1)
        assertEquals(expectedIntent, result[0].intent)
        assertEquals(0.95f, result[0].confidence)
    }

    @Test
    fun `should classify multiple intents`() = runBlocking {
        // Given
        val message = "Add 'buy milk' and show my tasks"
        val addTaskIntent = Intent("AddTask")
        val listTasksIntent = Intent("ListTasks")
        val expectedJson = """[
            {"intent": "AddTask", "confidence": 0.95},
            {"intent": "ListTasks", "confidence": 0.85}
        ]"""

        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns expectedJson
        coEvery { llmClient.promptReceivingText(any(), any(), any(), any()) } returns "no"

        every { intentRegistry.contains(addTaskIntent) } returns true
        every { intentRegistry.contains(listTasksIntent) } returns true
        every { intentRegistry.getDescriptions() } returns mapOf(
            addTaskIntent to "Add a new task to the user's task list", listTasksIntent to "Show the user's task list"
        )
        every { intentRegistry.getAll() } returns listOf(addTaskIntent, listTasksIntent)

        // When
        val result = intentClassifier.classifyIntent(createIncomingMessage(message))

        // Then
        assertThat(result).anySatisfy {
            assertThat(it.intent).isEqualTo(addTaskIntent)
            assertThat(it.confidence).isEqualTo(0.95f)
        }
        assertThat(result).anySatisfy {
            assertThat(it.intent).isEqualTo(listTasksIntent)
            assertThat(it.confidence).isEqualTo(0.85f)
        }
        assertEquals(2, result.size)
    }

    @Test
    fun `should return Unknown intent for invalid response`() = runBlocking {
        // Given
        val message = "Some message"

        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns "This is not a json response!"
        coEvery { llmClient.promptReceivingText(any(), any(), any(), any()) } returns "no"

        every { intentRegistry.getDescriptions() } returns emptyMap()

        // When
        val result = intentClassifier.classifyIntent(createIncomingMessage(message))

        // Then
        assertThat(result).anySatisfy {
            assertThat(it.intent).isEqualTo(CoreIntents.Unknown)
            assertThat(it.confidence).isEqualTo(0f)
        }
        assertEquals(1, result.size)
    }

    @Test
    fun `should return Unknown intent for failing llm`() = runBlocking {
        // Given
        val message = "Some message"

        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } throws Exception("Some error")
        coEvery { llmClient.promptReceivingText(any(), any(), any(), any()) } returns "no"

        every { intentRegistry.getDescriptions() } returns emptyMap()

        // When
        val result = intentClassifier.classifyIntent(createIncomingMessage(message))

        // Then
        assertThat(result).anySatisfy {
            assertThat(it.intent).isEqualTo(CoreIntents.Unknown)
            assertThat(it.confidence).isEqualTo(0f)
        }
        assertEquals(1, result.size)
    }

    private fun createIncomingMessage(text: String): IncomingFriendMessageReceived = IncomingFriendMessageReceived(
        FriendshipId("test-friendship"), UserMessage(
            SignalMessageId(Instant.now().epochSecond), text = text, channel = Channel.SIGNAL
        )
    )
} 