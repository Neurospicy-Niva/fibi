package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseIterationTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.StopRoutineForToday
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class RoutineLifecycleServiceTest {

    private val instanceRepository: RoutineRepository = mockk(relaxed = true)
    private val stepExecutor: RoutineStepExecutor = mockk(relaxed = true)
    private val phaseService: RoutinePhaseService = mockk(relaxed = true)
    private val parameterSetHandler: RoutineParameterSetHandler = mockk(relaxed = true)

    private lateinit var service: RoutineLifecycleHandler

    @BeforeEach
    fun setup() {
        service = RoutineLifecycleHandler(
            instanceRepository,
            stepExecutor,
            phaseService,
            parameterSetHandler
        )
    }

    @Test
    fun `onRoutinePhaseIterationTriggered delegates to phaseService`() {
        val instance = aRoutineInstance()
        every { instanceRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        val event = RoutinePhaseIterationTriggered(
            this.javaClass,
            instance.friendshipId,
            instance.instanceId,
            instance.currentPhaseId!!
        )

        service.onRoutinePhaseIterationTriggered(event)

        verify { phaseService.startPhaseIteration(instance, instance.currentPhaseId) }
    }

    @Test
    fun `onRoutineStepTriggered delegates to stepExecutor`() {
        val event = mockk<RoutineStepTriggered>()
        coJustRun { stepExecutor.executeStep(event) }

        service.onRoutineStepTriggered(event)

        coVerify { stepExecutor.executeStep(event) }
    }


    @Test
    fun `onRoutineParameterSet delegates to parameterSetHandler`() {
        val event = mockk<SetRoutineParameterRoutineStep>()
        justRun { parameterSetHandler.handleRoutineParameterSet(event) }

        service.onRoutineParameterSet(event)

        verify { parameterSetHandler.handleRoutineParameterSet(event) }
    }

    @Test
    fun `onRoutinePhaseStopped delegates to phaseService`() {
        val instance = aRoutineInstance()
        justRun { phaseService.handleStoppedRoutineIteration(any(), any()) }
        every { instanceRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        val event = StopRoutineForToday(this.javaClass, instance.friendshipId, instance.instanceId, "Overload")

        service.onRoutineStoppedForToday(event)

        verify { phaseService.handleStoppedRoutineIteration(instance, "Overload") }
    }
}