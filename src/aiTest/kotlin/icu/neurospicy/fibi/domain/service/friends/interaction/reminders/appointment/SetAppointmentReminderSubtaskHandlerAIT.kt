package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

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
import java.time.Duration
import java.time.Instant.now
import java.time.ZoneId
import java.util.*
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetAppointmentReminderSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var setAppointmentReminderSubtaskHandler: SetAppointmentReminderSubtaskHandler

    @BeforeEach
    override fun setUp() {
        super.setUp()
        calendarRepository.replaceCalendarAppointments(
            dummyAppointments(friendshipId),
            friendshipId,
            CalendarConfigId(),
            CalendarId("apfel")
        )
    }

    @Test
    fun `can handle subtasks of SetAppointmentReminder intent`() {
        assertTrue {
            setAppointmentReminderSubtaskHandler.canHandle(
                Subtask(SubtaskId("appt-1"), AppointmentReminderIntents.Set)
            )
        }
    }

    @ParameterizedTest
    @MethodSource("valid appointment reminder messages")
    fun `sets appointment reminder from message`(
        messageText: String,
        expectedTextParts: Set<String>,
        expectedKeywords: Set<String>,
        expectedBefore: Boolean,
        expectedOffset: Duration,
    ) = runBlocking {
        val receivedAt = now()
        val userMessage = UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, messageText, Channel.SIGNAL)
        val subtask = Subtask(
            SubtaskId("appt-2"),
            AppointmentReminderIntents.Set,
            messageText,
            mapOf("rawText" to messageText)
        )
        val context =
            GoalContext(Goal(AppointmentReminderIntents.Set), originalMessage = userMessage, subtasks = listOf(subtask))

        val (_, clarification, updatedSubtask) = setAppointmentReminderSubtaskHandler.handle(
            subtask,
            context,
            friendshipId
        )

        assertNull(clarification)
        val reminder = reminderRepository.findAppointmentRemindersBy(friendshipId).first()
        expectedTextParts.forEach {
            assertThat(reminder.text).containsIgnoringCase(it)
        }
        assertThat(reminder.matchingTitleKeywords.map { it.lowercase() }).containsAnyElementsOf(expectedKeywords.map { it.lowercase() })
        assertEquals(expectedBefore, reminder.remindBeforeAppointment)
        assertEquals(expectedOffset, reminder.offset)
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
    }

    @ParameterizedTest
    @MethodSource("incomplete appointment reminder messages")
    fun `asks clarification for incomplete reminder info`(
        messageText: String,
        expectedQuestion: String
    ) = runBlocking {
        val receivedAt = now()
        val userMessage = UserMessage(SignalMessageId(receivedAt.epochSecond), receivedAt, messageText, Channel.SIGNAL)
        val subtask = Subtask(
            SubtaskId("appt-clarify"),
            AppointmentReminderIntents.Set,
            messageText,
            mapOf("rawText" to messageText)
        )
        val context = GoalContext(Goal(AppointmentReminderIntents.Set), userMessage, subtasks = listOf(subtask))

        val (_, clarification, updatedSubtask) = setAppointmentReminderSubtaskHandler.handle(
            subtask,
            context,
            friendshipId
        )

        assertThat(clarification?.text).containsIgnoringCase(expectedQuestion)
        assertEquals(SubtaskStatus.InClarification, updatedSubtask.status)
    }

    companion object {
        @JvmStatic
        fun `valid appointment reminder messages`(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "Remind me after doctor visits to pick up my prescription",
                setOf("prescription"),
                setOf("doctor"),
                false,
                Duration.ofMinutes(15)
            ),
            Arguments.of(
                "Remind me before meetings to take a water bottle",
                setOf("water", "bottle"),
                setOf("meeting"),
                true,
                Duration.ofMinutes(15)
            ),
            Arguments.of(
                "After school pick ups, remind me to ask about homework",
                setOf("homework"),
                setOf("school", "pick up"),
                false,
                Duration.ofMinutes(15)
            ),
            Arguments.of(
                "Before yoga, remind me to take my mat",
                setOf("mat"),
                setOf("yoga"),
                true,
                Duration.ofMinutes(15)
            ), Arguments.of(
                "Remind me 10 minutes before dentist appointments to take my insurance card",
                setOf("insurance", "card"),
                setOf("dentist"),
                true,
                Duration.ofMinutes(10)
            ), Arguments.of(
                "Add an appointment reminder for every appointment starting with \"Alert:\" with message \"Be cautious\" 3 hours in advance!",
                setOf("Be cautious"),
                setOf("Alert"),
                true,
                Duration.ofHours(3)
            )

        )

        @JvmStatic
        fun `incomplete appointment reminder messages`(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "Remind me after appointments", "what"
            ),
            Arguments.of(
                "Add reminder for appointments", "what"
            ),
            Arguments.of(
                "Set an appointment reminder", "what"
            )
        )

        @JvmStatic
        private fun dummyAppointments(friendshipId: FriendshipId) = listOf(
            "Per doctor",
            "Pick up Cora from school",
            "Bring Jane to bed",
            "Football with friends",
            "Jogging",
            "Meeting with Jeff",
            "Eye doctor (Elwira)",
            "Yoga class",
            "Travel to Buago",
            "Holidays with Mike",
            "Parental Leave",
            "Pick up Elwira from preschool",
            "IT-Meetup",
            "edudoa meeting",
        ).map {
            Appointment(
                UUID.randomUUID().toString(),
                friendshipId,
                AppointmentId(UUID.randomUUID().toString()),
                CalendarConfigId(),
                CalendarId("apfel"),
                UUID.randomUUID().toString(),
                it,
                DateTimeInformation(now(), ZoneId.of("Z"), now()),
                DateTimeInformation(now(), ZoneId.of("Z"), now()),
                ""
            )
        }
    }
}