package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseActivated
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseDeactivated
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Handles AfterEvent trigger conditions by listening to phase lifecycle events
 * and rescheduling triggers that depend on these events.
 */
@Service
class RoutineAfterEventHandler(
    private val routineRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val routineScheduler: RoutineScheduler,
) {

    @EventListener
    @Async
    fun onPhaseActivated(event: PhaseActivated) {
        LOG.info(
            "Evaluating AfterEvent triggers for PHASE_ENTERED after phase {} activated in routine {}",
            event.phaseId,
            event.instanceId
        )
        rescheduleAfterEventTriggers(event.friendshipId, event.instanceId, RoutineAnchorEvent.PHASE_ENTERED, event.phaseId)
    }

    @EventListener
    @Async
    fun onPhaseDeactivated(event: PhaseDeactivated) {
        LOG.info(
            "Evaluating AfterEvent triggers for PHASE_LEFT after phase {} deactivated in routine {}",
            event.phaseId,
            event.instanceId
        )
        rescheduleAfterEventTriggers(event.friendshipId, event.instanceId, RoutineAnchorEvent.PHASE_LEFT, event.phaseId)
    }

    private fun rescheduleAfterEventTriggers(
        friendshipId: FriendshipId,
        instanceId: RoutineInstanceId,
        eventType: RoutineAnchorEvent,
        phaseId: RoutinePhaseId
    ) {
        val instance = routineRepository.findById(friendshipId, instanceId) ?: run {
            LOG.warn("Cannot reschedule AfterEvent triggers: routine instance {} not found", instanceId)
            return
        }

        val template = templateRepository.findById(instance.templateId) ?: run {
            LOG.warn(
                "Cannot reschedule AfterEvent triggers: template {} not found for instance {}",
                instance.templateId, instanceId
            )
            return
        }

        // Find all triggers that depend on this event type
        val affectedTriggers = template.triggers.filter { trigger ->
            when (val condition = trigger.condition) {
                is AfterEvent -> {
                    condition.eventType == eventType && 
                    (condition.phaseTitle == null || 
                     template.findPhase(phaseId)?.title == condition.phaseTitle)
                }
                else -> false
            }
        }

        if (affectedTriggers.isEmpty()) {
            LOG.debug("No AfterEvent triggers found for event {} in routine {}", eventType, instanceId)
            return
        }

        // Add event timestamp to routine instance parameters
        val eventTimestamp = Instant.now()
        val parameterKey = when (eventType) {
            RoutineAnchorEvent.PHASE_ENTERED -> "PHASE_ENTERED"
            RoutineAnchorEvent.PHASE_LEFT -> "PHASE_LEFT"
            RoutineAnchorEvent.ROUTINE_STARTED -> "ROUTINE_START"
        }
        
        val updatedInstance = instance.withParameter(parameterKey, eventTimestamp)
        routineRepository.save(updatedInstance)
        
        LOG.debug(
            "Added event timestamp {} for event {} to routine instance {}",
            eventTimestamp, eventType, instanceId
        )

        // Reschedule each affected trigger with the updated instance
        affectedTriggers.forEach { trigger ->
            LOG.debug(
                "Rescheduling AfterEvent trigger {} for event {} in routine {}",
                trigger.id, eventType, instanceId
            )
            routineScheduler.scheduleTrigger(updatedInstance, trigger)
        }

        LOG.info(
            "Rescheduled {} AfterEvent triggers for event {} in routine {}",
            affectedTriggers.size, eventType, instanceId
        )
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineAfterEventHandler::class.java)
    }
} 