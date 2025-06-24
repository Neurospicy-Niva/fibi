package icu.neurospicy.fibi.calendar.sync

import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.FriendshipId

/**
 * A data class representing the allowed parameters for triggering a vdirsyncer sync.
 */
data class CalendarSyncRequest(
    val friendshipId: FriendshipId,
    val calendarConfigId: CalendarConfigId,
    val configFilePath: String,
    val discoveryShFilePath: String,
    val calendarsDir: String,
)

