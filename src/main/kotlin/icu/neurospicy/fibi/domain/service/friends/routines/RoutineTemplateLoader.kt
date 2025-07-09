package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalTime

/**
 * Service responsible for parsing JSON content into RoutineTemplate objects.
 * This is a pure parser that transforms JSON strings into domain objects.
 */
@Service
class RoutineTemplateLoader(
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineTemplateLoader::class.java)
    }

    /**
     * Parse JSON string into RoutineTemplate
     */
    fun parseRoutineTemplate(jsonContent: String): RoutineTemplate {
        return try {
            val json = objectMapper.readTree(jsonContent)
            parseRoutineTemplate(json)
        } catch (e: Exception) {
            LOG.error("Failed to parse routine template JSON: ${e.message}", e)
            throw IllegalArgumentException("Invalid routine template JSON: ${e.message}", e)
        }
    }

    /**
     * Parse JsonNode into RoutineTemplate
     */
    fun parseRoutineTemplate(json: JsonNode): RoutineTemplate {
        val title = json["title"]?.asText() ?: throw IllegalArgumentException("Missing required field: title")
        val version = json["version"]?.asText() ?: throw IllegalArgumentException("Missing required field: version")
        val description =
            json["description"]?.asText() ?: throw IllegalArgumentException("Missing required field: description")

        val setupSteps = json["setupSteps"]?.map { parseStep(it) } ?: emptyList()
        val phases =
            json["phases"]?.map { parsePhase(it) } ?: throw IllegalArgumentException("Missing required field: phases")
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

    /**
     * Parse a step from JSON
     */
    fun parseStep(json: JsonNode): RoutineStep {
        val type = json["type"]?.asText() ?: throw IllegalArgumentException("Missing step type")
        val timeOfDay = json["timeOfDay"]?.asText()?.let { parseTimeOfDay(it) }

        return when (type) {
            "parameter_request" -> {
                val question = json["question"]?.asText()
                    ?: throw IllegalArgumentException("Missing question for parameter_request")
                val parameterKey =
                    json["parameterKey"]?.asText() ?: throw IllegalArgumentException("Missing parameterKey")
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
                val message = json["action"]?.asText() ?: json["message"]?.asText()
                ?: throw IllegalArgumentException("Missing message/action for action step")
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
                val message =
                    json["message"]?.asText() ?: throw IllegalArgumentException("Missing message for message step")

                MessageRoutineStep(
                    message = message,
                    timeOfDay = timeOfDay
                )
            }

            else -> throw IllegalArgumentException("Unknown step type: $type")
        }
    }

    /**
     * Parse time of day from string
     */
    fun parseTimeOfDay(timeString: String): TimeOfDay {
        try {
            val time = LocalTime.parse(timeString)
            return TimeOfDayLocalTime(time)
        } catch (e: Exception) {
        }
        return Regex("^\\$\\{(\\w+)}$").find(timeString)?.let { matchResult ->
            TimeOfDayReference(matchResult.groupValues.last())
        } ?: run {
            TimeOfDayExpression(expression = timeString)
        }
    }

    /**
     * Parse a phase from JSON
     */
    fun parsePhase(json: JsonNode): RoutinePhase {
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

    /**
     * Parse trigger condition from JSON
     */
    fun parseTriggerCondition(json: JsonNode): TriggerCondition {
        val type = json["type"]?.asText() ?: throw IllegalArgumentException("Missing trigger condition type")

        return when (type) {
            "AFTER_DAYS" -> {
                val value = json["value"]?.asInt() ?: throw IllegalArgumentException("Missing value for AFTER_DAYS")
                AfterDays(value)
            }

            "AFTER_PHASE_COMPLETIONS" -> {
                val phaseTitle = json["phaseTitle"]?.asText()
                    ?: throw IllegalArgumentException("Missing phaseTitle for AFTER_PHASE_COMPLETIONS")
                val times = json["times"]?.asInt() ?: json["value"]?.asInt()
                ?: throw IllegalArgumentException("Missing times/value for AFTER_PHASE_COMPLETIONS")
                // Convert phaseTitle to phaseId
                val phaseId = RoutinePhaseId.forTitle(phaseTitle)
                AfterPhaseCompletions(phaseId, times)
            }

            "AT_TIME_EXPRESSION" -> {
                val timeExpression = json["timeExpression"]?.asText() ?: json["expression"]?.asText()
                ?: throw IllegalArgumentException("Missing timeExpression for AT_TIME_EXPRESSION")
                // duration parsing removed
                // reference is now part of timeExpression
                AtTimeExpression(timeExpression)
            }

            "AFTER_EVENT" -> {
                val eventTypeString = json["eventType"]?.asText() ?: json["event"]?.asText()
                ?: throw IllegalArgumentException("Missing eventType/event for AFTER_EVENT")
                val eventType = RoutineAnchorEvent.valueOf(eventTypeString)
                val phaseTitle = json["phaseTitle"]?.asText()
                val timeExpression = json["timeExpression"]?.asText() ?: json["duration"]?.asText()
                // duration parsing removed
                AfterEvent(eventType, phaseTitle, timeExpression)
            }

            "PHASE_ENTERED" -> {
                val phaseTitle = json["phaseTitle"]?.asText()
                val timeExpression = json["timeExpression"]?.asText() ?: json["duration"]?.asText()
                AfterEvent(RoutineAnchorEvent.PHASE_ENTERED, phaseTitle, timeExpression)
            }

            "AFTER_PARAMETER_SET" -> {
                val parameterKey = json["parameterKey"]?.asText()
                    ?: throw IllegalArgumentException("Missing parameterKey for AFTER_PARAMETER_SET")
                AfterParameterSet(parameterKey)
            }

            "AFTER_DURATION" -> {
                val durationStr = json["duration"]?.asText() ?: json["value"]?.asText()
                ?: throw IllegalArgumentException("Missing duration/value for AFTER_DURATION")
                val reference = json["reference"]?.asText()
                if (reference != null) {
                    AfterDuration(reference, java.time.Duration.parse(durationStr))
                } else {
                    AfterDuration(null, java.time.Duration.parse(durationStr))
                }
            }

            else -> throw IllegalArgumentException("Unknown trigger condition type: $type")
        }
    }

    /**
     * Parse a trigger from JSON
     */
    fun parseTrigger(json: JsonNode): RoutineTrigger {
        val condition =
            parseTriggerCondition(json["condition"] ?: throw IllegalArgumentException("Missing trigger condition"))
        val effect = parseTriggerEffect(json["effect"] ?: throw IllegalArgumentException("Missing trigger effect"))

        return RoutineTrigger(condition = condition, effect = effect)
    }

    /**
     * Parse trigger effect from JSON
     */
    fun parseTriggerEffect(json: JsonNode): TriggerEffect {
        val type = json["type"]?.asText() ?: throw IllegalArgumentException("Missing trigger effect type")

        return when (type) {
            "SEND_MESSAGE" -> {
                val message =
                    json["message"]?.asText() ?: throw IllegalArgumentException("Missing message for SEND_MESSAGE")
                SendMessage(message)
            }

            "CREATE_TASK" -> {
                val taskDescription = json["taskDescription"]?.asText()
                    ?: throw IllegalArgumentException("Missing taskDescription for CREATE_TASK")
                val parameterKey = json["parameterKey"]?.asText()
                    ?: throw IllegalArgumentException("Missing parameterKey for CREATE_TASK")
                val expiryDateString =
                    json["expiryDate"]?.asText() ?: throw IllegalArgumentException("Missing expiryDate for CREATE_TASK")
                val expiryDate = Instant.parse(expiryDateString)
                CreateTask(taskDescription, parameterKey, expiryDate)
            }

            else -> throw IllegalArgumentException("Unknown trigger effect type: $type")
        }
    }
} 