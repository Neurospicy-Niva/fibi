package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset

abstract class CrudSubtaskHandler<E, F>(
    private val intent: Intent,
    private val entityHandler: CrudEntityHandler<E, F>,
    private val friendshipLedger: FriendshipLedger,
) : SubtaskHandler {

    override fun canHandle(intent: Intent): Boolean = intent == this.intent

    override suspend fun handle(subtask: Subtask, context: GoalContext, friendshipId: FriendshipId): SubtaskResult {
        return coroutineScope {
            LOG.info("Processing subtask ${subtask.intent}")
            val rawText = subtask.parameters["rawText"] as? String
                ?: return@coroutineScope SubtaskResult.failure("Missing rawText", subtask)
            val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
            val messageTime = context.originalMessage?.receivedAt ?: Instant.now()

            val allEntities = loadEntities(friendshipId)
            val previousData = getPreviousData(subtask)

            val idResultAsync = async {
                entityHandler.identifyEntityId(
                    allEntities, rawText, friendshipId = friendshipId, timezone = timezone, messageTime = messageTime,
                    messageId = context.originalMessage?.messageId, channel = context.originalMessage?.channel
                )
            }
            val dataResultAsync = async {
                entityHandler.extractEntityData(
                    rawText, previousData, friendshipId = friendshipId, timezone = timezone, messageTime = messageTime,
                    messageId = context.originalMessage?.messageId, channel = context.originalMessage?.channel
                )
            }
            val dataResult = dataResultAsync.await()
            val idResult = idResultAsync.await()
            val updatedParams = subtask.parameters + mapOf(
                "id" to idResult.id, "entityData" to dataResult.data
            ).filterValues { it != null }

            if (!dataResult.isComplete || idResult.needsClarification) {
                return@coroutineScope SubtaskResult.needsClarification(
                    subtask.copy(parameters = updatedParams),
                    buildClarificationQuestion(
                        idResult, dataResult
                    ),
                    successMessagePrompt = dataResult.responseMessage?.let { dataResult.responseMessage }
                )
            }

            applyUpdate(friendshipId, idResult.id, dataResult.data!!)
            SubtaskResult.success(
                updatedSubtask = subtask,
                responseGenerationPrompt = dataResult.responseMessage?.let { """Tell the user the intent ${subtask.intent} succeeded with result: ${dataResult.responseMessage}""" })
        }
    }

    private fun getPreviousData(subtask: Subtask): E? = try {
        @Suppress("UNCHECKED_CAST")
        subtask.parameters["entityData"] as? E
    } catch (e: ClassCastException) {
        null
    }

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        return coroutineScope {
            val rawText = subtask.parameters["rawText"] as? String
                ?: return@coroutineScope SubtaskClarificationResult.failure("Missing rawText", subtask)
            val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC
            val messageTime = context.originalMessage?.receivedAt ?: Instant.now()

            val allEntities = loadEntities(friendshipId)
            val previousData = getPreviousData(subtask)

            val idResultAsync = async {
                entityHandler.identifyEntityId(
                    allEntities,
                    rawText,
                    clarificationQuestion.text,
                    answer.text,
                    friendshipId,
                    timezone = timezone,
                    messageTime = messageTime,
                    messageId = context.originalMessage?.messageId,
                    channel = context.originalMessage?.channel
                )
            }
            val dataResultAsync = async {
                entityHandler.extractEntityData(
                    rawText,
                    previousData,
                    clarificationQuestion.text,
                    answer.text,
                    friendshipId,
                    timezone = timezone,
                    messageTime = messageTime,
                    messageId = context.originalMessage?.messageId,
                    channel = context.originalMessage?.channel
                )
            }

            val dataResult = dataResultAsync.await()
            val idResult = idResultAsync.await()
            val updatedParams = subtask.parameters + mapOf(
                "id" to idResult.id, "entityData" to dataResult.data
            ).filterValues { it != null }
            if (!dataResult.isComplete || idResult.needsClarification) {
                return@coroutineScope SubtaskClarificationResult.needsClarification(
                    updatedSubtask = subtask.copy(parameters = updatedParams),
                    buildClarificationQuestion(idResult, dataResult)
                )
            }

            applyUpdate(friendshipId, idResult.id, dataResult.data!!)
            SubtaskClarificationResult.success(
                updatedSubtask = subtask.copy(parameters = updatedParams),
                successMessageGenerationPrompt = dataResult.responseMessage
            )
        }
    }

    abstract suspend fun loadEntities(friendshipId: FriendshipId): List<F>
    abstract suspend fun applyUpdate(friendshipId: FriendshipId, id: String?, entity: E)

    private fun buildClarificationQuestion(
        idResult: IdResolutionResult, dataResult: ExtractionResult<E>,
    ): String {
        val parts = mutableListOf<String>()
        if (idResult.needsClarification) parts.add(getIdQuestion())
        idResult.clarifyingQuestion?.let { parts.add(it) }
        if (!dataResult.isComplete) parts.add(
            (dataResult.clarifyingQuestion ?: "").ifBlank { getDefaultDataQuestion() })
        return parts.joinToString(" ")
    }

    internal open fun getDefaultDataQuestion(): String = "What exactly do you want to update?"

    internal open fun getIdQuestion(): String = "Which item should be updated?"

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}