package icu.neurospicy.fibi.domain.model

import ch.qos.logback.core.CoreConstants.UTF_8_CHARSET
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.TimeZoneRegistry
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.io.ClassPathResource
import java.io.StringReader
import java.time.*
import java.time.Instant.now
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit.DAYS
import kotlin.test.assertNull

class AppointmentTest {

    private val tzRegistry: TimeZoneRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    init {
        // Ensure the timezone registry is initialized with proper data
        tzRegistry.register(tzRegistry.getTimeZone("Europe/Berlin"))
    }

    @Test
    fun `fromVEvent should correctly convert an iPhone OS VEvent to Appointment`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            CALSCALE:GREGORIAN
            PRODID:-//Apple Inc.//iPhone OS 17.5.1//EN
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:20241025T030000
            TZOFFSETTO:+0100
            TZOFFSETFROM:+0200
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            ATTENDEE;CN=John;EMAIL=john@example.com;LANGUAGE=de_DE;PARTSTAT=ACCEPTED;ROLE=REQ-PARTICIPANT:mailto:john@example.com
            CREATED:20240702T125000Z
            DTEND;TZID=Europe/Berlin:20240910T183000
            DTSTAMP:20240826T090818Z
            DTSTART;TZID=Europe/Berlin:20240910T150000
            LAST-MODIFIED:20240826T090811Z
            ORGANIZER;CN=Olga;EMAIL=olga@example.com;SCHEDULE-STATUS=5.0:mailto:olga@example.com
            SEQUENCE:2
            STATUS:CONFIRMED
            SUMMARY:Party all the time
            TRANSP:OPAQUE
            UID:ffffffff-1111-4444-8888-cccccccccccc
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val builder = CalendarBuilder()
        val calendar = builder.build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        // when
        val appointment =
            Appointment.fromVEvent(
                vEvent,
                FriendshipId(),
                CalendarConfigId(),
                CalendarId("test"),
                now().plus(365, DAYS),
                ZoneId.of("Europe/Berlin"),
                "default-calendar-id",
                "asdf"
            )
                .first()

