package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals

class RoutineTemplateValidationTest {

    private val objectMapper = ObjectMapper().registerModule(kotlinModule())
    private val templateLoader = RoutineTemplateLoader(objectMapper)

    @Test
    fun `should load simple hydration routine`() {
        val json = """
        {
          "title": "Simple Hydration Routine",
          "version": "1.0",
          "description": "A basic routine to help you stay hydrated throughout the day",
          "setupSteps": [
            {
              "type": "parameter_request",
              "question": "What time do you wake up?",
              "parameterKey": "wakeUpTime",
              "parameterType": "LOCAL_TIME"
            }
          ],
          "phases": [
            {
              "title": "Daily Hydration",
              "steps": [
                {
                  "type": "message",
                  "message": "Good morning! Start your day with a glass of water.",
                  "timeOfDay": "${'$'}{wakeUpTime}"
                },
                {
                  "type": "action",
                  "message": "Drink a glass of water",
                  "timeOfDay": "${'$'}{wakeUpTime}+PT30M",
                  "expectedDurationMinutes": 2,
                  "expectConfirmation": true
                }
              ]
            }
          ]
        }
        """.trimIndent()

        assertDoesNotThrow {
            val template = templateLoader.parseRoutineTemplate(json)
            assert(template.title == "Simple Hydration Routine")
            assert(template.setupSteps.size == 1)
            assert(template.phases.size == 1)
            assert(template.phases[0].steps.size == 2)
        }
    }

    @Test
    fun `should load neurodivergent sleep optimization routine`() {
        val json = """
        {
          "title": "Neurodivergent Sleep Optimization",
          "version": "1.0",
          "description": "A evidence-based bedtime routine designed for ADHD and autistic adults",
          "setupSteps": [
            {
              "type": "parameter_request",
              "question": "What time do you naturally start feeling sleepy?",
              "parameterKey": "naturalSleepTime",
              "parameterType": "LOCAL_TIME"
            }
          ],
          "phases": [
            {
              "title": "Wind-Down Signal Training",
              "steps": [
                {
                  "type": "message",
                  "message": "🌅 2 hours before natural sleep time: Starting your brain's wind-down signals",
                  "timeOfDay": "${'$'}{naturalSleepTime}-PT2H",
                  "expectedDurationMinutes": 1,
                  "expectConfirmation": true
                }
              ],
              "schedule": "DAILY"
            }
          ],
          "triggers": [
            {
              "condition": {
                "type": "AFTER_PHASE_COMPLETIONS",
                "phaseTitle": "Wind-Down Signal Training",
                "times": 3
              },
              "effect": {
                "type": "SEND_MESSAGE",
                "message": "🧠 Sleep Science: Your brain is learning to recognize wind-down cues!"
              }
            }
          ]
        }
        """.trimIndent()

        assertDoesNotThrow {
            val template = templateLoader.parseRoutineTemplate(json)
            assert(template.title == "Neurodivergent Sleep Optimization")
            assert(template.triggers.size == 1)
            val trigger = template.triggers[0]
            assert(trigger.condition is AfterPhaseCompletions)
            assert(trigger.effect is SendMessage)
        }
    }

    @Test
    fun `should handle complex time expressions`() {
        val json = """
        {
          "title": "Complex Time Expressions Test",
          "version": "1.0",
          "description": "Testing complex time expressions",
          "setupSteps": [
            {
              "type": "parameter_request",
              "question": "What time do you wake up?",
              "parameterKey": "wakeUpTime",
              "parameterType": "LOCAL_TIME"
            }
          ],
          "phases": [
            {
              "title": "Test Phase",
              "steps": [
                {
                  "type": "action",
                  "message": "Complex time expression test",
                  "timeOfDay": "${'$'}{wakeUpTime}+PT2H+PT30M",
                  "expectedDurationMinutes": 5,
                  "expectConfirmation": true
                }
              ]
            }
          ]
        }
        """.trimIndent()

        assertDoesNotThrow {
            val template = templateLoader.parseRoutineTemplate(json)
            assertEquals(
                (template.phases[0].steps[0].timeOfDay as? TimeOfDayExpression)?.expression,
                "${'$'}{wakeUpTime}+PT2H+PT30M"
            )
        }
    }

