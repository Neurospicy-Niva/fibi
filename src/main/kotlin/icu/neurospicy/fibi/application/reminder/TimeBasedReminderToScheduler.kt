package icu.neurospicy.fibi.application.reminder

import icu.neurospicy.fibi.domain.model.events.ReminderSet
import icu.neurospicy.fibi.domain.model.events.ReminderUnset
import icu.neurospicy.fibi.domain.model.events.ReminderUpdated
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import icu.neurospicy.fibi.outgoing.SchedulerService
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class TimeBasedReminderToScheduler(
    private val friendshipLedger: FriendshipLedger,
    private val reminderRepository: ReminderRepository,
    private val schedulerService: SchedulerService
) {
    @EventListener
    fun onSet(event: ReminderSet) {
        schedulerService.scheduleReminder(event.reminder)
    }

    @EventListener
    fun onUpdated(event: ReminderUpdated) {
        schedulerService.rescheduleReminder(event.reminder)

    }

    @EventListener
    fun onUnset(event: ReminderUnset) {
        schedulerService.removeSchedulerForReminder(event.friendshipId, event.reminderId)

    }

    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        friendshipLedger.findAllIds().map {
            it to reminderRepository.findTimeBasedRemindersBy(it)
        }.forEach {
            it.second.forEach { reminder ->
                schedulerService.scheduleReminder(reminder)
            }
        }
    }
}