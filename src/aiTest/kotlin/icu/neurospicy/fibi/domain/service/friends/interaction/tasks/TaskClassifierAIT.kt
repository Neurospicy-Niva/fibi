package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class TaskClassifierAIT : BaseAIT() {
    @Autowired
    lateinit var taskClassifier: TaskClassifier

    @ParameterizedTest
    @MethodSource("add task examples")
    fun `extracts expected text for add task`(message: String, expectedParts: Set<String>) = runBlocking<Unit> {
        //Arrange
        val userMessage = UserMessage(
            messageId = SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
        )
        // Act
        val extractAddTasks = taskClassifier.extractAddTasks(friendshipId, userMessage)
        // Assert
        expectedParts.forEach { expectedPart ->
            assertThat(extractAddTasks.first().relevantText).containsIgnoringCase(expectedPart)
        }
    }

    @ParameterizedTest
    @MethodSource("update task examples")
    fun `extracts expected text for update task`(message: String, expectedParts: Set<String>) = runBlocking<Unit> {
        //Arrange
        val userMessage = UserMessage(
            messageId = SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
        )
        // Act
        val extractAddTasks = taskClassifier.extractUpdateTasks(friendshipId, userMessage)
        // Assert
        expectedParts.forEach { expectedPart ->
            assertThat(extractAddTasks.first().relevantText).containsIgnoringCase(expectedPart)
        }
    }

    @ParameterizedTest
    @MethodSource("complete task examples")
    fun `extracts expected text for complete task`(message: String, expectedParts: Set<String>) = runBlocking<Unit> {
        //Arrange
        val userMessage = UserMessage(
            messageId = SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
        )
        // Act
        val extractAddTasks = taskClassifier.extractCompleteTasks(friendshipId, userMessage)
        // Assert
        expectedParts.forEach { expectedPart ->
            assertThat(extractAddTasks.first().relevantText).containsIgnoringCase(expectedPart)
        }
    }

    @ParameterizedTest
    @MethodSource("remove task examples")
    fun `extracts expected text for remove task`(message: String, expectedParts: Set<String>) = runBlocking<Unit> {
        //Arrange
        val userMessage = UserMessage(
            messageId = SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
        )
        // Act
        val extractAddTasks = taskClassifier.extractRemoveTasks(friendshipId, userMessage)
        // Assert
        expectedParts.forEach { expectedPart ->
            assertThat(extractAddTasks.first().relevantText).containsIgnoringCase(expectedPart)
        }
    }

    @ParameterizedTest
    @MethodSource("list task examples")
    fun `extracts expected text for list task`(message: String, expectedParts: Set<String>) = runBlocking<Unit> {
        //Arrange
        val userMessage = UserMessage(
            messageId = SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
        )
        // Act
        val extractAddTasks = taskClassifier.extractListTasks(friendshipId, userMessage)
        // Assert
        expectedParts.forEach { expectedPart ->
            assertThat(extractAddTasks.first().relevantText).containsIgnoringCase(expectedPart)
        }
    }


    companion object {
        @JvmStatic
        fun `add task examples`() = listOf(
            Arguments.of("Add task to buy milk", setOf("add task", "buy mil")),
            Arguments.of("Yes, I need to call the clinic. Add this task.", setOf("call", "clinic", "Add this task"))
        )

        @JvmStatic
        fun `update task examples`() = listOf(
            Arguments.of(
                "Update task \"Buy milk\" to \"Buy soy milk\"",
                setOf("update task", "buy milk", "buy soy milk")
            ),
            Arguments.of(
                "Oh my gosh. Update the task to go running with Maria. We are going to play Mario Card instead.",
                setOf("update the task to go running with maria", "play mario card")
            )
        )

        @JvmStatic
        fun `complete task examples`() = listOf(
            Arguments.of("Mark task \"Buy soy milk\" as complete", setOf("mark", "buy soy milk", "complete")),
            Arguments.of(
                "I finished my laundry. Mark this task as complete.",
                setOf("finished my laundry", "mark this task as complete")
            )
        )

        @JvmStatic
        fun `remove task examples`() = listOf(
            Arguments.of("Remove task \"Buy milk\"", setOf("remove task", "Buy milk")),
            Arguments.of(
                "I accidentally added Buy cola to my tasks. Please remove it from the list.",
                setOf("Buy cola", "remove it from the list")
            )
        )

        @JvmStatic
        fun `list task examples`() = listOf(
            Arguments.of("List all tasks", setOf("List all tasks")),
            Arguments.of("Please list all tasks concerning Elmos birthday.", setOf("list all tasks", "Elmos birthday"))
        )
    }
}