package icu.neurospicy.fibi.application.timers

import icu.neurospicy.fibi.domain.model.events.TimerSet
import icu.neurospicy.fibi.domain.model.events.TimerStopped
import icu.neurospicy.fibi.domain.model.events.TimerUpdated
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TimerRepository
import icu.neurospicy.fibi.outgoing.SchedulerService
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class TimerToScheduler(
    private val friendshipLedger: FriendshipLedger,
    private val timerRepository: TimerRepository,
    private val schedulerService: SchedulerService
) {
    @EventListener
    fun onSet(event: TimerSet) {
        schedulerService.scheduleTimer(event.timer)
    }

    @EventListener
    fun onUpdated(event: TimerUpdated) {
        schedulerService.rescheduleTimer(event.timer)

    }

    @EventListener
    fun onUnset(event: TimerStopped) {
        schedulerService.removeSchedulerForTimer(event.friendshipId, event.timerId)
    }

    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        friendshipLedger.findAllIds().map {
            it to timerRepository.findByFriendshipId(it)
        }.forEach {
            it.second.forEach { timer ->
                schedulerService.scheduleTimer(timer)
            }
        }
    }
}