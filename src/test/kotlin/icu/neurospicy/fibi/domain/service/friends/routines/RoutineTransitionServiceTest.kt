package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.builders.aParameterRequestStep
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutinePhase
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineTemplate
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseIterationCompleted
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseTriggered
import icu.neurospicy.fibi.domain.service.friends.routines.events.SetRoutineParameterRoutineStep
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime

@ExperimentalCoroutinesApi
class RoutineTransitionServiceTest {

    private val routineRepository: RoutineRepository = mockk(relaxed = true)
    private val templateRepository: RoutineTemplateRepository = mockk(relaxed = true)
    private val phaseService: RoutinePhaseService = mockk(relaxed = true)

    private lateinit var service: RoutineTransitionHandler

    @BeforeEach
    fun setup() {
        service = RoutineTransitionHandler(
            routineRepository,
            templateRepository,
            phaseService
        )
    }

    @Test
    fun `onRoutinePhaseTriggered delegates to phaseService`() {
        val instance = aRoutineInstance()
        val mockedInstance = mockk<RoutineInstance>()
        val event =
            RoutinePhaseTriggered(this.javaClass, instance.friendshipId, instance.instanceId, instance.currentPhaseId!!)
        every { routineRepository.findById(instance.friendshipId, instance.instanceId) } returns mockedInstance

        service.onRoutinePhaseTriggered(event)

        verify { phaseService.phaseTriggered(mockedInstance, instance.currentPhaseId) }
    }

    @Test
    fun `onPhaseIterationCompleted triggers transition evaluation`() {
        val activePhase = aRoutinePhase {
            this.title = "phase 1"
        }
        val template = aRoutineTemplate {
            this.phases = listOf(
                activePhase,
                aRoutinePhase {
                    this.title = "phase 2"
                    this.condition = AfterPhaseCompletions(activePhase.id, 2)
                }
            )
        }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = activePhase.id
            this.progress = RoutineProgress(
                iterations = listOf(
                    // Two completed iterations of phase1
                    PhaseIterationProgress(
                        phaseId = activePhase.id,
                        completedSteps = activePhase.steps.map { Completion(it.id) },
                        iterationStart = Instant.now().minusSeconds(10),
                        completedAt = Instant.now()
                    ),
                    PhaseIterationProgress(
                        phaseId = activePhase.id,
                        completedSteps = activePhase.steps.map { Completion(it.id) },
                        iterationStart = Instant.now().minusSeconds(10),
                        completedAt = Instant.now()
                    )
                )
            )
        }

        every { routineRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template
        justRun { phaseService.conditionFulfilled(instance, template.phases[1]) }

        val event = PhaseIterationCompleted(
            this.javaClass,
            instance.friendshipId,
            instance.instanceId,
            activePhase.id
        )

        service.onPhaseIterationCompleted(event)

        verify { phaseService.conditionFulfilled(instance, template.phases[1]) }
    }

    @Test
    fun `onRoutineParameterSet triggers transition for AfterParameterSet condition`() {
        val parameterKey = "alarm_time"
        val parameterRequestStep = aParameterRequestStep {
            this.parameterKey = parameterKey
            this.parameterType = RoutineParameterType.LOCAL_TIME
        }
        val activePhase = aRoutinePhase {
            this.title = "phase 1"
            this.steps = listOf(parameterRequestStep)
        }
        val template = aRoutineTemplate().copy(
            phases = listOf(
                activePhase,
                aRoutinePhase {
                    this.title = "phase 2"
                    this.condition = AfterParameterSet(parameterKey)
                }
            )
        )
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = activePhase.id
            this.parameters = mapOf(parameterKey to TypedParameter.fromValue(LocalTime.of(7, 0)))
        }

        every { routineRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template
        justRun { phaseService.conditionFulfilled(instance, template.phases[1]) }

        val event = SetRoutineParameterRoutineStep(
            this.javaClass,
            instance.friendshipId,
            instance.instanceId,
            activePhase.id,
            parameterRequestStep.id,
            parameterKey
        )

        service.onRoutineParameterSet(event)

        verify { phaseService.conditionFulfilled(instance, template.phases[1]) }
    }
} 