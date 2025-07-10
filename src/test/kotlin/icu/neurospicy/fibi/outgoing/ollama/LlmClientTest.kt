package icu.neurospicy.fibi.outgoing.ollama

import icu.neurospicy.fibi.domain.service.friends.tools.SimpleCalendarTools
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import java.time.Instant
import java.time.ZoneOffset

class LlmClientTest {
    @Nested
    inner class PromptReceivingText {
        @Test
        fun `prompts with tools`() = runBlocking<Unit> {
            val chatClient = mockk<ChatClient>()
            val client = LlmClient(chatClient)
            val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
            every { chatClient.prompt(any<Prompt>()) } returns requestSpec
            val answer = "Answer of the llm"
            every { requestSpec.tools(any()) } returns requestSpec
            every { requestSpec.toolContext(any()) } returns mockk {
                every { call() } returns mockk { every { content() } returns answer }
            }
            val tools = setOf(SimpleCalendarTools())
            assertThat(
                client.promptReceivingText(
                    listOf(UserMessage("Some message to the llm")),
                    mockk(relaxed = true),
                    ZoneOffset.UTC,
                    Instant.now(),
                    retryConfig = RetryConfig(maxRetries = 0, failWithException = true),
                    tools = tools
                )
            ).isEqualTo(answer)
            verify { chatClient.prompt(any<Prompt>()) }
            verify(exactly = 1) { requestSpec.tools(*tools.toTypedArray()) }
        }

        @Test
        fun `does not apply empty tools`() = runBlocking<Unit> {
            val chatClient = mockk<ChatClient>()
            val client = LlmClient(chatClient)
            val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
            every { chatClient.prompt(any<Prompt>()) } returns requestSpec
            val answer = "Answer of the llm"
            every { requestSpec.toolContext(any()) } returns mockk {
                every { call() } returns mockk { every { content() } returns answer }
            }
            assertThat(
                client.promptReceivingText(
                    listOf(UserMessage("Some message to the llm")),
                    mockk(relaxed = true),
                    ZoneOffset.UTC,
                    Instant.now(),
                    retryConfig = RetryConfig(maxRetries = 0, failWithException = true),
                    tools = emptySet()
                )
            ).isEqualTo(answer)
            verify { chatClient.prompt(any<Prompt>()) }
            verify(exactly = 0) { requestSpec.tools(any()) }
        }
    }

    @Nested
    inner class PromptReceivingJson {
        @Test
        fun `prompts with tools`() = runBlocking<Unit> {
            val chatClient = mockk<ChatClient>()
            val client = LlmClient(chatClient)
            val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
            every { chatClient.prompt(any<Prompt>()) } returns requestSpec
            val answer = """{"key":"value"}"""
            every { requestSpec.tools(any()) } returns requestSpec
            every { requestSpec.toolContext(any()) } returns mockk {
                every { call() } returns mockk { every { content() } returns answer }
            }
            val tools = setOf(SimpleCalendarTools())
            assertThat(
                client.promptReceivingJson(
                    listOf(UserMessage("Some message to the llm")),
                    mockk(relaxed = true),
                    ZoneOffset.UTC,
                    Instant.now(),
                    retryConfig = RetryConfig(maxRetries = 0, failWithException = true),
                    tools = tools
                )
            ).isEqualTo(answer)
            verify { chatClient.prompt(any<Prompt>()) }
            verify(exactly = 1) { requestSpec.tools(*tools.toTypedArray()) }
        }

        @Test
        fun `does not apply empty tools`() = runBlocking<Unit> {
            val chatClient = mockk<ChatClient>()
            val client = LlmClient(chatClient)
            val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
            every { chatClient.prompt(any<Prompt>()) } returns requestSpec
            val answer = """{"key":"value"}"""
            every { requestSpec.toolContext(any()) } returns mockk {
                every { call() } returns mockk { every { content() } returns answer }
            }
            assertThat(
                client.promptReceivingJson(
                    listOf(UserMessage("Some message to the llm. Answer in JSON!")),
                    mockk(relaxed = true),
                    ZoneOffset.UTC,
                    Instant.now(),
                    retryConfig = RetryConfig(maxRetries = 0, failWithException = true),
                    tools = emptySet()
                )
            ).isEqualTo(answer)
            verify { chatClient.prompt(any<Prompt>()) }
            verify(exactly = 0) { requestSpec.tools(any()) }
        }
    }
}