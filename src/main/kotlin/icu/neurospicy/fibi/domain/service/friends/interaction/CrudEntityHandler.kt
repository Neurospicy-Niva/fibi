package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import java.time.Instant
import java.time.ZoneId

interface CrudEntityHandler<E, F> {
    suspend fun extractEntityData(
        rawText: String,
        previousData: E?,
        clarificationQuestion: String? = null,
        answer: String? = null,
        friendshipId: FriendshipId,
        timezone: ZoneId,
        messageTime: Instant,
        messageId: MessageId?,
        channel: Channel?,
    ): ExtractionResult<E>

    suspend fun identifyEntityId(
        allEntities: List<F>,
        rawText: String,
        clarificationQuestion: String? = null,
        answer: String? = null,
        friendshipId: FriendshipId,
        timezone: ZoneId,
        messageTime: Instant,
        messageId: MessageId?,
        channel: Channel?,
    ): IdResolutionResult
}

data class ExtractionResult<E>(
    val data: E? = null,
    val missingFields: List<String> = emptyList(),
    val clarifyingQuestion: String? = null,
    val responseMessage: String? = null,
) {
    val isComplete: Boolean get() = data != null && missingFields.isEmpty() && clarifyingQuestion == null
}

sealed interface IdResolutionResult {
    val id: String?
    val clarifyingQuestion: String?
    val needsClarification: Boolean
}

data class ClarifiedIdResolutionResult(
    override val id: String? = null,
    override val clarifyingQuestion: String? = null,
) : IdResolutionResult {
    override val needsClarification: Boolean get() = clarifyingQuestion != null || id == null
}

data class NoActionResolutionResult(
    override val id: String? = null,
) : IdResolutionResult {
    override val clarifyingQuestion: String? get() = null
    override val needsClarification: Boolean get() = false
}