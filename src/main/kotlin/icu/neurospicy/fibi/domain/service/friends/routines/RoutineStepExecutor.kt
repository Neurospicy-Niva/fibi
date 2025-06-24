package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepTriggered
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RoutineStepExecutor(
    private val instanceRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
    private val goalContextRepository: GoalContextRepository,
    private val taskRepository: TaskRepository,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineStepExecutor::class.java)
    }

    fun executeStep(event: RoutineStepTriggered) {
        val (instance, template) = loadRoutineContext(event)
        validateStepContext(instance, event)
        val step = findStep(template, event)
        executeStepByType(event, instance, step)
    }

    private fun loadRoutineContext(event: RoutineStepTriggered): Pair<RoutineInstance, RoutineTemplate> {
        val instance = instanceRepository.findById(event.friendshipId, event.instanceId)
            ?: throw IllegalStateException(
                "Failed to handle triggered step ${event.stepId} of phase ${event.phaseId}. " +
                        "Routine instance with id ${event.instanceId} not found."
            )
        val template = templateRepository.findById(instance.templateId)
            ?: throw IllegalStateException(
                "Failed to handle triggered step ${event.stepId} of phase ${event.phaseId}, " +
                        "routine instance id ${event.instanceId}. Template ${instance.templateId} not found."
            )
        return Pair(instance, template)
    }

    private fun validateStepContext(instance: RoutineInstance, event: RoutineStepTriggered) {
        if (instance.currentPhaseId != event.phaseId) {
            throw IllegalStateException(
                "Failed to handle triggered step ${event.stepId}. " +
                        "Phase ${event.phaseId} is not active on instance ${event.instanceId}."
            )
        }
    }

    private fun findStep(template: RoutineTemplate, event: RoutineStepTriggered): RoutineStep {
        return template.phases.firstOrNull { it.id == event.phaseId }?.let {
            it.steps.firstOrNull { it.id == event.stepId }
                ?: throw IllegalStateException("Step ${event.stepId} was not found in routine ${template.templateId}")
        } ?: throw IllegalStateException("${event.phaseId} was not found in routine ${template.templateId}")
    }

    private fun executeStepByType(event: RoutineStepTriggered, instance: RoutineInstance, step: RoutineStep) {
        val stepParameters = mapOf(
            "phaseId" to event.phaseId,
            "stepId" to event.stepId
        )

        when (step) {
            is ParameterRequestStep -> handleParameterRequest(event, step, stepParameters)
            is MessageRoutineStep -> handleMessage(event, instance, step, stepParameters)
            is ActionRoutineStep -> handleAction(event, instance, step, stepParameters)
        }
    }

    private fun handleParameterRequest(
        event: RoutineStepTriggered,
        step: ParameterRequestStep,
        stepParameters: Map<String, Any>,
    ) {
        val subtaskId = SubtaskId.from(event.friendshipId, step.id)
        goalContextRepository.saveContext(
            event.friendshipId,
            GoalContext(
                Goal(RoutineIntents.AnswerQuestion, "A question shall be answered"),
                subtasks = listOf(
                    Subtask(
                        subtaskId,
                        RoutineIntents.AnswerQuestion,
                        parameters = stepParameters + mapOf(
                            "routineInstanceId" to event.instanceId,
                            "routineStepId" to step.id
                        )
                    )
                ),
                subtaskClarificationQuestions = listOf(SubtaskClarificationQuestion(step.description, subtaskId))
            )
        )
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass,
                event.friendshipId,
                OutgoingTextMessage(Channel.SIGNAL, step.description)
            )
        )
        eventLog.log(
            RoutineEventLogEntry(
                event.instanceId,
                event.friendshipId,
                RoutineEventType.STEP_PARAMETER_REQUESTED,
                Instant.now(),
                metadata = stepParameters.plus("subtaskId" to subtaskId)
            )
        )
        LOG.debug("Requested parameter in step ${event.stepId} of phase ${event.phaseId}, routine instance ${event.instanceId}.")
    }

    private fun handleMessage(
        event: RoutineStepTriggered,
        instance: RoutineInstance,
        step: MessageRoutineStep,
        stepParameters: Map<String, Any>,
    ) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass,
                event.friendshipId,
                OutgoingTextMessage(Channel.SIGNAL, step.message)
            )
        )
        instanceRepository.save(
            instance.copy(
                progress = instance.progress.copy(
                    iterations = instance.progress.iterations - instance.progress.iterations.first() + instance.progress.iterations.first()
                        .copy(
                            completedSteps = instance.progress.iterations.first().completedSteps.plus(Completion(step.id))
                        ),

                    )
            )
        )
        eventLog.log(
            RoutineEventLogEntry(
                event.instanceId,
                event.friendshipId,
                RoutineEventType.STEP_MESSAGE_SENT,
                Instant.now(),
                stepParameters
            )
        )
        LOG.debug("Send message for step ${event.stepId} of phase ${event.phaseId}, routine instance ${event.instanceId}.")
    }

    private fun handleAction(
        event: RoutineStepTriggered,
        instance: RoutineInstance,
        step: ActionRoutineStep,
        stepParameters: Map<String, Any>,
    ) {
        val eventLogParameters = stepParameters.toMutableMap()
        if (step.expectConfirmation) {
            val taskId = taskRepository.save(Task(owner = event.friendshipId, title = step.description)).let { it.id!! }
            instanceRepository.save(
                instance.copy(
                    concepts = instance.concepts + TaskRoutineConcept(
                        taskId,
                        step.id
                    )
                )
            )
            eventLogParameters["taskId"] = taskId
        }
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass,
                event.friendshipId,
                OutgoingTextMessage(Channel.SIGNAL, step.description)
            )
        )
        eventLog.log(
            RoutineEventLogEntry(
                event.instanceId,
                event.friendshipId,
                RoutineEventType.ACTION_STEP_MESSAGE_SENT,
                Instant.now(),
                metadata = eventLogParameters
            )
        )
    }
} 