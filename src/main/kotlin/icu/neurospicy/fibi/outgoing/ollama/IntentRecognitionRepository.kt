package icu.neurospicy.fibi.outgoing.ollama

import icu.neurospicy.fibi.domain.model.MessageId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.Instant.now

@Repository
class IntentRecognitionRepository(
    private val mongoTemplate: MongoTemplate
) {
    fun recognized(messageId: MessageId, text: String, intent: String, likelyOtherIntents: List<String>, model: String) {
        mongoTemplate.save(RecognizedIntent(messageId, text, intent, likelyOtherIntents, model, now()))
    }
}

@Document(collection = "recognized-intents")
data class RecognizedIntent(
    val messageId: MessageId,
    val text: String,
    val intent: String,
    val likelyOtherIntents: List<String>,
    val model: String,
    val createdAt: Instant
)