        // then
        val startAtTemporal = ZonedDateTime.of(2024, 9, 10, 15, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(startAtTemporal.toInstant(), appointment.startAt.instant)
        assertTrue(startAtTemporal.isEqual(appointment.startAt.localDate as ZonedDateTime))
        assertEquals(startAtTemporal.zone, appointment.startAt.zoneId)

        val endAtTemporal = ZonedDateTime.of(2024, 9, 10, 18, 30, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(endAtTemporal.toInstant(), appointment.endAt.instant)
        assertTrue(endAtTemporal.isEqual(appointment.endAt.localDate as ZonedDateTime))
        assertEquals(endAtTemporal.zone, appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary, "Summary should match")
    }

    @Test
    fun `fromVEvent should correctly convert an nextcloud VEvent to Appointment`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            PRODID:-//IDN nextcloud.com//Calendar app 4.6.3//EN
            CALSCALE:GREGORIAN
            VERSION:2.0
            BEGIN:VEVENT
            CREATED:20240725T085000Z
            DTSTAMP:20240725T085000Z
            LAST-MODIFIED:20240725T085152Z
            UID:4f41a09f-9505-4a20-9169-8628689ffb4b
            DTSTART;TZID=Europe/Berlin:20240819T170000
            DTEND;TZID=Europe/Berlin:20240819T180000
            STATUS:CONFIRMED
            SUMMARY:Party all the time
            END:VEVENT
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:DAYLIGHT
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            TZNAME:CEST
            DTSTART:19700329T020000
            END:DAYLIGHT
            BEGIN:STANDARD
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            TZNAME:CET
            DTSTART:19701025T030000
            END:STANDARD
            END:VTIMEZONE
            END:VCALENDAR""".trimIndent()

        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        // when
        val appointment =
            Appointment.fromVEvent(
                vEvent,
                FriendshipId(),
                CalendarConfigId(),
                CalendarId("test"),
                now().plus(365, DAYS),
                icsFileName = "default-calendar-id",
                hash = "asdf"
            ).first()

        // then
        val startAt = LocalDateTime.of(2024, 8, 19, 17, 0)
            .atZone(ZoneId.of("Europe/Berlin"))
        assertEquals(
            startAt.toInstant(),
            appointment.startAt.instant
        )
        assertTrue(startAt.isEqual(appointment.startAt.localDate as ZonedDateTime))
        assertEquals(ZoneId.of("Europe/Berlin"), appointment.startAt.zoneId)

        val endAt = LocalDateTime.of(2024, 8, 19, 18, 0)
            .atZone(ZoneId.of("Europe/Berlin"))
        assertEquals(
            endAt
                .toInstant(),
            appointment.endAt.instant
        )
        assertTrue(endAt.isEqual(appointment.endAt.localDate as ZonedDateTime))
        assertEquals(ZoneId.of("Europe/Berlin"), appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary, "Summary should match")
    }

    @Test
    fun `fromVEvent should correctly convert a mozilla VEvent to Appointment`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            PRODID:-//Mozilla.org/NONSGML Mozilla Calendar V1.1//EN
            VERSION:2.0
            BEGIN:VEVENT
            CREATED:20231215T101529Z
            LAST-MODIFIED:20240215T095000Z
            DTSTAMP:20240215T095000Z
            UID:partytime
            SUMMARY:Party all the time
            STATUS:CONFIRMED
            ORGANIZER;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;SCHEDULE-AGENT=CLIENT:mailto:susi@example.com
            ATTENDEE;CN=Jason Else;PARTSTAT=ACCEPTED;ROLE=REQ-PARTICIPANT:mailto:jason@example.com
            DTSTART:20240215T140000Z
            DTEND:20240215T150000Z
            SEQUENCE:1
            TRANSP:OPAQUE
            X-MOZ-INVITED-ATTENDEE:mailto:jason@example.com
            X-MOZ-RECEIVED-SEQUENCE:1
            X-MOZ-RECEIVED-DTSTAMP:20240215T095632Z
            END:VEVENT
            END:VCALENDAR""".trimIndent()

        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        // when
        val appointment =
            Appointment.fromVEvent(
                vEvent,
                FriendshipId(),
                CalendarConfigId(),
                CalendarId("test"),
                now().plus(365, DAYS),
                icsFileName = "default-calendar-id",
                hash = "asdf"
            ).first()

        // then
        val startAt = OffsetDateTime.of(2024, 2, 15, 14, 0, 0, 0, UTC)
        assertEquals(startAt.toInstant(), appointment.startAt.instant)
        assertTrue(startAt.isEqual(appointment.startAt.localDate as OffsetDateTime))
        assertEquals(ZoneId.of("+00:00"), appointment.startAt.zoneId)

        val endAt = OffsetDateTime.of(2024, 2, 15, 15, 0, 0, 0, UTC)
        assertEquals(endAt.toInstant(), appointment.endAt.instant)
        assertTrue(endAt.isEqual(appointment.endAt.localDate as OffsetDateTime))
        assertEquals(ZoneId.of("+00:00"), appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary, "Summary should match")
    }

