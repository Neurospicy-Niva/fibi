package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.Appointment
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.CalendarConfigurationRepository
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TimeRange
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset.UTC

class CalendarTools(
    private val calendarRepository: CalendarRepository,
    private val calendarConfigurationRepository: CalendarConfigurationRepository,
    private val friendshipLedger: FriendshipLedger,
    private val friendshipId: FriendshipId
) {
    @Tool(description = "Receive all appointments for a specific period - from start to end.")
    fun getAppointmentsInRange(
        @ToolParam(description = "DateTime in ISO 8601 format, e.g., 2014-08-14T08:21:11Z") start: String,
        @ToolParam(description = "DateTime in ISO 8601 format, e.g., 2014-08-15T08:21:32Z") end: String
    ): Result {
        LOG.debug("Fetching appointments of friend $friendshipId starting at $start up to $end")
        return toSortedAppointments(
            calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(Instant.parse(start), Duration.between(Instant.parse(start), Instant.parse(end))),
                friendshipId
            ), friendshipLedger.findBy(friendshipId)?.timeZone ?: UTC
        ).apply { LOG.info(this.message) }
    }

    @Tool(description = "Receive all appointments for a single day in the user's time zone.")
    fun getAppointmentsOfDay(
        @ToolParam(description = "Day in ISO 8601 format, e.g., 2014-08-14") day: String,
    ): Result {
        LOG.debug("Fetching appointments of friend $friendshipId on day $day")
        val friendsTimezone = friendshipLedger.findBy(friendshipId)?.timeZone ?: UTC
        val startOfDay = when {
            day == "today" -> LocalDate.now(friendsTimezone).atStartOfDay(friendsTimezone).toInstant()
            day == "tomorrow" -> LocalDate.now(friendsTimezone).plusDays(1).atStartOfDay(friendsTimezone).toInstant()
            day == "yesterday" -> LocalDate.now(friendsTimezone).minusDays(1).atStartOfDay(friendsTimezone).toInstant()
            else -> LocalDate.parse(day).atStartOfDay(friendsTimezone).toInstant()
        }
        return toSortedAppointments(
            calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(startOfDay, Duration.ofDays(1)),
                friendshipId
            ), friendsTimezone
        ).apply { LOG.info(this.message) }
    }

    private fun toSortedAppointments(
        appointments: List<Appointment>,
        friendsTimezone: ZoneId?
    ): Result = appointments
        .apply {
            if (this.isEmpty()) {
                return if (calendarConfigurationRepository.load(friendshipId).configurations.isEmpty()) {
                    Error("The user has not connected a calendar yet. In order to get appointments, the user needs to register a calendar first.")
                } else {
                    Success(emptyList<LlmAppointment>(), "Could not find any appointments.")
                }
            }
        }
        .asSequence()
        .sortedBy { it.startAt.instant }
        .map {
            LlmAppointment(
                it.summary,
                it.startAt.instant.atZone(friendsTimezone).toString(),
                it.startAt.instant.atZone(friendsTimezone).dayOfWeek.name,
                it.endAt.instant.atZone(friendsTimezone).toString(),
                it.endAt.instant.atZone(friendsTimezone).dayOfWeek.name
            )
        }
        .toList()
        .let { Success(it, "Successfully retrieved ${it.size} appointments") }

    companion object {
        private val LOG = LoggerFactory.getLogger(CalendarTools::class.java)
    }

    sealed interface Result {
        val message: String
    }

    data class Success<T>(val data: T, override val message: String) : Result
    data class Error(override val message: String) : Result
    data class LlmAppointment(
        val summary: String,
        val startAt: String,
        val startAtDay: String,
        val endAt: String,
        val endAtDay: String,
    )
}
