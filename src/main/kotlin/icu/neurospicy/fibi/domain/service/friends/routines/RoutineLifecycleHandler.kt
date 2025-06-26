package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseIterationTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.StopRoutineForToday
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Handles core lifecycle orchestration of routine instances.
 * Delegates to specialized services for domain-specific concerns.
 */
@Service
class RoutineLifecycleHandler(
    private val instanceRepository: RoutineRepository,
    private val stepExecutor: RoutineStepExecutor,
    private val routinePhaseService: RoutinePhaseService,
    private val parameterSetHandler: RoutineParameterSetHandler,
) {

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
    fun onRoutineParameterSet(event: SetRoutineParameterRoutineStep) {
        parameterSetHandler.handleRoutineParameterSet(event)
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