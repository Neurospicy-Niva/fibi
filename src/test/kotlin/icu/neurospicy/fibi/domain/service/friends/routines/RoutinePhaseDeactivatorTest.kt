package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.routines.builders.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseDeactivated
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalTime

class RoutinePhaseDeactivatorTest {

    private val scheduler: RoutineScheduler = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val eventLog: RoutineEventLog = mockk(relaxed = true)
    private val templateRepository: RoutineTemplateRepository = mockk(relaxed = true)
    private val goalContextRepository: GoalContextRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    private val instanceRepository: RoutineRepository = mockk(relaxed = true)

    private lateinit var deactivator: RoutinePhaseDeactivator

    @BeforeEach
    fun setup() {
        deactivator = RoutinePhaseDeactivator(
            scheduler,
            eventPublisher,
            eventLog,
            templateRepository,
            goalContextRepository,
            taskRepository,
            instanceRepository
        )
    }

    @Test
    fun `deactivatePhase removes all schedulers and publishes events`() {
        val phase = aRoutinePhase {
            steps = listOf(
                anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0)) },
                anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(8, 0)) }
            )
        }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val currentIteration = PhaseIterationProgress(
            phaseId = phase.id,
            iterationStart = Instant.now().minusSeconds(1800),
            completedSteps = listOf(Completion(phase.steps.first().id)), // First step completed
            completedAt = null
        )
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.progress = RoutineProgress(iterations = listOf(currentIteration))
        }

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns emptyList()

        deactivator.deactivatePhase(instance, phase.id)

        verify {
            // Remove phase iteration scheduler
            scheduler.removePhaseIterationSchedule(instance.friendshipId, instance.instanceId, phase.id)

            // Remove only incomplete step schedulers (second step should be removed, first shouldn't)
            scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, phase.id, phase.steps[1].id)
        }

        verify(exactly = 1) {
            // Should only remove scheduler for the incomplete step
            scheduler.removeScheduleFor(any(), any(), any(), any())
        }

        verify {
            // Log deactivation event
            eventLog.log(match<RoutineEventLogEntry> {
                it.routineInstanceId == instance.instanceId &&
                        it.friendshipId == instance.friendshipId &&
                        it.event == RoutineEventType.PHASE_DEACTIVATED &&
                        it.metadata["phaseId"] == phase.id
            })

            // Publish deactivation event
            eventPublisher.publishEvent(match<PhaseDeactivated> {
                it.friendshipId == instance.friendshipId &&
                        it.instanceId == instance.instanceId &&
                        it.phaseId == phase.id
            })
        }
    }

    @Test
    fun `deactivatePhase handles missing template gracefully`() {
        val instance = aRoutineInstance()
        every { templateRepository.findById(instance.templateId) } returns null
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns emptyList()

        deactivator.deactivatePhase(instance, instance.currentPhaseId!!)

        verify {
            // Should still remove phase iteration scheduler
            scheduler.removePhaseIterationSchedule(any(), any(), any())

            // Should still log and publish events
            eventLog.log(any())
            eventPublisher.publishEvent(any<PhaseDeactivated>())
        }

        verify(exactly = 0) {
            // Should not try to remove step schedulers
            scheduler.removeScheduleFor(any(), any(), any(), any())
        }
    }

    @Test
    fun `deactivatePhase handles missing phase gracefully`() {
        val phase = aRoutinePhase()
        val template = aRoutineTemplate { phases = listOf(phase) }
        val instance = aRoutineInstance { this.template = template }
        val nonExistentPhaseId = RoutinePhaseId.forTitle("NonExistent")

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns emptyList()

        deactivator.deactivatePhase(instance, nonExistentPhaseId)

        verify {
            // Should still remove phase iteration scheduler
            scheduler.removePhaseIterationSchedule(instance.friendshipId, instance.instanceId, nonExistentPhaseId)

            // Should still log and publish events
            eventLog.log(any())
            eventPublisher.publishEvent(any<PhaseDeactivated>())
        }

        verify(exactly = 0) {
            // Should not try to remove step schedulers
            scheduler.removeScheduleFor(any(), any(), any(), any())
        }
    }

    @Test
    fun `deactivatePhase removes all step schedulers when no iteration exists`() {
        val phase = aRoutinePhase {
            steps = listOf(
                anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0)) },
                anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(8, 0)) }
            )
        }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.progress = RoutineProgress(iterations = emptyList()) // No current iteration
        }

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns emptyList()

        deactivator.deactivatePhase(instance, phase.id)

        verify {
            // Should remove all step schedulers since no iteration exists (all steps considered incomplete)
            scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, phase.id, phase.steps[0].id)
            scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, phase.id, phase.steps[1].id)
        }

        verify(exactly = 2) {
            scheduler.removeScheduleFor(any(), any(), any(), any())
        }
    }

    @Test
    fun `deactivatePhase handles scheduler errors gracefully`() {
        val phase = aRoutinePhase {
            steps = listOf(anActionStep { timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0)) })
        }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
        }

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns emptyList()
        every {
            scheduler.removeScheduleFor(any(), any(), any(), any())
        } throws RuntimeException("Scheduler error")

        // Should not throw exception even if step scheduler removal fails
        deactivator.deactivatePhase(instance, phase.id)

        verify {
            // Should still complete other operations
            scheduler.removePhaseIterationSchedule(any(), any(), any())
            eventLog.log(any())
            eventPublisher.publishEvent(any<PhaseDeactivated>())
        }
    }

    @Test
    fun `deactivatePhase cleans up ongoing conversations for this phase`() {
        val step = aParameterRequestStep()
        val phase = aRoutinePhase {
            title = "Test Phase"
            steps = listOf(step)
        }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
        }

        val routineSubtask = Subtask(
            id = SubtaskId("test-subtask-id"),
            intent = RoutineIntents.AnswerQuestion,
            parameters = mapOf(
                "routineInstanceId" to instance.instanceId,
                "routineStepId" to step.id
            )
        )
        val clarificationQuestion = SubtaskClarificationQuestion("Question", routineSubtask.id)
        val goalContext = GoalContext(
            subtasks = listOf(routineSubtask),
            subtaskClarificationQuestions = listOf(clarificationQuestion)
        )

        every { templateRepository.findById(any()) } returns template
        every { goalContextRepository.loadContext(any()) } returns goalContext
        every { taskRepository.findByFriendshipId(any()) } returns emptyList()
        every { goalContextRepository.saveContext(any(), any()) } just Runs

        deactivator.deactivatePhase(instance, phase.id)

        verify {
            goalContextRepository.loadContext(instance.friendshipId)
            goalContextRepository.saveContext(instance.friendshipId, match {
                it.subtasks.isEmpty() && it.goal == null
            })
        }
    }

    @Test
    fun `deactivatePhase cleans up uncompleted tasks for this phase`() {
        val step = anActionStep()
        val phase = aRoutinePhase { steps = listOf(step) }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val taskId = "task-123"
        val task = Task(id = taskId, owner = FriendshipId(), title = "Test task", completed = false)
        val taskConcept = TaskRoutineConcept(taskId, step.id)
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.concepts = listOf(taskConcept)
        }

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns listOf(task)
        every { taskRepository.removeTask(instance.friendshipId, taskId) } returns task
        every { instanceRepository.save(any()) } just Runs

        deactivator.deactivatePhase(instance, phase.id)

        verify {
            // Should remove uncompleted task
            taskRepository.removeTask(instance.friendshipId, taskId)

            // Should update instance to remove task concept
            instanceRepository.save(match<RoutineInstance> {
                it.concepts.isEmpty()
            })
        }
    }

    @Test
    fun `deactivatePhase does not remove completed tasks`() {
        val step = anActionStep()
        val phase = aRoutinePhase { steps = listOf(step) }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val taskId = "task-123"
        val completedTask = Task(id = taskId, owner = FriendshipId(), title = "Test task", completed = true)
        val taskConcept = TaskRoutineConcept(taskId, step.id)
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.concepts = listOf(taskConcept)
        }

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } returns null
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns listOf(completedTask)
        every { instanceRepository.save(any()) } just Runs

        deactivator.deactivatePhase(instance, phase.id)

        verify(exactly = 0) {
            // Should NOT remove completed task
            taskRepository.removeTask(any(), any())
        }

        verify {
            // Should still remove task concept from instance
            instanceRepository.save(match<RoutineInstance> {
                it.concepts.isEmpty()
            })
        }
    }

    @Test
    fun `deactivatePhase handles conversation cleanup errors gracefully`() {
        val step = aParameterRequestStep()
        val phase = aRoutinePhase { steps = listOf(step) }
        val template = aRoutineTemplate { phases = listOf(phase) }
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
        }

        every { templateRepository.findById(template.templateId) } returns template
        every { templateRepository.findById(instance.templateId) } returns template
        every { goalContextRepository.loadContext(instance.friendshipId) } throws RuntimeException("Context error")
        every { taskRepository.findByFriendshipId(instance.friendshipId) } returns emptyList()

        // Should not throw exception even if conversation cleanup fails
        deactivator.deactivatePhase(instance, phase.id)

        verify {
            // Should still complete other operations
            scheduler.removePhaseIterationSchedule(any(), any(), any())
            eventLog.log(any())
            eventPublisher.publishEvent(any<PhaseDeactivated>())
        }
    }
} 