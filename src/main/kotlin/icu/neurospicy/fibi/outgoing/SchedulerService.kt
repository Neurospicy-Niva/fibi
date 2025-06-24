package icu.neurospicy.fibi.outgoing

import icu.neurospicy.fibi.domain.model.*

interface SchedulerService {
    /**
     * Schedules a routine based on its configuration.
     * The payload is generated internally.
     * Assumes that routine.id is not null.
     */
    fun scheduleRoutine(routine: RoutineConfiguration)

    /**
     * Reinitializes all active routines.
     */
    fun reinitializeRoutines(routines: List<RoutineConfiguration>)

    fun scheduleCalendarSync(friendshipId: FriendshipId, calendarConfigId: CalendarConfigId)

    fun scheduleReminder(reminder: Reminder)
    fun rescheduleReminder(reminder: Reminder)
    fun removeSchedulerForReminder(owner: FriendshipId, reminderId: String)
    fun scheduleReminder(reminder: AppointmentReminder, appointment: Appointment)
    fun rescheduleReminder(reminder: AppointmentReminder, appointment: Appointment)
    fun removeAppointmentReminderSchedulerFor(
        owner: FriendshipId, reminderId: String, appointmentId: AppointmentId
    )

    fun scheduleGeneratingMessage(friendshipId: FriendshipId, channel: Channel)
    fun removeGeneratingMessageScheduler(friendshipId: FriendshipId, channel: Channel)

    fun scheduleTimer(timer: Timer)
    fun rescheduleTimer(timer: Timer)
    fun removeSchedulerForTimer(friendshipId: FriendshipId, timerId: String)
}