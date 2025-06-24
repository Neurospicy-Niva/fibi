package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.IncomingFriendMessageReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.service.friends.interaction.tasks.TaskIntents
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

@ExtendWith(MockKExtension::class)
class ConversationOrchestratorTest {

    @MockK
    private lateinit var intentClassifier: IntentClassifier

    @MockK
    private lateinit var goalRefiner: GoalRefiner

    @MockK
    private lateinit var goalAchiever: GoalAchiever

    @MockK
    private lateinit var contextRepository: GoalContextRepository

    @MockK
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var orchestrator: ConversationOrchestrator

    private val friendshipId = FriendshipId()
    private val receivedAt: Instant = Instant.now()
    private val messageId = SignalMessageId(receivedAt.toEpochMilli())
    private val userMessage = someUserMessage().copy(messageId = messageId, receivedAt = receivedAt)

    private fun someUserMessage(): UserMessage = UserMessage(
        SignalMessageId(Instant.now().toEpochMilli()), Instant.now(), "Test message ${Math.random()}", Channel.SIGNAL
    )

    private val incomingEvent = IncomingFriendMessageReceived(
        friendshipId, userMessage
    )

    @BeforeEach
    fun setup() {
        orchestrator = ConversationOrchestrator(
            intentClassifier,
            goalRefiner,
            goalAchiever,
            contextRepository,
            eventPublisher,
            conversationContextService = mockk(relaxed = true),
            conversationRepository = mockk(relaxed = true),
        )
    }

