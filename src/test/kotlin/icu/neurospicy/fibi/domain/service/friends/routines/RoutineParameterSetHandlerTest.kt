package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aParameterRequestStep
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutinePhase
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineTemplate
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineParameterSet
import icu.neurospicy.fibi.domain.service.friends.routines.events.UpdatedRoutineSchedulersOnParameterChange
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant

@ExperimentalCoroutinesApi
class RoutineParameterSetHandlerTest {

    private val instanceRepository: RoutineRepository = mockk()
    private val templateRepository: RoutineTemplateRepository = mockk()
    private val routineScheduler: RoutineScheduler = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk()

    private lateinit var handler: RoutineParameterSetHandler

    @BeforeEach
    fun setup() {
        handler = RoutineParameterSetHandler(
            instanceRepository,
            templateRepository,
            routineScheduler,
            eventPublisher
        )
        coJustRun { routineScheduler.scheduleStep(any(), any(), any()) }
        coJustRun { routineScheduler.scheduleTrigger(any(), any()) }
        justRun { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `handleRoutineParameterSet updates schedulers`() {
        val parameterKey = "testParameter"
        val parameterRequestStep = aParameterRequestStep {
            this.parameterKey = parameterKey
        }
        val phaseWithParameterStep = aRoutinePhase {
            steps = listOf(parameterRequestStep)
        }
        val template = aRoutineTemplate {
            phases = listOf(phaseWithParameterStep)
            triggers = listOf(
                RoutineTrigger(
                    condition = AfterDuration(parameterKey, Duration.ofMinutes(2)),
                    effect = SendMessage("Hi there")
                )
            )
        }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phaseWithParameterStep.id
        }

        every { instanceRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template

        val event = RoutineParameterSet(
            RoutineLifecycleService::class.java,
            instance.friendshipId,
            instance.instanceId,
            instance.currentPhaseId!!,
            parameterRequestStep.id,
            parameterKey
        )

        handler.handleRoutineParameterSet(event)

        coVerify(exactly = 1) { routineScheduler.scheduleStep(any(), any(), any()) }
        coVerify(exactly = 1) { routineScheduler.scheduleTrigger(any(), any()) }
        verify {
            eventPublisher.publishEvent(
                match<UpdatedRoutineSchedulersOnParameterChange> {
                    it.friendshipId == instance.friendshipId && it.instanceId == instance.instanceId && it.stepIds.size == 1 && it.triggerIds.size == 1
                })
        }
    }

    @Test
    fun `handleRoutineParameterSet only reschedules incomplete steps`() {
        val parameterKey = "testParameter"
        val completedStep = aParameterRequestStep {
            this.question = "Completed Step"
            this.parameterKey = parameterKey
        }
        val incompleteStep = aParameterRequestStep {
            this.question = "Incomplete Step"
            this.parameterKey = parameterKey
        }
        val phase = aRoutinePhase {
            steps = listOf(completedStep, incompleteStep)
        }
        val template = aRoutineTemplate {
            phases = listOf(phase)
            triggers = emptyList()
        }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.progress = RoutineProgress(
                iterations = listOf(
                    PhaseIterationProgress(
                        phaseId = phase.id,
                        iterationStart = Instant.now(),
                        completedSteps = listOf(Completion(completedStep.id))
                    )
                )
            )
        }

        every { instanceRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template

        val event = RoutineParameterSet(
            RoutineLifecycleService::class.java,
            instance.friendshipId,
            instance.instanceId,
            instance.currentPhaseId!!,
            completedStep.id,
            parameterKey
        )

        handler.handleRoutineParameterSet(event)

        coVerify(exactly = 1) { routineScheduler.scheduleStep(instance, incompleteStep, phase.id) }
        coVerify(exactly = 0) { routineScheduler.scheduleStep(instance, completedStep, phase.id) }
    }

    @Test
    fun `handleRoutineParameterSet reschedules triggers when no phase is active`() {
        val parameterKey = "testParameter"
        val template = aRoutineTemplate {
            this.phases = listOf(aRoutinePhase { steps = emptyList() })
            this.triggers = listOf(
                RoutineTrigger(
                    condition = AfterDuration(parameterKey, Duration.ofMinutes(2)),
                    effect = SendMessage("Hi there")
                )
            )
        }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = null
            this.progress = RoutineProgress(iterations = emptyList())
        }

        every { instanceRepository.findById(instance.friendshipId, instance.instanceId) } returns instance
        every { templateRepository.findById(template.templateId) } returns template

        val event = RoutineParameterSet(
            RoutineLifecycleService::class.java,
            instance.friendshipId,
            instance.instanceId,
            RoutinePhaseId.forTitle("any"), // This is not used in the handler
            RoutineStepId.forDescription("any"), // This is not used in the handler
            parameterKey
        )

        handler.handleRoutineParameterSet(event)

        coVerify(exactly = 1) { routineScheduler.scheduleTrigger(instance, template.triggers.first()) }
        coVerify(exactly = 0) { routineScheduler.scheduleStep(any(), any(), any()) }
        verify {
            eventPublisher.publishEvent(
                match<UpdatedRoutineSchedulersOnParameterChange> {
                    it.triggerIds.size == 1 && it.stepIds.isEmpty()
                })
        }
    }
} 