package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId

/**
 * Scheduler interface for routine-related jobs with clear distinction between scheduler types:
 * 
 * 1. **TriggerCondition-based schedulers** (phase.condition):
 *    - Handle future phase activations based on AfterDays, AfterDuration, etc.
 *    - One-time execution when condition is met
 *    - Should persist across phase transitions
 * 
 * 2. **Schedule-based iteration schedulers** (phase.schedule):
 *    - Handle recurring phase iterations based on cron expressions (DAILY, WEEKLY, etc.)
 *    - Continuous execution while phase is active
 *    - Should be removed when phase is deactivated
 * 
 * 3. **Step schedulers** (step.timeOfDay):
 *    - Handle individual step reminders within a phase iteration
 *    - One-time execution per iteration
 *    - Should be removed when steps are completed or phase is deactivated
 */
interface RoutineScheduler {
    fun scheduleTrigger(result: RoutineInstance, timeBasedTrigger: RoutineTrigger)
    
    fun scheduleStep(instance: RoutineInstance, step: RoutineStep, phaseId: RoutinePhaseId)
    
    /** Schedules phase activation based on TriggerCondition (phase.condition) */
    fun schedulePhaseActivation(instance: RoutineInstance, phase: RoutinePhase)
    
    /** Schedules recurring phase iterations based on schedule expression (phase.schedule) */
    fun schedulePhaseIterations(instance: RoutineInstance, phase: RoutinePhase)
    
    fun removeScheduleFor(friendshipId: FriendshipId, instanceId: RoutineInstanceId, phaseId: RoutinePhaseId, stepId: RoutineStepId)
    
    /** Removes recurring iteration scheduler (phase.schedule-based) */
    fun removePhaseIterationSchedule(friendshipId: FriendshipId, instanceId: RoutineInstanceId, phaseId: RoutinePhaseId)
    
    /** Removes TriggerCondition-based activation scheduler (phase.condition-based) */
    fun removePhaseActivationSchedule(friendshipId: FriendshipId, instanceId: RoutineInstanceId, phaseId: RoutinePhaseId)
}