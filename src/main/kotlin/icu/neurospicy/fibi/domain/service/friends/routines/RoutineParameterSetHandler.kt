package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.UpdatedRoutineSchedulersOnParameterChange
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class RoutineParameterSetHandler(
    private val instanceRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val routineScheduler: RoutineScheduler,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun handleRoutineParameterSet(event: SetRoutineParameterRoutineStep) {
        val instance = instanceRepository.findById(event.friendshipId, event.instanceId) ?: return
        val template = templateRepository.findById(instance.templateId) ?: return
        val parameterKey = event.parameterKey

        // Reschedule triggers regardless of the current phase state
        val affectedTriggers = template.triggers.filter { trigger ->
            when (val condition = trigger.condition) {
                is AfterDuration -> condition.reference == parameterKey
                else -> false
            }
        }.map { it.id }

        affectedTriggers.forEach { triggerId ->
            val trigger = template.triggers.find { it.id == triggerId } ?: return@forEach
            routineScheduler.scheduleTrigger(instance, trigger)
        }

        // Reschedule steps only if there is an active iteration
        val affectedSteps = instance.progress.iterations.firstOrNull()?.let { currentIteration ->
            val completedStepIds = currentIteration.completedSteps.map { it.id }.toSet()
            template.phases.flatMap { phase ->
                phase.steps.filter { step ->
                    val isAffected = step is ParameterRequestStep && step.parameterKey == parameterKey
                    val isNotCompleted = !completedStepIds.contains(step.id)
                    isAffected && isNotCompleted
                }.map { step -> Pair(phase.id, step.id) }
            }
        } ?: emptyList()

        affectedSteps.forEach { (phaseId, stepId) ->
            val phase = template.phases.find { it.id == phaseId } ?: return@forEach
            val step = phase.steps.find { it.id == stepId } ?: return@forEach
            routineScheduler.scheduleStep(instance, step, phaseId)
        }

        eventPublisher.publishEvent(
            UpdatedRoutineSchedulersOnParameterChange(
                this.javaClass, event.friendshipId, instance.instanceId, affectedSteps, affectedTriggers
            )
        )
    }
} 