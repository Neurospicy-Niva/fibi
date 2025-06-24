package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId

/**
 * Repository for storing and retrieving conversation goal contexts.
 */
interface GoalContextRepository {
    /**
     * Loads the context for a specific friendship.
     * Returns null if no context exists or if the context has expired.
     */
    fun loadContext(friendshipId: FriendshipId): GoalContext?
    
    /**
     * Saves a context for a specific friendship.
     */
    fun saveContext(friendshipId: FriendshipId, context: GoalContext)
}
