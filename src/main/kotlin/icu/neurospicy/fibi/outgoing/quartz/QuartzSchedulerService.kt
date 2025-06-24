package icu.neurospicy.fibi.outgoing.quartz

import icu.neurospicy.fibi.application.GeneratingMessageJob
import icu.neurospicy.fibi.application.reminder.AppointmentReminderJob
import icu.neurospicy.fibi.application.reminder.TimeBasedReminderJob
import icu.neurospicy.fibi.application.routine.GenericRoutineJob
import icu.neurospicy.fibi.application.timers.TimerJob
import icu.neurospicy.fibi.calendar.sync.GenericCalendarJob
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.Timer
import icu.neurospicy.fibi.outgoing.SchedulerService
import org.quartz.*
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.JobBuilder.newJob
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.Trigger
import org.quartz.TriggerBuilder.newTrigger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.ZonedDateTime.now
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random.Default.nextInt

@Service
class QuartzSchedulerService(
    private val scheduler: Scheduler,
) : SchedulerService {

    companion object {
        private val LOG = LoggerFactory.getLogger(QuartzSchedulerService::class.java)
        private const val ROUTINE_GROUP = "routineJobs"
        private const val CALENDAR_GROUP = "calendarJobs"
        private const val TIME_BASED_REMINDER_GROUP = "timeBasedReminderJobs"
        private const val TIMER_GROUP = "timerJobs"
        private const val APPOINTMENT_REMINDER_GROUP = "appointmentReminderJobs"
        private const val GENERATING_MESSAGE_GROUP = "generatingMessageJobs"
    }

    /**
     * Schedules a routine based on its configuration.
     * The payload is generated internally.
     * Assumes that routine.id is not null.
     */
    override fun scheduleRoutine(routine: RoutineConfiguration) {
        if (!routine.enabled) {
            LOG.debug("Routine ${routine.id} for friendshipId=${routine.friendshipId} is not enabled; skipping scheduling.")
            return
        }

        // Compute the next occurrence based on the trigger.
        val triggerTime = computeNextOccurrence(routine.trigger)

        // Generate a payload from the routine configuration.
        val payload = generatePayload(routine)

        val jobKeyStr = "routine-${routine.friendshipId}-${routine.id}"
        scheduleJob(
            jobKeyStr,
            ROUTINE_GROUP,
            triggerTime,
            GenericRoutineJob::class.java,
            payload,
            simpleSchedule().withIntervalInHours(24).repeatForever().withMisfireHandlingInstructionFireNow()
        )
    }

    /**
     * Reinitializes all active routines.
     */
    override fun reinitializeRoutines(routines: List<RoutineConfiguration>) {
        routines.forEach { scheduleRoutine(it) }
    }

    /**
     * Generic method to schedule a job using Quartz. Deletes job first if it existed.
     */
    fun scheduleJob(
        jobKeyStr: String,
        group: String,
        triggerTime: ZonedDateTime,
        jobClass: Class<out Job>,
        payload: Map<String, Any>,
        scheduleBuilder: SimpleScheduleBuilder,
    ) {
        val jobKey = JobKey.jobKey(jobKeyStr, group)
        val jobData = JobDataMap(payload)
        val trigger = newTrigger().withIdentity("trigger-$jobKeyStr", group)
            .startAt(Date.from(triggerTime.toInstant())).withSchedule(
                scheduleBuilder
            ).build()
        scheduleJob(jobKey, jobClass, jobData, trigger)
    }

    /**
     * Generic method to schedule a job using Quartz. Deletes job first if it existed.
     */
    fun scheduleJob(
        jobKeyStr: String,
        group: String,
        jobClass: Class<out Job>,
        payload: Map<String, Any>,
        cronExpression: String,
    ) {
        val jobKey = JobKey.jobKey(jobKeyStr, group)
        val jobData = JobDataMap(payload)
        val trigger = newTrigger().withIdentity("trigger-$jobKeyStr", group)
            .withSchedule(cronSchedule(cronExpression))
            .build()
        scheduleJob(jobKey, jobClass, jobData, trigger)
    }


    private fun scheduleJob(
        jobKey: JobKey,
        jobClass: Class<out Job>,
        jobData: JobDataMap,
        trigger: Trigger,
    ) {
        deleteJob(jobKey)
        val jobDetail = newJob(jobClass).withIdentity(jobKey).usingJobData(jobData).build()
        scheduler.scheduleJob(jobDetail, trigger)
        LOG.debug("Scheduled job {} to run at {}", jobKey, trigger.nextFireTime)
    }

    fun deleteJob(jobKeyStr: String, group: String) {
        val jobKey = JobKey.jobKey(jobKeyStr, group)
    }

    private fun deleteJob(jobKey: JobKey) {
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey)
        }
    }

    /**
     * Computes the next occurrence for a TimeBasedTrigger.
     */
    private fun computeNextOccurrence(trigger: LocalTimeBasedTrigger): ZonedDateTime {
        val zoneId = trigger.timezone
        var nextOccurrence =
            now(zoneId).withHour(trigger.localTime.hour).withMinute(trigger.localTime.minute).withSecond(0).withNano(0)
        if (now(zoneId) >= nextOccurrence) {
            nextOccurrence = nextOccurrence.plusDays(1)
        }
        return nextOccurrence
    }

    override fun scheduleCalendarSync(friendshipId: FriendshipId, calendarConfigId: CalendarConfigId) {
        // Compute the next occurrence
        val triggerTime = computeNextOccurrence(nextInt(0, 20))

        // Generate a payload from the routine configuration.
        val payload = generatePayload(friendshipId, calendarConfigId)

        val jobKeyStr = "calendar-${friendshipId}-${calendarConfigId}"
        scheduleJob(
            jobKeyStr,
            CALENDAR_GROUP,
            triggerTime,
            GenericCalendarJob::class.java,
            payload,
            simpleSchedule().withIntervalInMinutes(20).repeatForever().withMisfireHandlingInstructionFireNow()
        )
    }

    private fun computeNextOccurrence(startMinutes: Int): ZonedDateTime {
        val now = now()
        return now.withMinute(startMinutes.times(ceil(now.minute.toFloat() / startMinutes.toFloat()).toInt()).mod(60))
            .let { if (it > now) it else it.plusHours(1) }
    }

    private fun generatePayload(routine: RoutineConfiguration): Map<String, Any> {
        return mapOf(
            "friendshipId" to routine.friendshipId.toString(),
            "routineId" to routine.id!!.toString(), // routine.id is not null by design.
            "routineType" to routine.name, // e.g. "Morning routine"
        )
    }

    private fun generatePayload(friendshipId: FriendshipId, calendarConfigId: CalendarConfigId): Map<String, Any> {
        return mapOf(
            "friendshipId" to friendshipId.toString(),
            "calendarConfigId" to calendarConfigId.toString(),
        )
    }

    override fun scheduleReminder(reminder: Reminder) {
        val jobKeyStr = "reminder-${reminder.owner}-${reminder._id}"
        scheduleJob(
            jobKeyStr,
            TIME_BASED_REMINDER_GROUP,
            reminder.trigger.localTime.atZone(reminder.trigger.timezone),
            TimeBasedReminderJob::class.java,
            mapOf("owner" to reminder.owner.toString(), "reminderId" to reminder._id!!),
            simpleSchedule().withMisfireHandlingInstructionFireNow()
        )
    }

    override fun removeSchedulerForReminder(owner: FriendshipId, reminderId: String) {
        val jobKeyStr = "reminder-${owner}-${reminderId}"
        scheduler.deleteJob(JobKey.jobKey(jobKeyStr, TIME_BASED_REMINDER_GROUP))
    }

    override fun rescheduleReminder(reminder: Reminder) {
        scheduleReminder(reminder)
    }

    override fun scheduleReminder(reminder: AppointmentReminder, appointment: Appointment) {
        val remindAt = if (reminder.remindBeforeAppointment) appointment.startAt else appointment.endAt
        val jobKeyStr = "reminder-${reminder.owner}-${reminder._id}-${appointment.appointmentId}"
        scheduleJob(
            jobKeyStr,
            APPOINTMENT_REMINDER_GROUP,
            remindAt.instant.atZone(remindAt.zoneId),
            AppointmentReminderJob::class.java,
            mapOf(
                "owner" to reminder.owner.toString(),
                "reminderId" to reminder._id!!,
                "relatedVEvent" to appointment.relatedVEvent,
                "startAt" to appointment.startAt.instant
            ),
            simpleSchedule().withMisfireHandlingInstructionFireNow()
        )
    }

    override fun rescheduleReminder(reminder: AppointmentReminder, appointment: Appointment) {
        scheduleReminder(reminder, appointment)
    }

    override fun removeAppointmentReminderSchedulerFor(
        owner: FriendshipId, reminderId: String, appointmentId: AppointmentId,
    ) {
        val jobKeyStr = "reminder-${owner}-${reminderId}-${appointmentId}"
        scheduler.deleteJob(JobKey.jobKey(jobKeyStr, APPOINTMENT_REMINDER_GROUP))
    }

    override fun scheduleGeneratingMessage(friendshipId: FriendshipId, channel: Channel) {
        val jobKeyStr = "message-${friendshipId}-${channel}"
        scheduleJob(
            jobKeyStr, GENERATING_MESSAGE_GROUP, now(), GeneratingMessageJob::class.java, mapOf(
                "receiver" to friendshipId.toString(), "channel" to channel.name
            ), simpleSchedule().withIntervalInSeconds(4).withRepeatCount(25)
        )
    }

    override fun removeGeneratingMessageScheduler(friendshipId: FriendshipId, channel: Channel) {
        val jobKeyStr = "message-${friendshipId}-${channel}"
        scheduler.deleteJob(JobKey.jobKey(jobKeyStr, GENERATING_MESSAGE_GROUP))
    }

    override fun scheduleTimer(timer: Timer) {
        val jobKeyStr = "timer-${timer.owner}-${timer._id}"
        scheduleJob(
            jobKeyStr,
            TIMER_GROUP,
            (timer.startedAt + timer.duration).atZone(ZoneId.systemDefault()),
            TimerJob::class.java,
            mapOf("owner" to timer.owner.toString(), "timerId" to timer._id!!),
            simpleSchedule().withMisfireHandlingInstructionFireNow()
        )
    }

    override fun rescheduleTimer(timer: Timer) {
        scheduleTimer(timer)
    }

    override fun removeSchedulerForTimer(
        friendshipId: FriendshipId, timerId: String,
    ) {
        val jobKeyStr = "timer-${friendshipId}-${timerId}"
        scheduler.deleteJob(JobKey.jobKey(jobKeyStr, TIMER_GROUP))
    }
}