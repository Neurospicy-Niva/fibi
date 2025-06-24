package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseActivated
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RoutinePhaseActivator(
    private val scheduler: RoutineScheduler,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
    private val routineRepository: RoutineRepository,
) {
    fun activatePhase(oldInstance: RoutineInstance, phase: RoutinePhase) {
        val updateRoutine = oldInstance.withCurrentPhase(phaseId = phase.id)
        routineRepository.save(updateRoutine)

        scheduler.schedulePhaseIterations(updateRoutine, phase)

        eventPublisher.publishEvent(PhaseActivated(this.javaClass, updateRoutine.instanceId, phase.id))
        eventLog.log(
            RoutineEventLogEntry(
                routineInstanceId = updateRoutine.instanceId,
                friendshipId = updateRoutine.friendshipId,
                event = RoutineEventType.PHASE_ACTIVATED,
                timestamp = Instant.now(),
                metadata = mapOf("phaseId" to phase.id),
            )
        )
    }
}