package icu.neurospicy.fibi.application.routine

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.RoutineConfigurationId
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class GenericRoutineJob(
    private val applicationEventPublisher: ApplicationEventPublisher
) : Job {

    companion object {
        private val LOG = LoggerFactory.getLogger(GenericRoutineJob::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        val friendshipId = FriendshipId(jobData.getString("friendshipId"))
        val routineId = RoutineConfigurationId(jobData.getString("routineId"))
        val routineType = jobData.getString("routineType")
        LOG.info("Executing GenericRoutineJob for friendshipId=$friendshipId, routineId=$routineId, routineType=$routineType")

        // Publish an application event carrying the routine execution details.
        val event = RoutineExecutionEvent(this.javaClass, friendshipId, routineId, routineType)
        applicationEventPublisher.publishEvent(event)
    }
}