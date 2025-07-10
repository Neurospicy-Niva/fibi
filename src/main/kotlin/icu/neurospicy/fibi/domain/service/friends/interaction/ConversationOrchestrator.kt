package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.OutgoingGeneratedMessage
import icu.neurospicy.fibi.domain.model.events.IncomingFriendMessageReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.ConversationRepository
import icu.neurospicy.fibi.domain.service.ConversationContextService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

private const val MAX_MINUTES_TO_CLARIFICATION = 15

@Component
class ConversationOrchestrator(
    private val intentClassifier: IntentClassifier,
    private val goalRefiner: GoalRefiner,
    private val goalAchiever: GoalAchiever,
    private val contextRepository: GoalContextRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val conversationContextService: ConversationContextService,
    private val conversationRepository: ConversationRepository,
) {

    @EventListener(IncomingFriendMessageReceived::class)
    fun onMessage(event: IncomingFriendMessageReceived) = runBlocking {
        val message = event.message
        conversationRepository.addUserResponse(event.friendshipId, message)
        var goalContext = contextRepository.loadContext(event.friendshipId)
        LOG.debug("Received incoming friend message for {} with current goal {}", event.message.text, goalContext)

        var clarifiedIntent: Intent? = null
        val resultPrompts = mutableListOf<String>()
        // Step 1: Resolve clarification responses
        if (goalContext?.lastUpdated?.let { Duration.between(it, Instant.now()) }?.abs()?.toMinutes()
                ?.let { it < MAX_MINUTES_TO_CLARIFICATION } ?: false
        ) {
            if (goalContext.pendingGoalClarification()) {
                LOG.info("Goal clarification is pending, processing answer")
                val response = goalRefiner.handleClarification(event.friendshipId, goalContext, message)
                if (response.clarified()) {
                    clarifiedIntent = response.intent
                    // Update context immediately to clear pending clarification
                    goalContext = goalContext.copy(
                        goalClarificationQuestion = null,
                        lastUpdated = Instant.now()
                    )
                    contextRepository.saveContext(event.friendshipId, goalContext)
                } else {
                    contextRepository.saveContext(
                        event.friendshipId, goalContext.copy(
                            goalClarificationQuestion = goalContext.goalClarificationQuestion?.copy(prompt = response.questionGenerationPrompt!!)
                        )
                    )
                    sendMessage(event, response.questionGenerationPrompt!!)
                    return@runBlocking
                }
            }
            if (goalContext.pendingSubtaskClarification()) {
                LOG.info("Subtask clarification is pending, processing answer")
                val response = goalAchiever.handleClarification(event.friendshipId, goalContext, message)
                contextRepository.saveContext(event.friendshipId, response.updatedContext)
                if (response.clarified()) {
                    goalContext = response.updatedContext
                    response.successMessageGenerationPrompt?.apply { resultPrompts += this }
                } else {
                    sendMessage(
                        event, """
${response.successMessageGenerationPrompt?.let { "Their current task has the following response:\n${it}" } ?: ""}
Ask the user the following question to continue with their task:
${response.clarificationQuestion!!}
Make it easy and friendly to answer.""")
                    return@runBlocking
                }
            }
        } else {
            // Either no goal or conversation is outdated
            conversationContextService.startNewConversation(event.friendshipId, CoreIntents.Unknown, message)
            goalContext = null
        }

        // Step 2: Classify intent (skip if we have a clarified intent)
        val intents = if (clarifiedIntent != null) {
            LOG.info("Using clarified intent: $clarifiedIntent, skipping intent classification")
            listOf(IntentClassifier.IntentClassification(clarifiedIntent, 1f))
        } else {
            conversationRepository.findByFriendshipId(event.friendshipId)?.let { intentClassifier.classifyIntent(it) }
                ?: intentClassifier.classifyIntent(event)
        }
        LOG.info("Primary intents {}", intents.filterNot { it.confidence < 0.75 })
        val primaryIntent = intents.maxByOrNull { it.confidence }?.intent ?: CoreIntents.Unknown
        if (primaryIntent == CoreIntents.Smalltalk || primaryIntent == CoreIntents.Unknown) {
            if (goalContext != null) {
                val unstructuredResponse = goalRefiner.onUnstructuredIntent(goalContext, message)
                contextRepository.saveContext(event.friendshipId, unstructuredResponse.updatedContext)

                if (unstructuredResponse.responseGenerationPrompt != null) {
                    sendMessage(event, unstructuredResponse.responseGenerationPrompt)
                    return@runBlocking
                }
            }

            sendMessage(
                event, """
                    You are an empathic assistant for neurodivergent people. 
                    Answer kindly and briefly.
            """.trimIndent()
            )
            return@runBlocking
        }


        // Step 3: Determine goal
        val refinedGoalContext = goalRefiner.refineGoal(intents, event.friendshipId, message, goalContext)
        if (refinedGoalContext.goal != null && refinedGoalContext.goal.intent != goalContext?.goal?.intent) {
            conversationContextService.startNewConversation(event.friendshipId, refinedGoalContext.goal.intent, message)
        }
        goalContext = refinedGoalContext
        if (goalContext.pendingGoalClarification()) {
            contextRepository.saveContext(event.friendshipId, goalContext)
            sendMessage(event, goalContext.goalClarificationQuestion!!.prompt)
            return@runBlocking
        }
        LOG.info("Current goal: ${goalContext.goal} with ${goalContext.subtasks.filterNot { it.completed() }.size} ongoing subtasks")

        // Step 4: Advance in goal achievement (for subtasks)
        val result = goalAchiever.advance(goalContext, event.friendshipId, message)
        goalContext = result.updatedContext
        contextRepository.saveContext(event.friendshipId, goalContext)

        // Step 5: Answer
        resultPrompts += result.subtaskSuccessGenerationPrompts.joinToString("---\n").let { "\n---$it" }
        when {
            result.clarificationNeeded() -> {
                LOG.info("Sending message for task clarification")
                val questions =
                    result.updatedContext.subtaskClarificationQuestions.joinToString("\n") { "- ${it.text}" }

                val msg = """
For the answer combine the following instructions:${resultPrompts}
---
Ask the user to answer the following question(s) to continue with their task(s):
$questions
Make it easy and friendly to answer.

⚠️ Just return the questions, no explanation!
---
                """.trimIndent()
                sendMessage(event, msg)
            }

            result.complete() -> {
                LOG.info("Sending message that goal ${result.updatedContext.goal?.intent} is completed")
                contextRepository.saveContext(event.friendshipId, GoalContext.none())
                sendMessage(
                    event,
                    "For the answer combine the following instructions:${resultPrompts}\n\"Answer by asking if there is more todo.\""
                )
                conversationContextService.endConversation(event.friendshipId)
            }

            result.subtaskSuccessGenerationPrompts.isNotEmpty() -> {
                sendMessage(
                    event, """
For the answer combine the following instructions:${resultPrompts}
                """.trimIndent()
                )
            }

            else -> {
                LOG.info("Sending message that achieving goal ${goalContext.goal?.intent} is ongoing")
                sendMessage(
                    event, """
                    The friend intended to achieve the goal ${goalContext.goal?.intent} by initially sending the message "${goalContext.originalMessage?.text}".
                    While achieving the goal the following subtasks are not yet done: ${
                        goalContext.subtasks.filterNot { it.completed() }.joinToString { "\"${it.intent}\"" }
                    }.
                    Tell the friend about this current ongoing process.
                """.trimIndent())
            }
        }
    }

    private fun sendMessage(event: IncomingFriendMessageReceived, messageDescription: String) {
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingGeneratedMessage(
                    event.message.channel, messageDescription
                ), event.message.messageId
            )
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}

