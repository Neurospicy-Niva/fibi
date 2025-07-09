package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStarted
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.*

@Service
class SetupRoutine(
    private val templates: RoutineTemplateRepository,
    private val instances: RoutineRepository,
    private val scheduler: RoutineScheduler,
    private val eventPublisher: ApplicationEventPublisher,
    private val routinePhaseService: RoutinePhaseService,
) {
    fun execute(
        routineTemplateId: RoutineTemplateId, friendshipId: FriendshipId, parameters: Map<String, Any>,
    ): RoutineInstance {
        val template = templates.findById(routineTemplateId)
            ?: throw NoSuchElementException("No routine with id $routineTemplateId")

        val missing = template.setupSteps.filter { it is ParameterRequestStep }.map { it as ParameterRequestStep }
            .map { it.parameterKey }.filter { it !in parameters.keys }

        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("Missing parameters: $missing")
        }

        val firstPhase = template.phases.firstOrNull() ?: throw IllegalArgumentException("Routine has no phases")

        val instance = RoutineInstance(
            _id = UUID.randomUUID().toString(),
            templateId = routineTemplateId,
            friendshipId = friendshipId,
            parameters = parameters.mapValues { TypedParameter.fromValue(it.value) },
            currentPhaseId = firstPhase.id
        )

        instances.save(instance)

        // Schedule all routine-level triggers
        template.triggers.filter { it.condition is TimeBasedTriggerCondition }.forEach { trigger ->
            scheduler.scheduleTrigger(instance, trigger)
        }
        eventPublisher.publishEvent(RoutineStarted(this.javaClass, instance.friendshipId, instance.instanceId))
        routinePhaseService.handleRoutineStart(instance)

        return instance
    }
}