    @Test
    fun `should handle pending goal clarification and directly return next clarification question`() = runBlocking {
        // Arrange
        val initialPendingGoalContext = goalContextNeedingClarification()
        val newClarificationQuestion = "Which is what you want, one thing or another?"
        val clarificationResponse = GoalClarificationResponse.needsClarification(newClarificationQuestion)

        every { contextRepository.loadContext(any()) } returns initialPendingGoalContext
        coEvery { goalRefiner.handleClarification(any(), any(), any()) } returns clarificationResponse
        every { contextRepository.saveContext(any(), any()) } returns Unit
        every { eventPublisher.publishEvent(any()) } returns Unit

        // Act
        orchestrator.onMessage(incomingEvent)

        // Assert
        coVerify { goalRefiner.handleClarification(friendshipId, initialPendingGoalContext, userMessage) }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> {
                (it.outgoingMessage as OutgoingGeneratedMessage).messageDescription == newClarificationQuestion
            })
        }
        verify {
            contextRepository.saveContext(friendshipId, match<GoalContext> {
                it.goalClarificationQuestion!!.prompt == newClarificationQuestion
            })
        }
        verify { intentClassifier wasNot Called }
        verify { goalAchiever wasNot Called }
    }

    @Test
    fun `should handle pending goal clarification and proceed normally on success`() = runBlocking {
        // Arrange
        val initialPendingGoalContext = goalContextNeedingClarification()
        val updatedContext = initialPendingGoalContext.copy(goalClarificationQuestion = null)
        val clarificationResponse = GoalClarificationResponse.clarified(TaskIntents.List)

        every { contextRepository.loadContext(any()) } returns initialPendingGoalContext
        coEvery { goalRefiner.handleClarification(any(), any(), any()) } returns clarificationResponse
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(CoreIntents.FollowUp, 0.8f)
        )
        coEvery { goalRefiner.refineGoal(any(), any(), any(), any()) } returns updatedContext
        coEvery { goalAchiever.advance(any(), any(), any()) } returns GoalAdvancementResult.completed(updatedContext)
        every { contextRepository.saveContext(any(), any()) } returns Unit
        every { eventPublisher.publishEvent(any()) } returns Unit

        // Act
        orchestrator.onMessage(incomingEvent)

        // Assert
        coVerify { goalRefiner.handleClarification(friendshipId, initialPendingGoalContext, userMessage) }
        verify { contextRepository.saveContext(friendshipId, updatedContext) }
        coVerify { intentClassifier.classifyIntent(any<Conversation>()) }
        coVerify { goalAchiever.advance(updatedContext, friendshipId, userMessage) }
        verify { eventPublisher.publishEvent(any()) }
    }

    private fun goalContextNeedingClarification(): GoalContext = GoalContext(
        goal = Goal(CoreIntents.Unknown), userMessage,
        goalClarificationQuestion = GoalClarificationQuestion(
            "Do you want to do one thing or another?", setOf(Intent("Do one thing"), Intent("Do another thing"))
        ),
    )

    @Test
    fun `should handle smalltalk with unstructured goal refiner and publish answer`() = runBlocking {
        // Arrange
        val initialTaskContext = getInitialTaskContext()
        every { contextRepository.loadContext(any()) } returns initialTaskContext
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(
                CoreIntents.Smalltalk, 1.0f
            )
        )
        coEvery { goalRefiner.onUnstructuredIntent(initialTaskContext, userMessage) } returns UnstructuredResponse(
            initialTaskContext
        )
        every { contextRepository.saveContext(any(), any()) } returns Unit
        every { eventPublisher.publishEvent(any()) } returns Unit
        // Act
        orchestrator.onMessage(incomingEvent)
        // Assert
        coVerify { goalRefiner.onUnstructuredIntent(initialTaskContext, userMessage) }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> {
                (it.outgoingMessage as OutgoingGeneratedMessage).messageDescription.contains("Answer kindly")
            })
        }
    }

    @Test
    fun `should handle smalltalk with unstructured goal refiner and publish their message`() = runBlocking {
        // Arrange
        val initialTaskContext = getInitialTaskContext()
        every { contextRepository.loadContext(any()) } returns initialTaskContext
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(
                CoreIntents.Smalltalk, 1.0f
            )
        )
        val question = "Do you actually want to stop your current task?"
        coEvery { goalRefiner.onUnstructuredIntent(initialTaskContext, userMessage) } returns UnstructuredResponse(
            initialTaskContext, question
        )
        every { contextRepository.saveContext(any(), any()) } returns Unit
        every { eventPublisher.publishEvent(any()) } returns Unit
        // Act
        orchestrator.onMessage(incomingEvent)
        // Assert
        coVerify { goalRefiner.onUnstructuredIntent(initialTaskContext, userMessage) }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> {
                (it.outgoingMessage as OutgoingGeneratedMessage).messageDescription == question
            })
        }
    }

    private fun getInitialTaskContext(): GoalContext = GoalContext(
        Goal(Intent("Add task")), someUserMessage(),
        subtasks = listOf(
            Subtask(
                SubtaskId("42"),
                Intent("Add task"),
                "Create the greatest task of all time!",
                mapOf("title" to null)
            )
        ),
    )

    @Test
    fun `should handle smalltalk without goal without processing, just publishing an answer`() = runBlocking {
        // Arrange
        val initialEmptyContext = null
        every { contextRepository.loadContext(any()) } returns initialEmptyContext
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(
                CoreIntents.Smalltalk, 1.0f
            )
        )
        every { eventPublisher.publishEvent(any()) } returns Unit
        // Act
        orchestrator.onMessage(incomingEvent)
        // Assert
        verify { goalRefiner wasNot Called }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> {
                (it.outgoingMessage as OutgoingGeneratedMessage).messageDescription.contains("Answer kindly")
            })
        }
    }

    @Test
    fun `should handle goal clarification needed after refinement`() = runBlocking {
        // Arrange
        val initialTaskContext = getInitialTaskContext()
        every { contextRepository.loadContext(any()) } returns initialTaskContext
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(Intent("Add Task"), 0.75f),
            IntentClassifier.IntentClassification(Intent("Add Reminder"), 0.75f)
        )
        every { eventPublisher.publishEvent(any()) } returns Unit
        every { contextRepository.saveContext(any(), any()) } returns Unit
        val clarificationQuestion = "This or that?"
        coEvery { goalRefiner.refineGoal(any(), any(), any(), any()) } returns GoalContext.unknown().copy(
            goalClarificationQuestion = GoalClarificationQuestion(
                clarificationQuestion, setOf(Intent("Add Task"), Intent("Add Reminder"))
            ),
        )
        // Act
        orchestrator.onMessage(incomingEvent)
        // Assert
        verify { goalAchiever wasNot Called }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> {
                (it.outgoingMessage as OutgoingGeneratedMessage).messageDescription == clarificationQuestion
            })
        }
    }

    @Test
    fun `should handle subtask clarification needed`() = runBlocking {
        // Arrange
        val addReminderTask = Subtask(SubtaskId("42"), Intent("Add Reminder"), "Set a reminder to prep meal for 8 pm.")
        val initialTaskContext = getInitialTaskContext().copy(subtasks = listOf(addReminderTask))
        every { contextRepository.loadContext(any()) } returns initialTaskContext
        val intentClassification = listOf(
            IntentClassifier.IntentClassification(
                CoreIntents.FollowUp, 0.95f
            )
        )
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns intentClassification
        val refinedContext = initialTaskContext.copy()
        coEvery { goalRefiner.refineGoal(any(), any(), any(), any()) } returns refinedContext
        val subtaskClarificationQuestion = "Who the fuck is alice?"
        val clarificationQuestion = SubtaskClarificationQuestion(subtaskClarificationQuestion, addReminderTask.id)
        val advancementResultNeedingClarification = GoalAdvancementResult.subtaskNeedsClarification(
            refinedContext,
            listOf(addReminderTask.copy(status = SubtaskStatus.InClarification)),
            listOf(clarificationQuestion)
        )
        coEvery { goalAchiever.advance(any(), any(), any()) } returns advancementResultNeedingClarification
        every { contextRepository.saveContext(any(), any()) } just runs
        every { eventPublisher.publishEvent(any()) } returns Unit
        // Act
        orchestrator.onMessage(incomingEvent)
        // Act
        coVerify { goalRefiner.refineGoal(intentClassification, friendshipId, incomingEvent.message, any()) }
        coVerify { goalAchiever.advance(refinedContext, friendshipId, userMessage) }
        verify {
            contextRepository.saveContext(friendshipId, match<GoalContext> {
                it.subtasks.any { subtask -> subtask.status == SubtaskStatus.InClarification } && it.subtaskClarificationQuestions.isNotEmpty()
            })
        }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> {
                (it.outgoingMessage as OutgoingGeneratedMessage).messageDescription.contains(
                    subtaskClarificationQuestion
                )
            })
        }
    }

    @Test
    fun `should handle completed goal`() = runBlocking {
        // Arrange
        val addReminderTask = Subtask(SubtaskId("42"), Intent("Add Reminder"), "Set a reminder to prep meal for 8 pm.")
        val initialTaskContext = getInitialTaskContext().copy(subtasks = listOf(addReminderTask))
        every { contextRepository.loadContext(any()) } returns initialTaskContext
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(
                CoreIntents.FollowUp, 0.95f
            )
        )
        val refinedContext = initialTaskContext.copy()
        coEvery { goalRefiner.refineGoal(any(), any(), any(), any()) } returns refinedContext
        val completedStatus = refinedContext.copy(
            subtasks = refinedContext.subtasks.map {
                it.copy(
                    status = SubtaskStatus.Completed
                )
            },
        )
        val subtaskSuccessPrompt = "Tell them the answer is 42"
        coEvery { goalAchiever.advance(any(), any(), any()) } returns GoalAdvancementResult.completed(
            completedStatus, subtaskSuccessGenerationPrompts = listOf(
                subtaskSuccessPrompt
            )
        )
        every { contextRepository.saveContext(any(), any()) } just runs
        every { eventPublisher.publishEvent(any()) } returns Unit
        //Act
        orchestrator.onMessage(incomingEvent)
        //Assert
        verify { contextRepository.saveContext(friendshipId, completedStatus) }
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> { messageCommand ->
                ((messageCommand.outgoingMessage as OutgoingGeneratedMessage).messageDescription).contains(
                    subtaskSuccessPrompt,
                    ignoreCase = true
                )
            })
        }
    }

    @Test
    fun `should handle no progress`() = runBlocking {
        // Arrange
        val addReminderTask = Subtask(SubtaskId("42"), Intent("Add Reminder"), "Set a reminder to prep meal for 8 pm.")
        val initialTaskContext = getInitialTaskContext().copy(subtasks = listOf(addReminderTask))
        every { contextRepository.loadContext(any()) } returns initialTaskContext
        coEvery { intentClassifier.classifyIntent(any<Conversation>()) } returns listOf(
            IntentClassifier.IntentClassification(
                CoreIntents.FollowUp, 0.95f
            )
        )
        val refinedContext = initialTaskContext.copy()
        coEvery { goalRefiner.refineGoal(any(), any(), any(), any()) } returns refinedContext
        coEvery { goalAchiever.advance(any(), any(), any()) } returns GoalAdvancementResult.ongoing(
            refinedContext,
            listOf(addReminderTask)
        )
        every { contextRepository.saveContext(any(), any()) } just runs
        every { eventPublisher.publishEvent(any()) } returns Unit
        //Act
        orchestrator.onMessage(incomingEvent)
        //Assert
        verify {
            eventPublisher.publishEvent(match<SendMessageCmd> { messageCommand ->
                ((messageCommand.outgoingMessage as OutgoingGeneratedMessage).messageDescription).let {
                    it.contains(
                        "The friend intended to achieve the goal ${initialTaskContext.goal?.intent}", ignoreCase = true
                    ) && it.contains("Tell the friend about this current ongoing process.", ignoreCase = true)
                }
            })
        }
    }
} 