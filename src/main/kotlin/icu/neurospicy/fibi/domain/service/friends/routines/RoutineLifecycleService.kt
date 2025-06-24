package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import icu.neurospicy.fibi.domain.service.friends.routines.events.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service


internal const val didUserIntendToStopRoutineQuestion =
    "The user deleted a task which is key part of a routine. Did the user explicitly intend to stop the routine?"

internal const val isUserStressedQuestion = "Does the user appear stressed or overwhelmed?"

internal const val wasDeletionOfTaskAMistakeQuestion = "Was deleting the specific task \"\$taskTitle\" a mistake?"

internal const val isStoppingRoutineHelpfulDueToOverwhelmQuestion =
    "By deleting the task, the user stopped a routine. Would it be helpful to pause the routine for now?"

/**
 * Handles the lifecycle of an active routine instance.
 */
@Service
class RoutineLifecycleService(
    private val instanceRepository: RoutineRepository,
    private val stepExecutor: RoutineStepExecutor,
    private val taskHandler: RoutineTaskHandler,
    private val routinePhaseService: RoutinePhaseService,
    private val parameterSetHandler: RoutineParameterSetHandler,
) {
    @EventListener
    @Async
    fun onRoutinePhaseTriggered(event: RoutinePhaseTriggered) {
        routinePhaseService.phaseTriggered(
            instanceRepository.findById(event.friendshipId, event.instanceId) ?: return, event.phaseId
        )
    }

    @EventListener
    @Async
    fun onRoutinePhaseIterationTriggered(event: RoutinePhaseIterationTriggered) {
        val instance = instanceRepository.findById(event.friendshipId, event.instanceId) ?: return
        if (instance.currentPhaseId == event.phaseId) {
            routinePhaseService.startPhaseIteration(
                instance, event.phaseId
            )
        }
    }

    @EventListener
    @Async
    fun onRoutineStepTriggered(event: RoutineStepTriggered) {
        stepExecutor.executeStep(event)
    }

    @EventListener
    @Async
    fun onRoutineParameterSet(event: RoutineParameterSet) {
        parameterSetHandler.handleRoutineParameterSet(event)
    }

    @EventListener
    @Async
    fun onTaskCompleted(event: TaskCompleted) {
        taskHandler.handleTaskCompleted(event)
    }

    @EventListener
    @Async
    fun onTaskRemoved(event: TaskRemoved) {
        taskHandler.handleTaskRemoved(event)
    }

    @EventListener
    @Async
    fun onRoutineStoppedForToday(event: StopRoutineForToday) {
        val instance = instanceRepository.findById(event.friendshipId, event.instanceId) ?: return
        routinePhaseService.handleStoppedRoutineIteration(instance, event.reason)
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}