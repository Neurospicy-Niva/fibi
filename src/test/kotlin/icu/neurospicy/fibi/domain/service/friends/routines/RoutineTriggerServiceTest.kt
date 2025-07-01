package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineTriggerFired
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineInstance
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage

class RoutineTriggerServiceTest {

    private val routineRepository = mockk<RoutineRepository>(relaxed = true)
    private val templateRepository = mockk<RoutineTemplateRepository>(relaxed = true)
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val eventLog = mockk<RoutineEventLog>(relaxed = true)
    private val messageVariableSubstitutor = mockk<MessageVariableSubstitutor>(relaxed = true)
    private val friendshipLedger = mockk<FriendshipLedger>(relaxed = true)
    
    private val service = RoutineTriggerService(
        routineRepository,
        templateRepository,
        taskRepository,
        eventPublisher,
        eventLog,
        messageVariableSubstitutor,
        friendshipLedger
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }
    
    private fun setupMocksForTriggerExecution(
        friendshipId: FriendshipId,
        instanceId: RoutineInstanceId,
        trigger: RoutineTrigger
    ) {
        val routineInstance = aRoutineInstance {
            this.friendshipId = friendshipId
        }
        every { routineRepository.findById(friendshipId, instanceId) } returns routineInstance
        every { templateRepository.findById(any()) } returns mockk<RoutineTemplate> {
            every { triggers } returns listOf(trigger)
        }
        every { friendshipLedger.findTimezoneBy(friendshipId) } returns ZoneId.of("UTC")
        every { messageVariableSubstitutor.substituteVariables(any(), any(), any()) } returns trigger.effect.let { 
            when (it) {
                is SendMessage -> it.message
                is CreateTask -> it.taskDescription
            }
        }
    }

