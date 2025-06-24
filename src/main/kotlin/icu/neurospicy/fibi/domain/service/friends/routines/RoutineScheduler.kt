package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId

interface RoutineScheduler {
    fun scheduleTrigger(result: RoutineInstance, timeBasedTrigger: RoutineTrigger)
    fun scheduleStep(instance: RoutineInstance, step: RoutineStep, phaseId: RoutinePhaseId)
    fun schedulePhase(instance: RoutineInstance, phase: RoutinePhase)
    fun schedulePhaseIterations(instance: RoutineInstance, phase: RoutinePhase)
    fun removeScheduleFor(friendshipId: FriendshipId, instanceId: RoutineInstanceId, phaseId: RoutinePhaseId, stepId: RoutineStepId)
}