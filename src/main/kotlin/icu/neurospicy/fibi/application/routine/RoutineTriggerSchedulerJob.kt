package icu.neurospicy.fibi.application.routine

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineInstanceId
import icu.neurospicy.fibi.domain.service.friends.routines.TriggerId
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineTriggerFired
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RoutineTriggerSchedulerJob(
    private val eventPublisher: ApplicationEventPublisher,
) : Job {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        val friendshipId = FriendshipId(jobData.getString("friendshipId"))
        val instanceId = RoutineInstanceId(jobData.getString("routineInstanceId"))
        val triggerId = TriggerId(jobData.getString("triggerId"))

        LOG.debug("Trigger $triggerId fired for routine instance $instanceId owned by friend $friendshipId")
        eventPublisher.publishEvent(RoutineTriggerFired(this.javaClass, friendshipId, instanceId, triggerId))
    }
}
