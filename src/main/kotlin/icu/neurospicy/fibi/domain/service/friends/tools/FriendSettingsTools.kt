package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.events.TimezoneChanged
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.context.ApplicationEventPublisher
import java.time.*
import java.time.ZoneOffset.UTC
import java.time.zone.ZoneRulesException
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.roundToInt


class FriendSettingsTools(
    private val friendshipLedger: FriendshipLedger,
    private val friendshipId: FriendshipId,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Tool(description = "The user provides their current local time to determine and save their timezone.")
    fun setTimezoneByLocalTime(@ToolParam(description = "Time in the format H:mm, e.g., 12:59 or 4:1.") localTimeStr: String): String {
        LOG.debug("Determining time zone of '{}' based on local time {}", friendshipId, localTimeStr)
        val toleranceMinutes: Long = 5
        val timePattern = Pattern.compile("(\\d?\\d):(\\d?\\d)")
        val matcher = timePattern.matcher(localTimeStr)
        matcher.find()
        if (matcher.groupCount() == 3) {
            return "Time does not match H:m"
        }
        val localTime = LocalTime.parse("${matcher.group(1).padStart(2, '0')}:${matcher.group(2).padStart(2, '0')}")
        val utcTime = LocalTime.now(UTC)

        val durationMinutes = Duration.between(utcTime, localTime).toMinutes()
        val calculatedZoneOffset = if (abs(durationMinutes) <= toleranceMinutes) {
            UTC
        } else {
            // Round the offset to the nearest 30 minutes
            val roundedOffsetMinutes = durationMinutes.toFloat().div(30).roundToInt().times(30)
            val offsetSeconds = roundedOffsetMinutes * 60

            // Convert the rounded difference into a ZoneOffset UTC+-12h
            ZoneOffset.ofTotalSeconds(offsetSeconds.toInt().mod(12 * 60 * 60))
        }
        val friend = friendshipLedger.findBy(friendshipId) ?: return "Friend with id $friendshipId does not exist"
        friend.timeZone?.let { currentTimeZone ->
            // Avoid timezone update if current time zone is equal to newly calculated
            when (currentTimeZone) {
                is ZoneOffset -> if (currentTimeZone.totalSeconds == calculatedZoneOffset.totalSeconds) return "Time zone is equal to existing time zone"
                else -> if (ZonedDateTime.now(currentTimeZone)
                        .isEqual(ZonedDateTime.now(calculatedZoneOffset))
                ) return "Existing time zone $currentTimeZone of friend has same offset as calculated offset timezone $calculatedZoneOffset but is more concrete. Keeping current timezone $currentTimeZone."
            }
        }
        friendshipLedger.updateZoneId(friendshipId, calculatedZoneOffset)
        LOG.debug("Saving time zone of '{}' to {}", friendshipId, calculatedZoneOffset)
        applicationEventPublisher.publishEvent(TimezoneChanged(this.javaClass, friendshipId, calculatedZoneOffset))
        return "Set timezone to $calculatedZoneOffset"
    }

    @Tool(description = "The user provides their current timezone with a zone id or similar.")
    fun setTimezoneByZone(@ToolParam(description = "Zone id to set, e.g., CET, Africa/Lubumbashi or Etc/GMT-10") zoneId: String): String {
        val newZone = try {
            ZoneId.of(zoneId)
        } catch (e: ZoneRulesException) {
            return "Invalid zone id $zoneId: ${e.message}"
        } catch (e: DateTimeException) {
            return "Invalid zone id $zoneId: ${e.message}"
        }
        val friend = friendshipLedger.findBy(friendshipId) ?: return "Friend with id $friendshipId does not exist"
        friend.timeZone?.let { currentTimeZone ->
            // Avoid timezone update if current time zone is equal to newly calculated
            if (currentTimeZone.equals(newZone)) return "Given timezone is equal to existing time zone"
        }
        friendshipLedger.updateZoneId(friendshipId, newZone)
        LOG.debug("Saving time zone of '{}' to {}", friendshipId, newZone)
        applicationEventPublisher.publishEvent(TimezoneChanged(this.javaClass, friendshipId, newZone))
        return "Set timezone to $newZone"
    }

    @Tool(description = "Get timezone of user")
    fun getTimezone(): String {
        return friendshipLedger.findBy(friendshipId)!!.timeZone.let { if (it == null) "User has no timezone set" else "User's time zone is: ${it.id}" }
    }

    @Tool(description = "Get user's current date time in ISO 8601 format")
    fun getCurrentDateTime(): String = LocalDateTime.now(friendshipLedger.findBy(friendshipId)!!.timeZone).toString()
        .apply { LOG.debug("Parsed current date time in ISO 8601 format") }

    companion object {
        private val LOG = LoggerFactory.getLogger(FriendSettingsTools::class.java)
    }
}
