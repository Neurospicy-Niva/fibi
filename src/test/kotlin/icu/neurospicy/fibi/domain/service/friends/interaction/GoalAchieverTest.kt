package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class GoalAchieverTest {

    private lateinit var goalAchiever: GoalAchiever
    private lateinit var llmClient: LlmClient
    private lateinit var intentRegistry: IntentRegistry
    private lateinit var subtaskHandlers: List<SubtaskHandler>
    private lateinit var subtaskRegistry: SubtaskRegistry

    private val friendshipId = FriendshipId("test-friend")
    private val messageId = SignalMessageId(Instant.now().toEpochMilli())
    private val userMessage = UserMessage(messageId, Instant.now(), "Test message", Channel.SIGNAL)

    @BeforeEach
    fun setup() {
        llmClient = mockk(relaxed = true)
        intentRegistry = mockk(relaxed = true)
        subtaskRegistry = mockk(relaxed = true)
        subtaskHandlers = mutableListOf()

        goalAchiever = GoalAchiever(
            subtaskHandlers,
            llmClient,
            friendshipLedger = mockk(relaxed = true),
        )
    }

    @Test
    fun `should handle successful clarification for subtask that needs it`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.InClarification
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(subtask), subtaskClarificationQuestions = listOf(
                SubtaskClarificationQuestion(
                    "What is the title?", subtask.id
                )
            )
        )

        val mockHandler = mockk<SubtaskHandler>()
        (subtaskHandlers as MutableList<SubtaskHandler>).add(mockHandler)

        every { mockHandler.canHandle(subtask) } returns true
        coEvery {
            mockHandler.tryResolveClarification(subtask, any(), userMessage, context, friendshipId)
        } returns SubtaskClarificationResult.success(updatedSubtask = subtask)
        // Act
        val result = goalAchiever.handleClarification(friendshipId, context, userMessage)

        // Assert
        assertTrue(result.clarified())
        assertTrue(result.updatedContext.subtaskClarificationQuestions.isEmpty())
        assertTrue(result.updatedContext.subtasks.none { it.status == SubtaskStatus.InClarification })
        assertNull(result.clarificationQuestion)
    }

    @Test
    fun `should request additional clarification for subtask`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.InClarification
        )
        val newQuestion = SubtaskClarificationQuestion("What priority level?", subtask.id)
        val context = GoalContext(
            goal = Goal(taskIntent), userMessage,
            subtasks = listOf(subtask),
            subtaskClarificationQuestions = listOf(newQuestion)
        )
        val mockHandler = mockk<SubtaskHandler>()
        (subtaskHandlers as MutableList<SubtaskHandler>).add(mockHandler)
        every { mockHandler.canHandle(subtask) } returns true
        coEvery {
            mockHandler.tryResolveClarification(subtask, newQuestion, userMessage, context, friendshipId)
        } returns SubtaskClarificationResult.needsClarification(updatedSubtask = subtask, newQuestion.text)


        // Act
        val result = goalAchiever.handleClarification(friendshipId, context, userMessage)

        // Assert
        assertFalse(result.clarified())
        assertEquals(result.updatedContext.subtaskClarificationQuestions.first().relatedSubtask, subtask.id)
        assertThat(result.updatedContext.subtaskClarificationQuestions.first().text).containsIgnoringCase("What priority level?")
        assertNotNull(result.clarificationQuestion)
        assertTrue(result.clarificationQuestion!!.contains("What priority level?"))
    }

    @Test
    fun `should abort subtask on abortion answer`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.InClarification
        )
        val newQuestion = SubtaskClarificationQuestion("What priority level?", subtask.id)
        val context = GoalContext(
            goal = Goal(taskIntent), userMessage,
            subtasks = listOf(subtask),
            subtaskClarificationQuestions = listOf(newQuestion)
        )
        val mockHandler = mockk<SubtaskHandler>()
        (subtaskHandlers as MutableList<SubtaskHandler>).add(mockHandler)
        every { mockHandler.canHandle(subtask) } returns true

        coEvery { llmClient.promptReceivingText(any(), any(), any(), any()) } returns "yes"
        // Act
        val result = goalAchiever.handleClarification(friendshipId, context, userMessage)

        // Assert
        assertTrue(result.clarified())
        assertTrue(result.successMessageGenerationPrompt!!.contains("aborted"))
    }


    @Test
    fun `should return original context and processing error when no subtask needs clarification`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.Pending
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(subtask),
        )

        // Act
        val result = goalAchiever.handleClarification(friendshipId, context, userMessage)

        // Assert
        assertTrue(result.hasProcessingError)
        assertEquals(context.goal, result.updatedContext.goal)
        assertEquals(context.subtasks, result.updatedContext.subtasks)
        assertEquals(context.goalClarificationQuestion, result.updatedContext.goalClarificationQuestion)
        assertEquals(context.subtaskClarificationQuestions, result.updatedContext.subtaskClarificationQuestions)
        assertNull(result.clarificationQuestion)
        assertTrue(result.hasProcessingError)
    }

    @Test
    fun `should return empty goal advancement when no subtasks exist`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val context = GoalContext(
            goal = Goal(taskIntent), userMessage,
        )

        // Act
        val result = goalAchiever.advance(context, friendshipId, userMessage)

        // Assert
        assertTrue(result.complete())
        assertEquals(context, result.updatedContext)
        assertEquals(emptyList(), result.updatedContext.subtaskClarificationQuestions)
    }

    @Test
    fun `should process subtask with handler and update status`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.Pending
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(subtask),
        )

        val mockHandler = mockk<SubtaskHandler>()
        (subtaskHandlers as MutableList<SubtaskHandler>).add(mockHandler)

        every { mockHandler.canHandle(subtask) } returns true
        coEvery {
            mockHandler.handle(subtask, context, any())
        } returns SubtaskResult.success(updatedSubtask = subtask)

        // Act
        val result = goalAchiever.advance(context, friendshipId, userMessage)

        // Assert
        assertTrue(result.complete())
        assertTrue(result.updatedContext.subtasks.all { it.status == SubtaskStatus.Completed })
        assertFalse(result.clarificationNeeded())
    }

    @Test
    fun `should process subtask and request clarification when need for clarification`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.Pending
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(subtask),
        )

        val mockHandler = mockk<SubtaskHandler>()
        (subtaskHandlers as MutableList<SubtaskHandler>).add(mockHandler)

        every { mockHandler.canHandle(subtask) } returns true
        coEvery {
            mockHandler.handle(subtask, context, any())
        } returns SubtaskResult.needsClarification(subtask, "What is the title?")

        // Act
        val result = goalAchiever.advance(context, friendshipId, userMessage)

        // Assert
        assertFalse(result.complete())
        assertThat(result.updatedContext.subtaskClarificationQuestions).withFailMessage { "Expecting clarification question" }.isNotEmpty.withFailMessage { "Expecting clarification question of subtask needing clarification" }
            .anyMatch { it.text == "What is the title?" }
        assertTrue(result.clarificationNeeded())

        // The updated context should contain the subtask with the clarification question
        val updatedSubtask = result.updatedContext.subtasks.first()
        assertEquals(updatedSubtask.status, SubtaskStatus.InClarification)
    }

    @Test
    fun `should process multiple subtasks in sequence`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask1 = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.Pending
        )
        val subtask2 = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Set a deadline", status = SubtaskStatus.Pending
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(subtask1, subtask2),
        )

        val mockHandler = mockk<SubtaskHandler>()
        (subtaskHandlers as MutableList<SubtaskHandler>).add(mockHandler)

        every { mockHandler.canHandle(any<Subtask>()) } returns true
        coEvery {
            mockHandler.handle(any(), any(), any())
        } returns SubtaskResult.success(updatedSubtask = subtask1) andThen SubtaskResult.inProgress(subtask2)

        // Act
        val result = goalAchiever.advance(context, friendshipId, userMessage)

        // Assert
        assertFalse(result.complete())

        // The first subtask should be completed, second still pending
        assertTrue { result.updatedContext.subtasks.any { SubtaskStatus.Completed == it.status } }
        assertTrue { result.updatedContext.subtasks.any { SubtaskStatus.InProgress == it.status } }
    }

    @Test
    fun `should mark goal as complete when all subtasks are completed`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val completedSubtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.Completed
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(completedSubtask),
        )

        // Act
        val result = goalAchiever.advance(context, friendshipId, userMessage)

        // Assert
        assertTrue(result.complete())
        assertEquals(context, result.updatedContext)
    }

    @Test
    fun `should return unchanged context when no handler for subtask exists`() = runBlocking {
        // Arrange
        val taskIntent = Intent("Task")
        val subtask = Subtask(
            SubtaskId("42"),
            intent = taskIntent, description = "Create a task", status = SubtaskStatus.Pending
        )

        val context = GoalContext(
            goal = Goal(taskIntent), userMessage, subtasks = listOf(subtask),
        )

        // No handlers added that can handle the subtask

        // Act
        val result = goalAchiever.advance(context, friendshipId, userMessage)

        // Assert
        assertFalse(result.complete())
        assertEquals(context, result.updatedContext)
    }
} 