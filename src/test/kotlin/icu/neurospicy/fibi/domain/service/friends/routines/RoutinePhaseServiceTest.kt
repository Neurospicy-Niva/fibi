package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.routines.builders.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseIterationStarted
import icu.neurospicy.fibi.domain.service.friends.routines.events.StoppedTodaysRoutine
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

class RoutinePhaseServiceTest {

    private val templateRepository = mockk<RoutineTemplateRepository>(relaxed = true)
    private val routineScheduler = mockk<RoutineScheduler>(relaxed = true)
    private val phaseActivator = mockk<RoutinePhaseActivator>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val eventLog = mockk<RoutineEventLog>(relaxed = true)
    private lateinit var service: RoutinePhaseService

    @BeforeEach
    fun setUp() {
        service = RoutinePhaseService(
            templateRepository,
            routineScheduler,
            phaseActivator,
            eventLog,
            eventPublisher,
        )
    }

    @Nested
    inner class HandleRoutineStart {
        @Test
        fun `schedules phases after days`() {
            val phase = aRoutinePhase { condition = AfterDays(3) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify { routineScheduler.schedulePhase(instance, phase) }
        }

        @Test
        fun `schedules phases after duration`() {
            val phase = aRoutinePhase { condition = AfterDuration(duration = Duration.ofDays(15)) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify { routineScheduler.schedulePhase(instance, phase) }
        }

        @Test
        fun `schedules phases after duration with parameter`() {
            val phase =
                aRoutinePhase { condition = AfterDuration(reference = "wakeUpTime", duration = Duration.ofDays(15)) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance {
                this.template = template
                this.parameters = mapOf("wakeUpTime" to LocalTime.of(7, 0))
            }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify { routineScheduler.schedulePhase(instance, phase) }
        }

        @Test
        fun `does not schedule phases after duration with non existing parameter`() {
            val phase =
                aRoutinePhase { condition = AfterDuration(reference = "wakeUpTime", duration = Duration.ofDays(15)) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify { routineScheduler wasNot Called }
        }

        @Test
        fun `does not schedule phase with condition after parameter set`() {
            val phase = aRoutinePhase { condition = AfterParameterSet("confirmedBuyingBreakfast") }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify { routineScheduler wasNot Called }
        }

        @Test
        fun `activates phase with condition after parameter set with parameter`() {
            val phase = aRoutinePhase { condition = AfterParameterSet("confirmedBuyingBreakfast") }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance {
                this.template = template
                this.parameters = mapOf("confirmedBuyingBreakfast" to true)
            }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify {
                routineScheduler wasNot Called
                phaseActivator.activatePhase(instance, phase)
            }
        }

        @Test
        fun `does not activate phase with condition after parameter set without parameter`() {
            val phase = aRoutinePhase { condition = AfterParameterSet("confirmedBuyingBreakfast") }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify {
                routineScheduler wasNot Called
                phaseActivator wasNot Called
            }
        }

        @Test
        fun `activates phase without condition`() {
            val phase = aRoutinePhase { title = "Simple Breakfast" }
            val phaseActivatedAfterCompletions = aRoutinePhase {
                title = "Healthy breakfast"
                condition = AfterPhaseCompletions(phase.id, 4)
            }
            val template = aRoutineTemplate { phases = listOf(phase, phaseActivatedAfterCompletions) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify {
                routineScheduler wasNot Called
                phaseActivator.activatePhase(instance, phase)
            }
        }

        @Test
        fun `schedules phase with condition after routine started`() {
            val phase = aRoutinePhase { condition = AfterEvent(RoutineAnchorEvent.ROUTINE_STARTED) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify {
                routineScheduler.schedulePhase(instance, phase)
                phaseActivator wasNot Called
            }
        }

        @Test
        fun `activates none but schedules`() {
            val phase = aRoutinePhase { condition = AfterDays(1) }
            val phaseActivatedAfterCompletions = aRoutinePhase {
                title = "Healthy breakfast"
                condition = AfterPhaseCompletions(phase.id, 4)
            }
            val template = aRoutineTemplate { phases = listOf(phaseActivatedAfterCompletions) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.handleRoutineStart(instance)
            verify {
                routineScheduler wasNot Called
                phaseActivator wasNot Called
            }
        }
    }

    @Test
    fun `phaseTriggered activates phase`() {
        val phase = aRoutinePhase()
        val template = aRoutineTemplate { phases = listOf(phase) }
        val instance = aRoutineInstance { this.template = template }
        every { templateRepository.findById(instance.templateId) } returns template

        service.phaseTriggered(instance, phase.id)
        verify { phaseActivator.activatePhase(instance, phase) }
    }

    @Nested
    inner class StartPhaseIteration {
        @Test
        fun `schedules steps for day`() {
            val steps = listOf(
                aMessageStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0)) },
                anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 10)) })
            val phase = aRoutinePhase { this.steps = steps }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.startPhaseIteration(instance, phase.id)

            verify(exactly = 2) { routineScheduler.scheduleStep(any(), any(), any()) }
            verify { eventPublisher.publishEvent(any<RoutinePhaseIterationStarted>()) }
            verify { eventLog.log(any()) }
        }

        @Test
        fun `schedules steps with timeOfDay reference`() {
            val stepWithReference = anActionStep {
                timeOfDay = TimeOfDayReference("wakeUpTime")
            }
            val phase = aRoutinePhase {
                steps = listOf(stepWithReference)
            }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance {
                this.template = template
                this.parameters = mapOf("wakeUpTime" to LocalTime.of(7, 0))
            }
            every { templateRepository.findById(template.templateId) } returns template

            service.startPhaseIteration(instance, phase.id)

            verify { routineScheduler.scheduleStep(instance, stepWithReference, phase.id) }
        }

        @Test
        fun `does not schedule steps without timeOfDay`() {
            val phase = aRoutinePhase {
                title = "No Schedule Phase"
                condition = AfterDays(1)
                steps = listOf(
                    anActionStep { message = "No scheduling" })
            }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.startPhaseIteration(instance, phase.id)

            verify { routineScheduler wasNot Called }
        }

        @Test
        fun `schedules multiple steps with timeOfDay and skips those without`() {
            val step1 = anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0)) }
            val step2 = anActionStep { message = "Skip 1" }
            val step3 = anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(8, 30)) }
            val phase = aRoutinePhase { steps = listOf(step1, step2, step3) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.startPhaseIteration(instance, phase.id)

            verify { routineScheduler.scheduleStep(instance, step1, phase.id) }
            verify { routineScheduler.scheduleStep(instance, step3, phase.id) }
            verify(exactly = 0) { routineScheduler.scheduleStep(instance, step2, phase.id) }
        }

        @Test
        fun `does not schedule step with unresolved timeOfDay reference`() {
            val step = anActionStep { timeOfDay = TimeOfDayReference("\${nonExistent}") }
            val phase = aRoutinePhase { steps = listOf(step) }
            val template = aRoutineTemplate { phases = listOf(phase) }
            val instance = aRoutineInstance { this.template = template }
            every { templateRepository.findById(template.templateId) } returns template

            service.startPhaseIteration(instance, phase.id)

            verify(exactly = 0) { routineScheduler.scheduleStep(any(), any(), any()) }
        }
    }


    @Nested
    inner class OnStoppedRoutineForToday {
        @Test
        fun `removes schedule for incomplete steps of the day`() {
            val incompleteScheduledSteps = listOf(
                aMessageStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(10, 0)) },
                aMessageStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(11, 0)) })
            val completeSteps = listOf(aMessageStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(5, 0)) })
            val phase = aRoutinePhase { this.steps = completeSteps + incompleteScheduledSteps }
            val template = aRoutineTemplate { this.phases = listOf(phase) }
            val instance = aRoutineInstance {
                this.template = template
                this.currentPhaseId = phase.id
                this.progress = RoutineProgress(
                    listOf(
                        PhaseIterationProgress(
                            phase.id, Instant.now().minusSeconds(60), completeSteps.map { Completion(it.id) })
                    )
                )
            }
            justRun {
                routineScheduler.removeScheduleFor(any(), any(), any(), any<RoutineStepId>())
            }
            justRun { eventLog.log(any()) }
            justRun { eventPublisher.publishEvent(any()) }
            every { templateRepository.findById(template.templateId) } returns template
            val reason = "Overload"

            service.handleStoppedRoutineIteration(instance, reason)

            verify(exactly = incompleteScheduledSteps.size) {
                routineScheduler.removeScheduleFor(
                    any(), any(), any(), any()
                )
            }
            incompleteScheduledSteps.forEach { step ->
                verify {
                    routineScheduler.removeScheduleFor(
                        instance.friendshipId, instance.instanceId, phase.id, step.id
                    )
                }
            }
            verify {
                eventLog.log(match<RoutineEventLogEntry> {
                    it.routineInstanceId == instance.instanceId && it.friendshipId == instance.friendshipId && it.event == RoutineEventType.ROUTINE_STOPPED_FOR_TODAY && Duration.between(
                        it.timestamp,
                        Instant.now()
                    ) < Duration.ofSeconds(3) && it.metadata["reason"] == reason
                })
                eventPublisher.publishEvent(match<StoppedTodaysRoutine> {
                    it.friendshipId == instance.friendshipId && it.instanceId == instance.instanceId
                })
            }
        }
    }
}