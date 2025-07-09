package icu.neurospicy.fibi.config

import icu.neurospicy.fibi.domain.service.friends.tools.SimpleCalendarTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LlmConfiguration {

    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient {
        return ChatClient.builder(chatModel).defaultTools(SimpleCalendarTools())
            .build()
    }

    @Bean
    @ConfigurationProperties(prefix = "fibi.llm")
    fun llmProperties(): LlmProperties {
        return LlmProperties()
    }

    @Bean
    fun defaultModel(llmProperties: LlmProperties): String {
        return llmProperties.defaultModel
    }

    @Bean
    fun complexTaskModel(llmProperties: LlmProperties): String {
        return llmProperties.complexTaskModel
    }

    @Bean
    fun messageGenerationModel(llmProperties: LlmProperties): String {
        return llmProperties.messageGenerationModel
    }
}

data class LlmProperties(
    var defaultModel: String = "[MODEL_NAME]",
    var complexTaskModel: String = "[MODEL_NAME]",
    var messageGenerationModel: String = "[MODEL_NAME]"
)