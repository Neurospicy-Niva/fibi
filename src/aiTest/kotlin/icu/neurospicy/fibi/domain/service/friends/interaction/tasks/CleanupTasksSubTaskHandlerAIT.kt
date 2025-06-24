package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.assertNull

class CleanupTasksSubTaskHandlerAIT : BaseAIT() {

    @Autowired
    private lateinit var cleanupTasksSubTaskHandler: CleanupTasksSubTaskHandler


    @Test
    fun `clarifies that a bunch of completed tasks is deleted`() = runBlocking {
        createTestTasks()
        val receivedAt = now()
        val message = "Clean up my task list"
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val subtask = Subtask(SubtaskId("42"), TaskIntents.Cleanup, "Clean up tasks", mapOf("rawText" to message))
        val context = GoalContext(Goal(TaskIntents.Cleanup), userMessage, subtasks = listOf(subtask))
        // Act
        val (successMessage, subtaskClarificationQuestion, updatedSubtask) = cleanupTasksSubTaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertThat(updatedSubtask.status).isEqualTo(SubtaskStatus.InClarification)
        assertThat(subtaskClarificationQuestion).isNotNull().extracting { it!!.text }.asString()
            .withFailMessage { "Clarification question should contain count of completed tasks" }
            .containsIgnoringCase(testTasks.filter { it.completed }.size.toString())
            .withFailMessage { "Clarification question should ask for deletion" }.contains("?")
        assertNull(successMessage)
    }

    @Test
    fun `tells that there are no completed tasks to cleanup`() = runBlocking {
        createIncompleteTestTasks()
        val receivedAt = now()
        val message = "Clean up my task list"
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val subtask = Subtask(SubtaskId("42"), TaskIntents.Cleanup, "Clean up tasks", mapOf("rawText" to message))
        val context = GoalContext(Goal(TaskIntents.Cleanup), userMessage, subtasks = listOf(subtask))
        // Act
        val (successMessage, subtaskClarificationQuestion, updatedSubtask) = cleanupTasksSubTaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertThat(updatedSubtask.status).isEqualTo(SubtaskStatus.Completed)
        assertThat(subtaskClarificationQuestion).withFailMessage { "Should not clarify if there are no tasks to cleanup" }
            .isNull()
        assertNotNull(successMessage, "Should answer that there is nothing to cleanup")
    }

    @Test
    fun `deletes all completed tasks on agreed cleanup`() = runBlocking {
        createTestTasks()
        val receivedAt = now()
        val initialMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, "Clean up my task list", Channel.SIGNAL
        )
        val message = "Yes"
        val answer = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("42"),
            TaskIntents.Cleanup,
            "Clean up tasks",
            mapOf("rawText" to "Clean up my task list"),
            status = SubtaskStatus.InClarification
        )
        val context = GoalContext(Goal(TaskIntents.Cleanup), initialMessage, subtasks = listOf(subtask))
        // Act
        val (subtaskClarificationQuestion, _) = cleanupTasksSubTaskHandler.tryResolveClarification(
            subtask, SubtaskClarificationQuestion(
                "Cleaning up will delete ${testTasks.filter { it.completed }.size} tasks. Are you sure?", subtask.id
            ), answer, context, friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).withFailMessage { "No completed tasks expected" }
            .noneMatch { it.completed }
        assertThat(subtaskClarificationQuestion).withFailMessage { "No clarifying question expected" }.isNull()
    }

    @Test
    fun `does not cleanup when user disagrees cleanup`() = runBlocking {
        createTestTasks()
        val receivedAt = now()
        val initialMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, "Clean up my task list", Channel.SIGNAL
        )
        val message = "No"
        val answer = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("42"),
            TaskIntents.Cleanup,
            "Clean up tasks",
            mapOf("rawText" to "Clean up my task list"),
            status = SubtaskStatus.InClarification
        )
        val context = GoalContext(Goal(TaskIntents.Cleanup), initialMessage, subtasks = listOf(subtask))
        // Act
        val (subtaskClarificationQuestion, _) = cleanupTasksSubTaskHandler.tryResolveClarification(
            subtask, SubtaskClarificationQuestion(
                "Cleaning up will delete ${testTasks.filter { it.completed }.size} tasks. Are you sure?", subtask.id
            ), answer, context, friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).withFailMessage { "Nothing should be done on disagreement" }
            .size().isEqualTo(testTasks.size)
        assertThat(subtaskClarificationQuestion).withFailMessage { "No clarifying question expected" }.isNull()
    }

    @Test
    fun `does ask another clarification question if intent is unclear`() = runBlocking<Unit> {
        createTestTasks()
        val receivedAt = now()
        val initialMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, "Clean up my task list", Channel.SIGNAL
        )
        val message = "What time is it?"
        val answer = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val subtask = Subtask(
            SubtaskId("42"),
            TaskIntents.Cleanup,
            "Clean up tasks",
            mapOf("rawText" to "Clean up my task list"),
            status = SubtaskStatus.InClarification
        )
        val context = GoalContext(Goal(TaskIntents.Cleanup), initialMessage, subtasks = listOf(subtask))
        // Act
        val (subtaskClarificationQuestion, _) = cleanupTasksSubTaskHandler.tryResolveClarification(
            subtask, SubtaskClarificationQuestion(
                "Cleaning up will delete ${testTasks.filter { it.completed }.size} tasks. Are you sure?", subtask.id
            ), answer, context, friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).withFailMessage { "Nothing should be done on disagreement" }
            .size().isEqualTo(testTasks.size)
        assertThat(subtaskClarificationQuestion).withFailMessage { "New clarification question is expected" }
            .isNotNull()
            .extracting { it!!.text }.asString().containsIgnoringCase("?")
    }

    lateinit var testTasks: List<Task>

    @BeforeEach
    fun setupTestTasks() {
        testTasks = listOf(
            Task(owner = friendshipId, title = "Organize books"),
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

    fun createCompletedTestTasks() {
        testTasks.filter { it.completed }.forEach { taskRepository.save(it) }
    }

    fun createIncompleteTestTasks() {
        testTasks.filter { !it.completed }.forEach { taskRepository.save(it) }
    }
}