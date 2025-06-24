package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutinePhase
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseActivated
import io.mockk.MockKVerificationScope
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class RoutinePhaseActivatorTest {

    private val scheduler = mockk<RoutineScheduler>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val eventLog = mockk<RoutineEventLog>(relaxed = true)
    private val repository = mockk<RoutineRepository>(relaxed = true)
    private val activator = RoutinePhaseActivator(scheduler, eventPublisher, eventLog, repository)

    @Test
    fun `schedules phase iterations, logs and publishes event`() {
        val phase = aRoutinePhase {
            this.steps = listOf(
                ActionRoutineStep(
                    message = "Do something",
                    timeOfDay = TimeOfDayReference("\${wakeUpTime}"),
                    expectConfirmation = true
                )
            )
        }
        val instance = aRoutineInstance {
            this.progress = RoutineProgress()
            this.parameters = mutableMapOf("wakeUpTime" to LocalTime.of(7, 0))
            this.currentPhaseId = null
        }

        activator.activatePhase(instance, phase)

        verify {
            scheduler.schedulePhaseIterations(
                matchesInstanceWithNewPhase(instance, phase),
                phase
            )
            eventPublisher.publishEvent(match {
                it is PhaseActivated && it.phaseId == phase.id && it.instanceId == instance.instanceId
            })
            eventLog.log(match {
                it.routineInstanceId == instance.instanceId &&
                        it.friendshipId == instance.friendshipId &&
                        it.event == RoutineEventType.PHASE_ACTIVATED &&
                        it.metadata["phaseId"] == phase.id
            })
            repository.save(matchesInstanceWithNewPhase(instance, phase))
        }
    }

    private fun MockKVerificationScope.matchesInstanceWithNewPhase(
        instance: RoutineInstance,
        phase: RoutinePhase,
    ): RoutineInstance = match {
        it.instanceId == instance.instanceId &&
                it.friendshipId == instance.friendshipId &&
                it.currentPhaseId == phase.id &&
                it.progress.iterations.first().phaseId == phase.id &&
                Duration.between(
                    it.progress.iterations.first().iterationStart,
                    Instant.now()
                ) <= Duration.ofSeconds(3) &&
                it.progress.iterations.first().completedSteps.isEmpty() &&
                it.progress.iterations.first().completedAt == null
    }
}