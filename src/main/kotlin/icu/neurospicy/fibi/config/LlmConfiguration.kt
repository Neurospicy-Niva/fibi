package icu.neurospicy.fibi.config

import icu.neurospicy.fibi.domain.service.friends.tools.SimpleCalendarTools
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LlmConfiguration {

    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient {
        return ChatClient.builder(chatModel).defaultTools(SimpleCalendarTools())
            .build()
    }
}