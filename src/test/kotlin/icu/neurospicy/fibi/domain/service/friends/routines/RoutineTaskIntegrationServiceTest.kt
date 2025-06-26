package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class RoutineTaskIntegrationServiceTest {

    private val taskHandler: RoutineTaskHandler = mockk(relaxed = true)
    private lateinit var service: RoutineTaskIntegrationService

    @BeforeEach
    fun setup() {
        service = RoutineTaskIntegrationService(taskHandler)
    }

    @Test
    fun `onTaskCompleted delegates to taskHandler`() {
        val event = mockk<TaskCompleted>()
        coJustRun { taskHandler.handleTaskCompleted(event) }

        service.onTaskCompleted(event)

        coVerify { taskHandler.handleTaskCompleted(event) }
    }

    @Test
    fun `onTaskRemoved delegates to taskHandler`() {
        val event = mockk<TaskRemoved>()
        coJustRun { taskHandler.handleTaskRemoved(event) }

        service.onTaskRemoved(event)

        coVerify { taskHandler.handleTaskRemoved(event) }
    }
} 