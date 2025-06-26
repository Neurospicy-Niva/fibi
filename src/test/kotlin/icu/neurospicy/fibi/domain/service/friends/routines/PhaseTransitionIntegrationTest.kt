package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContextRepository
import icu.neurospicy.fibi.domain.service.friends.routines.builders.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseActivated
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseDeactivated
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalTime

/**
 * Integration test demonstrating the complete phase transition lifecycle
 * including proper scheduler cleanup when phases transition.
 */
class PhaseTransitionIntegrationTest {

    private val scheduler: RoutineScheduler = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val eventLog: RoutineEventLog = mockk(relaxed = true)
    private val routineRepository: RoutineRepository = mockk(relaxed = true)
    private val templateRepository: RoutineTemplateRepository = mockk(relaxed = true)
    private val goalContextRepository: GoalContextRepository = mockk(relaxed = true)
    private val taskRepository: TaskRepository = mockk(relaxed = true)
    
    private lateinit var phaseDeactivator: RoutinePhaseDeactivator
    private lateinit var phaseActivator: RoutinePhaseActivator

    @BeforeEach
    fun setup() {
        every { goalContextRepository.loadContext(any()) } returns null
        every { taskRepository.findByFriendshipId(any()) } returns emptyList()
        
        phaseDeactivator = RoutinePhaseDeactivator(
            scheduler,
            eventPublisher,
            eventLog,
            templateRepository,
            goalContextRepository,
            taskRepository,
            routineRepository
        )
        
        phaseActivator = RoutinePhaseActivator(
            scheduler,
            eventPublisher,
            eventLog,
            routineRepository,
            phaseDeactivator
        )
    }

    @Test
    fun `complete phase transition with scheduler cleanup`() {
        // Setup: Create a routine with two phases
        val morningPhase = aRoutinePhase {
            title = "Morning Routine"
            steps = listOf(
                anActionStep { 
                    message = "Wake up"
                    timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0))
                },
                anActionStep { 
                    message = "Brush teeth"
                    timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 30))
                }
            )
        }
        
        val eveningPhase = aRoutinePhase {
            title = "Evening Routine"
            steps = listOf(
                anActionStep { 
                    message = "Prepare dinner"
                    timeOfDay = TimeOfDayLocalTime(LocalTime.of(18, 0))
                }
            )
        }
        
        val template = aRoutineTemplate {
            phases = listOf(morningPhase, eveningPhase)
        }
        
        // Current iteration with one incomplete step
        val currentIteration = PhaseIterationProgress(
            phaseId = morningPhase.id,
            iterationStart = Instant.now().minusSeconds(3600),
            completedSteps = listOf(Completion(morningPhase.steps.first().id)), // First step completed
            completedAt = null
        )
        
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = morningPhase.id // Currently in morning phase
            this.progress = RoutineProgress(iterations = listOf(currentIteration))
        }
        
        every { templateRepository.findById(template.templateId) } returns template
        every { routineRepository.save(any()) } returnsArgument 0

        // Action: Transition from morning phase to evening phase
        phaseActivator.activatePhase(instance, eveningPhase)

        // Verification: Complete cleanup and activation sequence
                 verifySequence {
             // 1. Deactivate old phase (morning)
             scheduler.removePhaseIterationSchedule(instance.friendshipId, instance.instanceId, morningPhase.id)
             scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, morningPhase.id, morningPhase.steps[1].id) // Only incomplete step
            
            eventLog.log(match<RoutineEventLogEntry> {
                it.event == RoutineEventType.PHASE_DEACTIVATED && 
                it.metadata["phaseId"] == morningPhase.id
            })
            
            eventPublisher.publishEvent(match<PhaseDeactivated> {
                it.phaseId == morningPhase.id
            })
            
            // 2. Save updated instance with new phase
            routineRepository.save(match<RoutineInstance> {
                it.currentPhaseId == eveningPhase.id &&
                it.progress.getCurrentIteration()?.phaseId == eveningPhase.id
            })
            
            // 3. Activate new phase (evening)
            scheduler.schedulePhaseIterations(any(), eveningPhase)
            
            eventPublisher.publishEvent(match<PhaseActivated> {
                it.phaseId == eveningPhase.id
            })
            
            eventLog.log(match<RoutineEventLogEntry> {
                it.event == RoutineEventType.PHASE_ACTIVATED && 
                it.metadata["phaseId"] == eveningPhase.id
            })
        }
    }

    @Test
    fun `phase transition cleans up all incomplete steps`() {
        val phase = aRoutinePhase {
            steps = listOf(
                anActionStep { message = "Step 1"; timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 0)) },
                anActionStep { message = "Step 2"; timeOfDay = TimeOfDayLocalTime(LocalTime.of(7, 30)) },
                anActionStep { message = "Step 3"; timeOfDay = TimeOfDayLocalTime(LocalTime.of(8, 0)) }
            )
        }
        
        val template = aRoutineTemplate { phases = listOf(phase) }
        
        // Only first step completed
        val currentIteration = PhaseIterationProgress(
            phaseId = phase.id,
            iterationStart = Instant.now().minusSeconds(3600),
            completedSteps = listOf(Completion(phase.steps.first().id)),
            completedAt = null
        )
        
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.progress = RoutineProgress(iterations = listOf(currentIteration))
        }
        
        every { templateRepository.findById(template.templateId) } returns template

        // Action: Deactivate the phase
        phaseDeactivator.deactivatePhase(instance, phase.id)

        // Verification: Should remove schedulers for steps 2 and 3 (incomplete), but not step 1 (completed)
        verify {
            scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, phase.id, phase.steps[1].id)
            scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, phase.id, phase.steps[2].id)
        }
        
        verify(exactly = 0) {
            scheduler.removeScheduleFor(instance.friendshipId, instance.instanceId, phase.id, phase.steps[0].id)
        }
        
        verify(exactly = 2) {
            scheduler.removeScheduleFor(any(), any(), any(), any())
        }
    }

    @Test
    fun `activating same phase does not trigger deactivation`() {
        val phase = aRoutinePhase()
        val instance = aRoutineInstance {
            this.currentPhaseId = phase.id
        }
        
        every { routineRepository.save(any()) } returnsArgument 0

        // Action: Activate the already active phase
        phaseActivator.activatePhase(instance, phase)

                 // Verification: No deactivation should occur
         verify(exactly = 0) {
             scheduler.removePhaseIterationSchedule(any(), any(), any())
             scheduler.removeScheduleFor(any(), any(), any(), any())
         }
        
        // But activation should still proceed
        verify {
            routineRepository.save(any())
            scheduler.schedulePhaseIterations(any(), phase)
            eventPublisher.publishEvent(any<PhaseActivated>())
        }
    }
} 