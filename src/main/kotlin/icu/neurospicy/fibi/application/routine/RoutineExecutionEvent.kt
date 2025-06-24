package icu.neurospicy.fibi.application.routine

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.RoutineConfigurationId
import org.springframework.context.ApplicationEvent

/**
 * An event published by a generic scheduled job.
 * Listeners will determine how to process the event based on the routineType.
 */
data class RoutineExecutionEvent(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val routineId: RoutineConfigurationId,
    val routineType: String
) : ApplicationEvent(_source)