package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import java.time.*

class ShortCutCalendarIdsTools(
    private val calendarRepository: CalendarRepository,
    private val friendshipId: FriendshipId,
    private val timezoneOfUser: ZoneId,
    private val now: ZonedDateTime = ZonedDateTime.now(timezoneOfUser),
) {
    @Tool(description = "Get ids of all today's appointments", returnDirect = true)
    fun `Get ids of all today's appointments`(): String = calendarRepository.loadAppointmentsForTimeRange(
        TimeRange(
            now.toLocalDate().atStartOfDay(timezoneOfUser).toInstant(), Duration.ofDays(1)
        ), friendshipId
    ).mapNotNull { it._id }.joinToString()

    @Tool(description = "Get ids of today's upcoming appointments", returnDirect = true)
    fun `Get ids of today's upcoming appointments`(): String = calendarRepository.loadAppointmentsForTimeRange(
        TimeRange(
            now.toInstant(), Duration.between(
                now,
                now.toLocalDate().plusDays(1).atStartOfDay(timezoneOfUser)
            )
        ), friendshipId
    ).mapNotNull { it._id }.joinToString()

    @Tool(description = "Get ids of all tomorrow's appointments", returnDirect = true)
    fun `Get ids of all tomorrow's appointments`(): String = calendarRepository.loadAppointmentsForTimeRange(
        TimeRange(
            now.toLocalDate().plusDays(1).atStartOfDay(timezoneOfUser).toInstant(), Duration.ofDays(1)
        ), friendshipId
    ).mapNotNull { it._id }.joinToString()

    @Tool(description = "Get ids of appointments in time range", returnDirect = true)
    fun `Get ids of appointments in time range`(
        @ToolParam(description = "Start of time range, ISO 8601 format", required = true) startAtString: String,
        @ToolParam(description = "End of time range, ISO 8601 format", required = true) endAtString: String,
    ): String {
        val startAt: Instant = parseIsoDateTime(startAtString)
        val endAt: Instant = parseIsoDateTime(endAtString)
        return calendarRepository.loadAppointmentsForTimeRange(
            TimeRange(startAt, Duration.between(startAt, endAt)), friendshipId
        ).mapNotNull { it._id }.joinToString()
    }

    private fun parseIsoDateTime(startAtString: String): Instant =
        if (startAtString.contains("Z")) ZonedDateTime.parse(startAtString).toInstant()
        else if (startAtString.contains("+")) OffsetDateTime.parse(startAtString).toInstant()
        else if (startAtString.contains("T")) LocalDateTime.parse(startAtString).atZone(timezoneOfUser).toInstant()
        else LocalDate.parse(startAtString).atStartOfDay(timezoneOfUser).toInstant()
}