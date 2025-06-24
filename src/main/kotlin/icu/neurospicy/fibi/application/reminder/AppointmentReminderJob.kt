package icu.neurospicy.fibi.application.reminder

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
data class AppointmentReminderJob(
    private val reminderRepository: ReminderRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val promptsConfiguration: PromptsConfiguration
) : Job {
    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        val owner = FriendshipId(jobData.getString("owner"))
        val reminder =
            jobData.getString("reminderId").let { reminderRepository.findAppointmentReminderBy(owner, it) ?: return }
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass,
                owner,
                reminder.text.let { OutgoingTextMessage(Channel.SIGNAL, reminder.text) }
            )
        )
    }
}