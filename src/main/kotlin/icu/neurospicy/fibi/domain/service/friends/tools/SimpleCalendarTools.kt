package icu.neurospicy.fibi.domain.service.friends.tools

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

class SimpleCalendarTools {

    @Tool(description = "Get week day of date, i.e., MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY")
    fun getWeekDay(
        @ToolParam(description = "Day in ISO 8601 format, e.g., 2014-08-14") day: String,
    ): String = dayOfWeekFor(LocalDate.parse(day)).apply { LOG.debug("Parsed weekday of day $this") }

    @Tool(description = "Get week days of next week starting from start day")
    fun getWeekDays(
        @ToolParam(description = "Start day in ISO 8601 format, e.g., 2014-08-14") startDay: String,
    ): String {
        val localStartDay = LocalDate.parse(startDay)
        return listOf(0L, 1, 2, 3, 4, 5, 6).map { localStartDay.plusDays(it) }.joinToString("\n") { day ->
            "- ${dayOfWeekFor(day)}"
        }.apply { LOG.debug("Parsed weekdays of days {}", this) }
    }

    @Tool(description = "Get today")
    fun getToday(toolContext: ToolContext): String {
        val zone = toolContext.context["timezone"]?.let { it as ZoneId } ?: return "Timezone of user is unknown"
        return dayOfWeekFor(ZonedDateTime.now(zone).toLocalDate())
    }

    @Tool(description = "Get tomorrow")
    fun getTomorrow(toolContext: ToolContext): String {
        val zone = toolContext.context["timezone"]?.let { it as ZoneId } ?: return "Timezone of user is unknown"
        return dayOfWeekFor(ZonedDateTime.now(zone).plusDays(1).toLocalDate())
    }

    @Tool(description = "Get next occurrence of week day")
    fun getNextOccurrenceOfDay(
        @ToolParam(description = "Week day, e.g., Monday or Tuesday") dayOfWeek: String, toolContext: ToolContext,
    ): String {
        val zone = toolContext.context["timezone"]?.let { it as ZoneId } ?: return "Timezone of user is unknown"
        val weekDay = DayOfWeek.entries.firstOrNull { it.name.lowercase() == dayOfWeek.lowercase() }
            ?: return "Cannot parse day of week $dayOfWeek"
        return dayOfWeekFor(
            ZonedDateTime.now(zone).with(java.time.temporal.TemporalAdjusters.next(weekDay)).toLocalDate()
        )
    }

    @Tool(description = "Get next occurrence of time. E.g., asking for 18:00 at 20 pm returns 18:00 on the next day")
    fun getNextOccurrenceOfTime(
        @ToolParam(description = "Time in ISO 8601 time format") time: String, toolContext: ToolContext,
    ): String {
        val zone = toolContext.context["timezone"]?.let { it as ZoneId } ?: return "Timezone of user is unknown"
        val timeToSet = LocalTime.parse(time)
        val now = ZonedDateTime.now(zone)
        val adjustedTime =
            now.withHour(timeToSet.hour).withMinute(timeToSet.minute).withSecond(timeToSet.second).withNano(0)
        return adjustedTime.plusDays(if (now.isBefore(adjustedTime)) 0 else 1).toString()
    }

    @Tool(description = "Get next weeks, format day of week, date")
    fun getNextWeeks(
        @ToolParam(
            required = false, description = "Count of weeks, default 1"
        ) countOfWeeks: Int,
        toolContext: ToolContext,
    ): String {
        val zone = toolContext.context["timezone"]?.let { it as ZoneId } ?: return "Timezone of user is unknown"
        val count = if (countOfWeeks <= 0) 1 else countOfWeeks
        val localStartDay = ZonedDateTime.now(zone).toLocalDate()
        val answer = StringBuilder()
        val requestedRange = localStartDay.rangeUntil(localStartDay.plusWeeks(count.toLong()))
        var day = localStartDay
        while (day in requestedRange) {
            answer.append(
                "- ${dayOfWeekFor(day)}${
                    when {
                        day.equals(localStartDay) -> " (Today)"
                        day.equals(
                            localStartDay.plusDays(
                                1
                            )
                        ) -> " (Tomorrow)"

                        else -> ""
                    }
                }\n"
            )
            day = day.plusDays(1)
        }
        LOG.debug("Printed next $count weeks")
        return answer.toString()
    }

    private fun formattedTime(dateTime: ZonedDateTime): String? = dateTime.format(
        DateTimeFormatter.ofLocalizedTime(
            FormatStyle.MEDIUM
        ).withLocale(Locale.US)
    )

    private fun dayOfWeekFor(day: LocalDate): String = "${
        day.dayOfWeek.toString().lowercase().replaceFirstChar { it.uppercase() }
    }, ${day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.US))}"

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}