    @Test
    fun `fromVEvent should correctly convert a whole day event VEvent to Appointment`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:DAVx5/4.4.3.2-ose ical4j/3.2.19 (com.appgenix.bizcal)
            BEGIN:VEVENT
            DTSTAMP:20241124T173800Z
            UID:ad31dc5a-8ffb-4a36-b9ec-528d78dc2453
            SUMMARY:Party all the time
            LOCATION:Part hall\, Ganymede
            DTSTART;VALUE=DATE:20241204
            DTEND;VALUE=DATE:20241205
            STATUS:CONFIRMED
            END:VEVENT
            END:VCALENDAR""".trimIndent()

        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        // when
        val appointment =
            Appointment.fromVEvent(
                vEvent,
                FriendshipId(),
                CalendarConfigId(),
                CalendarId("test"),
                now().plus(365, DAYS),
                icsFileName = "default-calendar-id",
                hash = "asdf"
            ).first()

        // then
        val startAt = LocalDate.of(2024, 12, 4)
        assertEquals(startAt.atStartOfDay(UTC).toInstant(), appointment.startAt.instant)
        assertTrue(startAt.isEqual(appointment.startAt.localDate as LocalDate))
        assertNull(appointment.startAt.zoneId)

        val endAt = LocalDate.of(2024, 12, 5)
        assertEquals(endAt.atStartOfDay(UTC).toInstant(), appointment.endAt.instant)
        assertTrue(endAt.isEqual(appointment.endAt.localDate as LocalDate))
        assertNull(appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary, "Summary should match")
    }

    @Test
    fun `should correctly parse start and end with TZID`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            CALSCALE:GREGORIAN
            PRODID:-//Apple Inc.//iPhone OS 17.5.1//EN
            BEGIN:VEVENT
            DTSTAMP:20240826T090818Z
            DTSTART;TZID=Europe/Berlin:20250211T110000
            DTEND;TZID=Europe/Berlin:20250211T120000
            SUMMARY:Party all the time
            UID:ffffffff-1111-4444-8888-cccccccccccc
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        val appointment =
            Appointment.fromVEvent(
                vEvent,
                FriendshipId(),
                CalendarConfigId(),
                CalendarId("test"),
                now().plus(365, DAYS),
                icsFileName = "default-calendar-id",
                hash = "asdf"
            ).first()

        val startAt = ZonedDateTime.of(2025, 2, 11, 11, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(startAt.toInstant(), appointment.startAt.instant)
        assertTrue(startAt.isEqual(appointment.startAt.localDate as ZonedDateTime))
        assertEquals(ZoneId.of("Europe/Berlin"), appointment.startAt.zoneId)

        val endAt = ZonedDateTime.of(2025, 2, 11, 12, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(endAt.toInstant(), appointment.endAt.instant)
        assertTrue(endAt.isEqual(appointment.endAt.localDate as ZonedDateTime))
        assertEquals(ZoneId.of("Europe/Berlin"), appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary)
    }

    @Test
    fun `should correctly parse start and end without TZID`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            CALSCALE:GREGORIAN
            PRODID:-//Apple Inc.//iPhone OS 17.5.1//EN
            BEGIN:VEVENT
            DTSTART:20250211T110000
            DTEND:20250211T120000
            DTSTAMP:20240826T090818Z
            SUMMARY:Party all the time
            UID:ffffffff-1111-4444-8888-cccccccccccc
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        val appointment =
            Appointment.fromVEvent(
                vEvent,
                FriendshipId(),
                CalendarConfigId(),
                CalendarId("test"),
                now().plus(365, DAYS),
                icsFileName = "default-calendar-id",
                hash = "asdf"
            ).first()

        val startAt = LocalDateTime.of(2025, 2, 11, 11, 0)
        assertEquals(startAt.toInstant(UTC), appointment.startAt.instant)
        assertTrue(startAt.isEqual(appointment.startAt.localDate as LocalDateTime))
        assertNull(appointment.startAt.zoneId)

        val endAt = LocalDateTime.of(2025, 2, 11, 12, 0)
        assertEquals(endAt.toInstant(UTC), appointment.endAt.instant)
        assertTrue(endAt.isEqual(appointment.endAt.localDate as LocalDateTime))
        assertNull(appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary)
    }

    @Test
    fun `should correctly parse start and end without TZID but TZID at calendar`() {
        // given
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            CALSCALE:GREGORIAN
            PRODID:-//Apple Inc.//iPhone OS 17.5.1//EN
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:20241025T030000
            TZOFFSETTO:+0100
            TZOFFSETFROM:+0200
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTART:20250211T110000
            DTEND:20250211T120000
            DTSTAMP:20240826T090818Z
            SUMMARY:Party all the time
            UID:ffffffff-1111-4444-8888-cccccccccccc
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        val appointment = Appointment.fromVEvent(
            vEvent, FriendshipId(), CalendarConfigId(), CalendarId("test"),
            now().plus(365, DAYS),
            ZoneId.of("Europe/Berlin"),
            "default-calendar-id", hash = "asdf"
        ).first()

        val startAt = ZonedDateTime.of(2025, 2, 11, 11, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(startAt.toInstant(), appointment.startAt.instant)
        assertTrue(startAt.isEqual(appointment.startAt.localDate as ZonedDateTime))
        assertEquals(ZoneId.of("Europe/Berlin"), appointment.startAt.zoneId)

        val endAt = ZonedDateTime.of(2025, 2, 11, 12, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertEquals(endAt.toInstant(), appointment.endAt.instant)
        assertTrue(endAt.isEqual(appointment.endAt.localDate as ZonedDateTime))
        assertEquals(ZoneId.of("Europe/Berlin"), appointment.endAt.zoneId)

        assertEquals("Party all the time", appointment.summary)
    }

    @Test
    fun `should correctly parse recurring event into multiple appointments`() {
        // given
        val count = 4
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            CALSCALE:GREGORIAN
            PRODID:-//Sh Ine.//Bright//EN
            BEGIN:VEVENT
            DTSTAMP:20240826T090818Z
            DTSTART;TZID=Europe/Berlin:20250211T110000
            DTEND;TZID=Europe/Berlin:20250211T120000
            SUMMARY:Party all the time
            UID:ffffffff-1111-4444-8888-cccccccccccc
            RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=TU;COUNT=$count
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        val appointments = Appointment.fromVEvent(
            vEvent,
            FriendshipId(),
            CalendarConfigId(),
            CalendarId("test"),
            now().plus(365, DAYS),
            icsFileName = "default-calendar-id",
            hash = "asdf"
        )

        assertThat(appointments).hasSize(count)
        assertThat(appointments).allSatisfy {
            assertThat(it.summary).isEqualTo("Party all the time")
        }
    }

    @Test
    fun `should correctly parse repetition of recurring event`() {
        // given
        val days = listOf(5, 10, 15, 20, 25)
        val count = 4 * days.size
        val icalInput = """
            BEGIN:VCALENDAR
            VERSION:2.0
            CALSCALE:GREGORIAN
            PRODID:-//Sh Ine.//Bright//EN
            BEGIN:VEVENT
            DTSTAMP:20240826T090818Z
            DTSTART;TZID=Europe/Berlin:20250211T110000
            DTEND;TZID=Europe/Berlin:20250211T120000
            SUMMARY:Party all the time
            UID:ffffffff-1111-4444-8888-cccccccccccc
            RRULE:FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=${days.joinToString(",") { it.toString() }};COUNT=$count
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val calendar = CalendarBuilder().build(StringReader(icalInput))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        val appointments = Appointment.fromVEvent(
            vEvent,
            FriendshipId(),
            CalendarConfigId(),
            CalendarId("test"),
            now().plus(365, DAYS),
            icsFileName = "default-calendar-id",
            hash = "asdf"
        )

