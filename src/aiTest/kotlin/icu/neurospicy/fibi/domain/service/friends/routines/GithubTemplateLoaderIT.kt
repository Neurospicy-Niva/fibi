package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.BaseAIT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class GithubTemplateLoaderIT : BaseAIT() {

    @Autowired
    private lateinit var routineTemplateService: RoutineTemplateService

    @Autowired
    private lateinit var routineTemplateLoader: RoutineTemplateLoader

    @Autowired
    private lateinit var githubRoutineTemplateLoader: GithubRoutineTemplateLoader

    @Autowired
    private lateinit var templateRepository: RoutineTemplateRepository

    @Autowired
    private lateinit var routineRepository: RoutineRepository


    @Nested
    inner class `GitHub Routine Loading Integration` {

        @Test
        fun `should successfully load and parse routines from GitHub repository`() {
            // Given: Real GitHub repository URL
            val githubUrl =
                "https://api.github.com/repos/Neurospicy-Niva/routines/contents/examples?ref=feature/update-to-new-routine-schema"

            // When: Load templates from GitHub
            val templates = routineTemplateService.loadTemplatesFromGithubUrl(githubUrl)

            // Then: Verify that routines were successfully loaded
            assertThat(templates).isNotEmpty()
            println("Successfully loaded ${templates.size} routine templates from GitHub")

            // Verify each template has valid structure
            templates.forEach { template ->
                println("Validating template: ${template.title} (${template.templateId})")

                assertThat(template.title).isNotEmpty()
                assertThat(template.version).isNotEmpty()
                assertThat(template.description).isNotEmpty()
                assertThat(template.phases).isNotEmpty()

                // Verify each phase has valid structure
                template.phases.forEach { phase ->
                    assertThat(phase.title).isNotEmpty()
                    assertThat(phase.steps).isNotEmpty()
                    println("  - Phase: ${phase.title} with ${phase.steps.size} steps")

                    // Verify each step has valid structure
                    phase.steps.forEach { step ->
                        when (step) {
                            is ActionRoutineStep -> {
                                assertThat(step.message).isNotEmpty()
                            }

                            is MessageRoutineStep -> {
                                assertThat(step.message).isNotEmpty()
                            }

                            is ParameterRequestStep -> {
                                assertThat(step.question).isNotEmpty()
                                assertThat(step.parameterKey).isNotEmpty()
                            }
                        }
                    }
                }

                // Verify triggers if present
                if (template.triggers.isNotEmpty()) {
                    println("  - Has ${template.triggers.size} triggers")
                    template.triggers.forEach { trigger ->
                        assertThat(trigger.condition).isNotNull()
                        assertThat(trigger.effect).isNotNull()
                    }
                }
            }
        }

        @Test
        fun `should handle all trigger types from GitHub routines`() {
            // Given: Load all templates from GitHub
            val templates = routineTemplateService.loadTemplatesFromGithubUrl(
                "https://api.github.com/repos/Neurospicy-Niva/routines/contents/examples?ref=main"
            )

            // Then: Verify that templates with triggers are properly parsed
            val templatesWithTriggers = templates.filter { it.triggers.isNotEmpty() }
            assertThat(templatesWithTriggers).isNotEmpty()

            println("Found ${templatesWithTriggers.size} templates with triggers")

            templatesWithTriggers.forEach { template ->
                println("Validating triggers for: ${template.title}")

                // Verify time-based triggers
                val timeTriggers = template.triggers.filter { it.condition is AtTimeExpression }
                if (timeTriggers.isNotEmpty()) {
                    println("  - ${timeTriggers.size} time-based triggers")
                    timeTriggers.forEach { trigger ->
                        val condition = trigger.condition as AtTimeExpression
                        assertThat(condition.timeExpression).isNotEmpty()
                    }
                }

                // Verify phase completion triggers
                val phaseTriggers = template.triggers.filter { it.condition is AfterPhaseCompletions }
                if (phaseTriggers.isNotEmpty()) {
                    println("  - ${phaseTriggers.size} phase completion triggers")
                    phaseTriggers.forEach { trigger ->
                        val condition = trigger.condition as AfterPhaseCompletions
                        assertThat(condition.phaseId).isNotNull()
                        assertThat(condition.times).isPositive()
                    }
                }

                // Verify day-based triggers
                val dayTriggers = template.triggers.filter { it.condition is AfterDays }
                if (dayTriggers.isNotEmpty()) {
                    println("  - ${dayTriggers.size} day-based triggers")
                    dayTriggers.forEach { trigger ->
                        val condition = trigger.condition as AfterDays
                        assertThat(condition.value).isPositive()
                    }
                }

                // Verify duration-based triggers
                val durationTriggers = template.triggers.filter { it.condition is AfterDuration }
                if (durationTriggers.isNotEmpty()) {
                    println("  - ${durationTriggers.size} duration-based triggers")
                    durationTriggers.forEach { trigger ->
                        val condition = trigger.condition as AfterDuration
                        assertThat(condition.duration).isNotNull()
                    }
                }

                // Verify effects
                template.triggers.forEach { trigger ->
                    when (trigger.effect) {
                        is SendMessage -> {
                            assertThat((trigger.effect as SendMessage).message).isNotEmpty()
                        }

                        is CreateTask -> {
                            assertThat((trigger.effect as CreateTask).taskDescription).isNotEmpty()
                            assertThat((trigger.effect as CreateTask).parameterKey).isNotEmpty()
                        }
                    }
                }
            }
        }

        @Test
        fun `should save loaded templates to repository`() {
            // Given: Load templates from GitHub
            val templates = routineTemplateService.loadTemplatesFromGithubUrl(
                "https://api.github.com/repos/Neurospicy-Niva/routines/contents/examples?ref=main"
            )

            assertThat(templates).isNotEmpty()

            // When: Save templates to repository
            templates.forEach { template ->
                templateRepository.save(template)
            }

            // Then: Verify templates are saved and can be retrieved
            templates.forEach { template ->
                val savedTemplate = templateRepository.findById(template.templateId)
                assertThat(savedTemplate).isNotNull()
                assertThat(savedTemplate!!.title).isEqualTo(template.title)
                assertThat(savedTemplate.version).isEqualTo(template.version)
                assertThat(savedTemplate.phases).hasSize(template.phases.size)
            }

            println("Successfully saved ${templates.size} templates to repository")
        }
    }
}