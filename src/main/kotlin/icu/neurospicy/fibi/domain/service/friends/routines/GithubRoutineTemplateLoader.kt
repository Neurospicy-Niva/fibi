package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Loads routine templates from the GitHub repository at startup and on schedule.
 * 
 * This service fetches routine templates from the public GitHub repository
 * https://github.com/Neurospicy-Niva/routines and loads them into the local
 * routine template repository. It runs at startup and daily at 3 AM to check
 * for updates.
 */
@Service
class GithubRoutineTemplateLoader(
    private val templateRepository: RoutineTemplateRepository,
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate,
    @Value("\${github.routines.repository.url:https://api.github.com/repos/Neurospicy-Niva/routines/contents}")
    private val githubApiUrl: String
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(GithubRoutineTemplateLoader::class.java)
        private const val ROUTINE_FILE_PATTERN = "routine"
    }

    /**
     * Load templates at application startup
     */
    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.info("Loading routine templates from GitHub at startup...")
        loadTemplatesFromGithub()
    }

    /**
     * Load templates daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun loadTemplatesScheduled() {
        LOG.info("Loading routine templates from GitHub (scheduled)...")
        loadTemplatesFromGithub()
    }

    /**
     * Load templates from GitHub repository
     */
    fun loadTemplatesFromGithub() {
        try {
            // Get list of files from GitHub API
            val files = fetchRepositoryContents()
            
            files.filter { it.isRoutineFile() }
                .forEach { file ->
                    try {
                        val template = loadFromUrl(file.downloadUrl)
                        if (template != null) {
                            // Check if template already exists to avoid duplicates
                            val existingTemplate = templateRepository.findById(template.templateId)
                            if (existingTemplate == null) {
                                templateRepository.save(template)
                                LOG.info("Loaded new routine template from GitHub: ${template.title}")
                            } else {
                                LOG.debug("Template already exists, skipping: ${template.title}")
                            }
                        }
                    } catch (e: Exception) {
                        LOG.error("Failed to load routine template from ${file.name}: ${e.message}", e)
                    }
                }
            
            LOG.info("Completed loading routine templates from GitHub")
            
        } catch (e: Exception) {
            LOG.error("Failed to fetch routine templates from GitHub: ${e.message}", e)
        }
    }

    /**
     * Load a single routine template from URL
     */
    fun loadFromUrl(url: String): RoutineTemplate? {
        return try {
            val headers = HttpHeaders()
            headers.set("Accept", "application/vnd.github.v3.raw")
            val entity = HttpEntity<String>(headers)
            
            val response = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
            val jsonContent = response.body ?: return null
            
            parseRoutineTemplate(jsonContent)
        } catch (e: Exception) {
            LOG.error("Failed to load routine template from URL $url: ${e.message}", e)
            null
        }
    }

    /**
     * Fetch repository contents from GitHub API
     */
    private fun fetchRepositoryContents(): List<GitHubFile> {
        return try {
            val response = restTemplate.getForObject(githubApiUrl, Array<GitHubFile>::class.java)
            response?.toList() ?: emptyList()
        } catch (e: Exception) {
            LOG.error("Failed to fetch repository contents: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Parse JSON content into RoutineTemplate
     */
    private fun parseRoutineTemplate(jsonContent: String): RoutineTemplate? {
        return try {
            val jsonNode = objectMapper.readTree(jsonContent)
            
            val title = jsonNode.get("title")?.asText() ?: return null
            val version = jsonNode.get("version")?.asText() ?: "1.0"
            val templateId = RoutineTemplateId.forTitleVersion(jsonNode.get("templateId")?.asText() ?: title, version)
            val description = jsonNode.get("description")?.asText() ?: ""
            
            val setupSteps = parseSetupSteps(jsonNode.get("setupSteps"))
            val phases = parsePhases(jsonNode.get("phases"))
            val triggers = parseTriggers(jsonNode.get("triggers"))
            
            RoutineTemplate(
                title = title,
                version = version,
                templateId = templateId,
                description = description,
                setupSteps = setupSteps,
                phases = phases,
                triggers = triggers
            )
        } catch (e: Exception) {
            LOG.error("Failed to parse routine template JSON: ${e.message}", e)
            null
        }
    }

    /**
     * Parse setup steps from JSON
     */
    private fun parseSetupSteps(setupStepsNode: JsonNode?): List<RoutineStep> {
        if (setupStepsNode == null || !setupStepsNode.isArray) return emptyList()
        
        return setupStepsNode.mapNotNull { stepNode ->
            parseStep(stepNode)
        }
    }

    /**
     * Parse phases from JSON
     */
    private fun parsePhases(phasesNode: JsonNode?): List<RoutinePhase> {
        if (phasesNode == null || !phasesNode.isArray) return emptyList()
        
        return phasesNode.mapNotNull { phaseNode ->
            try {
                val title = phaseNode.get("title")?.asText() ?: return@mapNotNull null
                val id = RoutinePhaseId.forTitle(phaseNode.get("phaseId")?.asText() ?: title)
                val condition = parseCondition(phaseNode.get("condition"))
                val steps = parseSteps(phaseNode.get("steps"))
                val schedule = parseSchedule(phaseNode.get("schedule")?.asText())
                
                RoutinePhase(
                    title = title,
                    id = id,
                    condition = condition,
                    steps = steps,
                    schedule = schedule
                )
            } catch (e: Exception) {
                LOG.error("Failed to parse phase: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Parse steps from JSON
     */
    private fun parseSteps(stepsNode: JsonNode?): List<RoutineStep> {
        if (stepsNode == null || !stepsNode.isArray) return emptyList()
        
        return stepsNode.mapNotNull { stepNode ->
            parseStep(stepNode)
        }
    }

    /**
     * Parse a single step from JSON
     */
    private fun parseStep(stepNode: JsonNode): RoutineStep? {
        return try {
            val type = stepNode.get("type")?.asText() ?: return null
            
            when (type) {
                "parameter_request" -> {
                    val parameterKey = stepNode.get("parameterKey")?.asText() ?: return null
                    val question = stepNode.get("question")?.asText() ?: ""
                    val parameterType = RoutineParameterType.valueOf(
                        stepNode.get("parameterType")?.asText() ?: "STRING"
                    )
                    val timeOfDay = parseTimeOfDay(stepNode.get("timeOfDay"))
                    
                    ParameterRequestStep(
                        parameterKey = parameterKey,
                        question = question,
                        parameterType = parameterType,
                        timeOfDay = timeOfDay
                    )
                }
                "action" -> {
                    val action = stepNode.get("action")?.asText() ?: return null
                    val timeOfDay = parseTimeOfDay(stepNode.get("timeOfDay"))
                    
                    ActionRoutineStep(
                        message = action,
                        timeOfDay = timeOfDay
                    )
                }
                "message" -> {
                    val message = stepNode.get("message")?.asText() ?: return null
                    val timeOfDay = parseTimeOfDay(stepNode.get("timeOfDay"))
                    
                    MessageRoutineStep(
                        message = message,
                        timeOfDay = timeOfDay
                    )
                }
                else -> {
                    LOG.warn("Unknown step type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse step: ${e.message}", e)
            null
        }
    }

    /**
     * Parse time of day from JSON
     */
    private fun parseTimeOfDay(timeNode: JsonNode?): TimeOfDay? {
        if (timeNode == null) return null
        
        return try {
            val timeString = timeNode.asText()
            if (timeString.contains("\${")) {
                // Parameter reference like "${wakeUpTime}"
                val parameterName = timeString.removePrefix("\${").removeSuffix("}")
                TimeOfDayReference(parameterName)
            } else {
                // Fixed time like "07:00"
                val time = LocalTime.parse(timeString)
                TimeOfDayLocalTime(time)
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse time of day: ${e.message}", e)
            null
        }
    }

    /**
     * Parse trigger condition from JSON
     */
    private fun parseCondition(conditionNode: JsonNode?): TriggerCondition? {
        if (conditionNode == null) return null
        
        return try {
            val type = conditionNode.get("type")?.asText() ?: return null
            
            when (type) {
                "AFTER_DAYS" -> {
                    val value = conditionNode.get("value")?.asInt() ?: return null
                    AfterDays(value)
                }
                "AFTER_PHASE_COMPLETIONS" -> {
                    val value = conditionNode.get("value")?.asInt() ?: return null
                    val phaseTitle = conditionNode.get("phaseTitle")?.asText() ?: return null
                    val phaseId = RoutinePhaseId.forTitle(phaseTitle)
                    AfterPhaseCompletions(phaseId, value)
                }
                "AFTER_DURATION" -> {
                    val duration = conditionNode.get("duration")?.asText()?.let { Duration.parse(it) } ?: return null
                    val reference = conditionNode.get("reference")?.asText()
                    AfterDuration(reference, duration)
                }
                "AFTER_EVENT" -> {
                    val eventString = conditionNode.get("event")?.asText() ?: return null
                    val eventType = RoutineAnchorEvent.valueOf(eventString)
                    val phaseTitle = conditionNode.get("phaseTitle")?.asText()
                    val duration = conditionNode.get("duration")?.asText()?.let { Duration.parse(it) } ?: Duration.ZERO
                    AfterEvent(eventType, phaseTitle, duration)
                }
                "AFTER_PARAMETER_SET" -> {
                    val parameterKey = conditionNode.get("parameterKey")?.asText() ?: return null
                    AfterParameterSet(parameterKey)
                }
                else -> {
                    LOG.warn("Unknown condition type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse condition: ${e.message}", e)
            null
        }
    }

    /**
     * Parse schedule expression from string
     */
    private fun parseSchedule(scheduleString: String?): ScheduleExpression {
        if (scheduleString.isNullOrBlank()) return ScheduleExpression.DAILY
        
        return try {
            ScheduleExpression.fromString(scheduleString)
        } catch (e: Exception) {
            LOG.error("Failed to parse schedule expression '$scheduleString': ${e.message}", e)
            ScheduleExpression.DAILY
        }
    }

    /**
     * Parse triggers from JSON
     */
    private fun parseTriggers(triggersNode: JsonNode?): List<RoutineTrigger> {
        if (triggersNode == null || !triggersNode.isArray) return emptyList()
        
        return triggersNode.mapNotNull { triggerNode ->
            try {
                val condition = parseCondition(triggerNode.get("condition")) ?: return@mapNotNull null
                val effect = parseEffect(triggerNode.get("effect")) ?: return@mapNotNull null
                
                RoutineTrigger(
                    condition = condition,
                    effect = effect
                )
            } catch (e: Exception) {
                LOG.error("Failed to parse trigger: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Parse trigger effect from JSON
     */
    private fun parseEffect(effectNode: JsonNode?): TriggerEffect? {
        if (effectNode == null) return null
        
        return try {
            val type = effectNode.get("type")?.asText() ?: return null
            
            when (type) {
                "SEND_MESSAGE" -> {
                    val message = effectNode.get("message")?.asText() ?: return null
                    SendMessage(message)
                }
                "CREATE_TASK" -> {
                    val taskDescription = effectNode.get("taskDescription")?.asText() ?: return null
                    val parameterKey = effectNode.get("parameterKey")?.asText() ?: return null
                    val expiryDate = effectNode.get("expiryDate")?.asText()?.let { Instant.parse(it) } ?: return null
                    
                    CreateTask(taskDescription, parameterKey, expiryDate)
                }
                else -> {
                    LOG.warn("Unknown effect type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse effect: ${e.message}", e)
            null
        }
    }

    /**
     * Data class for GitHub API file response
     */
    private data class GitHubFile(
        val name: String,
        val type: String,
        val download_url: String
    ) {
        val downloadUrl: String get() = download_url
        
        fun isRoutineFile(): Boolean {
            return type == "file" && 
                   name.endsWith(".json") && 
                   name.contains(ROUTINE_FILE_PATTERN, ignoreCase = true)
        }
    }
}