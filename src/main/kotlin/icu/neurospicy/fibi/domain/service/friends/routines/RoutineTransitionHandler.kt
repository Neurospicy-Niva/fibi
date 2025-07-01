package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseIterationCompleted
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Handles phase transitions in routine instances based on various trigger conditions.
 * Evaluates transition conditions and activates new phases when criteria are met.
 */
@Service
class RoutineTransitionHandler(
    private val routineRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val routinePhaseService: RoutinePhaseService,
) {

    @EventListener
    @Async
    fun onPhaseIterationCompleted(event: PhaseIterationCompleted) {
        LOG.info(
            "Evaluating phase transitions after completion of phase {} in routine {}",
            event.phaseId,
            event.instanceId
        )
        checkAndTriggerPhaseTransitions(event.friendshipId, event.instanceId)
    }

    @EventListener
    @Async
    fun onRoutineParameterSet(event: SetRoutineParameterRoutineStep) {
        LOG.info(
            "Evaluating phase transitions after parameter {} set in routine {}",
            event.parameterKey,
            event.instanceId
        )
        checkAndTriggerPhaseTransitions(event.friendshipId, event.instanceId)
    }

    @EventListener
    @Async
    fun onRoutinePhaseTriggered(event: RoutinePhaseTriggered) {
        routinePhaseService.phaseTriggered(
            routineRepository.findById(event.friendshipId, event.instanceId) ?: return, event.phaseId
        )
    }

    private fun checkAndTriggerPhaseTransitions(friendshipId: FriendshipId, instanceId: RoutineInstanceId) {
        val instance = routineRepository.findById(friendshipId, instanceId) ?: run {
            LOG.warn("Cannot check phase transitions: routine instance {} not found", instanceId)
            return
        }
        val template = templateRepository.findById(instance.templateId) ?: run {
            LOG.warn("Cannot check phase transitions: template {} not found for instance {}", 
                instance.templateId, instanceId)
            return
        }

        template.phases.forEach { phase ->
            // Skip if this phase is already active
            if (phase.id == instance.currentPhaseId) return@forEach

            // Skip phases that have no activation condition (should be started manually)
            val condition = phase.condition ?: return@forEach

            try {
                when (condition) {
                is AfterPhaseCompletions -> {
                    val completions = instance.progress.iterations.count {
                        it.phaseId == condition.phaseId && it.completedAt != null
                    }
                    if (completions >= condition.times) {
                        LOG.info(
                            "Activating phase {} after {} completions of phase {}",
                            phase.id, completions, condition.phaseId
                        )
                        routinePhaseService.conditionFulfilled(instance, phase)
                    }
                }

                is AfterParameterSet -> {
                    if (instance.parameters.containsKey(condition.parameterKey)) {
                        LOG.info(
                            "Activating phase {} after parameter {} was set",
                            phase.id, condition.parameterKey
                        )
                        routinePhaseService.conditionFulfilled(instance, phase)
                    }
                }

                is AfterEvent -> {
                    when (condition.eventType) {
                            RoutineAnchorEvent.PHASE_ENTERED -> Unit // Does not make sense for phases but triggers
                            RoutineAnchorEvent.PHASE_LEFT -> Unit // Does not make sense for phases but triggers  
                        RoutineAnchorEvent.ROUTINE_STARTED -> Unit // Already handled in RoutinePhaseService.handleRoutineStart
                    }
                }
                // Other conditions (AfterDays) are handled by scheduler
                else -> Unit
                }
            } catch (e: Exception) {
                LOG.error("Error evaluating condition for phase {}: {}", phase.id, e.message, e)
            }
        }
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
} 