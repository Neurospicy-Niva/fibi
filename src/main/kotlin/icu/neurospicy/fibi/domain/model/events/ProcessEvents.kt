package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.*
import org.springframework.context.ApplicationEvent

data class CalendarRegistrationActivityStarted(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
) : ApplicationEvent(_source)

class CalendarRegistrationActivityFinished(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val configs: Set<CalendarConfiguration>?,
    val channel: Channel?,
    val messageId: MessageId?
) : ApplicationEvent(_source) {
    fun wasSuccessful() = !configs.isNullOrEmpty()
}

data class SetUpMorningRoutineActivityStarted(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
) : ApplicationEvent(_source)

data class SetUpMorningRoutineActivityFinished(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val routineId: RoutineConfigurationId
) : ApplicationEvent(_source)