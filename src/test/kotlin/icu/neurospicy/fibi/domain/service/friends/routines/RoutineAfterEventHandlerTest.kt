package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseActivated
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseDeactivated
import icu.neurospicy.fibi.domain.service.friends.routines.builders.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*

class RoutineAfterEventHandlerTest {

    private lateinit var routineRepository: RoutineRepository
    private lateinit var templateRepository: RoutineTemplateRepository
    private lateinit var routineScheduler: RoutineScheduler
    private lateinit var handler: RoutineAfterEventHandler

    @BeforeEach
    fun setUp() {
        routineRepository = mockk()
        templateRepository = mockk()
        routineScheduler = mockk(relaxed = true)
        handler = RoutineAfterEventHandler(routineRepository, templateRepository, routineScheduler)
    }

    @Nested
    inner class `PhaseActivated Event Handling` {

        @Test
        fun `reschedules AfterEvent triggers for PHASE_ENTERED when phase is activated`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_ENTERED,
                            phaseTitle = "Breakfast",
                            timeExpression = "PT5M"
                        ),
                        effect = SendMessage("5 minutes after entering breakfast phase")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            every { routineScheduler.scheduleTrigger(any(), any()) } just Runs
            
            val event = PhaseActivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseActivated(event)
            
            verify {
                routineRepository.save(any())
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `handles complex time expressions for PHASE_ENTERED`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_ENTERED,
                            phaseTitle = "Breakfast",
                            timeExpression = "\${wakeUpTime}+PT30M"
                        ),
                        effect = SendMessage("30 minutes after wake up time")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            every { routineScheduler.scheduleTrigger(any(), any()) } just Runs
            
            val event = PhaseActivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseActivated(event)
            
            verify {
                routineRepository.save(any())
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `handles multiple AfterEvent triggers for PHASE_ENTERED`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_ENTERED,
                            phaseTitle = "Breakfast",
                            timeExpression = "PT5M"
                        ),
                        effect = SendMessage("5 minutes after entering breakfast phase")
                    ),
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_ENTERED,
                            phaseTitle = "Workout",
                            timeExpression = "PT10M"
                        ),
                        effect = SendMessage("10 minutes after entering workout phase")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            every { routineScheduler.scheduleTrigger(any(), any()) } just Runs
            
            val event = PhaseActivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseActivated(event)
            
            verify(exactly = 1) {
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `does not reschedule non-AfterEvent triggers for PHASE_ENTERED`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterDays(1),
                        effect = SendMessage("Tomorrow")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            
            val event = PhaseActivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseActivated(event)
            
            verify(exactly = 0) {
                routineScheduler.scheduleTrigger(any(), any())
            }
        }
    }

    @Nested
    inner class `PhaseDeactivated Event Handling` {

        @Test
        fun `reschedules AfterEvent triggers for PHASE_LEFT when phase is deactivated`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_LEFT,
                            phaseTitle = "Breakfast",
                            timeExpression = "PT5M"
                        ),
                        effect = SendMessage("5 minutes after leaving breakfast phase")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            every { routineScheduler.scheduleTrigger(any(), any()) } just Runs
            
            val event = PhaseDeactivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseDeactivated(event)
            
            verify {
                routineRepository.save(any())
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `handles complex time expressions for PHASE_LEFT`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_LEFT,
                            phaseTitle = "Breakfast",
                            timeExpression = "\${bedTime}-PT1H"
                        ),
                        effect = SendMessage("1 hour before bed time")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            every { routineScheduler.scheduleTrigger(any(), any()) } just Runs
            
            val event = PhaseDeactivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseDeactivated(event)
            
            verify {
                routineRepository.save(any())
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `handles multiple AfterEvent triggers for PHASE_LEFT`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_LEFT,
                            phaseTitle = "Breakfast",
                            timeExpression = "PT5M"
                        ),
                        effect = SendMessage("5 minutes after leaving breakfast phase")
                    ),
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterEvent(
                            eventType = RoutineAnchorEvent.PHASE_LEFT,
                            timeExpression = "PT10M"
                        ),
                        effect = SendMessage("10 minutes after leaving any phase")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            every { routineScheduler.scheduleTrigger(any(), any()) } just Runs
            
            val event = PhaseDeactivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseDeactivated(event)
            
            verify(exactly = 2) {
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `does not reschedule non-AfterEvent triggers for PHASE_LEFT`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            val template = aRoutineTemplate {
                title = "Morning Routine"
                version = "1.0"
                description = "A morning routine"
                phases = listOf(aRoutinePhase {
                    title = "Breakfast"
                    steps = listOf(aMessageStep { message = "Test message" })
                })
                triggers = listOf(
                    RoutineTrigger(
                        id = TriggerId(),
                        condition = AfterDays(1),
                        effect = SendMessage("Tomorrow")
                    )
                )
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns template
            every { routineRepository.save(any()) } returnsArgument 0
            
            val event = PhaseDeactivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseDeactivated(event)
            
            verify(exactly = 0) {
                routineScheduler.scheduleTrigger(any(), any())
            }
        }
    }

    @Nested
    inner class `Error Handling` {

        @Test
        fun `handles missing routine instance gracefully`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns null
            
            val event = PhaseActivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseActivated(event)
            
            verify(exactly = 0) { 
                routineScheduler.scheduleTrigger(any(), any())
            }
        }

        @Test
        fun `handles missing template gracefully`() {
            val friendshipId = FriendshipId()
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            
            val instance = aRoutineInstance {
                this.friendshipId = friendshipId
            }
            
            every { routineRepository.findById(friendshipId, instance.instanceId) } returns instance
            every { templateRepository.findById(instance.templateId) } returns null
            
            val event = PhaseActivated(
                this.javaClass,
                friendshipId,
                instance.instanceId,
                phaseId
            )
            
            handler.onPhaseActivated(event)
            
            verify(exactly = 0) { 
                routineScheduler.scheduleTrigger(any(), any())
            }
        }
    }
} 