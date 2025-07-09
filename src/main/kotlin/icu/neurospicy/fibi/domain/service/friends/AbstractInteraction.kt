package icu.neurospicy.fibi.domain.service.friends

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.MessageInteractionFinished
import icu.neurospicy.fibi.domain.model.events.MessageInteractionStarted
import org.springframework.context.ApplicationEventPublisher
import java.time.ZoneId

abstract class AbstractInteraction(
    private val eventPublisher: ApplicationEventPublisher
) {
    protected abstract suspend fun processUserRequestWithLlm(
        message: UserMessage, friendshipId: FriendshipId
    ): InteractionResult

    suspend fun interactWith(
        message: UserMessage, friendshipId: FriendshipId
    ): InteractionResult {
        eventPublisher.publishEvent(
            MessageInteractionStarted(
                this.javaClass, friendshipId, message.channel, message.messageId
            )
        )
        try {
            return processUserRequestWithLlm(message, friendshipId)
        } finally {
            eventPublisher.publishEvent(
                MessageInteractionFinished(
                    this.javaClass, friendshipId, message.channel, message.messageId
                )
            )
        }
    }

    protected fun appointmentsToPromptList(
        appointments: List<Appointment>, timezone: ZoneId
    ) = appointments.joinToString("\n") {
        val startTime = it.startAt.instant.atZone(timezone).toLocalTime()
        val endTime = it.endAt.instant.atZone(timezone).toLocalTime()
        "$startTime-$endTime: ${it.summary}"
    }.ifBlank { "No appointments yet" }

    protected fun tasksToPromptList(tasks: List<Task>) = tasks.joinToString("\n") {
        "- **${it.title}**${if (it.description != null) ": ${it.description}" else ""}, state:${if (it.completed) "complete" else "todo"}\n\t(id=${it.id})"
    }.ifBlank { "The user has no tasks yet." }

    protected fun appointmentRemindersToPromptList(appointmentReminders: List<AppointmentReminder>) =
        appointmentReminders.joinToString("\n|") { reminder ->
            "- Appointment reminder: matching titles containing=${reminder.matchingTitleKeywords.joinToString()},remindBeforeAppointment=${reminder.remindBeforeAppointment}" + (reminder.text.let {
                ", text='${it}'\n(id=${reminder._id})"
            }.ifBlank { "No appointment reminders found" })
        }

    protected fun timeBasedRemindersToPromptList(reminders: List<Reminder>) =
        reminders.joinToString("\n|") { reminder ->
            "- Time-based reminder: trigger=(localTime=${reminder.trigger.localTime},zoneId=${reminder.trigger.timezone})" + (reminder.text.let { ", text='${it}'" }) + "\n(id=${reminder._id})"
        }.ifBlank { "No time-based reminders found" }
}