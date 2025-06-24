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
import kotlin.test.*

class RemoveTaskSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var removeTaskSubtaskHandler: RemoveTaskSubtaskHandler

    @Test
    fun `can handle subtasks of RemoveTask intent`() {
        assertTrue { removeTaskSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TaskIntents.Remove)) }
    }

    @Test
    fun `removes task when clearly identified`() = runBlocking {
        createTestTasks()
        val task = taskRepository.save(Task(owner = friendshipId, title = "Organize books"))
        val userMessage = createUserMessage("Delete the task to organize books")

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Remove, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = removeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Remove), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status, "Expected subtask to be completed")
        assertFalse(
            taskRepository.findByFriendshipId(friendshipId).any { it.id == task.id }, "Expected task is deleted"
        )
        assertNull(subtaskClarificationQuestion, "Expected no clarification question")
    }

    @ParameterizedTest
    @MethodSource("removal examples")
    fun `removes indirectly identifieable task`(msg: String) = runBlocking {
        createTestTasks()
        val task = taskRepository.save(Task(owner = friendshipId, title = "Organize books"))
        val userMessage = createUserMessage(msg)

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Remove, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = removeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Remove), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status, "Expected subtask to be completed")
        assertFalse(
            taskRepository.findByFriendshipId(friendshipId).any { it.id == task.id }, "Expected task is deleted"
        )
        assertNull(subtaskClarificationQuestion, "Expected no clarification question")
    }

    @Test
    fun `asks clarification when task is ambiguous`() = runBlocking {
        createTestTasks()
        val userMessage = createUserMessage("Remove a task")

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Remove, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = removeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Remove), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertNotNull(subtaskClarificationQuestion, "Expecting clarification question")
        assertThat(subtaskClarificationQuestion!!.text).containsIgnoringCase("which")
        assertEquals(SubtaskStatus.InClarification, updatedSubtask.status, "Expected subtask to be in clarification")
    }

    @Test
    fun `removes most recent task when referring to -the- task`() = runBlocking {
        createTestTasks()
        val userMessage = createUserMessage("Remove the task")
        val recentlyCreatedTask =
            taskRepository.save(Task(owner = friendshipId, title = "Some arbitrary task recently created"))

        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.Remove, userMessage.text, mapOf("rawText" to userMessage.text))
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = removeTaskSubtaskHandler.handle(
            subtask, GoalContext(Goal(TaskIntents.Remove), userMessage, subtasks = listOf(subtask)), friendshipId
        )

        assertNull(subtaskClarificationQuestion, "Expecting no clarification question")
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status, "Expected subtask to be completed")
        assertNull(
            taskRepository.findByFriendshipId(friendshipId).firstOrNull { it.id == recentlyCreatedTask.id },
            "Expecting task to be deleted"
        )
    }

    @ParameterizedTest
    @MethodSource("clarify removal examples")
    fun `resolves clarification for task removal`(initialMessage: String, clarificationAnswer: String) = runBlocking {
        createTestTasks()
        val task = taskRepository.save(Task(owner = friendshipId, title = "Organize books"))
        val initialUserMessage = createUserMessage(initialMessage, now().minusSeconds(20))
        val clarificationUserMessage = createUserMessage(clarificationAnswer)

        val subtask = Subtask(
            SubtaskId("42"), TaskIntents.Remove, initialUserMessage.text, mapOf("rawText" to initialUserMessage.text)
        )
        val clarificationQuestion = SubtaskClarificationQuestion("Which task should I remove?", subtask.id)

        val (subtaskClarificationQuestion, _) = removeTaskSubtaskHandler.tryResolveClarification(
            subtask, clarificationQuestion, clarificationUserMessage, GoalContext(
                Goal(TaskIntents.Remove),
                initialUserMessage,
                subtasks = listOf(subtask),
                subtaskClarificationQuestions = listOf(clarificationQuestion)
            ), friendshipId
        )

        assertFalse(
            taskRepository.findByFriendshipId(friendshipId).any { it.id == task.id }, "Expected task is deleted"
        )
        assertNull(subtaskClarificationQuestion, "Expected no clarification question")
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
                completedAt = now().minusSeconds(3600 * 24),
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
        fun `clarify removal examples`(): List<Arguments> = listOf(
            Arguments.of("Remove a task", "The one to organize books"),
            Arguments.of("Delete a task", "Organize books"),
            Arguments.of("Delete a todo", "Something like sorting books"),
            Arguments.of("Delete todo", "Organizing books"),
            Arguments.of("Remove todo", "Please delete the organizing books one")
        )

        @JvmStatic
        fun `removal examples`(): List<Arguments> = listOf(
            Arguments.of("Remove the task. The one to organize books."),
            Arguments.of("Delete the task \"Organize books\""),
            Arguments.of("Delete the todo to sort books"),
            Arguments.of("Delete todo to organize books"),
            Arguments.of("Remove todo. Please delete the organizing books one")
        )

    }
}