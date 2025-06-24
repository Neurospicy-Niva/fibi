package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import org.springframework.stereotype.Component

@Component
class SetAppointmentReminderSubtaskContributor(
    private val classifier: AppointmentReminderClassifier
) : SubtaskContributor {
    override fun forIntent(): Intent = AppointmentReminderIntents.Set
    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return classifier.extractSetReminders(friendshipId, message).map {
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
class UpdateAppointmentReminderSubtaskContributor(
    private val classifier: AppointmentReminderClassifier
) : SubtaskContributor {
    override fun forIntent(): Intent = AppointmentReminderIntents.Update
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
class RemoveAppointmentReminderSubtaskContributor(
    private val classifier: AppointmentReminderClassifier
) : SubtaskContributor {
    override fun forIntent(): Intent = AppointmentReminderIntents.Remove
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
class ListAppointmentReminderSubtaskContributor(
    private val classifier: AppointmentReminderClassifier
) : SubtaskContributor {
    override fun forIntent(): Intent = AppointmentReminderIntents.List
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