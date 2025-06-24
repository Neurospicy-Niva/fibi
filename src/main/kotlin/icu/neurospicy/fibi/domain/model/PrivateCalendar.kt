package icu.neurospicy.fibi.domain.model

import com.maximeroussy.invitrode.WordGenerator
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.property.TzId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.*
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.Temporal
import net.fortuna.ical4j.model.parameter.TzId as ParameterTzId

@Document(collection = "private_calendar")
data class PrivateCalendar(
    @Id
    val id: String? = null,
    val calendarConfigId: CalendarConfigId,
    val calendarId: CalendarId,
    val name: String,
    val appointments: List<AppointmentId>,
    val lastUpdatedAt: Instant,
    val owner: FriendshipId,
)

@JvmInline
value class CalendarConfigId(
    private val word: String = WordGenerator().newWord(4).lowercase() + "_"
            + WordGenerator().newWord(6).lowercase() + "_"
            + WordGenerator().newWord(6).lowercase()
) {
    override fun toString(): String = word
}


@Document(collection = "appointments")
data class Appointment(
    @Id
    val _id: String? = null,
    val owner: FriendshipId,
    val appointmentId: AppointmentId,
    val calendarConfigId: CalendarConfigId,
    val calendarId: CalendarId,
    val relatedVEvent: String, // related VEvent by Uid.value
    val summary: String,
    val startAt: DateTimeInformation,
    val endAt: DateTimeInformation,
    val hash: String
) {
    companion object {
        @SuppressWarnings("kotlin:S107")
        fun fromVEvent(
            vEvent: VEvent,
            owner: FriendshipId,
            calendarConfigId: CalendarConfigId,
            calendarId: CalendarId,
            repetitionEnd: Instant,
            calendarZoneId: ZoneId? = null,
            icsFileName: String,
            hash: String
        ): List<Appointment> {
            val summary = vEvent.summary.value
            val uid = vEvent.uid.map { it.value }.orElse(icsFileName) ?: icsFileName

            // Process the start information using the generic Temporal value.
            val dateTimeStart = vEvent.getDateTimeStart<Temporal>()
            val startZoneId =
                dateTimeStart.getParameter<ParameterTzId>(Parameter.TZID).map { it.value as String }
                    .map { ZoneId.of(it) }.orElse(null)

            // Process the end information.
            val dateTimeEnd = vEvent.getDateTimeEnd<Temporal>()
            val endZoneId = dateTimeEnd.getParameter<ParameterTzId>(Parameter.TZID).map { it.value as String }
                .map { ZoneId.of(it) }.orElse(null)

            return try {
                vEvent.calculateRecurrenceSet<Temporal>(
                    Period(
                        dateTimeStart.date.minus(1, DAYS),
                        adaptToTemporal(repetitionEnd, startZoneId ?: UTC, dateTimeStart.date)
                    )
                ).map {
                    val startAt = DateTimeInformation.fromTemporal(it.start, startZoneId, calendarZoneId)
                    val endAt = DateTimeInformation.fromTemporal(it.end, endZoneId ?: startZoneId, calendarZoneId)
                    Appointment(
                        appointmentId = AppointmentId.from(uid, startAt.instant),
                        relatedVEvent = uid,
                        owner = owner,
                        calendarConfigId = calendarConfigId,
                        calendarId = calendarId,
                        hash = hash,
                        summary = summary,
                        startAt = startAt,
                        endAt = endAt,
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        private fun adaptToTemporal(instant: Instant, zoneId: ZoneId, time: Temporal): Temporal =
            when (time) {
                is LocalDate -> instant.atZone(zoneId).toLocalDate()
                is LocalDateTime -> instant.atZone(zoneId).toLocalDateTime()
                is OffsetDateTime -> instant.atZone(zoneId).toOffsetDateTime()
                is ZonedDateTime -> instant.atZone(zoneId)
                else -> instant
            }

    }
}

data class DateTimeInformation(
    val instant: Instant,
    val zoneId: ZoneId?,
    val localDate: Temporal,

    ) {
    companion object {
        fun fromTemporal(
            temporal: Temporal,
            temporalZoneId: ZoneId?,
            calendarZoneId: ZoneId?
        ): DateTimeInformation {
            var zoneId = temporalZoneId ?: calendarZoneId
            var temporalToSave = temporal
            return DateTimeInformation(
                when (temporal) {
                    is ZonedDateTime -> {
                        zoneId = temporalZoneId
                        temporalToSave = temporal.withZoneSameInstant(temporalZoneId)
                        temporal.toInstant()
                    }

                    is OffsetDateTime -> {
                        zoneId = temporal.toZonedDateTime().zone
                        temporal.toZonedDateTime().toInstant()
                    }

                    is LocalDateTime -> {
                        temporalToSave = if (zoneId == null) temporal else temporal.atZone(zoneId)
                        temporal.atZone(zoneId ?: UTC).toInstant()
                    }

                    is Instant -> temporal
                    is LocalDate -> temporal.atStartOfDay(zoneId ?: UTC).toInstant()
                    else -> throw IllegalArgumentException("Unknown date format")
                },
                zoneId, temporalToSave
            )
        }
    }
}

@JvmInline
value class CalendarId(
    private val path: String
) {
    override fun toString(): String = path
}

@JvmInline
value class AppointmentId(
    private val value: String
) {
    companion object {
        fun from(relatedVEvent: String, startAt: Instant): AppointmentId =
            AppointmentId("${relatedVEvent}-${startAt.atZone(UTC)}")

    }

    override fun toString(): String = value
}

@OptIn(ExperimentalStdlibApi::class)
fun calendarAndAppointmentsFrom(
    calendarConfigId: CalendarConfigId,
    calendarId: CalendarId,
    calendarName: String,
    owner: FriendshipId,
    icalCalendarContents: Map<String, StringReader>,
    repetitionEnd: Instant
): Result {
    val failedIcalIds = mutableListOf<String>()
    val appointments = icalCalendarContents.mapNotNull { idToIcalCalendarContent ->
        try {
            val builder = CalendarBuilder()
            val icalCalendar = builder.build(idToIcalCalendarContent.value)
            val calendarZoneId =
                icalCalendar.getComponent<VTimeZone>(net.fortuna.ical4j.model.Component.VTIMEZONE)
                    .map { vTimeZone ->
                        vTimeZone.getProperty<TzId>(Property.TZID).map {
                            if (ZoneId.getAvailableZoneIds().contains(it.value)) ZoneId.of(it.value) else null
                        }.orElse(null)
                    }.orElse(null)

            icalCalendar.getComponents<VEvent>(net.fortuna.ical4j.model.Component.VEVENT)
                .firstNotNullOf {
                    Appointment.fromVEvent(
                        it,
                        owner,
                        calendarConfigId,
                        calendarId,
                        repetitionEnd,
                        calendarZoneId,
                        idToIcalCalendarContent.key,
                        MessageDigest.getInstance("SHA-256")
                            .digest(idToIcalCalendarContent.value.toString().toByteArray(StandardCharsets.UTF_8))
                            .toHexString(format = HexFormat.Default),
                    )
                }
        } catch (e: Exception) {
            failedIcalIds.add(idToIcalCalendarContent.key)
            emptyList()
        }
    }.flatten()
    return Result(
        privateCalendar = PrivateCalendar(
            calendarConfigId = calendarConfigId,
            calendarId = calendarId,
            name = calendarName,
            appointments = appointments.map { it.appointmentId },
            lastUpdatedAt = Instant.now(),
            owner = owner
        ),
        appointments = appointments,
        failedIcalIds = failedIcalIds
    )
}

data class Result(
    val privateCalendar: PrivateCalendar,
    val appointments: List<Appointment>,
    val failedIcalIds: List<String>
)