package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment.AppointmentReminderIntents
import org.springframework.stereotype.Component

@Component
class ListRemindersSubtaskContributor(
) : SubtaskContributor {
    override fun forIntent(): Intent = GeneralReminderIntents.List
    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> = listOf(
        Subtask(
            SubtaskId.from(friendshipId, ReminderIntents.List, message.messageId),
            ReminderIntents.List,
            description = ReminderIntents.List.name,
            parameters = mapOf("rawText" to message.text)
        ), Subtask(
            SubtaskId.from(friendshipId, AppointmentReminderIntents.List, message.messageId),
            AppointmentReminderIntents.List,
            description = AppointmentReminderIntents.List.name,
            parameters = mapOf("rawText" to message.text)
        )
    )
}
