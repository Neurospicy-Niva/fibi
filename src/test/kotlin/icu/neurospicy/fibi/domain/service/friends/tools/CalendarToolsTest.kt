package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.InstanceOfAssertFactories
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

@ExtendWith(MockKExtension::class)
class CalendarToolsTest {
    @MockK
    lateinit var friendshipLedger: FriendshipLedger

    @MockK
    lateinit var calendarRepository: CalendarRepository

    @MockK
    lateinit var userMock: LedgerEntry
    val friendshipId = FriendshipId()

    @BeforeEach
    fun setUp() {
        every { friendshipLedger.findBy(friendshipId) } returns userMock
        every { userMock.timeZone } returns ZoneOffset.ofHours(1)
    }

    @Test
    fun `get appointments in range with offset timezone`() {
        every { calendarRepository.loadAppointmentsForTimeRange(any(), any()) } returns listOf(
            Appointment(
                "internal-id", FriendshipId(), AppointmentId("internal-id"), CalendarConfigId(), CalendarId("private"),
                "some-related-id", "Feed the troll",
                DateTimeInformation.fromTemporal(
                    Instant.parse("2025-02-28T10:00:00Z"),
                    ZoneOffset.ofHours(1),
                    ZoneId.of("UTC")
                ),
                DateTimeInformation.fromTemporal(
                    Instant.parse("2025-02-28T10:00:00Z").plusSeconds(600),
                    ZoneOffset.ofHours(1),
                    ZoneId.of("UTC")
                ),
                "HASH"
            )
        )
        assertThat(
            CalendarTools(calendarRepository, mockk(), friendshipLedger, friendshipId).getAppointmentsInRange(
                "2025-02-25T00:00:00+01:00",
                "2025-02-26T00:00:00+01:00"
            )
        ).let {
            it.isInstanceOf(CalendarTools.Success::class.java)
            it.extracting("data").asInstanceOf(InstanceOfAssertFactories.LIST).hasSize(1)
        }
    }

    @Test
    fun `get appointments in range with timezone Z`() {
        every { calendarRepository.loadAppointmentsForTimeRange(any(), any()) } returns listOf(
            Appointment(
                UUID.randomUUID().toString(), FriendshipId(), AppointmentId(""), CalendarConfigId(),
                CalendarId("private"), "some-related-id", "Feed the troll",
                DateTimeInformation.fromTemporal(
                    Instant.parse("2025-02-28T10:00:00Z"),
                    ZoneOffset.ofHours(1),
                    ZoneId.of("UTC")
                ),
                DateTimeInformation.fromTemporal(
                    Instant.parse("2025-02-28T10:00:00Z").plusSeconds(600),
                    ZoneOffset.ofHours(1),
                    ZoneId.of("UTC")
                ),
                "HASH"
            )
        )
        assertThat(
            CalendarTools(calendarRepository, mockk(), friendshipLedger, friendshipId).getAppointmentsInRange(
                "2025-02-28T00:00:00Z",
                "2025-02-28T23:59:59Z"
            )
        ).let {
            it.isInstanceOf(CalendarTools.Success::class.java)
            it.extracting("data").asInstanceOf(InstanceOfAssertFactories.LIST).hasSize(1)
        }
    }

    @Test
    fun `get appointments of day`() {
        every { calendarRepository.loadAppointmentsForTimeRange(any(), any()) } returns listOf(
            Appointment(
                UUID.randomUUID().toString(), FriendshipId(), AppointmentId(""),
                calendarConfigId = CalendarConfigId(),
                calendarId = CalendarId("private"), "some-related-id", "Feed the troll",
                DateTimeInformation.fromTemporal(
                    Instant.parse("2025-02-28T10:00:00Z"),
                    ZoneOffset.ofHours(1),
                    ZoneId.of("UTC")
                ),
                DateTimeInformation.fromTemporal(
                    Instant.parse("2025-02-28T10:00:00Z").plusSeconds(600),
                    ZoneOffset.ofHours(1),
                    ZoneId.of("UTC")
                ),
                "HASH"
            )
        )
        assertThat(
            CalendarTools(calendarRepository, mockk(), friendshipLedger, friendshipId).getAppointmentsOfDay(
                "2025-02-28"
            )
        ).let {
            it.isInstanceOf(CalendarTools.Success::class.java)
            it.extracting("data").asInstanceOf(InstanceOfAssertFactories.LIST).hasSize(1)
        }
    }
}