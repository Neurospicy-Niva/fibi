package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.outgoing.mongodb.ArchivedTask
import java.time.Instant

interface TaskRepository {
    fun save(task: Task): Task
    fun findByFriendshipId(friendshipId: FriendshipId): List<Task>
    fun rename(friendshipId: FriendshipId, id: String, title: String?, description: String?): Task?
    fun complete(friendshipId: FriendshipId, id: String, completed: Boolean, now: Instant): Task?
    fun cleanUp(friendshipId: FriendshipId, cleanUpAt: Instant): List<ArchivedTask>
    fun removeTask(friendshipId: FriendshipId, id: String): Task?
}