package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Handles integration between routine instances and the task management system.
 * Processes task completion and removal events that affect routine progress.
 */
@Service
class RoutineTaskIntegrationService(
    private val taskHandler: RoutineTaskHandler,
) {
    @EventListener
    @Async
    fun onTaskCompleted(event: TaskCompleted) {
        taskHandler.handleTaskCompleted(event)
    }

    @EventListener
    @Async
    fun onTaskRemoved(event: TaskRemoved) {
        taskHandler.handleTaskRemoved(event)
    }
} 