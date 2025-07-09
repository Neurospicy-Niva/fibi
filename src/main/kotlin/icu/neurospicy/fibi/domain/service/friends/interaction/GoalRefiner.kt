package icu.neurospicy.fibi.domain.service.friends.interaction

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset

// Define a constant for better maintainability
private const val INTENT_CONFIDENCE_THRESHOLD = 0.75

@Service
class GoalRefiner(
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val subtaskRegistry: SubtaskRegistry,
    private val intentRegistry: IntentRegistry,
    private val objectMapper: ObjectMapper,
    private val goalDeteminators: List<GoalDeterminator>,
    private val defaultModel: String,
) {
    val nonGoalIntents = setOf(CoreIntents.CancelGoal, CoreIntents.Unknown, CoreIntents.Smalltalk)

    /**
     * Determines if the provided intents and message provide enough information
     * to create a clear goal, or if additional clarification is needed.
     */
    suspend fun refineGoal(
        intents: List<IntentClassifier.IntentClassification>,
        friendshipId: FriendshipId,
        message: UserMessage,
        existingContext: GoalContext?,
    ): GoalContext {
        if (existingContext?.pendingGoalClarification() == true) {
            return existingContext
        }

        val primaryIntents = getPrimaryIntents(intents)
        if (primaryIntents.all { it in nonGoalIntents }) {
            return determineGoalForNonGoalIntents(primaryIntents, existingContext)
        }
        val newGoals: Set<Goal> = determineGoalsFor(primaryIntents, message, friendshipId)

        // user still wants to achieve old goal
        if (newGoals.size == 1 && existingContext?.goal == newGoals.first()) return existingContext

        // Check if new message is compatible with existing goal
        val isCompatible = existingContext?.goal?.let { goal ->
            isCompatibleWith(goal.intent.name, message.text, newGoals, friendshipId)
        } ?: false

        // gather subtasks for additional intents
        val subTasks =
            newGoals.map { it.intent }.filterNot { it == existingContext?.goal?.intent }
                .associateWith { subtaskRegistry.generateSubtasks(it, friendshipId, message) }

        return when {
            isCompatible -> {
                // Update existing goal context with new subtasks
                LOG.info("Extending goal with subtasks of $primaryIntents")
                existingContext.copy(
                    subtasks = subTasks.map { it.value }.flatten(),
                )
            }

            primaryIntents.size == 1 -> {
                // Create a new goal context with a single clear intent
                LOG.info("Setting goal for intent: ${primaryIntents.first()}")
                GoalContext(
                    originalMessage = message,
                    goal = Goal(primaryIntents.first()), subtasks = subTasks.map { it.value }.flatten(),
                )
            }

            else -> {
                // Need clarification between multiple potential intents
                GoalContext.unknown().copy(
                    originalMessage = message,
                    goal = Goal(CoreIntents.Unknown),
                    goalClarificationQuestion = GoalClarificationQuestion(
                        """
                        Explain that you need more clarity on the user's intent.
                        Ask a question to determine, which of the following intents shall be targeted next:
                        ${primaryIntents.joinToString("\n") { "- ${it.name}" }}
                        """.trimIndent(), primaryIntents
                    )
                )
            }
        }
    }

    private fun determineGoalsFor(
        primaryIntents: Set<Intent>, message: UserMessage, friendshipId: FriendshipId,
    ): Set<Goal> = runBlocking {
        primaryIntents.asFlow().map { intent ->
            (goalDeteminators.firstOrNull { it -> it.canHandle(intent) } ?: SimpleGoalDeterminator()).determineGoal(
                intent, message, friendshipId
            )
        }.toSet().flatten().toSet()
    }

    private fun getPrimaryIntents(intents: List<IntentClassifier.IntentClassification>): Set<Intent> {
        return intents.filter { it.confidence >= INTENT_CONFIDENCE_THRESHOLD }
            .filter { it.confidence == intents.maxOfOrNull { intent -> intent.confidence } }.map { it.intent }.ifEmpty {
                setOf(CoreIntents.Unknown)
            }.toSet()
    }

    private fun determineGoalForNonGoalIntents(
        primaryIntents: Set<Intent>, existingContext: GoalContext?,
    ): GoalContext =
        existingContext?.let { if (primaryIntents.contains(CoreIntents.CancelGoal)) null else it } ?: GoalContext.none()

    suspend fun handleClarification(
        friendshipId: FriendshipId, context: GoalContext, message: UserMessage,
    ): GoalClarificationResponse {
        val goalClarificationQuestion = context.goalClarificationQuestion
            ?: return GoalClarificationResponse.failed("GoalContext has no goal clarification question.")

        val allIntents = intentRegistry.getDescriptions()
        val receivedAt = Instant.now()
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC

        val prompt = """
            Given the user's goal: "${context.goal}"
            And the clarification questions: 
            ${goalClarificationQuestion.prompt}
            And the user's response: "${message.text}"
            
            Classify into one of the following intents:
            ${
            allIntents.entries.filter { goalClarificationQuestion.intents.contains(it.key) }
                .joinToString("\n") { "- \"${it.key.name}\": ${it.value}" }
        }
            
            Answer in plain JSON with:
            "isGoalClear": Is the goal now clear enough to proceed? (true/false)
            "intent": If yes, the classified intent
            "clarificationQuestion": If not, what additional question needs to be asked?
        """.trimIndent()

        val response = llmClient.promptReceivingText(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(defaultModel).temperature(0.0).build(),
            timezone,
            receivedAt
        )

        // Handle possible errors in response parsing
        return try {
            val jsonTree = objectMapper.readTree(response)
            val isGoalClear = jsonTree["isGoalClear"]?.asBoolean() ?: false

            if (isGoalClear) {
                val intentName = jsonTree["intent"]?.asText() ?: ""
                // Look up the Intent object by name, or fallback to Unknown
                val intent = allIntents.keys.associateBy { it.name }[intentName] ?: CoreIntents.Unknown

                if (intent == CoreIntents.Unknown) {
                    GoalClarificationResponse.clarified(CoreIntents.Unknown)
                } else {
                    LOG.info("")
                    GoalClarificationResponse.clarified(intent)
                }
            } else {
                val clarificationQuestion =
                    jsonTree["clarificationQuestion"]?.asText() ?: "What exactly are you trying to accomplish?"

                GoalClarificationResponse.needsClarification(
                    """
                    Thank the user for their response, but explain you still need more clarity.
                    Ask the following question:
                    $clarificationQuestion
                    """.trimIndent()
                )
            }
        } catch (e: Exception) {
            // In case of parsing error, return a generic clarification response
            GoalClarificationResponse.failed("I'm having trouble understanding your request. Could you please rephrase it?")
        }
    }

    private suspend fun isCompatibleWith(
        currentGoal: String, newMessageText: String, newGoals: Set<Goal>, friendshipId: FriendshipId,
    ): Boolean {
        val receivedAt = Instant.now()
        val timezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC

        val prompt = """
            Given the current goal: "$currentGoal"
            And the new message: "$newMessageText"
            With intents: ${newGoals.joinToString { it.intent.name }}
            Determine if the new message and intents are compatible with the current goal (yes/no).
        """.trimIndent()

        val response = llmClient.promptReceivingText(
            listOf(org.springframework.ai.chat.messages.UserMessage(prompt)),
            OllamaOptions.builder().model(defaultModel).temperature(0.0).build(),
            timezone,
            receivedAt
        )

        return response?.trim()?.lowercase()?.startsWith("yes") ?: false
    }

    /**
     * Handles unstructured intents (like smalltalk) within an existing goal context.
     */
    suspend fun onUnstructuredIntent(
        context: GoalContext, message: UserMessage,
    ): UnstructuredResponse {
        // Only ask about continuing with the goal if there are pending subtasks
        val hasPendingSubtasks = context.subtasks.any { !it.completed() }

        return if (hasPendingSubtasks) {
            val goalClarificationQuestion = """
            The user is currently working on the goal: "${context.goal}"
            They just sent an unrelated message: "${message.text}"
            
            Please:
            1. Respond to their message in a friendly way
            2. Gently remind them of their current goal
            3. Ask if they want to continue with the goal or abandon it
            """.trimIndent()

            UnstructuredResponse(
                updatedContext = context.copy(
                    originalMessage = message,
                    goalClarificationQuestion = GoalClarificationQuestion(
                        goalClarificationQuestion,
                        context.goal?.intent?.let { setOf(it) } ?: emptySet())),
                responseGenerationPrompt = goalClarificationQuestion)
        } else {
            // No pending subtasks, just keep the existing context
            UnstructuredResponse(updatedContext = context)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}