    @Test
    fun `should handle event-driven triggers`() {
        val json = """
        {
          "title": "Event-Driven Triggers Test",
          "version": "1.0",
          "description": "Testing event-driven triggers",
          "phases": [
            {
              "title": "Test Phase",
              "steps": [
                {
                  "type": "action",
                  "message": "Test action",
                  "timeOfDay": "09:00",
                  "expectedDurationMinutes": 5,
                  "expectConfirmation": true
                }
              ]
            }
          ],
          "triggers": [
            {
              "condition": {
                "type": "AFTER_DURATION",
                "reference": "PHASE_ENTERED",
                "duration": "PT72H"
              },
              "effect": {
                "type": "SEND_MESSAGE",
                "message": "Event-driven trigger test"
              }
            }
          ]
        }
        """.trimIndent()

        assertDoesNotThrow {
            val template = templateLoader.parseRoutineTemplate(json)
            assert(template.triggers.size == 1)
            val trigger = template.triggers[0]
            assert(trigger.condition is AfterDuration)
            val condition = trigger.condition as AfterDuration
            assert(condition.reference == "PHASE_ENTERED")
            assert(condition.duration.toString() == "PT72H")
        }
    }

    @Test
    fun `should handle task creation triggers`() {
        val json = """
        {
  "title": "Neurodivergent Sleep Optimization",
  "version": "1.0",
  "description": "A evidence-based bedtime routine designed for ADHD and autistic adults. Addresses delayed sleep phase, sensory sensitivities, racing thoughts, and circadian rhythm dysregulation.",
  "setupSteps": [
    {
      "type": "parameter_request",
      "question": "What time do you naturally start feeling sleepy? (Don't say what you think you 'should' - when does your brain actually get tired?)",
      "parameterKey": "naturalSleepTime",
      "parameterType": "LOCAL_TIME"
    },
    {
      "type": "parameter_request",
      "question": "What sensory experience helps you feel most calm? (warm bath, weighted blanket, soft music, essential oils, etc.)",
      "parameterKey": "calmingSensory",
      "parameterType": "STRING"
    },
    {
      "type": "parameter_request",
      "question": "What temperature do you sleep best in? (cool, warm, or varies)",
      "parameterKey": "sleepTemperature",
      "parameterType": "STRING"
    },
    {
      "type": "parameter_request",
      "question": "Do you need background noise to sleep or prefer silence? (white noise, fan, music, silence)",
      "parameterKey": "sleepSound",
      "parameterType": "STRING"
    }
  ],
  "phases": [
    {
      "title": "Wind-Down Signal Training",
      "steps": [
        {
          "type": "message",
          "message": "🌅 2 hours before natural sleep time: Starting your brain's wind-down signals",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H",
          "expectedDurationMinutes": 1,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "💡 Dim all lights to 30% or use warm-colored bulbs (this tells your brain to start melatonin production)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H+PT2M",
          "expectedDurationMinutes": 2,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "📱 Digital sunset: Put devices in night mode or use blue light filters",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H+PT5M",
          "expectedDurationMinutes": 2,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🧠 Brain dump: Write down everything on your mind for 5 minutes (external working memory for racing thoughts)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT1H",
          "expectedDurationMinutes": 5,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🛁 Begin your ${'$'}{calmingSensory} routine to signal safety to your nervous system",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT45M",
          "expectedDurationMinutes": 20,
          "expectConfirmation": true
        }
      ],
      "schedule": "DAILY"
    },
    {
      "title": "Sensory Sleep Environment",
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Wind-Down Signal Training",
        "times": 7
      },
      "steps": [
        {
          "type": "message",
          "message": "🌅 2-hour wind-down begins (your brain knows this pattern now!)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H",
          "expectedDurationMinutes": 1,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "💡 Light dimming ritual (melatonin signal)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H+PT2M",
          "expectedDurationMinutes": 2,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "📱 Digital sunset mode",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H+PT5M",
          "expectedDurationMinutes": 2,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🧠 Worry dump session (clear the mental clutter)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT1H",
          "expectedDurationMinutes": 5,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🛁 ${'$'}{calmingSensory} regulation ritual",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT45M",
          "expectedDurationMinutes": 20,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🌡️ Set bedroom to ${'$'}{sleepTemperature} temperature (neurodivergent brains are sensitive to temperature changes)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT30M",
          "expectedDurationMinutes": 3,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🔊 Set up your ${'$'}{sleepSound} environment exactly how your brain needs it",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT25M",
          "expectedDurationMinutes": 3,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🧸 Prepare your sleep comfort items (weighted blanket, fidget toy, whatever helps you feel safe)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT20M",
          "expectedDurationMinutes": 5,
          "expectConfirmation": true
        }
      ],
      "schedule": "DAILY"
    },
    {
      "title": "Complete Sleep Optimization",
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Sensory Sleep Environment",
        "times": 10
      },
      "steps": [
        {
          "type": "message",
          "message": "🌅 Wind-down sequence initiated (your circadian rhythm recognizes this!)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H",
          "expectedDurationMinutes": 1,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "💡 Light dimming (automatic melatonin trigger)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H+PT2M",
          "expectedDurationMinutes": 2,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "📱 Digital sunset (protecting your circadian clock)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT2H+PT5M",
          "expectedDurationMinutes": 2,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🧠 Mental declutter session",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT1H",
          "expectedDurationMinutes": 5,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🛁 ${'$'}{calmingSensory} nervous system regulation",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT45M",
          "expectedDurationMinutes": 20,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🌡️ Temperature optimization for ${'$'}{sleepTemperature} preference",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT30M",
          "expectedDurationMinutes": 3,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🔊 ${'$'}{sleepSound} environment setup",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT25M",
          "expectedDurationMinutes": 3,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🧸 Comfort items arranged for optimal regulation",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT20M",
          "expectedDurationMinutes": 5,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "🧘 Progressive muscle relaxation or gentle stretching (release physical tension)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT12M",
          "expectedDurationMinutes": 7,
          "expectConfirmation": true
        },
        {
          "type": "action",
          "message": "📚 Read something calming or listen to sleep stories (redirect hyperactive thoughts)",
          "timeOfDay": "${'$'}{naturalSleepTime}-PT5M",
          "expectedDurationMinutes": 5,
          "expectConfirmation": true
        },
        {
          "type": "message",
          "message": "😴 Time to rest. Remember: Your neurodivergent brain needs this routine to function optimally tomorrow.",
          "timeOfDay": "${'$'}{naturalSleepTime}",
          "expectedDurationMinutes": 1,
          "expectConfirmation": true
        }
      ],
      "schedule": "DAILY"
    }
  ],
  "triggers": [
    {
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Wind-Down Signal Training",
        "times": 3
      },
      "effect": {
        "type": "SEND_MESSAGE",
        "message": "🧠 Sleep Science: Your brain is learning to recognize wind-down cues! Neurodivergent people often have delayed melatonin production, so these early signals are crucial."
      }
    },
    {
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Wind-Down Signal Training",
        "times": 7
      },
      "effect": {
        "type": "SEND_MESSAGE",
        "message": "🌙 Great progress! Your circadian rhythm is starting to sync with your routine. Ready to optimize your sensory sleep environment?"
      }
    },
    {
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Sensory Sleep Environment",
        "times": 10
      },
      "effect": {
        "type": "SEND_MESSAGE",
        "message": "🎉 Excellent! You've trained your nervous system to recognize bedtime signals AND created a neurodivergent-friendly sleep environment. Ready for the complete protocol?"
      }
    },
    {
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Complete Sleep Optimization",
        "times": 14
      },
      "effect": {
        "type": "SEND_MESSAGE",
        "message": "🏆 Two weeks of optimized sleep! Your neurodivergent brain is getting the rest it needs. You should notice improvements in executive function, emotional regulation, and sensory processing."
      }
    },
    {
      "condition": {
        "type": "AFTER_DURATION",
        "reference": "PHASE_ENTERED",
        "duration": "PT72H"
      },
      "effect": {
        "type": "SEND_MESSAGE",
        "message": "💡 Sleep Tip: Remember, neurodivergent sleep needs are real and valid. Your brain requires this structure to regulate properly. You're doing important self-care work!"
      }
    },
    {
      "condition": {
        "type": "AFTER_PHASE_COMPLETIONS",
        "phaseTitle": "Complete Sleep Optimization",
        "times": 21
      },
      "effect": {
        "type": "CREATE_TASK",
        "taskDescription": "Consider tracking your sleep quality and energy levels to see how your optimized routine is helping your neurodivergent brain function better",
        "parameterKey": "sleepTrackingSetup",
        "expiryDate": "2025-12-31T23:59:59Z"
      }
    }
  ]
} 
        """.trimIndent()

        assertDoesNotThrow {
            val template = templateLoader.parseRoutineTemplate(json)
            assert(template.triggers.size == 6)
            val trigger = template.triggers[0]
            assert(trigger.effect is SendMessage)
            val effect = trigger.effect as SendMessage
            assert(effect.message == "\uD83E\uDDE0 Sleep Science: Your brain is learning to recognize wind-down cues! Neurodivergent people often have delayed melatonin production, so these early signals are crucial.")
        }
    }
} 