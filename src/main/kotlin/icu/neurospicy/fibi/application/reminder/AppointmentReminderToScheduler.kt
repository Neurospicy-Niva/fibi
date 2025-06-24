package icu.neurospicy.fibi.application.reminder

import icu.neurospicy.fibi.domain.model.Appointment
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.events.AppointmentReminderSet
import icu.neurospicy.fibi.domain.model.events.AppointmentReminderUnset
import icu.neurospicy.fibi.domain.model.events.AppointmentReminderUpdated
import icu.neurospicy.fibi.domain.model.events.AppointmentsUpdated
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import icu.neurospicy.fibi.outgoing.SchedulerService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant.now

/**
 * Listens for events indicating appointment or reminder modifications and app start
 * to set up the schedulers and update the related appointment id in the reminder.
 */
@Service
class AppointmentReminderToScheduler(
    private val friendshipLedger: FriendshipLedger,
    private val reminderRepository: ReminderRepository,
    private val calendarRepository: CalendarRepository,
    private val schedulerService: SchedulerService
) {
    @EventListener
    fun onSet(event: AppointmentReminderSet) {
        LOG.debug(
            "Setting schedulers for appointment reminder {} ({})",
            event.reminder._id,
            event.reminder.matchingTitleKeywords
        )
        setupSchedulersForMatchingAppointmentsAndSaveRelatedAppointments(event.reminder, event.friendshipId)
    }

    @EventListener
    fun onUpdated(event: AppointmentReminderUpdated) {
        LOG.debug(
            "Updating schedulers for appointment reminder {} ({})",
            event.reminder._id,
            event.reminder.matchingTitleKeywords
        )
        event.reminder.relatedAppointmentIds.forEach {
            schedulerService.removeAppointmentReminderSchedulerFor(event.friendshipId, event.reminder._id!!, it)
        }
        setupSchedulersForMatchingAppointmentsAndSaveRelatedAppointments(event.reminder, event.friendshipId)

    }

    @EventListener
    fun onUnset(event: AppointmentReminderUnset) {
        LOG.debug("Removing schedulers for appointment reminder ${event.reminderId}")
        event.relatedAppointmentIds.forEach {
            schedulerService.removeAppointmentReminderSchedulerFor(event.friendshipId, event.reminderId, it)
        }
    }

    @EventListener
    fun onAppointmentsUpdated(event: AppointmentsUpdated) {
        LOG.debug(
            "Updating schedulers for appointment reminders on appointment change (new {}, changed {}, deleted: {})",
            event.newAppointmentIds.size,
            event.changedAppointmentIds.size,
            event.deletedAppointmentIds
        )
        val newAndChangedAppointments =
            calendarRepository.loadAppointmentsByAppointmentId(
                event.owner,
                event.newAppointmentIds.plus(event.changedAppointmentIds)
            )
        reminderRepository.findAppointmentRemindersBy(event.owner).forEach { reminder ->
            //remove reminders for all deleted and changed appointments
            //(changed appointments might not match anymore)
            val relatedAppointmentIds =
                reminder.relatedAppointmentIds.minus(
                    event.deletedAppointmentIds.plus(event.changedAppointmentIds)
                        .filter { reminder.relatedAppointmentIds.contains(it) }.onEach { deletedAppointmentId ->
                            schedulerService.removeAppointmentReminderSchedulerFor(
                                event.owner, reminder._id!!, deletedAppointmentId
                            )
                        }.toSet()
                ).plus(
                    setupSchedulersForMatchingAppointmentsAndReturnIds(
                        newAndChangedAppointments, reminder
                    )
                )
            reminderRepository.updateRelatedAppointmentIds(reminder._id, relatedAppointmentIds)
        }
    }

    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.debug("Setting up appointment reminders")
        friendshipLedger.findAllIds().forEach { friendshipId ->
            reminderRepository.findAppointmentRemindersBy(friendshipId).forEach { reminder ->
                setupSchedulersForMatchingAppointmentsAndSaveRelatedAppointments(reminder, friendshipId)
            }
        }
    }

    private fun setupSchedulersForMatchingAppointmentsAndSaveRelatedAppointments(
        reminder: AppointmentReminder, friendshipId: FriendshipId
    ) {
        reminderRepository.updateRelatedAppointmentIds(
            reminder._id, setupSchedulersForMatchingAppointmentsAndReturnIds(
                calendarRepository.loadAppointmentsForTimeRange(
                    TimeRange(now(), Duration.ofDays(30)), friendshipId
                ), reminder
            )
        )
    }

    private fun setupSchedulersForMatchingAppointmentsAndReturnIds(
        appointments: List<Appointment>, reminder: AppointmentReminder
    ) = appointments.asSequence()
        .filter { (if (reminder.remindBeforeAppointment) it.startAt else it.endAt).instant.isAfter(now()) }
        .filter { reminder.matches(it.summary) }
        .onEach { schedulerService.scheduleReminder(reminder, it) }.map { it.appointmentId }.toSet()

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}