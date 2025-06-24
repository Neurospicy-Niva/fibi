package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import org.springframework.stereotype.Component

@Component
class AddTaskSubtaskContributor(
    private val taskClassifier: TaskClassifier
) : SubtaskContributor {
    override fun forIntent() = TaskIntents.Add

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        val extracted = taskClassifier.extractAddTasks(friendshipId, message)
        return extracted.map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = TaskIntents.Add,
                description = "Add task: ${it.relevantText.take(40)}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class ListTasksSubtaskContributor(
    private val taskClassifier: TaskClassifier
) : SubtaskContributor {
    override fun forIntent() = TaskIntents.List

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        val extracted = taskClassifier.extractListTasks(friendshipId, message)
        val relevant = extracted.firstOrNull()?.relevantText
        return if (relevant != null) listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                TaskIntents.List,
                description = "List tasks related to: ${relevant.take(40)}",
                parameters = mapOf("rawText" to relevant)
            )
        )
        else listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                TaskIntents.List,
                description = "List all tasks"
            )
        )
    }
}

@Component
class CompleteTaskSubtaskContributor(
    private val taskClassifier: TaskClassifier
) : SubtaskContributor {
    override fun forIntent() = TaskIntents.Complete

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        val extracted = taskClassifier.extractCompleteTasks(friendshipId, message)
        return extracted.map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = TaskIntents.Complete,
                description = "Complete task: ${it.relevantText.take(40)}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class UpdateTaskSubtaskContributor(
    private val taskClassifier: TaskClassifier
) : SubtaskContributor {
    override fun forIntent() = TaskIntents.Update

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        val extracted = taskClassifier.extractUpdateTasks(friendshipId, message)
        return extracted.map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = TaskIntents.Update,
                description = "Update task: ${it.relevantText.take(40)}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class RemoveTaskSubtaskContributor(
    private val taskClassifier: TaskClassifier
) : SubtaskContributor {
    override fun forIntent() = TaskIntents.Remove

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        val extracted = taskClassifier.extractRemoveTasks(friendshipId, message)
        return extracted.map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = TaskIntents.Remove,
                description = "Remove task: ${it.relevantText.take(40)}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class CleanupTasksSubtaskContributor : SubtaskContributor {
    override fun forIntent() = TaskIntents.Cleanup

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        return listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                TaskIntents.Cleanup,
                parameters = mapOf("rawText" to message.text)
            )
        )
    }
}
