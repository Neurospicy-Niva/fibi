package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.model.events.*
import icu.neurospicy.fibi.domain.repository.TaskRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class TaskRepositoryUsingMongodb(
    private val mongoTemplate: MongoTemplate,
    private val eventPublisher: ApplicationEventPublisher
) : TaskRepository {
    override fun save(task: Task): Task {
        val savedTask = mongoTemplate.save(task, "tasks")
        eventPublisher.publishEvent(
            if (task.id == null) TaskAdded(this.javaClass, task.owner, task) else TaskUpdated(
                this.javaClass,
                task.owner,
                savedTask, task
            )
        )
        return savedTask
    }

    override fun rename(friendshipId: FriendshipId, id: String, title: String?, description: String?): Task? {
        return mongoTemplate.findOne(
            query(where("_id").`is`(id).andOperator(where("owner").`is`(friendshipId.toString()))),
            Task::class.java, "tasks"
        )?.let {
            val savedTask =
                mongoTemplate.save(it.update(title = title ?: it.title, description = description ?: it.description))
            eventPublisher.publishEvent(TaskReworded(this.javaClass, friendshipId, savedTask, it))
            savedTask
        }
    }

    override fun complete(friendshipId: FriendshipId, id: String, completed: Boolean, now: Instant): Task? {
        return mongoTemplate.findOne(
            query(where("_id").`is`(id).andOperator(where("owner").`is`(friendshipId.toString()))),
            Task::class.java, "tasks"
        )?.let {
            if (it.completed) it else {
                val savedTask = mongoTemplate.save(if (completed) it.complete(completedAt = now) else it.incomplete())
                eventPublisher.publishEvent(TaskCompleted(this.javaClass, friendshipId, savedTask))
                savedTask
            }
        }
    }

    override fun findByFriendshipId(friendshipId: FriendshipId): List<Task> {
        val query = query(where("owner").`is`(friendshipId.toString()))
        return mongoTemplate.find(query, Task::class.java, "tasks")
    }

    override fun cleanUp(friendshipId: FriendshipId, cleanUpAt: Instant): List<ArchivedTask> =
        mongoTemplate.findAllAndRemove(
            query(
                where("owner").`is`(friendshipId.toString())
                    .andOperator(where("completed").`is`(true))
            ), Task::class.java, "tasks"
        )
            .let { it.map { t -> ArchivedTask(task = t, archivedAt = cleanUpAt) } }
            .onEach { at ->
                run {
                    mongoTemplate.save(at, "tasks_archive")
                }
            }.apply {
                eventPublisher.publishEvent(TasksCleanedUp(this.javaClass, friendshipId, this.map { it.task }))
            }

    override fun removeTask(friendshipId: FriendshipId, id: String): Task? = mongoTemplate.findOne(
        query(where("_id").`is`(id).andOperator(where("owner").`is`(friendshipId.toString()))),
        Task::class.java, "tasks"
    )?.apply {
        mongoTemplate.remove(this)
        eventPublisher.publishEvent(TaskRemoved(this.javaClass, friendshipId, this))
    }
}

data class ArchivedTask(
    val id: String? = null,
    val task: Task,
    val archivedAt: Instant
)