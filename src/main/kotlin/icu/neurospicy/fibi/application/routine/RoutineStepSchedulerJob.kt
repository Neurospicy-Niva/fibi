package icu.neurospicy.fibi.application.routine

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineInstanceId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutinePhaseId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineStepId
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepTriggered
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RoutineStepSchedulerJob(
    private val eventPublisher: ApplicationEventPublisher,
) : Job {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        val friendshipId = FriendshipId(jobData.getString("friendshipId"))
        val instanceId = RoutineInstanceId(jobData.getString("routineInstanceId"))
        val phaseId = RoutinePhaseId(jobData.getString("phaseId"))
        val stepId = RoutineStepId(jobData.getString("stepId"))

        LOG.debug("Step $stepId triggered for phase $phaseId of routine instance $instanceId owned by friend $friendshipId")
        eventPublisher.publishEvent(RoutineStepTriggered(this.javaClass, friendshipId, instanceId, phaseId, stepId))
    }
}

