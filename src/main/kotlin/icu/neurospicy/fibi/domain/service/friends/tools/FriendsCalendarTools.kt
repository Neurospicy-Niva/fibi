package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.FriendshipId
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class FriendsCalendarTools(
    private val friendshipId: FriendshipId,
    private val zoneId: ZoneId
) {

    private val LOG = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Returns the current date for the user in ISO format (e.g., 2025-04-16).")
    fun getCurrentDate(): String {
        val date = LocalDate.now(zoneId)
        LOG.debug("Returning current date {} for {} in {}", date, friendshipId, zoneId)
        return date.toString()
    }

    @Tool(description = "Returns the current time for the user in ISO-8601 local time format (e.g., 14:05:00).")
    fun getCurrentTime(): String {
        val time = LocalTime.now(zoneId).withNano(0)
        LOG.debug("Returning current time {} for {} in {}", time, friendshipId, zoneId)
        return time.withNano(0).withSecond(0).toString()
    }

    @Tool(description = "Returns the current day of the week for the user (e.g., Monday, Tuesday).")
    fun getCurrentWeekday(): String {
        val day = LocalDate.now(zoneId).dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
        LOG.debug("Returning current weekday {} for {} in {}", day, friendshipId, zoneId)
        return day
    }

    @Tool(description = "Returns the full current date and time for the user, in ISO-8601 format with timezone.")
    fun getCurrentDateTime(): String {
        val now = ZonedDateTime.now(zoneId)
        LOG.debug("Returning full current datetime {} for {} in {}", now, friendshipId, zoneId)
        return now.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }
}