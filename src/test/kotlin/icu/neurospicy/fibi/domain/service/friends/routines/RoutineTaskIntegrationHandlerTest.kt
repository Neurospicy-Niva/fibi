package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class RoutineTaskIntegrationHandlerTest {

    private val taskIntegrationService: RoutineTaskIntegrationService = mockk(relaxed = true)
    private lateinit var handler: RoutineTaskIntegrationHandler

    @BeforeEach
    fun setup() {
        handler = RoutineTaskIntegrationHandler(taskIntegrationService)
    }

    @Test
    fun `onTaskCompleted delegates to taskIntegrationService`() {
        val event = mockk<TaskCompleted>()
        justRun { taskIntegrationService.handleTaskCompleted(event) }

        handler.onTaskCompleted(event)

        verify { taskIntegrationService.handleTaskCompleted(event) }
    }

    @Test
    fun `onTaskRemoved delegates to taskIntegrationService`() {
        val event = mockk<TaskRemoved>()
        justRun { taskIntegrationService.handleTaskRemoved(event) }

        handler.onTaskRemoved(event)

        verify { taskIntegrationService.handleTaskRemoved(event) }
    }
} 