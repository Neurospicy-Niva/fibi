package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.events.CompletedRoutineStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseIterationCompleted
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
    fun onActionStepConfirmed(event: CompletedRoutineStep) {
        checkPhaseIterationCompletion(event.friendshipId, event.instanceId, event.phaseId)
    }

    private fun checkPhaseIterationCompletion(
        friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId,
        instanceId: RoutineInstanceId,
        phaseId: RoutinePhaseId,
    ) {
        val instance = routineRepository.findById(friendshipId, instanceId) ?: run {
            LOG.warn("Cannot check phase completion: routine instance {} not found", instanceId)
            return
        }
        val template = templateRepository.findById(instance.templateId) ?: run {
            LOG.warn(
                "Cannot check phase completion: template {} not found for instance {}",
                instance.templateId, instanceId
            )
            return
        }

        // Only check completion for the current active phase
        if (instance.currentPhaseId != phaseId) {
            LOG.debug(
                "Skipping completion check for phase {} as it's not the current active phase {} in instance {}",
                phaseId, instance.currentPhaseId, instanceId
            )
            return
        }

        val currentPhase = template.findPhase(phaseId) ?: run {
            LOG.warn("Phase {} not found in template {} for instance {}", phaseId, instance.templateId, instanceId)
            return
        }

        val currentIteration = instance.progress.getCurrentIteration() ?: run {
            LOG.warn("No current iteration found for instance {}", instanceId)
            return
        }

        // Check if current iteration is already completed
        if (currentIteration.completedAt != null) {
            LOG.debug("Phase iteration for phase {} in instance {} is already completed", phaseId, instanceId)
            return
        }

        val completedStepIds = currentIteration.completedSteps.map { it.id }.toSet()
        val allStepsCompleted = currentPhase.steps.all { step -> step.id in completedStepIds }

        if (allStepsCompleted) {
            LOG.info("Phase iteration completed for phase {} in routine instance {}", phaseId, instanceId)

            try {
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

                LOG.debug("Successfully marked phase {} as completed for instance {}", phaseId, instanceId)
            } catch (e: Exception) {
                LOG.error("Error marking phase {} as completed for instance {}: {}", phaseId, instanceId, e.message, e)
            }
        } else {
            LOG.debug(
                "Phase {} in instance {} not yet complete. Completed steps: {}, Required steps: {}",
                phaseId, instanceId, completedStepIds, currentPhase.steps.map { it.id })
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
} 