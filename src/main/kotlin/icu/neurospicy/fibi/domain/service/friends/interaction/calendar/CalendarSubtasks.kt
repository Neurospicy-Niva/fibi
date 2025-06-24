package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskContributor
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import org.springframework.stereotype.Component
import java.time.ZoneId

@Component
class RegisterCalendarSubtaskContributor : SubtaskContributor {
    override fun forIntent(): Intent = CalendarIntents.Register

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        return listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "Register calendar from user message",
                parameters = mapOf("rawText" to message.text)
            )
        )
    }
}


@Component
class CalendarQuerySubtaskContributor(
    private val classifier: CalendarQueryClassifier
) : SubtaskContributor {
    override fun forIntent(): Intent = CalendarIntents.Show

    override suspend fun provideSubtasks(
        intent: Intent, friendshipId: FriendshipId, message: UserMessage
    ): List<Subtask> {
        val classification = classifier.classify(
            message = message.text,
            timezone = ZoneId.of("Europe/Berlin"),
            receivedAt = message.receivedAt
        )

        if (classification.category == CalendarQueryCategory.NoSearch) {
            return emptyList()
        }

        return listOf(
            Subtask(
                SubtaskId.from(friendshipId, intent, message.messageId),
                intent = intent,
                description = "Query appointments: ${message.text}",
                parameters = mapOf(
                    "rawText" to message.text,
                    "queryCategory" to classification.category.name,
                    "keywords" to classification.keywords.joinToString(","),
                    "timeHint" to (classification.timeHint ?: "")
                )
            )
        )
    }
}