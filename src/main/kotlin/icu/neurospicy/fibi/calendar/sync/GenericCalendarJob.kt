package icu.neurospicy.fibi.calendar.sync

import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.FriendshipId
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class GenericCalendarJob(
    private val applicationEventPublisher: ApplicationEventPublisher
) : Job {

    companion object {
        private val LOG = LoggerFactory.getLogger(GenericCalendarJob::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        val friendshipId = FriendshipId(jobData.getString("friendshipId"))
        val calendarConfigId = CalendarConfigId(jobData.getString("calendarConfigId"))
        LOG.info("Executing GenericCalendarJob for friendshipId=$friendshipId, calendarConfigId=$calendarConfigId")

        // Publish an application event carrying the routine execution details.
        applicationEventPublisher.publishEvent(SyncCalendarCmd(friendshipId, calendarConfigId))
    }
}