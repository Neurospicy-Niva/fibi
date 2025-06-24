package icu.neurospicy.fibi.application.reminder

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

@Component
class ReminderEventsToSignalSender(
    val eventPublisher: ApplicationEventPublisher, val promptsConfiguration: PromptsConfiguration
) {
    @EventListener
    @Async
    fun onAppointmentReminderSet(event: AppointmentReminderSet) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId,
                OutgoingTextMessage(
                    SIGNAL,
                    "__Set reminder: Reminding ${if (event.reminder.remindBeforeAppointment) "before" else "after"} appointments containing ${event.reminder.matchingTitleKeywords.joinToString()}}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onAppointmentReminderUpdated(event: AppointmentReminderUpdated) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId,
                OutgoingTextMessage(
                    SIGNAL,
                    "__Updated reminder: Reminding ${if (event.reminder.remindBeforeAppointment) "before" else "after"} appointments containing ${event.reminder.matchingTitleKeywords.joinToString()}}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onAppointmentReminderUnset(event: AppointmentReminderUnset) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingTextMessage(
                    SIGNAL, "__Removed reminder__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTimeBasedReminderSet(event: ReminderSet) {
        val formattedTriggerTime = event.reminder.trigger.localTime.atZone(event.reminder.trigger.timezone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.US))
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingTextMessage(
                    SIGNAL, "__Set reminder: ${formattedTriggerTime}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTimeBasedReminderUpdated(event: ReminderUpdated) {
        val formattedTriggerTime = event.reminder.trigger.localTime.atZone(event.reminder.trigger.timezone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.US))
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingTextMessage(
                    SIGNAL, "__Updated reminder: ${formattedTriggerTime}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTimeBasedReminderUnset(event: ReminderUnset) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingTextMessage(
                    SIGNAL, "__Removed reminder__"
                )
            )
        )
    }
}