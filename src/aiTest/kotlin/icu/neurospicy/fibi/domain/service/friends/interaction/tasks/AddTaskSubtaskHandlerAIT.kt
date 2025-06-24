package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.SubtaskStatus
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant.now
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddTaskSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var addTaskSubtaskHandler: AddTaskSubtaskHandler

    @Test
    fun `can handle subtasks of AddTask intent`() {
        assertTrue { addTaskSubtaskHandler.canHandle(Subtask(SubtaskId("42"), TaskIntents.Add)) }
    }

    @ParameterizedTest
    @MethodSource("add task examples")
    fun testAddTask(
        message: String, titleParts: Set<String>, descriptionParts: Set<String>, isCompleted: Boolean
    ) = runBlocking {
        val receivedAt = now()
        val userMessage = UserMessage(
            SignalMessageId(receivedAt.epochSecond), receivedAt, message, Channel.SIGNAL
        )
        val addTask = Subtask(SubtaskId("42"), TaskIntents.Add, message, mapOf("rawText" to message))
        val (_, subtaskClarificationQuestion, updatedSubtask) = addTaskSubtaskHandler.handle(
            addTask, GoalContext(
                Goal(TaskIntents.Add), originalMessage = userMessage,
                subtasks = listOf(
                    addTask
                ),
            ), friendshipId
        )
        // Assert
        assertThat(taskRepository.findByFriendshipId(friendshipId)).anySatisfy { task ->
            assertTrue { titleParts.all { task.title.contains(it, ignoreCase = true) } }
            assertTrue { descriptionParts.all { task.description!!.contains(it, ignoreCase = true) } }
            assertEquals(task.completed, isCompleted)
        }
        assertNull(subtaskClarificationQuestion)
        assertTrue(updatedSubtask.status == SubtaskStatus.Completed)
    }

    @ParameterizedTest
    @MethodSource("clarify adding task examples")
    fun `takes parameters from answer to clarification`(
        message: String,
        clarificationAnswer: String,
        titleParts: Set<String>,
        descriptionParts: Set<String>,
        isCompleted: Boolean
    ) = runBlocking<Unit> {
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
        val addTask = Subtask(SubtaskId("42"), TaskIntents.Add, message, mapOf("rawText" to message))
        val clarificationQuestion = SubtaskClarificationQuestion("", addTask.id)
        // Act
        val (subtaskClarificationQuestion, _, _, _) = addTaskSubtaskHandler.tryResolveClarification(
            addTask, clarificationQuestion, clarificationMessage, GoalContext(
                Goal(TaskIntents.Add), originalMessage = userMessage,
                subtasks = listOf(addTask),
                subtaskClarificationQuestions = listOf(clarificationQuestion)
            ), friendshipId
        )
        // Assert
        assertNull(subtaskClarificationQuestion)
        assertThat(taskRepository.findByFriendshipId(friendshipId)).anySatisfy { task ->
            assertTrue { titleParts.all { task.title.contains(it, ignoreCase = true) } }
            assertTrue { descriptionParts.all { task.description!!.contains(it, ignoreCase = true) } }
            assertEquals(task.completed, isCompleted)
        }
    }


    companion object {
        @JvmStatic
        fun `add task examples`(): List<Arguments> = listOf(
            Arguments.of(
                "Add a task to organize my books", setOf("books", "organize"), emptySet<String>(), false
            ), Arguments.of(
                "I already did my homework. Add the task and mark it done.", setOf("homework"), emptySet<String>(), true
            ), Arguments.of(
                "Add a task: I need to water my plants.", setOf("water", "plant"), emptySet<String>(), false
            ), Arguments.of(
                "Add todo to call mom", setOf("call", "mom"), emptySet<String>(), false
            ),
            Arguments.of(
                "Add a task to organize my books", setOf("books", "organize"), emptySet<String>(), false
            ),
            Arguments.of(
                "I just had an idea. Maybe I should read the book about monsters. Add a task for that.",
                setOf("book", "monster"), emptySet<String>(), false
            ),
            Arguments.of(
                "The attic is so dirty. I need a task to clean it in the next days.",
                setOf("attic", "clean"),
                emptySet<String>(),
                false
            ),
            Arguments.of(
                "Oh my gosh! I found my glasses which need to be repaired. That's another to do!",
                setOf("glass", "repair"), emptySet<String>(), false
            ),
            Arguments.of(
                "I just had a meeting with my cooperation partner. I need to schedule a follow-up meeting. Add a task to determine a date and time",
                setOf("follow-up", "meeting"), emptySet<String>(), false
            )
        )

        @JvmStatic
        fun `clarify adding task examples`(): List<Arguments> = listOf(
            Arguments.of(
                "Add a task",
                "Eat an apple - description: take the green one)",
                setOf("eat", "apple"),
                setOf("green"),
                false
            ), Arguments.of(
                "Create task", "Answer Mail of Chris", setOf("mail", "chris"), emptySet<String>(), false
            ), Arguments.of(
                "Add todo",
                "Title is Clean the kitchen, description is dont forget the sink",
                setOf("clean", "kitchen"),
                setOf("sink"),
                false
            ), Arguments.of(
                "Create todo",
                "Title \"Answer Janes letter\", details: \"Need to search for more information on trucks\"",
                setOf("jane", "letter"),
                setOf("search", "truck"),
                false
            ), Arguments.of(
                "Create task",
                "Answer Mail of Chris - it's already done",
                setOf("mail", "chris"),
                emptySet<String>(),
                true
            )
        )
    }

}