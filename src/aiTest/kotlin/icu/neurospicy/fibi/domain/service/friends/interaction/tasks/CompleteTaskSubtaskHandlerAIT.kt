package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.Instant.now
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompleteTaskSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var completeTaskSubtaskHandler: CompleteTaskSubtaskHandler

    @Test
    fun `can handle subtasks of CompleteTask intent`() {
        assertTrue { completeTaskSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TaskIntents.Complete)) }
    }

    @Test
    fun `completes task when clearly identified`() = runBlocking {
        createTestTasks()
        val task = taskRepository.save(Task(owner = friendshipId, title = "Organize books"))
        val userMessage = createUserMessage("Mark the task to organize books as done")

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Complete, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessage, subtaskClarificationQuestion, updatedSubtask) = completeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Complete), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status, "Expected subtask to be completed")
        val updated = taskRepository.findByFriendshipId(friendshipId).first { it.id == task.id }
        assertTrue(updated.completed)
        assertNotNull(updated.completedAt)
        assertNull(subtaskClarificationQuestion, "Expected no clarification question")
    }

    @ParameterizedTest
    @MethodSource("completion examples")
    fun `completes indirectly identifieable task`(msg: String) = runBlocking {
        createTestTasks()
        val task = taskRepository.save(Task(owner = friendshipId, title = "Organize books"))
        val userMessage = createUserMessage(msg)

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Complete, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = completeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Complete), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status, "Expected subtask to be completed")
        val updated = taskRepository.findByFriendshipId(friendshipId).first { it.id == task.id }
        assertTrue(updated.completed)
        assertNotNull(updated.completedAt)
        assertNull(subtaskClarificationQuestion, "Expected no clarification question")
    }

    @Test
    fun `asks clarification when task is ambiguous`() = runBlocking {
        createTestTasks()
        val userMessage = createUserMessage("Mark task as done")

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Complete, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = completeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Complete), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertNotNull(subtaskClarificationQuestion, "Expecting clarification question")
        assertThat(subtaskClarificationQuestion!!.text).containsIgnoringCase("which")
        assertEquals(SubtaskStatus.InClarification, updatedSubtask.status, "Expected subtask to be in clarification")
    }

    @ParameterizedTest
    @MethodSource("clarify completion examples")
    fun `resolves clarification for task completion`(initialMessage: String, clarificationAnswer: String) =
        runBlocking {
            createTestTasks()
            val task = taskRepository.save(Task(owner = friendshipId, title = "Organize books"))
            val initialUserMessage = createUserMessage(initialMessage, now().minusSeconds(20))
            val clarificationUserMessage = createUserMessage(clarificationAnswer)

            val subtask =
                Subtask(
                    SubtaskId("42"),
                    TaskIntents.Complete,
                    initialUserMessage.text,
                    mapOf("rawText" to initialUserMessage.text)
                )
            val clarificationQuestion = SubtaskClarificationQuestion("Which task should I mark as done?", subtask.id)

            val (subtaskClarificationQuestion, successMessageGenerationPrompt, _) = completeTaskSubtaskHandler.tryResolveClarification(
                subtask, clarificationQuestion, clarificationUserMessage, GoalContext(
                    Goal(TaskIntents.Complete), initialUserMessage,
                    subtasks = listOf(subtask),
                    subtaskClarificationQuestions = listOf(clarificationQuestion)
                ), friendshipId
            )

            val updated = taskRepository.findByFriendshipId(friendshipId).first { it.id == task.id }
            assertTrue(updated.completed)
            assertNotNull(updated.completedAt)
            assertNull(subtaskClarificationQuestion, "Expected no clarification question")
            assertNotNull(successMessageGenerationPrompt)
        }

    private fun createUserMessage(msg: String, receivedAt: Instant = now()): UserMessage = UserMessage(
        SignalMessageId(receivedAt.epochSecond), receivedAt, msg, Channel.SIGNAL
    )

    lateinit var testTasks: List<Task>

    @BeforeEach
    fun setupTestTasks() {
        testTasks = listOf(
            Task(
                owner = friendshipId,
                title = "Organize DVDs",
                completed = true,
                completedAt = now().minusSeconds(3600 * 24)
            ),
            Task(owner = friendshipId, title = "Sort video games"),
            Task(owner = friendshipId, title = "Read the puppy magazine"),
            Task(owner = friendshipId, title = "Go jogging"),
            Task(
                owner = friendshipId, title = "Answer letter to Mike", description = "Tell them to try the ponytail"
            ),
            Task(
                owner = friendshipId,
                title = "Jane-Bday: Choose gift",
                description = "They talked about a decoration for their garden",
                completed = true,
                completedAt = now().minusSeconds(3600 * 4)
            ),
            Task(
                owner = friendshipId,
                title = "Jane-Bday: Buy gift",
                completed = true,
                completedAt = now().minusSeconds(3600 * 4)
            ),
            Task(
                owner = friendshipId,
                title = "Jane-Bday: Write letter",
                description = "Already prepared text with AI!",
                completed = true,
                completedAt = now().minusSeconds(3600 * 2)
            ),
            Task(
                owner = friendshipId,
                title = "Jane-Bday: Package gift",
                completed = true,
                completedAt = now().minusSeconds(3600)
            ),
            Task(
                owner = friendshipId, title = "Jane-Bday: Send package"
            ),
        ).map { task -> task.copy(lastModifiedAt = now().minusSeconds(3600)) }
    }

    fun createTestTasks() {
        testTasks.forEach { taskRepository.save(it) }
    }

    companion object {
        @JvmStatic
        fun `clarify completion examples`(): List<Arguments> = listOf(
            Arguments.of("Mark a task done", "The one to organize books"),
            Arguments.of("Finish a task", "Organize books"),
            Arguments.of("Complete a todo", "Sorting books"),
            Arguments.of("Mark todo done", "Organizing books"),
            Arguments.of("Check off a task", "Please mark the organizing books one")
        )

        @JvmStatic
        fun `completion examples`(): List<Arguments> = listOf(
            Arguments.of("Mark the task. The one to organize books."),
            Arguments.of("Mark the task \"Organize books\" as complete"),
            Arguments.of("Mark the todo to sort books as done"),
            Arguments.of("Finish todo to organize books"),
            Arguments.of("Check off todo. Please complete the organizing books one")
        )
    }
}