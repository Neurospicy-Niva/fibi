package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.service.friends.communication.FriendStateAnalyzer
import icu.neurospicy.fibi.domain.service.friends.communication.Mood
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.ConfirmedActionStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.StopRoutineForToday
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class RoutineTaskIntegrationService(
    private val instanceRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
    private val goalContextRepository: GoalContextRepository,
    private val friendStateAnalyzer: FriendStateAnalyzer,
    private val chatRepository: ChatRepository,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineTaskIntegrationService::class.java)

        internal const val DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION =
            "The user deleted a task which is key part of a routine. Did the user explicitly intend to stop the routine?"

        internal const val IS_USER_STRESSED_QUESTION = "Does the user appear stressed or overwhelmed?"

        internal const val WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION =
            "Was deleting the specific task \"\$taskTitle\" a mistake?"

        internal const val IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION =
            "By deleting the task, the user stopped a routine. Would it be helpful to pause the routine for now?"
    }

    fun handleTaskCompleted(event: TaskCompleted) {
        val taskId = event.task.id!!
        val friendshipId = event.owner
        instanceRepository.findByConceptRelatedToTask(friendshipId, taskId).forEach { instance ->
            instance.concepts.filter { it is TaskRoutineConcept && it.linkedTaskId == taskId }.forEach { concept ->
                // Check if step is already completed in current iteration
                val currentIteration = instance.progress.iterations.first()
                if (currentIteration.completedSteps.any { it.id == concept.linkedStep }) {
                    // Step already completed, skip adding duplicate
                    instanceRepository.save(
                        instance.copy(
                            concepts = instance.concepts - concept
                        )
                    )
                    return@forEach
                }

                instanceRepository.save(
                    instance.copy(
                        concepts = instance.concepts - concept,
                        progress = instance.progress.copy(
                            iterations = instance.progress.iterations - instance.progress.iterations.first() + instance.progress.iterations.first()
                                .copy(
                                    completedSteps = instance.progress.iterations.first().completedSteps + Completion(
                                        concept.linkedStep,
                                        Instant.now()
                                    )
                                )
                        )
                    )
                )
                eventLog.log(
                    RoutineEventLogEntry(
                        instance.instanceId,
                        friendshipId,
                        RoutineEventType.ACTION_STEP_CONFIRMED,
                        Instant.now(),
                        mapOf("stepId" to concept.linkedStep, "taskId" to taskId)
                    )
                )
                eventPublisher.publishEvent(
                    ConfirmedActionStep(
                        this.javaClass,
                        friendshipId,
                        instance.instanceId,
                        instance.currentPhaseId!!,
                        concept.linkedStep
                    )
                )
            }
        }
    }

    fun handleTaskRemoved(event: TaskRemoved) {
        if (event.task.completed) return
        val taskId = event.task.id!!
        val friendshipId = event.owner
        val instances = instanceRepository.findByConceptRelatedToTask(friendshipId, taskId)
        if (instances.isEmpty()) return
        if (instances.size > 1) LOG.warn("There are more than one routine instances for task ${event.task.id}")
        val instance = instances.first()

        val recentMessages = loadRecentMessages(friendshipId)
        val questions = listOf(
            DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION,
            IS_USER_STRESSED_QUESTION,
            WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace("\$taskTitle", event.task.title),
            IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION
        )
        val (answers, emotions) = runBlocking {
            friendStateAnalyzer.analyze(
                recentMessages,
                questions = questions
            )
        }
        val intendedStop =
            answers.firstOrNull { it.question == DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION }?.answer == true
        val stressedOrSad = emotions.any { it.mood in setOf(Mood.Stressed, Mood.Sad) && it.confidence > 0.75 }
        val userStressed = answers.firstOrNull { it.question == IS_USER_STRESSED_QUESTION }?.answer == true
                || stressedOrSad
        val mistakenDelete = answers.firstOrNull {
            it.question == WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace(
                "\$taskTitle",
                event.task.title
            )
        }?.answer == true
        val pauseHelpful =
            answers.firstOrNull { it.question == IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION }?.answer == true
        when {
            !intendedStop && mistakenDelete -> handleOnTaskRemovedWhenMistake(instance, event)
            userStressed -> handleOnTaskRemovedWhenOverwhelmed(instance, friendshipId, event)
            pauseHelpful -> handleOnTaskRemovedWhenPauseIsHelpful(instance, friendshipId, event)
            intendedStop && !userStressed -> handleOnTaskRemovedWhenIntendedStop(instance, friendshipId, event)
            else -> handleOnTaskRemovedWhenUncertain(instance, event)
        }
    }

    private fun handleOnTaskRemovedWhenIntendedStop(
        instance: RoutineInstance,
        friendshipId: FriendshipId,
        event: TaskRemoved,
    ) {
        val templateTitle = templateRepository.findById(instance.templateId)?.title ?: "<The title is not known>"
        prepareAndSaveGoalToStopRoutineToday(
            instance = instance,
            friendshipId = friendshipId,
            question = "It looks like you wanted to stop the routine. Would you like to be reminded to resume later?",
            taskId = event.task.id!!
        )
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass,
                event.owner,
                OutgoingGeneratedMessage(
                    Channel.SIGNAL,
                    """
                    The user has deleted the task "${event.task.title}" of an ongoing routine called "$templateTitle", and it seems they explicitly intended to stop the routine.

                    Write a supportive message. The message should:
                    - Acknowledge that the user chose to stop the routine
                    - Offer a gentle follow-up whether they want to be reminded later to resume
                    - Use a warm and understanding tone, informal, no pressure
                    - End with a caring touch (e.g. 💛)

                    Keep it friendly and optional. Do not expect a definitive decision.
                    """.trimIndent()
                )
            )
        )
    }

    private fun handleOnTaskRemovedWhenPauseIsHelpful(
        instance: RoutineInstance,
        friendshipId: FriendshipId,
        event: TaskRemoved,
    ) {
        val templateTitle =
            templateRepository.findById(instance.templateId)?.title ?: "<The title is not known>"
        eventPublisher.publishEvent(
            StopRoutineForToday(
                _source = this::class.java,
                friendshipId = friendshipId,
                instanceId = instance.instanceId,
                reason = "Stopping the routine appeared helpful due to user's overwhelm"
            )
        )
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingGeneratedMessage(
                    Channel.SIGNAL, """
                          The user just deleted the task "${event.task.title}" that was part of an active routine named "$templateTitle". According to recent messages, pausing the routine now seems helpful.

                          Write a supportive, friendly and encouraging message in German. Address the user with "du". The message should:
                          - Empathize with the user, implying it might have been too much at the moment
                          - Suggest that it's okay to pause for now
                          - Offer to resume later, only if the user feels like it
                          - Use a warm and gentle tone, include a heart emoji (e.g., 💛)

                          Don't ask for a decision. Avoid pressure. Just show understanding and support.
                      """.trimIndent()
                )
            )
        )
    }

    private fun handleOnTaskRemovedWhenOverwhelmed(
        instance: RoutineInstance,
        friendshipId: FriendshipId,
        event: TaskRemoved,
    ) {
        val instanceId = instance.instanceId
        eventPublisher.publishEvent(
            StopRoutineForToday(
                _source = this::class.java,
                friendshipId = friendshipId,
                instanceId = instanceId,
                reason = "User appeared stressed or overwhelmed after task removal"
            )
        )
        val templateTitle =
            templateRepository.findById(instance.templateId)?.title ?: "<The title is not known>"
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingGeneratedMessage(
                    Channel.SIGNAL, """
                                The user just deleted the task "${event.task.title}" that was part of an active routine named "$templateTitle". Based on their recent messages, they seem to be overwhelmed or emotionally burdened.
                        
                                Write a supportive, friendly and encouraging message. The message should:
                                - Express empathy and validate their feelings (e.g., stress or overwhelm)
                                - Let them know it's okay to take a break for today
                                - Reassure them they can continue the routine later if they want
                                - Use a warm and gentle tone with a caring emoji (e.g., 💛)
                        
                                Avoid giving instructions or asking for decisions right now. Keep the tone light and comforting.
                            """.trimIndent()
                )
            )
        )
    }

    private fun handleOnTaskRemovedWhenMistake(
        instance: RoutineInstance,
        event: TaskRemoved,
    ) {
        val templateTitle = templateRepository.findById(instance.templateId)?.title ?: "<The title is not known>"
        val friendshipId = event.owner
        prepareAndSaveGoalToStopRoutineToday(
            instance = instance,
            friendshipId = friendshipId,
            question = "It looks as if the task \"${event.task.title}\" has been deleted by mistake. Do you want to recreate the task and continue the routine?",
            taskId = event.task.id!!
        )
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingGeneratedMessage(
                    Channel.SIGNAL, """
                        The user just deleted the task "${event.task.title}" that was part of an active routine named "$templateTitle". Based on their recent messages, it looks as if the task has been deleted by mistake.
                
                        Write a supportive, friendly and encouraging message. The message should:
                        - Let them know it's okay to take a break for today
                        - Ask them, if they want to recreate the task and continue the routine
                        - Use a warm and gentle tone
                
                        Avoid being pushy. Keep the tone light and comforting.
                    """.trimIndent()
                )
            )
        )
    }

    private fun handleOnTaskRemovedWhenUncertain(
        instance: RoutineInstance,
        event: TaskRemoved,
    ) {
        val templateTitle = templateRepository.findById(instance.templateId)?.title ?: "<The title is not known>"
        val friendshipId = event.owner
        prepareAndSaveGoalToStopRoutineToday(
            instance = instance,
            friendshipId = friendshipId,
            question = "Do you want to recreate the task and continue the routine or do you want to continue later?",
            taskId = event.task.id!!
        )
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingGeneratedMessage(
                    Channel.SIGNAL, """
                        The user just deleted the task "${event.task.title}" that was part of an active routine named "$templateTitle". The friend might not know about the connection to the routine.
                
                        Write a supportive, friendly and encouraging message. The message should:
                        - Let them know it's okay to take a break for today
                        - Ask them, if they want to recreate the task and continue the routine now
                        - Ask them, if they want to continue the routine later
                        - Use a warm and gentle tone
                
                        Avoid being pushy. Keep the tone light and comforting.
                    """.trimIndent()
                )
            )
        )
    }

    private fun prepareAndSaveGoalToStopRoutineToday(
        instance: RoutineInstance,
        friendshipId: FriendshipId,
        question: String,
        taskId: String,
    ) {
        val concept = instance.concepts.first { it.linkedEntityId == taskId }
        val subtaskId = SubtaskId.from(friendshipId, concept.linkedStep)
        goalContextRepository.saveContext(
            friendshipId,
            GoalContext(
                goal = Goal(
                    RoutineIntents.StopRoutineToday,
                    "User wants to stop the routine for today"
                ),
                subtasks = listOf(
                    Subtask(
                        id = subtaskId,
                        intent = RoutineIntents.StopRoutineToday,
                        parameters = mapOf(
                            "routineInstanceId" to instance.instanceId,
                            "taskId" to taskId,
                            "stepId" to concept.linkedStep
                        )
                    )
                ),
                subtaskClarificationQuestions = listOf(
                    SubtaskClarificationQuestion(
                        text = question,
                        relatedSubtask = subtaskId
                    )
                )
            )
        )
    }

    private fun loadRecentMessages(friendshipId: FriendshipId): List<Message> {
        return chatRepository.findHistory(friendshipId).timeline.filter {
            Duration.between(
                Instant.now(),
                it.let {
                    when (it) {
                        is UserMessage -> it.receivedAt; is FibiMessage -> it.sentAt
                    }
                }).abs() < Duration.ofMinutes(15)
        }.take(5)
    }
} 