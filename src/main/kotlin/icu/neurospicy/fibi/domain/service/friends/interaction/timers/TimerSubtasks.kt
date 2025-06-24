package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import org.springframework.stereotype.Component

@Component
class SetTimerSubtaskContributor(
    private val timerClassifier: TimerClassifier
) : SubtaskContributor {
    override fun forIntent() = TimerIntents.Set

    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return timerClassifier.extractSetTimers(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "Set timer: ${it.description}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class UpdateTimerSubtaskContributor(
    private val timerClassifier: TimerClassifier
) : SubtaskContributor {
    override fun forIntent() = TimerIntents.Update

    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return timerClassifier.extractUpdateTimers(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "Update timer: ${it.description}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class RemoveTimerSubtaskContributor(
    private val timerClassifier: TimerClassifier
) : SubtaskContributor {
    override fun forIntent() = TimerIntents.Remove

    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        return timerClassifier.extractRemoveTimers(friendshipId, message).map {
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "Remove timer: ${it.description}",
                parameters = mapOf("rawText" to it.relevantText)
            )
        }
    }
}

@Component
class ListTimersSubtaskContributor(
    private val timerClassifier: TimerClassifier
) : SubtaskContributor {
    override fun forIntent() = TimerIntents.List

    override suspend fun provideSubtasks(
        intent: Intent,
        friendshipId: FriendshipId,
        message: UserMessage
    ): List<Subtask> {
        val extracted = timerClassifier.extractListTimers(friendshipId, message)
        val relevant = extracted.firstOrNull()?.relevantText
        return if (relevant != null) listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "List timers related to: ${relevant.take(40)}",
                parameters = mapOf("rawText" to relevant)
            )
        )
        else listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "List all timers"
            )
        )
    }
}