package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage

// Intent system
@JvmInline
value class Intent(val name: String) {
    override fun toString(): String = name
}

interface IntentContributor {
    fun intent(): Intent
    fun description(): String
}

interface SubtaskContributor {
    fun forIntent(): Intent
    suspend fun provideSubtasks(intent: Intent, friendshipId: FriendshipId, message: UserMessage): List<Subtask>
}

object CoreIntents {
    val Smalltalk = Intent("Smalltalk")
    val CancelGoal = Intent("CancelGoal")
    val Unknown = Intent("Unknown")
    val FollowUp = Intent("FollowUp")
}

