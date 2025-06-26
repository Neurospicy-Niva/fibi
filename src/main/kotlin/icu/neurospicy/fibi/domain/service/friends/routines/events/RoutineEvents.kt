package icu.neurospicy.fibi.domain.service.friends.routines.events

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineInstanceId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutinePhaseId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineStepId
import icu.neurospicy.fibi.domain.service.friends.routines.TriggerId
import org.springframework.context.ApplicationEvent
import java.time.Instant


data class RoutineTriggerScheduled(
    val _source: Any,
    val routineInstanceId: RoutineInstanceId,
    val triggerId: TriggerId,
    val scheduledAt: Instant,
) : ApplicationEvent(_source)

data class RoutineStepScheduled(
    val _source: Any,
    val routineInstanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
    val stepId: RoutineStepId,
    val scheduledAt: Instant,
) : ApplicationEvent(_source)

data class RoutinePhaseScheduled(
    val _source: Any,
    val routineInstanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
    val scheduledAt: Instant,
) : ApplicationEvent(_source)

data class RoutinePhaseIterationsScheduled(
    val _source: Any,
    val routineInstanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)


data class RoutinePhaseIterationStarted(
    val _source: Any,
    val routineInstanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)

data class PhaseIterationCompleted(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)

data class RoutineStarted(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
) : ApplicationEvent(_source)

data class PhaseActivated(
    val _source: Any,
    val instanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)

data class PhaseDeactivated(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)


data class RoutineTriggerFired(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val triggerId: TriggerId,
) : ApplicationEvent(_source)

data class RoutinePhaseTriggered(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)


data class RoutinePhaseIterationTriggered(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
) : ApplicationEvent(_source)

data class RoutineStepTriggered(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val phaseId: RoutinePhaseId,
    val stepId: RoutineStepId,
) : ApplicationEvent(_source)

sealed interface CompletedRoutineStep {
    val instanceId: RoutineInstanceId
    val phaseId: RoutinePhaseId
    val stepId: RoutineStepId
    val friendshipId: FriendshipId
}

data class ConfirmedActionStep(
    val _source: Any,
    override val friendshipId: FriendshipId,
    override val instanceId: RoutineInstanceId,
    override val phaseId: RoutinePhaseId,
    override val stepId: RoutineStepId,
) : ApplicationEvent(_source), CompletedRoutineStep

data class SetRoutineParameterRoutineStep(
    val _source: Any,
    override val friendshipId: FriendshipId,
    override val instanceId: RoutineInstanceId,
    override val phaseId: RoutinePhaseId,
    override val stepId: RoutineStepId,
    val parameterKey: String,
) : ApplicationEvent(_source), CompletedRoutineStep

data class SentMessageForRoutineStep(
    val _source: Any,
    override val friendshipId: FriendshipId,
    override val instanceId: RoutineInstanceId,
    override val phaseId: RoutinePhaseId,
    override val stepId: RoutineStepId,
) : ApplicationEvent(_source), CompletedRoutineStep


data class UpdatedRoutineSchedulersOnParameterChange(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val stepIds: List<Pair<RoutinePhaseId, RoutineStepId>>,
    val triggerIds: List<TriggerId>,
) : ApplicationEvent(_source)


data class StopRoutineForToday(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
    val reason: String? = null,
) : ApplicationEvent(_source)


data class StoppedTodaysRoutine(
    val _source: Any,
    val friendshipId: FriendshipId,
    val instanceId: RoutineInstanceId,
) : ApplicationEvent(_source)