    @Nested
    inner class `Trigger Condition Tests` {
        
        @Nested
        inner class `Time-based Conditions` {
            
            @Test
            fun `handles AfterDays condition`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterDays(value = 3),
                    effect = SendMessage("Test message")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
            
            @Test
            fun `handles AfterDuration condition`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterDuration(
                        reference = "wakeUpTime",
                        duration = Duration.ofHours(2)
                    ),
                    effect = SendMessage("2 hours after wake up")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
            
            @Test
            fun `handles AtTimeExpression condition`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AtTimeExpression(
                        timeExpression = "\${wakeUpTime}+PT30M"
                    ),
                    effect = SendMessage("30 minutes after wake up")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
        }
        
        @Nested
        inner class `Event-based Conditions` {
            
            @Test
            fun `handles AfterEvent condition with ROUTINE_STARTED`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterEvent(
                        eventType = RoutineAnchorEvent.ROUTINE_STARTED,
                        timeExpression = "PT15M"
                    ),
                    effect = SendMessage("15 minutes after routine started")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
            
            @Test
            fun `handles AfterEvent condition with PHASE_ENTERED`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterEvent(
                        eventType = RoutineAnchorEvent.PHASE_ENTERED,
                        phaseTitle = "Morning Routine",
                        timeExpression = "PT5M"
                    ),
                    effect = SendMessage("5 minutes after entering morning routine")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
            
            @Test
            fun `handles AfterEvent condition with PHASE_LEFT`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterEvent(
                        eventType = RoutineAnchorEvent.PHASE_LEFT,
                        phaseTitle = "Morning Routine",
                        timeExpression = "PT10M"
                    ),
                    effect = SendMessage("10 minutes after leaving morning routine")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
        }
        
        @Nested
        inner class `State-based Conditions` {
            
            @Test
            fun `handles AfterPhaseCompletions condition`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                val phaseId = RoutinePhaseId.forTitle("Test Phase")
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterPhaseCompletions(
                        phaseId = phaseId,
                        times = 5
                    ),
                    effect = SendMessage("Phase completed 5 times")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
            
            @Test
            fun `handles AfterParameterSet condition`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterParameterSet(
                        parameterKey = "alarmTime"
                    ),
                    effect = SendMessage("Parameter alarmTime was set")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                    })
                }
            }
        }
    }
    
    @Nested
    inner class `Trigger Effect Tests` {
        
        @Nested
        inner class `SendMessage Effects` {
            
            @Test
            fun `executes SendMessage effect with variable substitution`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterDays(value = 1),
                    effect = SendMessage("Hello \${USER_NAME}! Today is \${CURRENT_DATE}")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                        it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains("Hello")
                    })
                }
            }
            
            @Test
            fun `executes SendMessage effect with calendar and task variables`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterEvent(
                        eventType = RoutineAnchorEvent.PHASE_ENTERED,
                        phaseTitle = "Work Session",
                        timeExpression = "PT1H"
                    ),
                    effect = SendMessage("You have \${CALENDAR_EVENTS_COUNT} upcoming events and \${TASK_COUNT} pending tasks.")
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                        it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains("upcoming events")
                    })
                }
            }
        }
        
        @Nested
        inner class `CreateTask Effects` {
            
            @Test
            fun `executes CreateTask effect`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                val expiryDate = Instant.now().plusSeconds(3600)
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterDays(value = 1),
                    effect = CreateTask(
                        taskDescription = "Remember to take medication",
                        parameterKey = "medicationTaken",
                        expiryDate = expiryDate
                    )
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                every { taskRepository.save(any()) } returns mockk<Task>(relaxed = true) { every { id } returns "test-task-id" }
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    taskRepository.save(any())
                    eventLog.log(any())
                }
            }
            
            @Test
            fun `executes CreateTask effect with variable substitution`() {
                val friendshipId = FriendshipId()
                val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
                val instanceId = routineInstance.instanceId
                val triggerId = TriggerId()
                val expiryDate = Instant.now().plusSeconds(3600)
                
                val trigger = RoutineTrigger(
                    id = triggerId,
                    condition = AfterDays(value = 1),
                    effect = CreateTask(
                        taskDescription = "Complete \${TASK_COUNT} tasks today",
                        parameterKey = "tasksCompleted",
                        expiryDate = expiryDate
                    )
                )
                
                val event = RoutineTriggerFired(
                    this.javaClass,
                    friendshipId,
                    instanceId,
                    triggerId
                )
                
                setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
                every { taskRepository.save(any()) } returns mockk<Task>(relaxed = true) { every { id } returns "test-task-id" }
                
                service.onRoutineTriggerFired(event)
                
                verify {
                    taskRepository.save(any())
                    eventLog.log(any())
                }
            }
        }
    }
    
    @Nested
    inner class `Integration Tests` {
        
        @Test
        fun `handles complex trigger with multiple conditions and effects`() {
            val friendshipId = FriendshipId()
            val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
            val instanceId = routineInstance.instanceId
            val triggerId = TriggerId()
            
            // Test a complex scenario with time expression and parameter reference
            val trigger = RoutineTrigger(
                id = triggerId,
                condition = AtTimeExpression(
                    timeExpression = "\${wakeUpTime}+PT2H"
                ),
                effect = SendMessage("It's been 2 hours since you woke up at \${wakeUpTime}. Time for a break!")
            )
            
            val event = RoutineTriggerFired(
                this.javaClass,
                friendshipId,
                instanceId,
                triggerId
            )
            
            setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
            
            service.onRoutineTriggerFired(event)
            
                            verify {
                    eventPublisher.publishEvent(match<SendMessageCmd> {
                        it.friendshipId == friendshipId
                        it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains("It's been 2 hours")
                    })
                }
        }
    }
    
    @Nested
    inner class `Error Handling Tests` {
        
        @Test
        fun `handles trigger with invalid time expression gracefully`() {
            val friendshipId = FriendshipId()
            val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
            val instanceId = routineInstance.instanceId
            val triggerId = TriggerId()
            
            val trigger = RoutineTrigger(
                id = triggerId,
                condition = AtTimeExpression(
                    timeExpression = "invalid-expression"
                ),
                effect = SendMessage("This should still work")
            )
            
            val event = RoutineTriggerFired(
                this.javaClass,
                friendshipId,
                instanceId,
                triggerId
            )
            
            setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
            
            // Should not throw exception, should still execute the effect
            service.onRoutineTriggerFired(event)
            
            verify {
                eventPublisher.publishEvent(match<SendMessageCmd> {
                    it.friendshipId == friendshipId
                })
            }
        }
        
        @Test
        fun `handles trigger with missing parameter reference gracefully`() {
            val friendshipId = FriendshipId()
            val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
            val instanceId = routineInstance.instanceId
            val triggerId = TriggerId()
            
            val trigger = RoutineTrigger(
                id = triggerId,
                condition = AfterDays(value = 1),
                effect = SendMessage("Hello \${MISSING_PARAMETER}! How are you?")
            )
            
            val event = RoutineTriggerFired(
                this.javaClass,
                friendshipId,
                instanceId,
                triggerId
            )
            
            setupMocksForTriggerExecution(friendshipId, instanceId, trigger)
            
            // Should not throw exception, should substitute with empty string or placeholder
            service.onRoutineTriggerFired(event)
            
            verify {
                eventPublisher.publishEvent(match<SendMessageCmd> {
                    it.friendshipId == friendshipId
                    it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains("Hello")
                })
            }
        }
        
        @Test
        fun `handles missing routine instance gracefully`() {
            val friendshipId = FriendshipId()
            val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
            val instanceId = routineInstance.instanceId
            val triggerId = TriggerId()
            
            val event = RoutineTriggerFired(
                this.javaClass,
                friendshipId,
                instanceId,
                triggerId
            )
            
            every { routineRepository.findById(friendshipId, instanceId) } returns null
            
            // Should not throw exception
            service.onRoutineTriggerFired(event)
            
            verify(exactly = 0) { 
                eventPublisher.publishEvent(any<SendMessageCmd>())
            }
        }
        
        @Test
        fun `handles missing template gracefully`() {
            val friendshipId = FriendshipId()
            val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
            val instanceId = routineInstance.instanceId
            val triggerId = TriggerId()
            
            val event = RoutineTriggerFired(
                this.javaClass,
                friendshipId,
                instanceId,
                triggerId
            )
            
            every { routineRepository.findById(friendshipId, instanceId) } returns mockk<RoutineInstance>(relaxed = true)
            every { templateRepository.findById(any()) } returns null
            
            // Should not throw exception
            service.onRoutineTriggerFired(event)
            
            verify(exactly = 0) { 
                eventPublisher.publishEvent(any<SendMessageCmd>())
            }
        }
        
        @Test
        fun `handles missing trigger gracefully`() {
            val friendshipId = FriendshipId()
            val routineInstance = aRoutineInstance { this.friendshipId = friendshipId }
            val instanceId = routineInstance.instanceId
            val triggerId = TriggerId()
            
            val event = RoutineTriggerFired(
                this.javaClass,
                friendshipId,
                instanceId,
                triggerId
            )
            
            every { routineRepository.findById(friendshipId, instanceId) } returns mockk<RoutineInstance>(relaxed = true)
            every { templateRepository.findById(any()) } returns mockk<RoutineTemplate> {
                every { triggers } returns emptyList()
                every { templateId } returns RoutineTemplateId.forTitleVersion("test", "1.0")
            }
            
            // Should not throw exception
            service.onRoutineTriggerFired(event)
            
            verify(exactly = 0) { 
                eventPublisher.publishEvent(any<SendMessageCmd>())
            }
        }
    }
} 