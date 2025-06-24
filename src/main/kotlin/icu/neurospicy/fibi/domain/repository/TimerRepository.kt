package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Timer
import java.time.Duration

interface TimerRepository {
    fun save(timer: Timer): Timer
    fun findByFriendshipId(friendshipId: FriendshipId): List<Timer>
    fun remove(friendshipId: FriendshipId, id: String)
    fun update(friendshipId: FriendshipId, id: String, duration: Duration?, label: String?)
    fun expired(owner: FriendshipId, id: String)

}
