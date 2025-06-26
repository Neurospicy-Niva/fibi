package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutinePhase
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineTemplate
import icu.neurospicy.fibi.domain.service.friends.routines.builders.anActionStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.ConfirmedActionStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseIterationCompleted
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

@ExperimentalCoroutinesApi
class RoutineCompletionServiceTest {

    private val routineRepository: RoutineRepository = mockk(relaxed = true)
    private val templateRepository: RoutineTemplateRepository = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val eventLog: RoutineEventLog = mockk(relaxed = true)

    private lateinit var service: RoutineCompletionHandler

    @BeforeEach
    fun setup() {
        service = RoutineCompletionHandler(
            routineRepository,
            templateRepository,
            eventPublisher,
            eventLog
        )
    }

    @Test
    fun `onActionStepConfirmed triggers phase completion check when all steps completed`() {
        val phase = aRoutinePhase {
            this.steps = listOf(
                anActionStep {
                    this.message = "Do step 1"
                },
                anActionStep {
                    this.message = "Do step 2"
                },
            )
        }
        val template = aRoutineTemplate {
            this.phases = listOf(
                phase
            )
        }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            progress = RoutineProgress(
                iterations = listOf(
                    PhaseIterationProgress(
                        phaseId = phase.id,
                        completedSteps = listOf(
                            Completion(phase.steps.first().id),
                            Completion(phase.steps.last().id)
                        ),
                        iterationStart = Instant.now(),
                        completedAt = null
                    )
                )
            )
        }

        every { routineRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template
        justRun { eventPublisher.publishEvent(any<PhaseIterationCompleted>()) }
        justRun { eventLog.log(any()) }

        val event = ConfirmedActionStep(
            this.javaClass,
            instance.friendshipId,
            instance.instanceId,
            phase.id,
            phase.steps.last().id
        )

        service.onActionStepConfirmed(event)

        verify {
            routineRepository.save(match { savedInstance ->
                savedInstance.progress.iterations.first().completedAt != null &&
                        savedInstance.progress.iterations.first().completedSteps.map { it.id }
                            .containsAll(phase.steps.map { it.id })
            })
        }
        verify { eventPublisher.publishEvent(any<PhaseIterationCompleted>()) }
        verify { eventLog.log(any()) }
    }

    @Test
    fun `onActionStepConfirmed skips completion for inactive phase`() {
        val instance = aRoutineInstance().copy(
            currentPhaseId = RoutinePhaseId.forTitle("active-phase")
        )
        every { routineRepository.findById(instance.friendshipId, instance.instanceId) } returns instance

        val event = ConfirmedActionStep(
            this.javaClass,
            instance.friendshipId,
            instance.instanceId,
            RoutinePhaseId.forTitle("other-phase"), // Different from current phase
            RoutineStepId.forDescription("step1")
        )

        service.onActionStepConfirmed(event)

        verify(exactly = 0) { routineRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<PhaseIterationCompleted>()) }
    }

    @Test
    fun `onActionStepConfirmed skips already completed iteration`() {
        val template = aRoutineTemplate()
        val instance = aRoutineInstance {
            this.template = template
            progress = RoutineProgress(
                iterations = listOf(
                    PhaseIterationProgress(
                        phaseId = template.phases.first().id,
                        completedSteps = template.phases.first().steps.map { Completion(it.id) },
                        iterationStart = Instant.now().minusSeconds(30),
                        completedAt = Instant.now() // Already completed
                    )
                )
            )
        }

        every { routineRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template

        val event = ConfirmedActionStep(
            this.javaClass,
            instance.friendshipId,
            instance.instanceId,
            template.phases.first().id,
            template.phases.first().steps.first().id,
        )

        service.onActionStepConfirmed(event)

        verify(exactly = 0) { routineRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<PhaseIterationCompleted>()) }
    }
} 