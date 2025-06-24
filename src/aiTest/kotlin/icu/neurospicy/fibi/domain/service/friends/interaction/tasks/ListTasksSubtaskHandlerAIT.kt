package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShowTaskSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var showTasksSubtaskHandler: ListTasksSubtaskHandler

    @Test
    fun `can handle subtasks of List task intent`() {
        assertTrue { showTasksSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TaskIntents.List)) }
    }

    @ParameterizedTest
    @MethodSource("message to expected tasks")
    fun `lists tasks as expected`(
        message: String, expectedTitles: Set<String>
    ) = runBlocking {
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        createTestTasks()
        val subtask = Subtask(SubtaskId("42"), TaskIntents.List, "List tasks", mapOf("rawText" to message))
        val context = GoalContext(Goal(TaskIntents.List), userMessage, subtasks = listOf(subtask))
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = showTasksSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed, "Subtask should be complete")
        assertNull(subtaskClarificationQuestion, "No clarification expected")
        assertNotNull(successMessageGenerationPrompt, "Response generation prompt should not be null")
        expectedTitles.forEach {
            assertThat(successMessageGenerationPrompt).containsIgnoringCase(it)
                .withFailMessage { "Expected response \"$successMessageGenerationPrompt\" to contain \"$it\"" }
        }
    }

    @Test
    fun `empty task list does not produce null answer`() = runBlocking {
        val listAllTasksMessage = "List all my tasks"
        val userMessage = UserMessage(
            SignalMessageId(now().epochSecond), now(), listAllTasksMessage, Channel.SIGNAL
        )
        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.List, listAllTasksMessage, mapOf("rawText" to listAllTasksMessage))
        val context = GoalContext(Goal(TaskIntents.List), userMessage, subtasks = listOf(subtask))
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = showTasksSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed, "Subtask should be complete")
        assertThat(successMessageGenerationPrompt).doesNotContainIgnoringCase("null")
        assertThat(successMessageGenerationPrompt).containsIgnoringCase("no tasks")
        assertNull(subtaskClarificationQuestion, "No clarification expected")
    }

    @Test
    fun `all tasks can be listed`() = runBlocking {
        val listAllTasksMessage = "List all my tasks"
        val userMessage = UserMessage(
            SignalMessageId(now().epochSecond), now(), listAllTasksMessage, Channel.SIGNAL
        )
        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.List, listAllTasksMessage, mapOf("rawText" to listAllTasksMessage))
        val context = GoalContext(Goal(TaskIntents.List), userMessage, subtasks = listOf(subtask))
        createTestTasks()
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = showTasksSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed, "Subtask should be complete")
        testTasks.forEach { testTask ->
            assertThat(successMessageGenerationPrompt).containsIgnoringCase(testTask.title)
        }
        assertNull(subtaskClarificationQuestion, "No clarification expected")
    }

    @Test
    fun `all complete tasks can be listed`() = runBlocking {
        val listAllTasksMessage = "Which tasks are already done?"
        val userMessage = UserMessage(
            SignalMessageId(now().epochSecond), now(), listAllTasksMessage, Channel.SIGNAL
        )
        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.List, listAllTasksMessage, mapOf("rawText" to listAllTasksMessage))
        val context = GoalContext(Goal(TaskIntents.List), userMessage, subtasks = listOf(subtask))
        createTestTasks()
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = showTasksSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed, "Subtask should be complete")
        testTasks.filter { it.completed }.forEach { testTask ->
            assertThat(successMessageGenerationPrompt).containsIgnoringCase(testTask.title)
        }
        assertNull(subtaskClarificationQuestion, "No clarification expected")
    }

    @Test
    fun `all ongoing tasks can be listed`() = runBlocking {
        val listAllTasksMessage = "What do I have to do?"
        val userMessage = UserMessage(
            SignalMessageId(now().epochSecond), now(), listAllTasksMessage, Channel.SIGNAL
        )
        val subtask =
            Subtask(SubtaskId("42"), TaskIntents.List, listAllTasksMessage, mapOf("rawText" to listAllTasksMessage))
        val context = GoalContext(Goal(TaskIntents.List), userMessage, subtasks = listOf(subtask))
        createTestTasks()
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = showTasksSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed, "Subtask should be complete")
        testTasks.filter { !it.completed }.forEach { testTask ->
            assertThat(successMessageGenerationPrompt).containsIgnoringCase(testTask.title)
        }
        assertNull(subtaskClarificationQuestion, "No clarification expected")
    }

    companion object {
        @JvmStatic
        fun `message to expected tasks`(): List<Arguments> = listOf(
            Arguments.of(
                "List my sorting and organizing tasks", setOf("Organize books", "Organize DVDs", "Sort video games")
            ),
            Arguments.of("Are there any tasks concerning Mike?", setOf("Answer letter to Mike")),
            Arguments.of("Do I have any sport task?", setOf("Go jogging")),
            Arguments.of("Are there any open todos regarding Janes birthday?", setOf("Send package")),
            Arguments.of(
                "Show my incomplete todos", setOf(
                    "Organize books",
                    "Organize DVDs",
                    "Sort video games",
                    "Read the puppy magazine",
                    "Go jogging",
                    "Send package"
                )
            ),
            Arguments.of("Show my complete tasks", setOf("Choose gift", "Buy gift", "Write letter")),
        )
    }

    lateinit var testTasks: List<Task>

    @BeforeEach
    fun setupTestTasks() {
        testTasks = listOf(
            Task(owner = friendshipId, title = "Organize books"),
            Task(owner = friendshipId, title = "Organize DVDs"),
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
}
