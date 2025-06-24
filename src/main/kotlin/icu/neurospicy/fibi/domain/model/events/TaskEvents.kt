package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Task
import org.springframework.context.ApplicationEvent

data class TaskAdded(val _source: Class<*>, val owner: FriendshipId, val task: Task) : ApplicationEvent(_source)
data class TaskUpdated(val _source: Class<*>, val owner: FriendshipId, val task: Task, val oldVersion: Task) :
    ApplicationEvent(_source)

/**
 * Title and/or description of task changed.
 */
data class TaskReworded(val _source: Class<*>, val owner: FriendshipId, val task: Task, val oldVersion: Task) :
    ApplicationEvent(_source)

data class TaskCompleted(val _source: Class<*>, val owner: FriendshipId, val task: Task) : ApplicationEvent(_source)
data class TasksCleanedUp(val _source: Class<*>, val owner: FriendshipId, val tasks: List<Task>) : ApplicationEvent(_source)
data class TaskRemoved(val _source: Class<*>, val owner: FriendshipId, val task: Task) : ApplicationEvent(_source)
