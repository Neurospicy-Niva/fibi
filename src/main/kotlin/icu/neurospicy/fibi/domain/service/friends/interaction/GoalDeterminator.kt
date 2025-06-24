package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage

/**
 * Determine
 */
interface GoalDeterminator {
    fun canHandle(intent: Intent): Boolean
    suspend fun determineGoal(intent: Intent, message: UserMessage, friendshipId: FriendshipId): Set<Goal>
}

class SimpleGoalDeterminator : GoalDeterminator {
    override fun canHandle(intent: Intent): Boolean = true
    override suspend fun determineGoal(intent: Intent, message: UserMessage, friendshipId: FriendshipId): Set<Goal> =
        setOf(Goal(intent))
}