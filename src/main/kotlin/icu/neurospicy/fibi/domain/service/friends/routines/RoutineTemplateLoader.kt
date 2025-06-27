package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/**
 * Loads routine templates from JSON files at application startup.
 * Scans for *.json files in the classpath and parses them as routine templates.
 */
@Service
class RoutineTemplateLoader(
    private val templateRepository: RoutineTemplateRepository,
    private val objectMapper: ObjectMapper,
) {
    
    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineTemplateLoader::class.java)
    }
    
    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.info("Loading routine templates from JSON files...")
        loadTemplatesFromResources()
    }
    
    private fun loadTemplatesFromResources() {
        val resolver = PathMatchingResourcePatternResolver()
        try {
            val resources = resolver.getResources("classpath*:**/*routine*.json")
            
            resources.forEach { resource ->
                try {
                    loadTemplate(resource)
                } catch (e: Exception) {
                    LOG.error("Failed to load routine template from ${resource.filename}: ${e.message}", e)
                }
            }
            
            LOG.info("Successfully loaded ${resources.size} routine template(s)")
        } catch (e: Exception) {
            LOG.error("Failed to scan for routine template files: ${e.message}", e)
        }
    }
    
    private fun loadTemplate(resource: Resource) {
        LOG.debug("Loading template from: ${resource.filename}")
        
        val json = objectMapper.readTree(resource.inputStream)
        val template = parseRoutineTemplate(json)
        
        // Check if template already exists
        val existingTemplate = templateRepository.findById(template.templateId)
        if (existingTemplate != null) {
            LOG.debug("Template ${template.templateId} already exists, skipping")
            return
        }
        
        templateRepository.save(template)
        LOG.info("Loaded routine template: ${template.title} (${template.templateId})")
    }
    
    private fun parseRoutineTemplate(json: JsonNode): RoutineTemplate {
        val title = json["title"]?.asText() ?: throw IllegalArgumentException("Missing required field: title")
        val version = json["version"]?.asText() ?: throw IllegalArgumentException("Missing required field: version")
        val description = json["description"]?.asText() ?: throw IllegalArgumentException("Missing required field: description")
        
        val setupSteps = json["setupSteps"]?.map { parseStep(it) } ?: emptyList()
        val phases = json["phases"]?.map { parsePhase(it) } ?: throw IllegalArgumentException("Missing required field: phases")
        val triggers = json["triggers"]?.map { parseTrigger(it) } ?: emptyList()
        
        return RoutineTemplate(
            title = title,
            version = version,
            description = description,
            setupSteps = setupSteps,
            phases = phases,
            triggers = triggers
        )
    }
    
    private fun parseStep(json: JsonNode): RoutineStep {
        val type = json["type"]?.asText() ?: throw IllegalArgumentException("Missing step type")
        val timeOfDay = json["timeOfDay"]?.asText()?.let { parseTimeOfDay(it) }
        
        return when (type) {
            "parameter_request" -> {
                val question = json["question"]?.asText() ?: throw IllegalArgumentException("Missing question for parameter_request")
                val parameterKey = json["parameterKey"]?.asText() ?: throw IllegalArgumentException("Missing parameterKey")
                val parameterType = json["parameterType"]?.asText()?.let { 
                    RoutineParameterType.valueOf(it) 
                } ?: throw IllegalArgumentException("Missing or invalid parameterType")
                
                ParameterRequestStep(
                    question = question,
                    parameterKey = parameterKey,
                    parameterType = parameterType,
                    timeOfDay = timeOfDay
                )
            }
            "action" -> {
                val message = json["message"]?.asText() ?: throw IllegalArgumentException("Missing message for action step")
                val expectConfirmation = json["expectConfirmation"]?.asBoolean() ?: false
                val expectedDurationMinutes = json["expectedDurationMinutes"]?.asInt()
                
                ActionRoutineStep(
                    message = message,
                    expectConfirmation = expectConfirmation,
                    expectedDurationMinutes = expectedDurationMinutes,
                    timeOfDay = timeOfDay
                )
            }
            "message" -> {
                val message = json["message"]?.asText() ?: throw IllegalArgumentException("Missing message for message step")
                
                MessageRoutineStep(
                    message = message,
                    timeOfDay = timeOfDay
                )
            }
            else -> throw IllegalArgumentException("Unknown step type: $type")
        }
    }
    
    private fun parseTimeOfDay(timeString: String): TimeOfDay {
        return when {
            timeString.startsWith("\${") -> TimeOfDayReference(timeString)
            else -> {
                try {
                    val time = LocalTime.parse(timeString)
                    TimeOfDayLocalTime(time)
                } catch (e: Exception) {
                    // If it's not a valid LocalTime, treat it as a reference
                    TimeOfDayReference(timeString)
                }
            }
        }
    }
    
    private fun parsePhase(json: JsonNode): RoutinePhase {
        val title = json["title"]?.asText() ?: throw IllegalArgumentException("Missing phase title")
        val steps = json["steps"]?.map { parseStep(it) } ?: throw IllegalArgumentException("Missing phase steps")
        val condition = json["condition"]?.let { parseTriggerCondition(it) }
        val schedule = json["schedule"]?.asText()?.let { 
            ScheduleExpression.fromString(it)
        } ?: ScheduleExpression.DAILY
        
        return RoutinePhase(
            title = title,
            steps = steps,
            condition = condition,
            schedule = schedule
        )
    }
    
    private fun parseTriggerCondition(json: JsonNode): TriggerCondition {
        val type = json["type"]?.asText() ?: throw IllegalArgumentException("Missing trigger condition type")
        
        return when (type) {
            "AFTER_DAYS" -> {
                val value = json["value"]?.asInt() ?: throw IllegalArgumentException("Missing value for AFTER_DAYS")
                AfterDays(value)
            }
            "AFTER_PHASE_COMPLETIONS" -> {
                val phaseTitle = json["phaseTitle"]?.asText() ?: throw IllegalArgumentException("Missing phaseTitle for AFTER_PHASE_COMPLETIONS")
                val times = json["times"]?.asInt() ?: throw IllegalArgumentException("Missing times for AFTER_PHASE_COMPLETIONS")
                // Convert phaseTitle to phaseId
                val phaseId = RoutinePhaseId.forTitle(phaseTitle)
                AfterPhaseCompletions(phaseId, times)
            }
            "AFTER_DURATION" -> {
                val durationString = json["duration"]?.asText() ?: throw IllegalArgumentException("Missing duration for AFTER_DURATION")
                val duration = Duration.parse(durationString)
                val reference = json["reference"]?.asText()
                AfterDuration(reference, duration)
            }
            "AFTER_EVENT" -> {
                val eventTypeString = json["eventType"]?.asText() ?: throw IllegalArgumentException("Missing eventType for AFTER_EVENT")
                val eventType = RoutineAnchorEvent.valueOf(eventTypeString)
                val phaseTitle = json["phaseTitle"]?.asText()
                val durationString = json["duration"]?.asText()
                val duration = durationString?.let { Duration.parse(it) }
                AfterEvent(eventType, phaseTitle, duration)
            }
            "AFTER_PARAMETER_SET" -> {
                val parameterKey = json["parameterKey"]?.asText() ?: throw IllegalArgumentException("Missing parameterKey for AFTER_PARAMETER_SET")
                AfterParameterSet(parameterKey)
            }
            else -> throw IllegalArgumentException("Unknown trigger condition type: $type")
        }
    }
    
    private fun parseTrigger(json: JsonNode): RoutineTrigger {
        val condition = parseTriggerCondition(json["condition"] ?: throw IllegalArgumentException("Missing trigger condition"))
        val effect = parseTriggerEffect(json["effect"] ?: throw IllegalArgumentException("Missing trigger effect"))
        
        return RoutineTrigger(condition = condition, effect = effect)
    }
    
    private fun parseTriggerEffect(json: JsonNode): TriggerEffect {
        val type = json["type"]?.asText() ?: throw IllegalArgumentException("Missing trigger effect type")
        
        return when (type) {
            "SEND_MESSAGE" -> {
                val message = json["message"]?.asText() ?: throw IllegalArgumentException("Missing message for SEND_MESSAGE")
                SendMessage(message)
            }
            "CREATE_TASK" -> {
                val taskDescription = json["taskDescription"]?.asText() ?: throw IllegalArgumentException("Missing taskDescription for CREATE_TASK")
                val parameterKey = json["parameterKey"]?.asText() ?: throw IllegalArgumentException("Missing parameterKey for CREATE_TASK")
                val expiryDateString = json["expiryDate"]?.asText() ?: throw IllegalArgumentException("Missing expiryDate for CREATE_TASK")
                val expiryDate = Instant.parse(expiryDateString)
                CreateTask(taskDescription, parameterKey, expiryDate)
            }
            else -> throw IllegalArgumentException("Unknown trigger effect type: $type")
        }
    }
} 