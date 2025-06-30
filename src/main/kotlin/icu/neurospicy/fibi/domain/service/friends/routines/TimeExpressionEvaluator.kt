package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.iso8601arithmetic.TemporalContext
import icu.neurospicy.iso8601arithmetic.TemporalExpressionEvaluator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

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
        return try {
            // Build context from routine parameters
            val context = buildTemporalContext(instance, zoneId)
            
            // Preprocess the expression to handle spacing for ISO 8601 arithmetic
            val processedExpression = preprocessExpression(expression)
            
            // Evaluate the expression
            val result = temporalEvaluator.evaluate(processedExpression, context)
            LOG.debug("Evaluated time expression '$expression' to $result for routine ${instance.instanceId}")
            result
        } catch (e: Exception) {
            LOG.warn("Failed to evaluate time expression '$expression' for routine ${instance.instanceId}: ${e.message}")
            null
        }
    }
    
    /**
     * Preprocesses the expression to ensure proper spacing for ISO 8601 arithmetic.
     * Converts expressions like "${wakeUpTime}+PT15M" to "${wakeUpTime} + PT15M"
     */
    private fun preprocessExpression(expression: String): String {
        return expression
            .replace(Regex("(\\$\\{[^}]+\\})([+-])"), "$1 $2 ")
            .replace(Regex("([+-])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z0-9]+)([+-])"), "$1 $2 ")  // Handle cases like PT30M-PT10M
            .replace(Regex("\\s+"), " ") // Clean up multiple spaces
            .trim()
    }
    
    /**
     * Builds a TemporalContext from routine instance parameters.
     */
    private fun buildTemporalContext(instance: RoutineInstance, zoneId: ZoneId): TemporalContext {
        val context = TemporalContext.create()
        
        instance.parameters.forEach { (key, typedParam) ->
            when (typedParam.type) {
                RoutineParameterType.LOCAL_TIME -> {
                    val localTime = typedParam.getAs<LocalTime>()
                    // Convert LocalTime to LocalDateTime using today's date
                    val localDateTime = LocalDateTime.now(zoneId).with(localTime)
                    context.addDateTime(key, localDateTime)
                }
                RoutineParameterType.DATE -> {
                    val localDate = typedParam.getAs<java.time.LocalDate>()
                    val localDateTime = localDate.atStartOfDay()
                    context.addDateTime(key, localDateTime)
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