package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.MessageId
import java.time.Instant
import java.time.Instant.now

interface CalendarActivityRepository {
    fun startRegistration(calendarRegistration: CalendarRegistration)
    fun loadActiveRegistration(friendshipId: FriendshipId): CalendarRegistration?
    fun finishRegistration(friendshipId: FriendshipId, finishedAt: Instant)
    fun cancelRegistration(friendshipId: FriendshipId, cancelledAt: Instant)
}

data class CalendarRegistration(
    val id: String?,
    val friendshipId: FriendshipId,
    val startedBy: Source,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val cancelledAt: Instant?,
) {
    companion object {
        fun started(friendshipId: FriendshipId, source: Source): CalendarRegistration =
            CalendarRegistration(null, friendshipId, source, now(), null, null)
    }
}

sealed interface Source

data class MessageSource(
    val messageId: MessageId,
    val channel: Channel,
) : Source