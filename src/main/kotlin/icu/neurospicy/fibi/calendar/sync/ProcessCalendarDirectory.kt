package icu.neurospicy.fibi.calendar.sync

import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.CalendarId
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.calendarAndAppointmentsFrom
import icu.neurospicy.fibi.domain.repository.CalendarConfigurationRepository
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.quartz.QuartzSchedulerService
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant.now
import java.time.ZoneId
import java.time.temporal.ChronoUnit.DAYS


/**
 * This route is triggered after a vdirsyncer sync has completed.
 * Instead of processing every file change with a file watcher, it scans the entire directory,
 * aggregates all ICS files per calendar, processes them in parallel to determine what has changed,
 * and then publishes a single CalendarSynchronized event.
 */
@Component
class ProcessCalendarDirectory(
    private val calendarRepository: CalendarRepository,
    private val calendarConfigurationRepository: CalendarConfigurationRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val friendshipLedger: FriendshipLedger,
    private val quartzSchedulerService: QuartzSchedulerService
) : RouteBuilder() {
    override fun configure() {
        from("direct:processCalendarDirectory")
            .routeId("calendarFileAggregationRoute")
            .filter {
                (it.getProperty("calendarsDir") as String).isNotBlank() && it.getProperty("calendarConfigId") != null
                        && it.getProperty("friendshipId") != null
            }
            .process { exchange ->
                log.info("Starting to process calendar directory: ${exchange.getProperty("calendarConfigId")}")
                val calendarsConvertIcsRequest = exchange.message.body as CalendarsConvertIcsRequest
                val calendarConfigId = calendarsConvertIcsRequest.calendarConfigId
                val owner = calendarsConvertIcsRequest.friendshipId
                val calendarsDir = calendarsConvertIcsRequest.calendarsDir
                //gather zone ids of appointments to determine zone of friend
                val zoneIds = mutableListOf<ZoneId?>()
                //parse each calendar
                val calendarNames = File(calendarsDir).listFiles()?.filter { it.isDirectory }?.map { subCalendarDir ->
                    // Determine the calendar display name: try to read a file named "displayname" in the same folder.
                    val displayNameFile = File(subCalendarDir, "displayname")
                    val calendarName = if (displayNameFile.exists()) {
                        displayNameFile.readText().trim()
                    } else {
                        subCalendarDir.name
                    }
                    val calendarId = CalendarId(subCalendarDir.name)
                    log.info(
                        "Processing appointments of calendar {} belonging to config {}",
                        calendarId,
                        calendarConfigId
                    )
                    val icalCalendarContents = subCalendarDir.listFiles { a -> a.name.endsWith(".ics") }?.mapNotNull {
                        it.name to unfoldIcsContent(
                            it.inputStream().readAllBytes().decodeToString()
                        ).reader()
                    }?.toMap() ?: emptyMap()
                    val (privateCalendar, newAppointments, failedIcalIds) = calendarAndAppointmentsFrom(
                        calendarConfigId, calendarId, calendarName, owner, icalCalendarContents, now().plus(366, DAYS)
                    )
                    log.info(
                        "Saving calendar {} of calendar config {} for friendship {} with {} appointments",
                        calendarName, calendarConfigId, owner, newAppointments.size,
                    )
                    if (failedIcalIds.isNotEmpty()) {
                        log.warn("Failed to parse appointments: {}", failedIcalIds)
                    }
                    calendarRepository.save(privateCalendar)
                    calendarRepository.replaceCalendarAppointments(newAppointments, owner, calendarConfigId, calendarId)
                    // save zone ids of appointments (might be applied to friend's time zone)
                    zoneIds.addAll(newAppointments.map { it.startAt.zoneId })
                    calendarName
                } ?: emptyList()

                updateZoneIdOfUser(owner, zoneIds)
                calendarConfigurationRepository.synchronized(owner, calendarConfigId, now())
                eventPublisher.publishEvent(CalendarSynchronized(owner, calendarConfigId, calendarNames))
                quartzSchedulerService.scheduleCalendarSync(owner, calendarConfigId)
            }
    }

    private fun updateZoneIdOfUser(
        friendshipId: FriendshipId,
        zoneIds: List<ZoneId?>
    ) {
        val friend = friendshipLedger.findBy(friendshipId) ?: return
        if (friend.timeZone != null) return
        val mostFrequentZoneId = zoneIds
            .filterNotNull()
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        if (mostFrequentZoneId != null) {
            log.info("Setting timezone of friend to $mostFrequentZoneId because it is most frequent in their calendar.")
            friendshipLedger.updateZoneId(friendshipId, mostFrequentZoneId)
        }
    }
}

/**
 * unfoldIcsContent fixes problems with ics-files created by vdirsyncer while syncing, so that ical4j can handle the
 * content.
 *
 * Background: vdirsyncer saves file content in a way, that ical4j cannot handle some files. It adds a line break after
 * a certain count of characters in a line and adds a spacing at the start of the new line.
 * @param corruptContent the content of the ics-file with single lines broken into multiple
 * @return the file content with fixed line breaks
 */
fun unfoldIcsContent(corruptContent: String): String {
    val sb = StringBuilder()
    // Split on line breaks (supporting CRLF and LF)
    corruptContent.lines().forEach { line ->
        if (line.startsWith(" ") || line.startsWith("\t")) {
            // Continuation of previous line: append the line after trimming leading whitespace
            sb.append(line.trimStart())
        } else {
            // If not the first line, add a newline before appending
            if (sb.isNotEmpty()) {
                sb.append("\r\n")
            }
            sb.append(line)
        }
    }
    return sb.toString()
}

data class CalendarsConvertIcsRequest(
    val friendshipId: FriendshipId,
    val calendarConfigId: CalendarConfigId,
    val calendarsDir: String,
)