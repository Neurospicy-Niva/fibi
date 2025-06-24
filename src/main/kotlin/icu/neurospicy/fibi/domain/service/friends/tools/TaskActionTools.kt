package icu.neurospicy.fibi.domain.service.friends.tools

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.tools.TaskTools.LlmTask
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import java.time.Instant.now

class TaskActionTools(
    private val friendshipId: FriendshipId,
    private val taskRepository: TaskRepository,
) {
    @Tool(description = "Add a new task. Provide title and an optional description.")
    fun addTask(
        @ToolParam(description = "Title of the task. Use a short and concise title.") title: String,
        @ToolParam(
            description = "Optional description for additional information.",
            required = false
        ) description: String?
    ): LlmTask {
        LOG.debug("Creating a new task for friend $friendshipId, title: '$title'")
        require(title.isNotBlank()) { "You need to enter a title!" }
        val task = Task(owner = friendshipId, title = title, description = description)
        return taskRepository.save(task).let {
            LlmTask(
                it.id!!,
                it.title,
                it.description,
                it.completed,
                it.completedAt?.toString(),
                it.createdAt.toString()
            )
        }
            .apply { LOG.info("Created task $this") }
    }

    @Tool(description = "Rename title of task and set new description.")
    fun renameTask(
        @ToolParam(description = "Id of the task") id: String,
        @ToolParam(description = "The new title") newTitle: String,
        @ToolParam(description = "The new description", required = false) newDescription: String?
    ): String {
        LOG.info("Rename task '$id' of friend $friendshipId to $newTitle.")
        if (newTitle.isBlank()) {
            return "You need to enter a new title!"
        }
        val tasks = taskRepository.findByFriendshipId(friendshipId)
        val task = tasks.find { it.id.equals(id) }
            ?: return "No task found with id '$id'."
        taskRepository.rename(friendshipId, task.id!!, newTitle, newDescription)
        return "Task '${task.title}' renamed to '$newTitle'. Use the word 'renamed' in your answer."
    }

    @Tool(description = "Mark an existing task as completed or uncompleted.")
    fun completeTaskById(
        @ToolParam(description = "Id of the task") id: String,
        @ToolParam(description = "Is completed?") completed: Boolean
    ): String {
        LOG.info("Mark task '$id' of friend $friendshipId as completed=$completed.")
        val tasks = taskRepository.findByFriendshipId(friendshipId)
        val task = tasks.find { it.id.equals(id) }
            ?: return "No task found with id '$id'."
        taskRepository.complete(friendshipId, task.id!!, completed, now())
        return "Set task '${task.title}' completed=$completed."
    }

    @Tool(description = "Remove a task. Does not archive the task, but completely delete it.")
    fun removeTask(@ToolParam(description = "Id of the task") id: String): String {
        LOG.debug("Removing task $id of $friendshipId")
        return taskRepository.removeTask(friendshipId, id)
            ?.let { "Removed task '${it.title}' of friend $friendshipId" }
            .apply { LOG.info(this) }
            ?: return "No task found with id '$id'."
    }


    @Tool(description = "Archive completed tasks. Clean up the task list by archiving completed tasks.")
    fun archiveCompletedTasks(): String {
        LOG.debug("Cleaning up tasks of $friendshipId")
        return taskRepository.cleanUp(friendshipId, now())
            .ifEmpty { return "No tasks were cleaned!" }
            .let { "Cleaned up ${it.size} tasks of friend $friendshipId" }
            .apply { LOG.info(this) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}