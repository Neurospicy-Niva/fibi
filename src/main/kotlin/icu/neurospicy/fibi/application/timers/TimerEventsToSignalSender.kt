package icu.neurospicy.fibi.application.timers

import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.model.events.TimerSet
import icu.neurospicy.fibi.domain.model.events.TimerStopped
import icu.neurospicy.fibi.domain.model.events.TimerUpdated
import icu.neurospicy.fibi.domain.repository.TimerRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class TimerEventsToSignalSender(
    val eventPublisher: ApplicationEventPublisher,
    private val timerRepository: TimerRepository
) {
    @EventListener
    @Async
    fun onTimerSet(event: TimerSet) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingTextMessage(
                    SIGNAL, "__Added timer ${event.timer.label.ifBlank { "without name" }}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTimerUpdated(event: TimerUpdated) {
        val oldLabel = timerRepository.findByFriendshipId(event.friendshipId).first { it._id == event.timer._id }.label
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId,
                OutgoingTextMessage(
                    SIGNAL, "__Updated timer ${event.timer.label.ifBlank { "without name" }}__ --$oldLabel--"
                ),
            )
        )
    }

    @EventListener
    @Async
    fun onTimerStopped(event: TimerStopped) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingTextMessage(
                    SIGNAL, "__Removed timer__"
                )
            )
        )
    }
}