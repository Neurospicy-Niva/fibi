package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.core.io.ClassPathResource

class RoutineTemplateLoaderTest {
    
    @Test
    fun `loads morning planning routine from JSON`() {
        val templateRepository = mockk<RoutineTemplateRepository>()
        val templateSlot = slot<RoutineTemplate>()
        
        every { templateRepository.findById(any()) } returns null
        every { templateRepository.save(capture(templateSlot)) } returnsArgument 0
        
        val loader = RoutineTemplateLoader(templateRepository, ObjectMapper())
        
        // Load the template directly
        val resource = ClassPathResource("morning-planning-routine.example.json")
        loader.javaClass.getDeclaredMethod("loadTemplate", org.springframework.core.io.Resource::class.java).apply {
            isAccessible = true
            invoke(loader, resource)
        }
        
        verify { templateRepository.save(any()) }
        
        val savedTemplate = templateSlot.captured
        assertEquals("Morning Planning Routine", savedTemplate.title)
        assertEquals("1.0", savedTemplate.version)
        assertEquals(2, savedTemplate.setupSteps.size)
        assertEquals(3, savedTemplate.phases.size)
        assertEquals(3, savedTemplate.triggers.size)
        
        // Verify setup steps
        val wakeUpTimeStep = savedTemplate.setupSteps.find { 
            it is ParameterRequestStep && it.parameterKey == "wakeUpTime" 
        } as ParameterRequestStep
        assertEquals(RoutineParameterType.LOCAL_TIME, wakeUpTimeStep.parameterType)
        
        // Verify phases
        val firstPhase = savedTemplate.phases[0]
        assertEquals("Building Morning Calm", firstPhase.title)
        assertNull(firstPhase.condition) // First phase has no condition
        assertEquals(2, firstPhase.steps.size)
        
        val secondPhase = savedTemplate.phases[1]
        assertEquals("Adding Daily Planning", secondPhase.title)
        assertNotNull(secondPhase.condition)
        assertTrue(secondPhase.condition is AfterPhaseCompletions)
        
        // Verify triggers
        val firstTrigger = savedTemplate.triggers[0]
        assertTrue(firstTrigger.condition is AfterPhaseCompletions)
        assertTrue(firstTrigger.effect is SendMessage)
    }
    
    @Test
    fun `loads simple hydration routine from JSON`() {
        val templateRepository = mockk<RoutineTemplateRepository>()
        val templateSlot = slot<RoutineTemplate>()
        
        every { templateRepository.findById(any()) } returns null
        every { templateRepository.save(capture(templateSlot)) } returnsArgument 0
        
        val loader = RoutineTemplateLoader(templateRepository, ObjectMapper())
        
        // Load the template directly
        val resource = ClassPathResource("simple-hydration-routine.json")
        loader.javaClass.getDeclaredMethod("loadTemplate", org.springframework.core.io.Resource::class.java).apply {
            isAccessible = true
            invoke(loader, resource)
        }
        
        verify { templateRepository.save(any()) }
        
        val savedTemplate = templateSlot.captured
        assertEquals("Daily Hydration Reminder", savedTemplate.title)
        assertEquals("1.0", savedTemplate.version)
        assertEquals(1, savedTemplate.setupSteps.size)
        assertEquals(1, savedTemplate.phases.size)
        assertEquals(2, savedTemplate.triggers.size)
        
        // Verify the hydration phase has 4 steps (4 reminders per day)
        val hydrationPhase = savedTemplate.phases[0]
        assertEquals("Hydration Reminders", hydrationPhase.title)
        assertEquals(4, hydrationPhase.steps.size)
        
        // Verify times are correctly parsed
        val morningStep = hydrationPhase.steps[0] as ActionRoutineStep
        assertTrue(morningStep.timeOfDay is TimeOfDayLocalTime)
        assertEquals("09:00", (morningStep.timeOfDay as TimeOfDayLocalTime).time.toString())
    }
    
    @Test
    fun `loads evening wind-down routine from JSON`() {
        val templateRepository = mockk<RoutineTemplateRepository>()
        val templateSlot = slot<RoutineTemplate>()
        
        every { templateRepository.findById(any()) } returns null
        every { templateRepository.save(capture(templateSlot)) } returnsArgument 0
        
        val loader = RoutineTemplateLoader(templateRepository, ObjectMapper())
        
        // Load the template directly
        val resource = ClassPathResource("evening-wind-down-routine.json")
        loader.javaClass.getDeclaredMethod("loadTemplate", org.springframework.core.io.Resource::class.java).apply {
            isAccessible = true
            invoke(loader, resource)
        }
        
        verify { templateRepository.save(any()) }
        
        val savedTemplate = templateSlot.captured
        assertEquals("Evening Wind-Down", savedTemplate.title)
        assertEquals(2, savedTemplate.phases.size)
        
        // Verify parameter references work
        val secondPhase = savedTemplate.phases[1]
        val activityStep = secondPhase.steps.find { 
            it.description.contains("relaxationActivity") 
        } as ActionRoutineStep
        assertTrue(activityStep.message.contains("\${relaxationActivity}"))
        
        // Verify phase progression
        assertTrue(secondPhase.condition is AfterDays)
        assertEquals(3, (secondPhase.condition as AfterDays).value)
    }
} 