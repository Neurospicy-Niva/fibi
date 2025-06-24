package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListRemindersSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var listRemindersSubtaskHandler: ListRemindersSubtaskHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
        friendshipLedger.updateZoneId(friendshipId, ZoneId.of("Europe/Berlin"))
        insertTestReminders()
    }

    @Test
    fun `can handle subtasks of ListTimeBasedReminders intent`() {
        assertTrue {
            listRemindersSubtaskHandler.canHandle(
                Subtask(
                    SubtaskId("42"),
                    ReminderIntents.List
                )
            )
        }
    }

    @ParameterizedTest
    @MethodSource("query to expected reminders")
    fun `returns expected reminders for query`(
        messageText: String,
        expectedTextFragments: List<String>
    ) = runBlocking {
        val receivedAt = Instant.now()
        val userMessage = UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, messageText, Channel.SIGNAL)
        val subtask = Subtask(
            SubtaskId("reminder-list"),
            ReminderIntents.List,
            messageText,
            mapOf("rawText" to messageText)
        )
        val context = GoalContext(Goal(ReminderIntents.List), originalMessage = userMessage, subtasks = listOf(subtask))

        val (response, clarification, updatedSubtask) = listRemindersSubtaskHandler.handle(
            subtask,
            context,
            friendshipId
        )

        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertNull(clarification)
        expectedTextFragments.forEach {
            assertThat(response).containsIgnoringCase(it)
        }
    }

    private fun insertTestReminders() {
        val now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
        val reminders = listOf(
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(1).withHour(7).withMinute(0).toLocalDateTime(), now.zone),
                "Feed the dogs"
            ),
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(1).withHour(13).withMinute(0).toLocalDateTime(), now.zone),
                "Go for a walk with the dogs"
            ),
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(2).withHour(9).withMinute(30).toLocalDateTime(), now.zone),
                "Doctor appointment"
            ),
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(3).withHour(18).toLocalDateTime(), now.zone),
                "Call grandma"
            ),
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(4).withHour(20).toLocalDateTime(), now.zone),
                "Water the plants"
            ),
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(5).withHour(10).toLocalDateTime(), now.zone),
                "Buy birthday gift"
            ),
            Reminder(
                null,
                friendshipId,
                DateTimeBasedTrigger(now.plusDays(6).withHour(8).toLocalDateTime(), now.zone),
                "Yoga class"
            ),
        )
        reminders.forEach {
            reminderRepository.setReminder(it)
        }
    }

    companion object {
        @JvmStatic
        fun `query to expected reminders`(): Stream<Arguments> = Stream.of(
            Arguments.of("When is the next time I go for a walk with my dogs?", listOf("walk", "dogs")),
            Arguments.of(
                "List all upcoming reminders",
                listOf("feed", "walk", "doctor", "call", "water", "gift", "yoga")
            ),
            Arguments.of("Reminders for tomorrow", listOf("feed", "walk")),
            Arguments.of("Do I have anything about plants?", listOf("water")),
            Arguments.of("Remind me about grandma", listOf("call"))
        )
    }
}