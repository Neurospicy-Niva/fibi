package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import org.springframework.stereotype.Component

@Component
class SetTimeBasedReminderSubtaskContributor(
    private val classifier: TimeBasedReminderClassifier
) : SubtaskContributor {
    override fun forIntent() = ReminderIntents.Set
    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return classifier.extractAddReminders(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent,
                description = it.description,
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class UpdateTimeBasedReminderSubtaskContributor(
    private val classifier: TimeBasedReminderClassifier
) : SubtaskContributor {
    override fun forIntent() = ReminderIntents.Update
    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return classifier.extractUpdateReminders(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent,
                description = it.description,
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class RemoveTimeBasedReminderSubtaskContributor(
    private val classifier: TimeBasedReminderClassifier
) : SubtaskContributor {
    override fun forIntent() = ReminderIntents.Remove
    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return classifier.extractRemoveReminders(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent,
                description = it.description,
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class ListTimeBasedReminderSubtaskContributor(
    private val classifier: TimeBasedReminderClassifier
) : SubtaskContributor {
    override fun forIntent() = ReminderIntents.List
    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return classifier.extractListReminders(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent,
                description = it.description,
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}