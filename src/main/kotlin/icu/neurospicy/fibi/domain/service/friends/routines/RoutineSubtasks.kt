package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import org.springframework.stereotype.Component

@Component
class SelectRoutineSubtaskContributor : SubtaskContributor {
    override fun forIntent(): Intent = RoutineIntents.Start

    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage,
    ): List<Subtask> {
        return listOf(
            Subtask(
                SubtaskId.from(friendshipId, RoutineIntents.Select, message.messageId),
                intent = RoutineIntents.Select,
                description = "Select routine to start",
                parameters = mapOf("rawText" to message.text)
            ),
            Subtask(
                SubtaskId.from(friendshipId, RoutineIntents.Setup, message.messageId),
                intent = RoutineIntents.Setup,
                description = "Setup routine defining parameters",
                parameters = mapOf("rawText" to message.text)
            )

        )
    }
}
