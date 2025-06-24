package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.CalendarConfigurations
import icu.neurospicy.fibi.domain.model.FriendshipId
import java.time.Instant

interface CalendarConfigurationRepository {
    fun save(friendshipId: FriendshipId, calendarConfigurations: CalendarConfigurations)
    fun load(friendshipId: FriendshipId): CalendarConfigurations
    fun synchronized(friendshipId: FriendshipId, calendarConfigId: CalendarConfigId, syncedAt: Instant)
    fun remove(friendshipId: FriendshipId, calendarConfigId: CalendarConfigId)
}