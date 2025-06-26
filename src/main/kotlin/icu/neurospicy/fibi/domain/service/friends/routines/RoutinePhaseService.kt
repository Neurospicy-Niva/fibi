package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseIterationStarted
import icu.neurospicy.fibi.domain.service.friends.routines.events.StoppedTodaysRoutine
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RoutinePhaseService(
    private val templateRepository: RoutineTemplateRepository,
    private val routineScheduler: RoutineScheduler,
    private val phaseActivator: RoutinePhaseActivator,
    private val routineEventLog: RoutineEventLog,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun handleRoutineStart(instance: RoutineInstance) {
        val template = templateRepository.findById(instance.templateId) ?: return

        template.phases.forEach { phase ->
            when (val condition = phase.condition) {
                is AfterDays -> {
                    // Schedule phase to start after specified days
                    routineScheduler.schedulePhase(instance, phase)
                }

                is AfterDuration -> {
                    if (condition.reference != null) {
                        // Check if the referenced parameter exists
                        if (instance.parameters[condition.reference] != null) {
                            routineScheduler.schedulePhase(instance, phase)
                        }
                        // If parameter doesn't exist, don't schedule
                    } else {
                        // No reference, schedule immediately
                        routineScheduler.schedulePhase(instance, phase)
                    }
                }

                is AfterParameterSet -> {
                    // Check if the parameter is set
                    if (instance.parameters[condition.parameterKey] != null) {
                        phaseActivator.activatePhase(instance, phase)
                    }
                    // If parameter is not set, don't activate
                }

                is AfterPhaseCompletions -> {
                    if (instance.progress.iterations.count { it.phaseId == condition.phaseId && it.completedAt != null } >= condition.times) {
                        phaseActivator.activatePhase(instance, phase)
                    }
                }

                is AfterEvent -> {
                    if (condition.eventType == RoutineAnchorEvent.ROUTINE_STARTED) {
                        routineScheduler.schedulePhase(instance, phase)
                    }
                }

                null -> {
                    // No condition, activate immediately
                    phaseActivator.activatePhase(instance, phase)
                }
            }
        }
    }

    fun phaseTriggered(instance: RoutineInstance, phaseId: RoutinePhaseId) {
        val template = templateRepository.findById(instance.templateId) ?: return
        phaseActivator.activatePhase(instance, template.phases.firstOrNull { it.id == phaseId } ?: return)
    }

    fun startPhaseIteration(instance: RoutineInstance, phaseId: RoutinePhaseId) {
        val template = templateRepository.findById(instance.templateId) ?: return
        val phase = template.phases.firstOrNull { it.id == phaseId } ?: return
        phase.steps.forEach { step ->
            when (step.timeOfDay) {
                is TimeOfDayLocalTime -> routineScheduler.scheduleStep(instance, step, phaseId)
                is TimeOfDayReference -> if (instance.parameters[(step.timeOfDay as TimeOfDayReference).reference] != null) {
                    routineScheduler.scheduleStep(instance, step, phaseId)
                }

                else -> Unit
            }
        }
        routineEventLog.log(
            RoutineEventLogEntry(
                instance.instanceId,
                friendshipId = instance.friendshipId,
                event = RoutineEventType.PHASE_ITERATION_STARTED,
                timestamp = Instant.now(),
                metadata = mapOf("phaseId" to phaseId),
            )
        )
        eventPublisher.publishEvent(RoutinePhaseIterationStarted(this.javaClass, instance.instanceId, phaseId))
    }

    fun handleStoppedRoutineIteration(instance: RoutineInstance, reason: String? = null) {
        val template = templateRepository.findById(instance.templateId) ?: return
        val phase = template.phases.firstOrNull { it.id == instance.currentPhaseId } ?: return
        val completedSteps = instance.progress.iterations.first().completedSteps.map { it.id }
        phase.steps.filterNot { it.id in completedSteps }.forEach { step ->
            routineScheduler.removeScheduleFor(
                instance.friendshipId,
                instance.instanceId,
                instance.currentPhaseId!!,
                step.id
            )
        }
        routineEventLog.log(
            RoutineEventLogEntry(
                instance.instanceId,
                instance.friendshipId,
                RoutineEventType.ROUTINE_STOPPED_FOR_TODAY,
                Instant.now(),
                reason?.let { mapOf("reason" to it) } ?: emptyMap(),
            )
        )
        eventPublisher.publishEvent(StoppedTodaysRoutine(this.javaClass, instance.friendshipId, instance.instanceId))
    }

    fun conditionFulfilled(instance: RoutineInstance, phase: RoutinePhase) {
        phaseActivator.activatePhase(instance, phase)
    }

    companion object {
        val LOG = LoggerFactory.getLogger(this::class.java)
    }
}