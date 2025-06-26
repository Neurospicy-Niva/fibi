package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Handles task-related events that affect routine instances.
 * Delegates to the task integration service for business logic.
 */
@Service
class RoutineTaskIntegrationHandler(
    private val taskIntegrationService: RoutineTaskIntegrationService,
) {
    @EventListener
    @Async
    fun onTaskCompleted(event: TaskCompleted) {
        taskIntegrationService.handleTaskCompleted(event)
    }

    @EventListener
    @Async
    fun onTaskRemoved(event: TaskRemoved) {
        taskIntegrationService.handleTaskRemoved(event)
    }
} 