        assertThat(appointments).hasSize(count)
        assertThat(appointments).allSatisfy {
            assertThat(it.startAt.localDate.get(ChronoField.DAY_OF_MONTH)).isIn(days)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["example_repetition_1.ics", "example_repetition_2.ics", "example_repetition_3.ics"])
    fun `should parse repetition of recurring event correctly`(fileName: String) {
        // given
        val calendar =
            CalendarBuilder().build(StringReader(ClassPathResource(fileName).getContentAsString(UTF_8_CHARSET)))
        val vEvent = calendar.getComponents<VEvent>(Component.VEVENT).first()

        val appointments = Appointment.fromVEvent(
            vEvent,
            FriendshipId(),
            CalendarConfigId(),
            CalendarId("test"),
            now().plus(365, DAYS),
            icsFileName = "default-calendar-id",
            hash = "asdf"
        )

        assertThat(appointments).allSatisfy {
            assertThat(it.summary).isEqualTo("Travelling")
        }
        assertThat(appointments).hasSizeGreaterThan(20)
    }

    @ParameterizedTest
    @ValueSource(strings = ["example_repetition_1.ics", "example_repetition_2.ics", "example_repetition_3.ics"])
    fun `should parse calendar with recurring event correctly`(fileName: String) {
        // given
        val calendar = mapOf(fileName to StringReader(ClassPathResource(fileName).getContentAsString(UTF_8_CHARSET)))

        val privateCalendar = calendarAndAppointmentsFrom(
            CalendarConfigId(),
            CalendarId("personal"),
            "Personal",
            FriendshipId(),
            calendar,
            now().plus(365, DAYS)
        )

        assertThat(privateCalendar.appointments).allSatisfy {
            assertThat(it.summary).isEqualTo("Travelling")
        }
        assertThat(privateCalendar.appointments).hasSizeGreaterThan(20)
    }

}