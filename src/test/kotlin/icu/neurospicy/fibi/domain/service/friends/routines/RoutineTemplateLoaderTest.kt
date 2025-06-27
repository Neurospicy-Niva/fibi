package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RoutineTemplateLoaderTest {

    private val objectMapper = ObjectMapper().registerModule(kotlinModule())
    private val routineTemplateLoader = RoutineTemplateLoader(objectMapper)

    @Test
    fun `parseRoutineTemplate should parse morning planning routine correctly`() {
        // given
        val json = """
        {
          "title": "Morning Planning Routine",
          "version": "1.0",
          "description": "A structured morning routine that builds from meditation to full planning",
          "setupSteps": [
            {
              "type": "parameter_request",
              "parameterKey": "wakeUpTime",
              "question": "What time do you usually wake up?",
              "parameterType": "LOCAL_TIME"
            }
          ],
          "phases": [
            {
              "title": "Building Morning Calm",
              "condition": {
                "type": "AFTER_PARAMETER_SET",
                "parameterKey": "wakeUpTime"
              },
              "steps": [
                {
                  "type": "message",
                  "message": "Good morning! Let's start with some mindful breathing.",
                  "timeOfDay": "${'$'}{wakeUpTime}"
                }
              ],
              "schedule": "DAILY"
            }
          ],
          "triggers": [
            {
              "condition": {
                "type": "AFTER_DAYS",
                "value": 7
              },
              "effect": {
                "type": "SEND_MESSAGE",
                "message": "Great job completing your first week!"
              }
            }
          ]
        }
        """.trimIndent()

        // when
        val result = routineTemplateLoader.parseRoutineTemplate(json)

        // then
        assertNotNull(result)
        assertEquals("Morning Planning Routine", result.title)
        assertEquals("1.0", result.version)
        assertEquals("A structured morning routine that builds from meditation to full planning", result.description)
        assertEquals(1, result.setupSteps.size)
        assertEquals(1, result.phases.size)
        assertEquals(1, result.triggers.size)
    }

    @Test
    fun `parseRoutineTemplate should parse simple hydration routine correctly`() {
        // given  
        val json = """
        {
          "title": "Simple Hydration Routine",
          "version": "1.0",
          "description": "Daily water intake reminders",
          "phases": [
            {
              "title": "Hydration Reminders",
              "steps": [
                {
                  "type": "message",
                  "message": "Time to drink water!",
                  "timeOfDay": "09:00"
                }
              ],
              "schedule": "DAILY"
            }
          ],
          "triggers": []
        }
        """.trimIndent()

        // when
        val result = routineTemplateLoader.parseRoutineTemplate(json)

        // then
        assertNotNull(result)
        assertEquals("Simple Hydration Routine", result.title)
        assertEquals("1.0", result.version)
        assertEquals("Daily water intake reminders", result.description)
        assertEquals(0, result.setupSteps.size)
        assertEquals(1, result.phases.size)
        assertEquals(0, result.triggers.size)

        val phase = result.phases[0]
        assertEquals("Hydration Reminders", phase.title)
        assertEquals(1, phase.steps.size)

        val step = phase.steps[0] as MessageRoutineStep
        assertEquals("Time to drink water!", step.message)
        val timeOfDay = step.timeOfDay as TimeOfDayLocalTime
        assertEquals("09:00", timeOfDay.time.toString())
    }

    @Test  
    fun `parseRoutineTemplate should parse complex routine with all step types correctly`() {
        // given
        val json = """
        {
          "title": "Complex Evening Routine",
          "version": "2.0",
          "description": "Complete evening wind-down routine",
          "setupSteps": [
            {
              "type": "parameter_request",
              "parameterKey": "windDownTime",
              "question": "What time do you want to start winding down?",
              "parameterType": "LOCAL_TIME"
            }
          ],
          "phases": [
            {
              "title": "Wind Down Phase",
              "condition": {
                "type": "AFTER_PHASE_COMPLETIONS",
                "phaseTitle": "previous-phase",
                "times": 3
              },
              "steps": [
                {
                  "type": "action",
                  "action": "Dim the lights and put away devices",
                  "timeOfDay": "${'$'}{windDownTime}",
                  "expectConfirmation": true,
                  "expectedDurationMinutes": 10
                },
                {
                  "type": "message",
                  "message": "Take deep breaths and relax"
                }
              ],
              "schedule": "DAILY"
            }
          ],
          "triggers": [
            {
              "condition": {
                "type": "AFTER_DURATION",
                "duration": "PT30M",
                "reference": "windDownTime"
              },
              "effect": {
                "type": "CREATE_TASK",
                "taskDescription": "Prepare bedroom for sleep",
                "parameterKey": "bedReady",
                "expiryDate": "2025-12-31T23:59:59Z"
              }
            }
          ]
        }
        """.trimIndent()

        // when
        val result = routineTemplateLoader.parseRoutineTemplate(json)

        // then
        assertNotNull(result)
        assertEquals("Complex Evening Routine", result.title)
        assertEquals("2.0", result.version)
        assertEquals("Complete evening wind-down routine", result.description)
        assertEquals(1, result.setupSteps.size)
        assertEquals(1, result.phases.size)
        assertEquals(1, result.triggers.size)

        // Verify setup step
        val setupStep = result.setupSteps[0] as ParameterRequestStep
        assertEquals("windDownTime", setupStep.parameterKey)
        assertEquals("What time do you want to start winding down?", setupStep.question)
        assertEquals(RoutineParameterType.LOCAL_TIME, setupStep.parameterType)

        // Verify phase
        val phase = result.phases[0]
        assertEquals("Wind Down Phase", phase.title)
        assertEquals(2, phase.steps.size)

        // Verify action step
        val actionStep = phase.steps[0] as ActionRoutineStep
        assertEquals("Dim the lights and put away devices", actionStep.message)
        assertEquals(true, actionStep.expectConfirmation)
        assertEquals(10, actionStep.expectedDurationMinutes)
        val timeOfDay = actionStep.timeOfDay as TimeOfDayReference
        assertEquals("windDownTime", timeOfDay.reference)

        // Verify message step
        val messageStep = phase.steps[1] as MessageRoutineStep
        assertEquals("Take deep breaths and relax", messageStep.message)

        // Verify trigger
        val trigger = result.triggers[0]
        val condition = trigger.condition as AfterDuration
        assertEquals("PT30M", condition.duration.toString())
        assertEquals("windDownTime", condition.reference)

        val effect = trigger.effect as CreateTask
        assertEquals("Prepare bedroom for sleep", effect.taskDescription)
        assertEquals("bedReady", effect.parameterKey)
    }
} 