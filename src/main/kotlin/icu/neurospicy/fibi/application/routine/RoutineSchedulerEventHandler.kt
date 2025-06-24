package icu.neurospicy.fibi.application.routine

import icu.neurospicy.fibi.domain.model.events.SetUpMorningRoutineActivityFinished
import icu.neurospicy.fibi.domain.model.events.TimezoneChanged
import icu.neurospicy.fibi.domain.repository.RoutineConfigurationRepository
import icu.neurospicy.fibi.outgoing.SchedulerService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class RoutineSchedulerEventHandler(
    private val routineConfigurationRepository: RoutineConfigurationRepository,
    private val schedulerService: SchedulerService
) {
    /**
     * Listens for events that indicates a morning routine has been configured and sets up scheduler.
     */
    @EventListener
    fun onRoutineSetupFinished(event: SetUpMorningRoutineActivityFinished) {
        LOG.info("Setting up scheduler for morning routine ${event.routineId} for friend ${event.friendshipId}")
        routineConfigurationRepository.findBy(event.routineId)?.let { schedulerService.scheduleRoutine(it) }
    }

    @EventListener
    fun onTimezoneChanges(event: TimezoneChanged) {
        LOG.info("Timezone of user changed, rescheduling routines for friendship ${event.friendshipId}")
        val updatedRoutineConfigurations = routineConfigurationRepository.findByFriendshipId(event.friendshipId)
            .map { it.copy(trigger = it.trigger.copy(timezone = event.newTimezone)) }
        updatedRoutineConfigurations.forEach(routineConfigurationRepository::save)
        schedulerService.reinitializeRoutines(updatedRoutineConfigurations)
    }

    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.info("Setting up schedulers for morning routines.")
        schedulerService.reinitializeRoutines(routineConfigurationRepository.findAll(routineType = "Morning routine"))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineSchedulerEventHandler::class.java)
    }
}