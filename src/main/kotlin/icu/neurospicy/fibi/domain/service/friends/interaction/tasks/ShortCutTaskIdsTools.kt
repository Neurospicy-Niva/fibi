package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.domain.model.Task
import org.springframework.ai.tool.annotation.Tool

class ShortCutTaskIdsTools(private val tasks: List<Task>) {
    @Tool(description = "Get ids of all tasks", returnDirect = true)
    fun listAllTaskIds(): String {
        return tasks.joinToString { it.id!! }
    }

    @Tool(description = "Get ids of complete tasks", returnDirect = true)
    fun listCompleteTaskIds(): String {
        return tasks.filter { it.completed }.joinToString { it.id!! }
    }

    @Tool(description = "Get ids of ongoing tasks", returnDirect = true)
    fun listOngoingTaskIds(): String {
        return tasks.filter { !it.completed }.joinToString { it.id!! }
    }
}