package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Timer
import org.springframework.context.ApplicationEvent

data class TimerSet(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val timer: Timer
) : ApplicationEvent(_source)

data class TimerUpdated(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val timer: Timer
) : ApplicationEvent(_source)

data class TimerStopped(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val timerId: String
) : ApplicationEvent(_source)

data class TimerExpired(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val timerId: String
) : ApplicationEvent(_source)
