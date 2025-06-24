package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.ADVANCED_MODEL
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.ai.chat.messages.UserMessage as AiUserMessage

/**
 * Supports the user in selecting routines.
 */
@Component
class SelectRoutineSubtaskHandler(
    private val templateRepository: RoutineTemplateRepository,
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val objectMapper: ObjectMapper,
) : SubtaskHandler {
    override fun canHandle(intent: Intent): Boolean {
        return intent == RoutineIntents.Select
    }

    override suspend fun handle(
        subtask: Subtask,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskResult {
        val templates = templateRepository.loadAll()
        if (templates.isEmpty()) {
            return SubtaskResult.failure("No routines available.", subtask)
        }

        val rawText = subtask.parameters["rawText"] as? String ?: return SubtaskResult.failure("No message", subtask)
        val prompt = buildPrompt(rawText, templates)

        val llmResponse = llmClient.promptReceivingJson(
            listOf(AiUserMessage(prompt)),
            OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.7).build(),
            friendshipLedger.findTimezoneBy(friendshipId) ?: ZoneOffset.UTC,
            context.originalMessage?.receivedAt ?: Instant.now()
        )?.takeIf { it.isNotBlank() } ?: return SubtaskResult.failure("LLM failed", subtask)

        val (routineId, possibleRoutineIds, clarificationNeeded) = objectMapper.readValue(
            llmResponse, ClassificationResponse::class.java
        )

        return when {
            clarificationNeeded -> SubtaskResult.needsClarification(
                subtask.copy(parameters = subtask.parameters + ("possibleRoutineIds" to possibleRoutineIds.orEmpty())),
                clarifyingQuestionWith(templates.filter { routine ->
                    possibleRoutineIds?.contains(routine.templateId.toString()) ?: false
                }.ifEmpty { templates })
            )

            templates.any { it.templateId.toString() == routineId } -> {
                val matchingTemplate = templates.first { it.templateId.toString() == routineId }
                SubtaskResult.success(
                    "The user wants to start a new routine with \"$rawText\". Let them now, that the routine with name \"${matchingTemplate.title}\" was selected. Now, it has to be set up to start it.",
                    subtask,
                    mapOf("routineTemplateId" to matchingTemplate.templateId),
                )
            }

            else -> SubtaskResult.needsClarification(
                subtask.copy(parameters = subtask.parameters.filter { it.key != "possibleRoutineIds" }),
                clarifyingQuestionWith(templates),

                )
        }
    }

    override suspend fun tryResolveClarification(
        subtask: Subtask,
        clarificationQuestion: SubtaskClarificationQuestion,
        answer: UserMessage,
        context: GoalContext,
        friendshipId: FriendshipId,
    ): SubtaskClarificationResult {
        val templates = templateRepository.loadAll()
            .ifEmpty { return SubtaskClarificationResult.failure("No matching templates", subtask) }
        val rawText = subtask.parameters["rawText"] as? String ?: return SubtaskClarificationResult.failure(
            "No message", subtask
        )
        val prompt = if (subtask.parameters["possibleRoutineIds"] != null) {
            val matchingTemplates = (subtask.parameters["possibleRoutineIds"] as? List<*>)!!.let { routineIds ->
                templates.filter { routineIds.contains(it.templateId.toString()) }
            }.ifEmpty { templates }
            buildDifferentiationPrompt(matchingTemplates, rawText, clarificationQuestion.text, answer.text)
        } else {

            buildPrompt(rawText, templates, clarificationQuestion.text, answer.text)
        }

        val llmResponse = llmClient.promptReceivingJson(
            listOf(AiUserMessage(prompt)),
            OllamaOptions.builder().model(ADVANCED_MODEL).temperature(0.0).topP(0.7).build(),
            friendshipLedger.findTimezoneBy(friendshipId) ?: ZoneOffset.UTC,
            context.originalMessage?.receivedAt ?: Instant.now()
        )?.takeIf { it.isNotBlank() } ?: return SubtaskClarificationResult.failure("LLM failed", subtask)

        val (routineId, possibleRoutineIds, clarificationNeeded) = objectMapper.readValue(
            llmResponse, ClassificationResponse::class.java
        )

        return when {
            clarificationNeeded -> SubtaskClarificationResult.needsClarification(
                subtask.copy(parameters = subtask.parameters + ("possibleRoutineIds" to possibleRoutineIds)),
                clarifyingQuestionWith(templates.filter { routine ->
                    possibleRoutineIds?.contains(routine.templateId.toString()) ?: false
                }.ifEmpty { templates })
            )

            templates.any { it.templateId.toString() == routineId } -> {
                val matchingTemplate = templates.first { it.templateId.toString() == routineId }
                SubtaskClarificationResult.success(
                    "The user wants to start a new routine with \"$rawText\" and \"${answer.text}\". Let them now, that the routine with name \"${matchingTemplate.title}\" was selected. Now, it has to be set up to start it.",
                    subtask,
                    mapOf("routineTemplateId" to matchingTemplate.templateId),
                )
            }

            else -> SubtaskClarificationResult.needsClarification(
                subtask.copy(parameters = subtask.parameters.filter { it.key != "possibleRoutineIds" }),
                clarifyingQuestionWith(templates)
            )
        }
    }

    private fun clarifyingQuestionWith(templates: List<RoutineTemplate>): String =
        "I could not find a routine for your request. These are the predefined routines:\n${
            templates.joinToString("\n") { "- ${it.title}: ${it.description}" }
        }"

    private fun buildPrompt(
        rawText: String,
        templates: List<RoutineTemplate>,
        question: String? = null,
        answer: String? = null,
    ): String {
        val list = templates.joinToString("\n") {
            "- Title: \"${it.title}\", ID: \"${it.templateId}\", Description: \"${it.description}\""
        }
        return """
You are helping a user select a routine from a predefined list.

Responsibilities:
1. If the user clearly refers to a routine by title or description, return its ID as `routineId` and set `clarificationNeeded: false`.
2. If multiple routines match equally well, set `clarificationNeeded: true` and include all matching IDs in `possibleRoutineIds`.
3. If no match is found or you are unsure, set `clarificationNeeded: true` and leave `routineId` as null.

⚠ DO NOT guess. Use only the provided routines.

Available routines:
$list

Conversation:
"$rawText"${question.let { "\n---\"$question\"\n---\n\"$answer\"" }}

Respond with JSON:
{
  "routineId": "id-of-clear-match-or-null",
  "possibleRoutineIds": ["id1", "id2"], // if clarification is needed
  "clarificationNeeded": true-or-false
}
""".trimIndent()
    }

    data class ClassificationResponse(
        val routineId: String?,
        var possibleRoutineIds: List<String>? = null,
        val clarificationNeeded: Boolean,
    ) {
        init {
            if (possibleRoutineIds?.let { it.size > 1 } != true) possibleRoutineIds = null
        }
    }

    private fun buildDifferentiationPrompt(
        matchingTemplates: List<RoutineTemplate>,
        initialMessage: String,
        clarifyingQuestion: String,
        answer: String,
    ): String = """
You are helping a user select a routine from a predefined list.

Responsibilities:
1. If the user clearly refers to a routine by title or description, return its ID as `routineId` and set `clarificationNeeded: false`.
2. If multiple routines match equally well, set `clarificationNeeded: true` and include all matching IDs in `possibleRoutineIds`.
3. If no match is found or you are unsure, set `clarificationNeeded: true` and leave `routineId` as null.

⚠ DO NOT guess. Use only the provided routines.

Conversation:
"$initialMessage"
---
"$clarifyingQuestion"
---
"$answer"

Based on this clarification from the user, choose one routineId from the following options:
Options:
${matchingTemplates.joinToString("\n") { "- Title: \"${it.title}\", ID: \"${it.templateId}\", Description: \"${it.description}\"" }}

Respond with JSON:
{
  "routineId": "id-of-clear-match-or-null",
  "possibleRoutineIds": ["id1", "id2"], // if clarification is needed
  "clarificationNeeded": true-or-false
}
            """.trimIndent()
}