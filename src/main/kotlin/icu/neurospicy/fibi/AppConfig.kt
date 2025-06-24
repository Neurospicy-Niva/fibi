package icu.neurospicy.fibi

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import icu.neurospicy.fibi.outgoing.mongodb.converters.*
import org.apache.camel.CamelContext
import org.apache.camel.ProducerTemplate
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.client.RestTemplate


@Configuration
@EnableAsync
class AppConfig {
    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    @Bean
    fun producerTemplate(camelContext: CamelContext): ProducerTemplate {
        return camelContext.createProducerTemplate()
    }

    @Bean
    fun converters(): MongoCustomConversions {
        return MongoCustomConversions(
            listOf(
                DocumentToZonedDateTimeConverter(),
                ZonedDateTimeToDocumentConverter(),
                DocumentToOffsetDateTimeConverter(),
                OffsetDateTimeToDocumentConverter(),
                DocumentToTemporalConverter(),
                DateToTemporalConverter()
            )
        )
    }

    @Bean
    fun jackson2ObjectMapperBuilder(
        applicationContext: ApplicationContext,
        customizers: List<Jackson2ObjectMapperBuilderCustomizer>
    ): Jackson2ObjectMapperBuilder {
        val builder =
            Jackson2ObjectMapperBuilder().modules(JavaTimeModule()).modules(kotlinModule())
                .findModulesViaServiceLoader(true)
        customizers.forEach { it.customize(builder) }
        return builder.applicationContext(applicationContext)
    }
}