package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStarted
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.LocalTime

class SetupRoutineTest {
    @Test
    fun `creates a routine instance from template and parameters`() {
        //given
        val routineTemplateId = RoutineTemplateId.forTitleVersion("focus-start", "1.0")

        val phase = RoutinePhase(
            title = "Startphase", condition = AfterDays(3), steps = listOf(
                ActionRoutineStep(
                    message = "Drink a glass of water.",
                    timeOfDay = TimeOfDayReference("\${wakeUpTime}"),
                    expectConfirmation = true
                )
            )
        )

        val template = templateWith(
            routineTemplateId, phase = phase, setupSteps = listOf(
                ParameterRequestStep(
                    question = "When do you want to wake up?",
                    parameterKey = "wakeUpTime",
                    parameterType = RoutineParameterType.LOCAL_TIME,
                )
            )
        )
        val mockTemplateRepository = mockk<RoutineTemplateRepository>()
        every { mockTemplateRepository.findById(any()) } returns template
        val mockRoutineRepository = mockk<RoutineRepository>()
        every { mockRoutineRepository.save(any()) } returns Unit

        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val routinePhaseService = mockk<RoutinePhaseService>()
        justRun { routinePhaseService.handleRoutineStart(any()) }
        val setupRoutine = SetupRoutine(
            mockTemplateRepository, mockRoutineRepository, mockk(relaxed = true), eventPublisher,
            routinePhaseService,
        )
        val parameterWakeUpTime = LocalTime.of(7, 0)
        val friendshipId = FriendshipId()
        // when
        val result = setupRoutine.execute(
            routineTemplateId = routineTemplateId,
            friendshipId = friendshipId,
            parameters = mapOf("wakeUpTime" to parameterWakeUpTime)
        )
        //then
        assertEquals(routineTemplateId, result.templateId)
        assertEquals(friendshipId, result.friendshipId)
        assertEquals(parameterWakeUpTime, result.parameters["wakeUpTime"]?.value)
        assertTrue(result.progress.iterations.first().completedSteps.isEmpty())
        verify { mockRoutineRepository.save(result) }
        verify { eventPublisher.publishEvent(match { it is RoutineStarted && it.instanceId == result.instanceId }) }
        verify { routinePhaseService.handleRoutineStart(result) }
    }


    @Test
    fun `activates triggers with static duration`() {
        //given
        val routineTemplateId = RoutineTemplateId.forTitleVersion("focus-static", "1.0")
        val timeBasedTrigger = RoutineTrigger(
            condition = AfterDuration(reference = "ROUTINE_STARTED", duration = Duration.ofDays(20)),
            effect = SendMessage("You did it!")
        )
        val template = templateWith(routineTemplateId, trigger = timeBasedTrigger)

        val mockTemplateRepository = mockk<RoutineTemplateRepository>()
        every { mockTemplateRepository.findById(any()) } returns template
        val mockRoutineRepository = mockk<RoutineRepository>(relaxed = true)
        val mockRoutineScheduler = mockk<RoutineScheduler>()
        every { mockRoutineScheduler.scheduleTrigger(any(), any()) } returns Unit

        val routinePhaseService = mockk<RoutinePhaseService>()
        justRun { routinePhaseService.handleRoutineStart(any()) }
        val setupRoutine = SetupRoutine(
            mockTemplateRepository,
            mockRoutineRepository,
            mockRoutineScheduler,
            mockk(relaxed = true),
            routinePhaseService,
        )
        //when
        val result = setupRoutine.execute(
            routineTemplateId = routineTemplateId, friendshipId = FriendshipId(), parameters = emptyMap()
        )

        verify { mockRoutineScheduler.scheduleTrigger(result, timeBasedTrigger) }
        verify(atLeast = 0, atMost = 0) { mockRoutineScheduler.scheduleTrigger(result, not(timeBasedTrigger)) }
        verify { routinePhaseService.handleRoutineStart(result) }
    }

