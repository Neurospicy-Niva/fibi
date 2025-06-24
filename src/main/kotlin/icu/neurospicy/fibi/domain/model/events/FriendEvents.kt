package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.FriendshipId
import org.springframework.context.ApplicationEvent
import java.time.ZoneId

data class TimezoneChanged(
    val _source: Class<Any>, val friendshipId: FriendshipId, val newTimezone: ZoneId
) : ApplicationEvent(_source)