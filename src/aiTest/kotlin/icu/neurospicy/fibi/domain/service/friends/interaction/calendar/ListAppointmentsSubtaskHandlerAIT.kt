package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.CalendarConfigurationRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt
import java.time.*
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ListAppointmentsSubtaskHandlerAIT : BaseAIT() {

    @Autowired
    lateinit var listAppointmentsSubtaskHandler: ListAppointmentsSubtaskHandler

    @Autowired
    lateinit var calendarConfigurationRepository: CalendarConfigurationRepository
    var calendarConfigId: CalendarConfigId = CalendarConfigId()

    @BeforeEach
    override fun setUp() {
        super.setUp()
        calendarConfigurationRepository.save(
            friendshipId, CalendarConfigurations(
                setOf(
                    CalendarConfiguration(
                        friendshipId, calendarConfigId, "http://example.com"
                    )
                )
            )
        )
        val dummyAppointments = dummyAppointments(friendshipId)
        calendarRepository.replaceCalendarAppointments(
            dummyAppointments, friendshipId, calendarConfigId, CalendarId("private")
        )
    }

    @Test
    fun `shows todays appointments`() = runTestWithMessage(
        "Show me all appointments of today",
        expected = calendarRepository.loadAppointmentsForTimeRange(
            TimeRange(
                ZonedDateTime.now(ZoneId.of("Europe/Berlin")).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                Duration.ofDays(1)
            ), friendshipId
        ).map { it.summary }.toSet(),
        category = CalendarQueryCategory.SpecificTimeRange,
    )

    @Test
    fun `shows next 5 days appointments`() {
        val now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
        runTestWithMessage(
            "What appointments do I have in the next 5 days?",
            expected = calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(
                    now.toInstant(),
                    Duration.ofDays(5).minusHours(now.hour.toLong()).minusMinutes(now.minute.toLong())
                        .minusSeconds(now.second.toLong())
                ), friendshipId
            ).map { it.summary }.toSet(),
            category = CalendarQueryCategory.RelativeTimeRange,
        )
    }

    @Test
    fun `shows upcoming medical appointments`() = runTestWithMessage(
        "Show me upcoming appointments with a doctor",
        expected = setOf("Orthopaedist appointment"),
        category = CalendarQueryCategory.KeywordSearch,
    )

    @Test
    fun `shows meetings with Jane this month`() = runTestWithMessage(
        "Show me all appointments with Jane this month",
        expected = setOf("Yoga with Jane", "Call with Jane"),
        category = CalendarQueryCategory.KeywordSearch,
    )

    @Test
    fun `shows last dental visit`() = runTestWithMessage(
        "When was my last visit to the dentist?",
        expected = setOf("Dentist follow-up"),
        category = CalendarQueryCategory.KeywordSearch,
    )

    @Test
    fun `shows pick up from school`() = runTestWithMessage(
        "Do I have to pick up from school in the next 3 days?",
        expected = setOf("pick up maria from school"),
        category = CalendarQueryCategory.KeywordInRelativeTimeRange,
    )

    @Test
    fun `shows todays doctor appointment`() = runTestWithMessage(
        "When do I have to got to the doctor today?",
        expected = setOf("visit to the doctor"),
        category = CalendarQueryCategory.KeywordInSpecificTimeRange,
    )

    private fun runTestWithMessage(
        messageText: String,
        expected: Set<String>,
        category: CalendarQueryCategory,
    ) = runBlocking {
        val userMessage = UserMessage(
            SignalMessageId(Instant.now().epochSecond), Instant.now(), messageText, Channel.SIGNAL
        )

        val subtask = Subtask(
            SubtaskId("appt-${UUID.randomUUID()}"), CalendarIntents.Show, messageText, parameters = mapOf(
                "rawText" to messageText,
                "queryCategory" to category,
            )
        )
        val context = GoalContext(
            Goal(CalendarIntents.Show), originalMessage = userMessage, subtasks = listOf(subtask)
        )
        val (text, clarification, updatedSubtask) = listAppointmentsSubtaskHandler.handle(
            subtask, context, friendshipId
        )

        assertNotNull(text)
        expected.forEach { assertThat(text).containsIgnoringCase(it) }
        assertEquals(SubtaskStatus.Completed, updatedSubtask.status)
        assertThat(clarification).isNull()
    }

    @AfterEach
    fun cleanup() {
        calendarConfigurationRepository.remove(friendshipId, calendarConfigId)
    }

    companion object {
        fun dummyAppointments(friendshipId: FriendshipId): List<Appointment> {
            val now = ZonedDateTime.now()
            val tz = ZoneId.of("Europe/Berlin")
            fun appt(summary: String, daysFromNow: Long): Appointment {
                val start =
                    now.plusDays(daysFromNow).withHour(nextInt(7, 21)).withMinute(nextInt(0, 4) * 15).withSecond(0)
                        .withNano(0)
                val end = start.plusHours(nextInt(1, 4).toLong()).withMinute(nextInt(0, 4) * 15)
                return Appointment(
                    UUID.randomUUID().toString(),
                    friendshipId,
                    AppointmentId(UUID.randomUUID().toString()),
                    CalendarConfigId(),
                    CalendarId("apfel"),
                    UUID.randomUUID().toString(),
                    summary,
                    DateTimeInformation(start.toInstant(), tz, start.toLocalDateTime()),
                    DateTimeInformation(
                        end.toInstant(), tz, end.toLocalDateTime()
                    ),
                    ""
                )
            }

            val lastMondayOffsetDays = Duration.between(
                now, now.with(java.time.temporal.TemporalAdjusters.previous(DayOfWeek.MONDAY))
            ).toDays()
            return listOf(
                appt("Visit to the doctor", 0),
                appt("Bring Maria to bed", 0),
                appt("Project meeting with Jeff", 2),
                appt("Yoga with Jane", 5),
                appt("Dentist follow-up", -3),
                appt("Orthopaedist appointment", 7),
                appt("Call with Jane", 15),
                appt("Gym", 1),
            ) + lastMondayOffsetDays.rangeUntil(lastMondayOffsetDays.plus(5))
                .map { appt("Pick up Maria from school", it) } + lastMondayOffsetDays.plus(7)
                .rangeUntil(lastMondayOffsetDays.plus(7 + 5)).map { appt("Pick up Maria from school", it) }
        }
    }
}