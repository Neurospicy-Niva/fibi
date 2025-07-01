package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.iso8601arithmetic.TemporalExpressionEvaluator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant

/**
 * Service for evaluating complex time expressions in routine templates.
 * Handles parameter substitution and arithmetic operations using the ISO 8601 arithmetic library.
 */
@Service
class TimeExpressionEvaluator {
    
    companion object {
        private val LOG = LoggerFactory.getLogger(TimeExpressionEvaluator::class.java)
        private val temporalEvaluator = TemporalExpressionEvaluator()
    }
    
    /**
     * Evaluates a time expression using routine instance parameters.
     * Returns null if the expression cannot be evaluated (e.g., missing parameters).
     */
    fun evaluateTimeExpression(
        expression: String,
        instance: RoutineInstance,
        zoneId: ZoneId = ZoneId.of("UTC")
    ): LocalDateTime? {
        val context = buildTemporalContext(instance, zoneId)
        val variablePattern = Regex("\\$\\{([^}]+)\\}\\+PT([0-9]+[HM])")
        val match = variablePattern.matchEntire(expression.replace(" ", ""))
        if (match != null) {
            val variableName = match.groupValues[1]
            val durationStr = "PT" + match.groupValues[2]
            val base = context[variableName]
            val duration = java.time.Duration.parse(durationStr)
            return base?.plus(duration)
        }
        // fallback: try to evaluate as a plain datetime or duration
        return try {
            temporalEvaluator.evaluate(expression.replace(" ", ""), context)
        } catch (ex: Exception) {
            LOG.warn("Failed to evaluate time expression '$expression' for routine ${instance.instanceId}: ${ex.message}")
            null
        }
    }
    
    /**
     * Evaluates a time expression for trigger conditions (alias for evaluateTimeExpression).
     * This method is called by the scheduler for trigger conditions.
     */
    fun evaluateAtTimeExpression(
        expression: String,
        instance: RoutineInstance,
        zoneId: ZoneId = ZoneId.of("UTC")
    ): LocalDateTime? {
        return evaluateTimeExpression(expression, instance, zoneId)
    }
    
    /**
     * Substitutes variables in the expression with their actual values from the context.
     * Variables are in the format ${VAR_NAME}
     */
    private fun substituteVariables(expression: String, context: Map<String, LocalDateTime>): String {
        val variablePattern = Regex("\\$\\{([^}]+)\\}")
        val isoFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        return variablePattern.replace(expression) { matchResult ->
            val variableName = matchResult.groupValues[1]
            val value = context[variableName]
            if (value != null) {
                value.format(isoFormatter)
            } else {
                throw IllegalArgumentException("Variable not found in context: $variableName")
            }
        }
    }
    
    /**
     * Preprocesses the expression to ensure proper spacing for ISO 8601 arithmetic.
     * Converts expressions like "2025-07-01T10:00:00+PT15M" to "2025-07-01T10:00:00 + PT15M"
     */
    private fun preprocessExpression(expression: String): String {
        return expression
            .replace(Regex("([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?)([+-])"), "$1 $2 ")
            .replace(Regex("([+-])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z0-9]+)([+-])"), "$1 $2 ")  // Handle cases like PT30M-PT10M
            .replace(Regex("\\s+"), " ") // Clean up multiple spaces
            .trim()
    }
    
    /**
     * Builds a temporal context from routine instance parameters and pre-defined system variables.
     */
    private fun buildTemporalContext(instance: RoutineInstance, zoneId: ZoneId): Map<String, LocalDateTime> {
        val context = mutableMapOf<String, LocalDateTime>()
        
        // Add pre-defined system variables
        val now = LocalDateTime.now(zoneId)
        context["NOW"] = now
        
        // Add routine lifecycle timestamps
        val routineStartTime = instance.startedAt.atZone(zoneId).toLocalDateTime()
        context["ROUTINE_START"] = routineStartTime
        
        // Add phase event timestamps from routine instance parameters
        // These are set by the RoutineAfterEventHandler when events occur
        val phaseEnteredTimestamp = instance.parameters["PHASE_ENTERED"]?.value as? Instant
        if (phaseEnteredTimestamp != null) {
            context["PHASE_ENTERED"] = phaseEnteredTimestamp.atZone(zoneId).toLocalDateTime()
        } else {
            // Fallback to current time if not set
            context["PHASE_ENTERED"] = now
        }
        
        val phaseLeftTimestamp = instance.parameters["PHASE_LEFT"]?.value as? Instant
        if (phaseLeftTimestamp != null) {
            context["PHASE_LEFT"] = phaseLeftTimestamp.atZone(zoneId).toLocalDateTime()
        } else {
            // Fallback to current time if not set
            context["PHASE_LEFT"] = now
        }
        
        // Add routine parameters
        instance.parameters.forEach { (key, typedParam) ->
            when (typedParam.type) {
                RoutineParameterType.LOCAL_TIME -> {
                    val localTime = typedParam.getAs<LocalTime>()
                    // Convert LocalTime to LocalDateTime using today's date
                    val localDateTime = LocalDateTime.now(zoneId).with(localTime)
                    context[key] = localDateTime
                }
                RoutineParameterType.DATE -> {
                    val localDate = typedParam.getAs<java.time.LocalDate>()
                    val localDateTime = localDate.atStartOfDay()
                    context[key] = localDateTime
                }
                RoutineParameterType.INSTANT -> {
                    val instant = typedParam.getAs<Instant>()
                    val localDateTime = instant.atZone(zoneId).toLocalDateTime()
                    context[key] = localDateTime
                }
                // For other types, convert to string (they'll be handled as literals if needed)
                else -> {
                    // We could add these as variables, but the library primarily works with temporal values
                    LOG.debug("Skipping non-temporal parameter '$key' of type ${typedParam.type}")
                }
            }
        }
        
        return context
    }
} 