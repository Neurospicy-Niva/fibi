package icu.neurospicy.fibi.domain.service.friends.interaction.timezones

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import icu.neurospicy.fibi.domain.service.friends.interaction.timers.TimerIntents
import org.springframework.stereotype.Component

@Component
class SetTimezoneSubtaskContributor : SubtaskContributor {
    override fun forIntent() = TimerIntents.Set

    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = TimezoneIntents.GeneralTimezoneIntent,
                description = "Set timezone",
                parameters = mapOf("rawText" to message)
            )
        )
    }
}
