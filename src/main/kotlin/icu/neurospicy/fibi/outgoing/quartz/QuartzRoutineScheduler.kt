package icu.neurospicy.fibi.outgoing.quartz

import icu.neurospicy.fibi.application.routine.RoutinePhaseIterationSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutinePhaseSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutineStepSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutineTriggerSchedulerJob
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.routines.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseIterationsScheduled
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseScheduled
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepScheduled
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineTriggerScheduled
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime


private const val GROUP_ROUTINE_JOBS_KEY = "routineJobs"

@Service
class QuartzRoutineScheduler(
    private val friendshipLedger: FriendshipLedger,
    private val quartzSchedulerService: QuartzSchedulerService,
    private val eventPublisher: ApplicationEventPublisher,
    private val routineEventLog: RoutineEventLog,
    private val timeExpressionEvaluator: TimeExpressionEvaluator,
) : RoutineScheduler {
    override fun scheduleTrigger(
        result: RoutineInstance,
        timeBasedTrigger: RoutineTrigger,
    ) {
        val condition = timeBasedTrigger.condition
        if (condition !is TimeBasedTriggerCondition) return

        val zoneId = friendshipLedger.findTimezoneBy(result.friendshipId) ?: ZoneId.of("UTC")
        val triggerTime: ZonedDateTime = when (condition) {
            is AfterDays -> ZonedDateTime.now(zoneId).plusDays(condition.value.toLong())
            is AfterDuration -> {
                val baseTime = if (condition.reference != null) {
                    val ref = result.parameters[condition.reference]?.value as? Instant
                    ref?.atZone(zoneId) ?: return
                } else {
                    ZonedDateTime.now(zoneId)
                }
                baseTime.plus(condition.duration)
            }
            is AtTimeExpression -> {
                timeExpressionEvaluator.evaluateAtTimeExpression(condition.timeExpression, result, zoneId)
                    ?.atZone(zoneId) ?: return
            }

            is AfterEvent -> {
                timeExpressionEvaluator.evaluateAtTimeExpression(condition.timeExpression, result, zoneId)
                    ?.atZone(zoneId) ?: return
            }

            else -> return
        }

        val payload = mapOf(
            "friendshipId" to result.friendshipId.toString(),
            "routineInstanceId" to result.instanceId.toString(),
            "triggerId" to timeBasedTrigger.id.toString()
        )

        val jobKeyStr = "routine-trigger-${result.friendshipId}-${result.instanceId}-${timeBasedTrigger.id}"
        quartzSchedulerService.scheduleJob(
            jobKeyStr,
            GROUP_ROUTINE_JOBS_KEY,
            triggerTime,
            RoutineTriggerSchedulerJob::class.java,
            payload,
            simpleSchedule().withMisfireHandlingInstructionFireNow()
        )
        LOG.info("Scheduled time-based trigger: ${timeBasedTrigger.id} for routine ${result.instanceId} at $triggerTime")
        eventPublisher.publishEvent(
            RoutineTriggerScheduled(
                this.javaClass, result.instanceId, timeBasedTrigger.id, triggerTime.toInstant()
            )
        )
        routineEventLog.log(
            RoutineEventLogEntry(
                routineInstanceId = result.instanceId,
                friendshipId = result.friendshipId,
                event = RoutineEventType.TRIGGER_SCHEDULED,
                timestamp = Instant.now(),
                metadata = mapOf(
                    "triggerId" to timeBasedTrigger.id.toString(), "scheduledAt" to triggerTime.toInstant()
                )
            )
        )
    }

    override fun scheduleStep(
        instance: RoutineInstance,
        step: RoutineStep,
        phaseId: RoutinePhaseId,
    ) {
        val zoneId = friendshipLedger.findTimezoneBy(instance.friendshipId) ?: ZoneId.of("UTC")

        val triggerTime: ZonedDateTime = when (val tod = step.timeOfDay) {
            is TimeOfDayLocalTime -> {
                val now = ZonedDateTime.now(zoneId)
                var time = now.withHour(tod.time.hour).withMinute(tod.time.minute).withSecond(0).withNano(0)
                if (time.isBefore(now)) {
                    time = time.plusDays(1)
                }
                time
            }

            is TimeOfDayReference -> {
                val ref = instance.parameters[tod.reference]?.value as? Instant ?: return
                ref.atZone(zoneId)
            }


            is TimeOfDayExpression -> {
                val evaluatedTime =
                    timeExpressionEvaluator.evaluateTimeExpression(tod.expression, instance, zoneId) ?: return
                val zonedDateTime = evaluatedTime.atZone(zoneId)
                // If the time is in the past, schedule for tomorrow
                val now = ZonedDateTime.now(zoneId)
                if (zonedDateTime.isBefore(now)) {
                    zonedDateTime.plusDays(1)
                } else {
                    zonedDateTime
                }
            }

            null -> return // No time specified; nothing to schedule
        }

        val payload = mapOf(
            "friendshipId" to instance.friendshipId.toString(),
            "routineInstanceId" to instance.instanceId.toString(),
            "phaseId" to phaseId.toString(),
            "stepId" to step.id.toString()
        )

        val jobKeyStr = "routine-step-${instance.friendshipId}-${instance.instanceId}-${phaseId}-${step.id}"
        quartzSchedulerService.scheduleJob(
            jobKeyStr,
            GROUP_ROUTINE_JOBS_KEY,
            triggerTime,
            RoutineStepSchedulerJob::class.java,
            payload,
            simpleSchedule().withMisfireHandlingInstructionFireNow()
        )
        LOG.info("Scheduled step '${step.id}' in phase $phaseId for routine ${instance.instanceId} at $triggerTime")
        eventPublisher.publishEvent(
            RoutineStepScheduled(
                this.javaClass, instance.instanceId, phaseId, step.id, triggerTime.toInstant()
            )
        )
        routineEventLog.log(
            RoutineEventLogEntry(
                routineInstanceId = instance.instanceId,
                friendshipId = instance.friendshipId,
                event = RoutineEventType.STEP_SCHEDULED,
                timestamp = Instant.now(),
                metadata = mapOf(
                    "stepId" to step.id.toString(),
                    "phaseId" to phaseId.toString(),
                    "scheduledAt" to triggerTime.toInstant()
                )
            )
        )
    }

    override fun schedulePhaseActivation(instance: RoutineInstance, phase: RoutinePhase) {
        val zoneId = friendshipLedger.findTimezoneBy(instance.friendshipId) ?: ZoneId.of("UTC")
        val triggerTime: ZonedDateTime = when (val condition = phase.condition) {
            is AfterDays -> {
                LocalTime.of(0, 0).atDate(ZonedDateTime.now(zoneId).plusDays(condition.value.toLong()).toLocalDate())
                    .atZone(zoneId)
            }

            is AfterDuration -> {
                val baseTime = if (condition.reference != null) {
                    val ref = instance.parameters[condition.reference]?.value as? Instant
                    ref?.atZone(zoneId) ?: return
                } else {
                    ZonedDateTime.now(zoneId)
                }
                baseTime.plus(condition.duration)
            }

            is AtTimeExpression -> {
                timeExpressionEvaluator.evaluateAtTimeExpression(condition.timeExpression, instance, zoneId)
                    ?.atZone(zoneId) ?: return
            }

            is AfterEvent -> {
                timeExpressionEvaluator.evaluateAtTimeExpression(condition.timeExpression, instance, zoneId)
                    ?.atZone(zoneId) ?: return
            }

            else -> return // No time specified; nothing to schedule
        }

        val payload = mapOf(
            "friendshipId" to instance.friendshipId.toString(),
            "routineInstanceId" to instance.instanceId.toString(),
            "phaseId" to phase.id.toString()
        )

        val jobKeyStr = "routine-phase-${instance.friendshipId}-${instance.instanceId}-${phase.id}"
        quartzSchedulerService.scheduleJob(
            jobKeyStr,
            GROUP_ROUTINE_JOBS_KEY,
            triggerTime,
            RoutinePhaseSchedulerJob::class.java,
            payload,
            simpleSchedule().withMisfireHandlingInstructionFireNow()
        )
        LOG.info("Scheduled phase start ${phase.id} for routine ${instance.instanceId} at $triggerTime")
        eventPublisher.publishEvent(
            RoutinePhaseScheduled(
                this.javaClass, instance.instanceId, phase.id, triggerTime.toInstant()
            )
        )
        routineEventLog.log(
            RoutineEventLogEntry(
                routineInstanceId = instance.instanceId,
                friendshipId = instance.friendshipId,
                event = RoutineEventType.PHASE_SCHEDULED,
                timestamp = Instant.now(),
                metadata = mapOf(
                    "phaseId" to phase.id.toString(),
                    "scheduledAt" to triggerTime.toInstant()
                )
            )
        )
    }

    override fun schedulePhaseIterations(instance: RoutineInstance, phase: RoutinePhase) {
        val payload = mapOf(
            "friendshipId" to instance.friendshipId.toString(),
            "routineInstanceId" to instance.instanceId.toString(),
            "phaseId" to phase.id.toString()
        )
        val cronExpression = phase.schedule.cronExpression
        val jobKeyStr = "routine-phase-iteration-${instance.friendshipId}-${instance.instanceId}-${phase.id}"
        quartzSchedulerService.scheduleJob(
            jobKeyStr,
            GROUP_ROUTINE_JOBS_KEY,
            RoutinePhaseIterationSchedulerJob::class.java,
            payload,
            cronExpression
        )
        LOG.info("Scheduled phase iterations ${phase.id} for routine ${instance.instanceId} with cron $cronExpression")
        eventPublisher.publishEvent(
            RoutinePhaseIterationsScheduled(
                this.javaClass, instance.instanceId, phase.id
            )
        )
        routineEventLog.log(
            RoutineEventLogEntry(
                routineInstanceId = instance.instanceId,
                friendshipId = instance.friendshipId,
                event = RoutineEventType.PHASE_ITERATIONS_SCHEDULED,
                timestamp = Instant.now(),
                metadata = mapOf(
                    "phaseId" to phase.id.toString(),
                    "scheduledAt" to phase.schedule.cronExpression
                )
            )
        )
    }

    override fun removeScheduleFor(
        friendshipId: FriendshipId,
        instanceId: RoutineInstanceId,
        phaseId: RoutinePhaseId,
        stepId: RoutineStepId,
    ) {
        val jobKeyStr = "routine-step-${friendshipId}-${instanceId}-${phaseId}-${stepId}"
        quartzSchedulerService.deleteJob(jobKeyStr, GROUP_ROUTINE_JOBS_KEY)
    }

    override fun removePhaseIterationSchedule(
        friendshipId: FriendshipId,
        instanceId: RoutineInstanceId,
        phaseId: RoutinePhaseId,
    ) {
        val jobKeyStr = "routine-phase-iteration-${friendshipId}-${instanceId}-${phaseId}"
        quartzSchedulerService.deleteJob(jobKeyStr, GROUP_ROUTINE_JOBS_KEY)
        LOG.info("Removed phase iteration schedule for phase {} in routine {}", phaseId, instanceId)
    }

    override fun removePhaseActivationSchedule(
        friendshipId: FriendshipId,
        instanceId: RoutineInstanceId,
        phaseId: RoutinePhaseId,
    ) {
        val jobKeyStr = "routine-phase-${friendshipId}-${instanceId}-${phaseId}"
        quartzSchedulerService.deleteJob(jobKeyStr, GROUP_ROUTINE_JOBS_KEY)
        LOG.info("Removed phase activation schedule for phase {} in routine {}", phaseId, instanceId)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(QuartzRoutineScheduler::class.java)
    }
}