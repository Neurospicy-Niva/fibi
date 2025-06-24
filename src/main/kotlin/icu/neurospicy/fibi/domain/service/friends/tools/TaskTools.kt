package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool

class TaskTools(
    private val friendshipId: FriendshipId,
    private val taskRepository: TaskRepository,
) {
    @Tool(description = "List all tasks of the friend.")
    fun listTasks(): List<LlmTask> {
        LOG.debug("Gathering tasks of friend $friendshipId")
        return taskRepository.findByFriendshipId(friendshipId)
            .map {
                LlmTask(
                    it.id!!,
                    it.title,
                    it.description,
                    it.completed,
                    it.completedAt?.toString(),
                    it.createdAt.toString()
                )
            }
            .apply { LOG.info("Found ${this.size} tasks of friend $friendshipId") }
    }

    data class LlmTask(
        val id: String,
        val title: String,
        val description: String?,
        val completed: Boolean,
        val completedAt: String?,
        val createdAt: String
    )

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}