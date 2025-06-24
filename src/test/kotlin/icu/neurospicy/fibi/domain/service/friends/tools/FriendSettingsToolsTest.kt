package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.LedgerEntry
import icu.neurospicy.fibi.domain.model.RelationStatus.Friend
import icu.neurospicy.fibi.domain.model.events.TimezoneChanged
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZoneOffset.UTC

@ExtendWith(MockKExtension::class)
class FriendSettingsToolsTest {
    @MockK
    lateinit var friendshipLedger: FriendshipLedger

    @MockK
    lateinit var eventPublisher: ApplicationEventPublisher

    @BeforeEach
    fun setUp() {
        every { eventPublisher.publishEvent(any<TimezoneChanged>()) } just Runs
    }

    @Test
    fun `set timezone to +1 when current is UTC`() {
        val oneHourAhead = LocalTime.now(UTC).plusHours(1)
        val localTimeStr = "${oneHourAhead.hour}:${oneHourAhead.minute}"
        val capturedZoneId = slot<ZoneId>()
        val friendshipId = FriendshipId()
        every { friendshipLedger.updateZoneId(friendshipId, capture(capturedZoneId)) } just Runs
        every { friendshipLedger.findBy(friendshipId) } returns
                LedgerEntry(friendshipId = friendshipId, relationStatus = Friend, timeZone = UTC)
        //when
        FriendSettingsTools(
            friendshipLedger,
            friendshipId,
            eventPublisher
        ).setTimezoneByLocalTime(localTimeStr)
        //then
        assertThat(capturedZoneId.captured).isEqualTo(ZoneOffset.ofHours(1))
        verify { eventPublisher.publishEvent(any<TimezoneChanged>()) }
    }

    @Test
    fun `does not set timezone to +1 when current is +1`() {
        val oneHourAhead = LocalTime.now(UTC).plusHours(1)
        val localTimeStr = "${oneHourAhead.hour}:${oneHourAhead.minute}"
        val friendshipId = FriendshipId()
        every { friendshipLedger.findBy(friendshipId) } returns
                LedgerEntry(friendshipId = friendshipId, relationStatus = Friend, timeZone = ZoneOffset.ofHours(1))
        //when
        FriendSettingsTools(
            friendshipLedger,
            friendshipId,
            eventPublisher
        ).setTimezoneByLocalTime(localTimeStr)
        //then
        verify(exactly = 0) { eventPublisher.publishEvent(any<TimezoneChanged>()) }
        verify(exactly = 0) { friendshipLedger.updateZoneId(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["CET", "Africa/Lubumbashi", "Etc/GMT-10", "UTC", "Europe/Amsterdam"])
    fun `set timezone by zone id saves and publishes it`(zoneIdStr: String) {
        val capturedZoneId = slot<ZoneId>()
        val friendshipId = FriendshipId()
        every { friendshipLedger.updateZoneId(friendshipId, capture(capturedZoneId)) } just Runs
        every { friendshipLedger.findBy(friendshipId) } returns
                LedgerEntry(friendshipId = friendshipId, relationStatus = Friend, timeZone = UTC)
        //when
        FriendSettingsTools(friendshipLedger, friendshipId, eventPublisher).setTimezoneByZone(zoneIdStr)
        //then
        assertThat(capturedZoneId.captured).isEqualTo(ZoneId.of(zoneIdStr))
        verify { eventPublisher.publishEvent(any<TimezoneChanged>()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["CET", "Africa/Lubumbashi", "Etc/GMT-10", "UTC", "Europe/Amsterdam"])
    fun `set timezone by zone id does nothing when not changed`(zoneIdStr: String) {
        val friendshipId = FriendshipId()
        every { friendshipLedger.findBy(friendshipId) } returns
                LedgerEntry(friendshipId = friendshipId, relationStatus = Friend, timeZone = ZoneId.of(zoneIdStr))
        //when
        FriendSettingsTools(friendshipLedger, friendshipId, eventPublisher).setTimezoneByZone(zoneIdStr)
        //then
        verify(exactly = 0) { eventPublisher.publishEvent(any<TimezoneChanged>()) }
        verify(exactly = 0) { friendshipLedger.updateZoneId(any(), any()) }
    }
}