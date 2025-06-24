package icu.neurospicy.fibi.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "tasks")
data class Task(
    @Id
    val id: String? = null,
    val owner: FriendshipId,
    val title: String,
    val description: String? = null,
    val completed: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val lastModifiedAt: Instant = Instant.now()
) {

    fun update(
        title: String = this.title,
        description: String? = this.description,
        completed: Boolean = this.completed,
        completedAt: Instant? = this.completedAt,
        lastModifiedAt: Instant = Instant.now()
    ): Task {
        return this.copy(
            title = title,
            description = description,
            completed = completed,
            completedAt = completedAt,
            lastModifiedAt = lastModifiedAt
        )
    }

    fun complete(completedAt: Instant? = null): Task {
        val now = Instant.now()
        return this.copy(
            completed = true,
            completedAt = completedAt ?: now,
            lastModifiedAt = completedAt ?: now
        )
    }

    fun incomplete(lastModifiedAt: Instant = Instant.now()): Task {
        return this.copy(
            completed = false,
            completedAt = null,
            lastModifiedAt = lastModifiedAt
        )
    }
}