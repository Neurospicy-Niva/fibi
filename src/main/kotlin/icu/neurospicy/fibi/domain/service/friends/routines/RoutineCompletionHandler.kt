package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.events.ConfirmedActionStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseIterationCompleted
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Handles detection and processing of step completions and phase iteration completions.
 * Monitors routine progress and publishes completion events when milestones are reached.
 */
@Service
class RoutineCompletionHandler(
    private val routineRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
) {

    @EventListener
    @Async
    fun onActionStepConfirmed(event: ConfirmedActionStep) {
        checkPhaseIterationCompletion(event.friendshipId, event.instanceId, event.phaseId)
    }

    @EventListener
    @Async
    fun onParameterRequestCompleted(event: SetRoutineParameterRoutineStep) {
        checkPhaseIterationCompletion(event.friendshipId, event.instanceId, event.phaseId)
    }

    @EventListener
    @Async
    fun onMessageStepCompleted(event: RoutineStepTriggered) {
        // Message steps are auto-completed when sent, check if phase iteration is complete
        checkPhaseIterationCompletion(event.friendshipId, event.instanceId, event.phaseId)
    }

    private fun checkPhaseIterationCompletion(
        friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId,
        instanceId: RoutineInstanceId,
        phaseId: RoutinePhaseId,
    ) {
        val instance = routineRepository.findById(friendshipId, instanceId) ?: return
        val template = templateRepository.findById(instance.templateId) ?: return

        // Only check completion for the current active phase
        if (instance.currentPhaseId != phaseId) return

        val currentPhase = template.phases.find { it.id == phaseId } ?: return
        val currentIteration = instance.progress.iterations.firstOrNull() ?: return

        // Check if current iteration is already completed
        if (currentIteration.completedAt != null) return

        val completedStepIds = currentIteration.completedSteps.map { it.id }.toSet()
        val allStepsCompleted = currentPhase.steps.all { step -> step.id in completedStepIds }

        if (allStepsCompleted) {
            LOG.info("Phase iteration completed for phase {} in routine instance {}", phaseId, instanceId)

            // Mark phase iteration as complete
            val completedIteration = currentIteration.copy(completedAt = Instant.now())
            val updatedInstance = instance.copy(
                progress = instance.progress.copy(
                    iterations = listOf(completedIteration) + instance.progress.iterations.drop(1)
                )
            )
            routineRepository.save(updatedInstance)

            // Log completion event
            eventLog.log(
                RoutineEventLogEntry(
                    routineInstanceId = instanceId,
                    friendshipId = friendshipId,
                    event = RoutineEventType.PHASE_COMPLETED,
                    timestamp = Instant.now(),
                    metadata = mapOf("phaseId" to phaseId)
                )
            )

            // Publish phase iteration completed event for transition evaluation
            eventPublisher.publishEvent(
                PhaseIterationCompleted(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    phaseId
                )
            )
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
} 