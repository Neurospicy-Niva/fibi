package icu.neurospicy.fibi.application.timers

import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.OutgoingGeneratedMessage
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.TimerRepository
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
data class TimerJob(
    private val timerRepository: TimerRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val promptsConfiguration: PromptsConfiguration
) : Job {
    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        val owner = FriendshipId(jobData.getString("owner"))
        val timer = jobData.getString("timerId").let { timerId ->
            timerRepository.findByFriendshipId(owner).firstOrNull { it._id == timerId } ?: return
        }
        val messageDescription =
            "Tell the friend their ${timer.duration} timer has expired. Translate the ISO format duration into natural language.${if (timer.label.isNotBlank()) "Focus on the timer's name \"${timer.label}\" in your answer and include it nicely." else ""}"
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, owner, OutgoingGeneratedMessage(
                    SIGNAL,
                    messageDescription,
                )
            )
        )
        timerRepository.expired(owner, timer._id!!)
    }
}