    @Test
    fun `activates triggers with parameter-based duration`() {
        val routineTemplateId = RoutineTemplateId.forTitleVersion("focus-params", "1.0")
        val trigger = RoutineTrigger(
            condition = AfterDuration(reference = "wakeUpTime", duration = Duration.ofMinutes(15)),
            effect = SendMessage("Time to reflect!")
        )

        val template = templateWith(routineTemplateId, trigger = trigger)
        val mockTemplateRepository = mockk<RoutineTemplateRepository>()
        every { mockTemplateRepository.findById(any()) } returns template
        val mockRoutineRepository = mockk<RoutineRepository>(relaxed = true)
        val mockRoutineScheduler = mockk<RoutineScheduler>()
        every { mockRoutineScheduler.scheduleTrigger(any(), any()) } returns Unit

        val routinePhaseService = mockk<RoutinePhaseService>()
        justRun { routinePhaseService.handleRoutineStart(any()) }
        val setupRoutine = SetupRoutine(
            mockTemplateRepository,
            mockRoutineRepository,
            mockRoutineScheduler,
            mockk(relaxed = true),
            routinePhaseService,
        )
        val result = setupRoutine.execute(
            routineTemplateId = routineTemplateId,
            friendshipId = FriendshipId(),
            parameters = mapOf("wakeUpTime" to LocalTime.of(6, 30))
        )

        verify { mockRoutineScheduler.scheduleTrigger(result, trigger) }
        verify(atLeast = 0, atMost = 0) { mockRoutineScheduler.scheduleTrigger(result, not(trigger)) }
        verify { routinePhaseService.handleRoutineStart(result) }
    }

    private fun templateWith(
        routineTemplateId: RoutineTemplateId,
        phase: RoutinePhase = RoutinePhase(
            title = "Start", condition = AfterDays(1), steps = listOf(MessageRoutineStep("Default step"))
        ),
        setupSteps: List<RoutineStep> = emptyList(),
        trigger: RoutineTrigger? = null,
    ): RoutineTemplate = RoutineTemplate(
        _id = "db-id",
        templateId = routineTemplateId,
        title = "focus-start",
        version = "1.0",
        description = "Simple focus routine",
        setupSteps = setupSteps,
        phases = listOf(phase),
        triggers = if (trigger != null) listOf(trigger) else emptyList()
    )

    @Test
    fun `throws exception if template not found`() {
        val routineTemplateId = RoutineTemplateId.forTitleVersion("not-found", "1.0")
        val mockTemplateRepository = mockk<RoutineTemplateRepository>()
        every { mockTemplateRepository.findById(routineTemplateId) } returns null

        val setupRoutine = SetupRoutine(
            templates = mockTemplateRepository,
            instances = mockk(relaxed = true),
            scheduler = mockk(),
            eventPublisher = mockk(),
            mockk(),
        )

        assertThrows<NoSuchElementException> {
            setupRoutine.execute(routineTemplateId, FriendshipId(), parameters = emptyMap())
        }
    }

    @Test
    fun `throws exception if required setup parameter missing`() {
        val routineTemplateId = RoutineTemplateId.forTitleVersion("missing-param", "1.0")
        val template = RoutineTemplate(
            _id = "db-id",
            templateId = routineTemplateId,
            title = "missing-param",
            version = "1.0",
            description = "Missing parameter test",
            setupSteps = listOf(
                ParameterRequestStep(
                    question = "When do you want to wake up?",
                    parameterKey = "wakeUpTime",
                    parameterType = RoutineParameterType.LOCAL_TIME,
                )
            ),
            phases = listOf(
                RoutinePhase(
                    title = "Start", condition = AfterDays(1), steps = listOf(MessageRoutineStep("Default step"))
                )
            )
        )

        val setupRoutine = SetupRoutine(
            templates = mockk {
                every { findById(routineTemplateId) } returns template
            },
            instances = mockk(relaxed = true),
            scheduler = mockk(),
            eventPublisher = mockk(),
            mockk(),
        )

        val parametersWithoutRequiredWakeUpTime = emptyMap<String, Any>()
        val ex = assertThrows<IllegalArgumentException> {
            setupRoutine.execute(routineTemplateId, FriendshipId(), parameters = parametersWithoutRequiredWakeUpTime)
        }
        assertTrue(ex.message!!.contains("wakeUpTime"))
    }
}