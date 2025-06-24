package icu.neurospicy.fibi.calendar.sync

import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.FriendshipId

data class CalendarSynchronized(
    val friendshipId: FriendshipId,
    val calendarConfigId: CalendarConfigId,
    val calendarNames: List<String>
)
