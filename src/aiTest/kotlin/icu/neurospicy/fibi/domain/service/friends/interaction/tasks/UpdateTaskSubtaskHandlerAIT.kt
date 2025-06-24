package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateTaskSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var updateTaskSubtaskHandler: UpdateTaskSubtaskHandler

    @Test
    fun `can handle subtasks of UpdateTask intent`() {
        assertTrue { updateTaskSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TaskIntents.Update)) }
    }

    @ParameterizedTest
    @MethodSource("update task examples")
    fun testUpdateTask(
        taskTemplate: TaskTemplate, message: String, taskExpectations: TaskExpectations
    ) = runBlocking {
        val oldTask = taskRepository.save(
            Task(
                owner = friendshipId,
                title = taskTemplate.title ?: "Clean the kitchen",
                description = taskTemplate.description,
                completed = taskTemplate.isCompleted == true,
                completedAt = if (taskTemplate.isCompleted == true) now() else null
            )
        )
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val updateTask = Subtask(SubtaskId("42"), TaskIntents.Update, message, mapOf("rawText" to message))
        createTestTasks()
        // Act
        val (_, subtaskClarificationQuestion, _) = updateTaskSubtaskHandler.handle(
            updateTask, GoalContext(
                Goal(TaskIntents.Update), userMessage,
                subtasks = listOf(
                    updateTask
                ),
            ), friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).filteredOn { it.id == oldTask.id }.first()
            .matches({ task ->
                taskExpectations.titleParts?.all { task.title.contains(it, ignoreCase = true) } ?: true
            }, "expecting title to contain ${taskExpectations.titleParts}").matches({ task ->
                taskExpectations.descriptionParts?.all {
                    task.description!!.contains(
                        it, ignoreCase = true
                    )
                } ?: true
            }, "expecting description to contain ${taskExpectations.descriptionParts}").matches(
                { task -> taskExpectations.isCompleted?.let { it == task.completed } ?: true },
                "expecting completed to be ${taskExpectations.isCompleted}"
            )
        assertNull(subtaskClarificationQuestion)
        assertTrue(updateTask.status == SubtaskStatus.Completed)
    }

    @Ignore
    @ParameterizedTest
    @MethodSource("adding things to existing task information")
    fun testUpdateExistingInformationOfTask(
        taskTemplate: TaskTemplate, message: String, taskExpectations: TaskExpectations
    ) = runBlocking {
        val oldTask = taskRepository.save(
            Task(
                owner = friendshipId,
                title = taskTemplate.title ?: "Clean the kitchen",
                description = taskTemplate.description,
                completed = taskTemplate.isCompleted == true,
                completedAt = if (taskTemplate.isCompleted == true) now() else null
            )
        )
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val updateTask = Subtask(SubtaskId("42"), TaskIntents.Update, message, mapOf("rawText" to message))
        createTestTasks()
        // Act
        val (_, subtaskClarificationQuestion, _) = updateTaskSubtaskHandler.handle(
            updateTask, GoalContext(
                Goal(TaskIntents.Update), userMessage,
                subtasks = listOf(
                    updateTask
                ),
            ), friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).filteredOn { it.id == oldTask.id }.first()
            .matches({ task ->
                taskExpectations.titleParts?.all { task.title.contains(it, ignoreCase = true) } ?: true
            }, "expecting title to contain ${taskExpectations.titleParts}").matches({ task ->
                taskExpectations.descriptionParts?.all {
                    task.description!!.contains(
                        it, ignoreCase = true
                    )
                } ?: true
            }, "expecting description to contain ${taskExpectations.descriptionParts}").matches(
                { task -> taskExpectations.isCompleted?.let { it == task.completed } ?: true },
                "expecting completed to be ${taskExpectations.isCompleted}"
            )
        assertNull(subtaskClarificationQuestion)
        assertTrue(updateTask.status == SubtaskStatus.Completed)
    }

    @ParameterizedTest
    @MethodSource("clarify updating task examples")
    fun `takes parameters from answer to clarification`(
        taskTemplate: TaskTemplate, message: String, clarificationAnswer: String, taskExpectations: TaskExpectations
    ) = runBlocking {
        val oldTask = taskRepository.save(
            Task(
                owner = friendshipId,
                title = taskTemplate.title ?: "Clean the kitchen",
                description = taskTemplate.description,
                completed = taskTemplate.isCompleted == true,
                completedAt = if (taskTemplate.isCompleted == true) now() else null
            )
        )
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.minusSeconds(10).epochSecond),
            receivedAt.minusSeconds(10),
            message,
            Channel.SIGNAL
        )
        val clarificationMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, clarificationAnswer, Channel.SIGNAL
        )
        val updateTask = Subtask(SubtaskId("42"), TaskIntents.Update, message, mapOf("rawText" to message))
        // Act
        val clarificationQuestion = SubtaskClarificationQuestion(
            "Which task do you want to update? What exactly should be updated?", updateTask.id
        )
        val (subtaskClarificationQuestion, _) = updateTaskSubtaskHandler.tryResolveClarification(
            updateTask, clarificationQuestion, clarificationMessage, GoalContext(
                Goal(TaskIntents.Update), userMessage,
                subtasks = listOf(
                    updateTask
                ),
                subtaskClarificationQuestions = listOf(clarificationQuestion),
            ), friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).filteredOn { it.id == oldTask.id }.first()
            .matches({ task ->
                taskExpectations.titleParts?.all { task.title.contains(it, ignoreCase = true) } ?: true
            }, "expecting title to contain ${taskExpectations.titleParts}").matches({ task ->
                taskExpectations.descriptionParts?.all {
                    task.description!!.contains(
                        it, ignoreCase = true
                    )
                } ?: true
            }, "expecting description to contain ${taskExpectations.descriptionParts}").matches(
                { task -> taskExpectations.isCompleted?.let { it == task.completed } ?: true },
                "expecting completed to be ${taskExpectations.isCompleted}"
            )
        assertNull(subtaskClarificationQuestion)
    }

    @Test
    fun `updates an existing task successfully`() = runBlocking {
        val existingTask = taskRepository.save(
            taskRepository.save(
                Task(
                    owner = friendshipId, title = "Old Title", description = "Old Description", completed = false
                )
            )
        )
        createTestTasks()
        val updateMessage = "Update task with title \"Old Title\": change title to \"New Title\" and mark as done"
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, updateMessage, Channel.SIGNAL
        )
        val updateTask = Subtask(SubtaskId("42"), TaskIntents.Update, updateMessage, mapOf("rawText" to updateMessage))
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = updateTaskSubtaskHandler.handle(
            updateTask, GoalContext(
                Goal(TaskIntents.Update), userMessage,
                subtasks = listOf(updateTask),
            ), friendshipId
        )
        // Assert
        val updatedTask = taskRepository.findByFriendshipId(friendshipId).first { it.id == existingTask.id!! }
        assertNotNull(updatedTask)
        assertThat(updatedTask).matches { it.title.contains("New Title", ignoreCase = true) }.matches { it.completed }
        assertNull(subtaskClarificationQuestion)
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed)
    }

    @Test
    fun `updates recently added task when referring to the task`() = runBlocking {
        val existingTask = taskRepository.save(
            taskRepository.save(
                Task(
                    owner = friendshipId,
                    title = "Mail to Clara",
                    description = "Invite them to a coffee",
                    completed = false
                )
            )
        )
        createTestTasks()
        val updateMessage = "Ooops, it is not Clara, it is Larissa. Please, rename the task to 'Mail to Larissa'"
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, updateMessage, Channel.SIGNAL
        )
        val updateTask = Subtask(SubtaskId("42"), TaskIntents.Update, updateMessage, mapOf("rawText" to updateMessage))
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = updateTaskSubtaskHandler.handle(
            updateTask, GoalContext(
                Goal(TaskIntents.Update), userMessage,
                subtasks = listOf(updateTask),
            ), friendshipId
        )
        // Assert
        val updatedTask = taskRepository.findByFriendshipId(friendshipId).first { it.id == existingTask.id!! }
        assertNotNull(updatedTask)
        assertThat(updatedTask.title).isEqualTo("Mail to Larissa")
        assertThat(updatedTask.description).withFailMessage { "Description should not have changed" }
            .isEqualTo(existingTask.description)
        assertThat(updatedTask.completed).withFailMessage { "Completed should not have changed" }
            .isEqualTo(existingTask.completed)
        assertNull(subtaskClarificationQuestion)
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed)
    }

    @ParameterizedTest
    @MethodSource("missing information needing clarification")
    fun `verify clarification question is asked`(
        taskTemplate: TaskTemplate,
        message: String,
        clarificationQuestionParts: Set<String>,
        clarificationParameters: TaskExpectations,
        taskExpectations: TaskExpectations
    ) = runBlocking {
        val oldTask = taskRepository.save(
            Task(
                owner = friendshipId,
                title = taskTemplate.title ?: "Do something task",
                description = taskTemplate.description,
                completed = taskTemplate.isCompleted == true,
                completedAt = if (taskTemplate.isCompleted == true) now() else null
            )
        )
        createTestTasks()
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.minusSeconds(10).epochSecond),
            receivedAt.minusSeconds(10),
            message,
            Channel.SIGNAL
        )
        val updateTask = Subtask(SubtaskId("42"), TaskIntents.Update, message, mapOf("rawText" to message))
        // Act
        val (successMessageGenerationPrompt, subtaskClarificationQuestion, updatedSubtask) = updateTaskSubtaskHandler.handle(
            updateTask, GoalContext(
                Goal(TaskIntents.Update), userMessage,
                subtasks = listOf(
                    updateTask
                ),
            ), friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).filteredOn { it.id == oldTask.id }.first()
            .matches({ task ->
                taskExpectations.titleParts?.all { task.title.contains(it, ignoreCase = true) } ?: true
            }, "expecting title to contain ${taskExpectations.titleParts}").matches({ task ->
                taskExpectations.descriptionParts?.all {
                    task.description!!.contains(
                        it, ignoreCase = true
                    )
                } ?: true
            }, "expecting description to contain ${taskExpectations.descriptionParts}").matches(
                { task -> taskExpectations.isCompleted?.let { it == task.completed } ?: true },
                "expecting completed to be ${taskExpectations.isCompleted}"
            )
        assertNotNull(subtaskClarificationQuestion)
        clarificationQuestionParts.forEach {
            assertThat(subtaskClarificationQuestion.text).containsIgnoringCase(it)
        }
        assertThat(updatedSubtask.status).isEqualTo(SubtaskStatus.InClarification)
        clarificationParameters.titleParts?.forEach {
            assertThat(
                updatedSubtask.parameters["entityData"] as TaskChanges
            ).extracting { changes -> changes.title }.asString().containsIgnoringCase(it)
        }
        clarificationParameters.descriptionParts?.forEach {
            assertThat(
                updatedSubtask.parameters["entityData"] as TaskChanges
            ).extracting { changes -> changes.description }.asString().containsIgnoringCase(it)
        }
        clarificationParameters.isCompleted?.apply {
            assertThat(
                updatedSubtask.parameters["entityData"] as TaskChanges
            ).extracting { changes -> changes.completed }.isEqualTo(this)
        }
        clarificationParameters.idIsKnown?.apply {
            assertTrue { (updatedSubtask.parameters["id"] as String) == oldTask.id }
        }
        assertTrue { updatedSubtask.status == SubtaskStatus.InClarification }
    }


    companion object {
        @JvmStatic
        fun `update task examples`(): List<Arguments> = listOf(
            Arguments.of(
                TaskTemplate(
                    "Organize books", "", false
                ),
                "Update the task to organize books. Set description to Sort by color like a rainbow",
                TaskExpectations(
                    titleParts = setOf("books", "organize"),
                    descriptionParts = setOf("sort", "rainbow"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate(
                    "Homework", "Math, English, Geography", false
                ), "Mark my homework task as done", TaskExpectations(
                    titleParts = setOf("homework"),
                    descriptionParts = setOf("math", "english", "geography"),
                    isCompleted = true
                )
            ), Arguments.of(
                TaskTemplate(
                    "Water plants", "", false
                ), "Change task: water my plants. New title: Water conifers", TaskExpectations(
                    titleParts = setOf("water", "conifer"), descriptionParts = emptySet(), isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate(
                    "Call mom", "", false
                ),
                "Finish todo to call mom",
                TaskExpectations(titleParts = setOf("call", "mom"), descriptionParts = emptySet(), isCompleted = true)
            ), Arguments.of(
                TaskTemplate("Clean the kitchen", "", false),
                "Actually, change the task to clean the bathroom instead of the kitchen",
                TaskExpectations(titleParts = setOf("bathroom", "clean"))
            )
        )

        @JvmStatic
        fun `adding things to existing task information`(): List<Arguments> = listOf(
            Arguments.of(
                TaskTemplate("Buy groceries", "Buy batteries", false),
                "Update my grocery task. Add vegetables and fruits to the description",
                TaskExpectations(
                    titleParts = setOf("Buy groceries"),
                    descriptionParts = setOf("vegetables", "fruits", "batteries"),
                    isCompleted = false
                )
            )
        )

        @JvmStatic
        fun `clarify updating task examples`(): List<Arguments> = listOf(
            Arguments.of(
                TaskTemplate(
                    "Organize books", "", false
                ), "Update a task", "Add description \"Sort by color\" to the organizing books task", TaskExpectations(
                    titleParts = setOf("organize books"), descriptionParts = setOf("sort", "color"), isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate(
                    "Organize books", "Sort by color like a rainbow", false
                ),
                "Modify a task's title",
                "Instead of \"organizing books\", I want to \"Organize DVDs\"! Adapt the title.",
                TaskExpectations(
                    titleParts = setOf("organize", "dvd"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate(
                    "Organize books", "Sort by color like a rainbow", false
                ), "Update a todo", "Mark organizing my books task as done", TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = true
                )
            ), Arguments.of(
                TaskTemplate(
                    "Organize books", "Sort by color like a rainbow", false
                ),
                "Modify the todo called Organize books",
                "Title \"Answer Janes letter\", description: \"Need to search for more information on trucks\"",
                TaskExpectations(
                    titleParts = setOf("jane", "letter"),
                    descriptionParts = setOf("search", "truck"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate(
                    "Organize books", "Sort by color like a rainbow", false
                ),
                "Modify the task concerning book organization",
                "It's already done!",
                TaskExpectations(),
                TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = true
                )
            )
        )

        @JvmStatic
        fun `missing information needing clarification`(): List<Arguments> = listOf(
            Arguments.of(
                TaskTemplate("Organize books", "", false),
                "Update a task",
                setOf("which"),
                TaskExpectations(),
                TaskExpectations(
                    titleParts = setOf("organize books"), descriptionParts = setOf(""), isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate("Organize books", "Sort by color like a rainbow", false),
                "Modify a task",
                setOf("which", "what"),
                TaskExpectations(),
                TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate("Organize books", "Sort by color like a rainbow", false),
                "Mark a todo as done",
                setOf("which"),
                TaskExpectations(isCompleted = true),
                TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate("Organize books", "Sort by color like a rainbow", false),
                "Modify a todo set description \"Remember the cartridge\"",
                setOf("which"),
                TaskExpectations(descriptionParts = setOf("Remember the cartridge")),
                TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate("Organize books", "Sort by color like a rainbow", false),
                "Modify the task concerning books",
                setOf("what"),
                TaskExpectations(idIsKnown = true),
                TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = false
                )
            ), Arguments.of(
                TaskTemplate("Organize books", "Sort by color like a rainbow", false),
                "Update task, new title shall be \"Organize DVDs\".",
                setOf("which"),
                TaskExpectations(
                    titleParts = setOf("organize", "dvd"),
                ),
                TaskExpectations(
                    titleParts = setOf("organize books"),
                    descriptionParts = setOf("sort by color like a rainbow"),
                    isCompleted = false
                )
            )
        )
    }

    private lateinit var testTasks: List<Task>

    @BeforeEach
    fun setupTestTasks() {
        testTasks = listOf(
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

data class TaskExpectations(
    val idIsKnown: Boolean? = null,
    val titleParts: Set<String>? = null,
    val descriptionParts: Set<String>? = null,
    val isCompleted: Boolean? = null,
)

data class TaskTemplate(
    val title: String?,
    val description: String?,
    val isCompleted: Boolean?,
)