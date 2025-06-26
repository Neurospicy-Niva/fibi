package icu.neurospicy.fibi.domain.service.friends.routines

import com.maximeroussy.invitrode.WordGenerator
import icu.neurospicy.fibi.domain.model.FriendshipId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Document(collection = "routine-template")
data class RoutineTemplate(
    @Id
    val _id: String? = null,
    val title: String,
    val version: String,
    @Indexed(unique = true) val templateId: RoutineTemplateId = RoutineTemplateId.forTitleVersion(title, version),
    val description: String,
    val setupSteps: List<RoutineStep> = emptyList(),
    val phases: List<RoutinePhase>,
    val triggers: List<RoutineTrigger> = emptyList(),
) {
    init {
        require(phases.isNotEmpty()) { "Phases must not be empty" }
        require(title.isNotEmpty()) { "Titles must not be empty" }
        require(description.isNotEmpty()) { "Description must not be empty" }
        require(version.isNotEmpty()) { "Version must not be empty" }
    }

    /**
     * Finds a phase by its ID.
     */
    fun findPhase(phaseId: RoutinePhaseId): RoutinePhase? {
        return phases.find { it.id == phaseId }
    }
}

@JvmInline
value class RoutineTemplateId(private val combinedId: String) {
    override fun toString(): String {
        return combinedId
    }

    companion object {
        @JvmStatic
        fun forTitleVersion(title: String, version: String) = RoutineTemplateId(
            "${
                title.replace(Regex("[^a-zA-Z0-9-_]"), "").trim().plus("-")
                    .plus(title.hashCode().toString(16).takeLast(5))
            }:${version.replace(Regex("[^a-zA-Z0-9-_\\.]"), "").trim()}".lowercase()
        )
    }
}

data class RoutinePhase(
    val title: String,
    val id: RoutinePhaseId = RoutinePhaseId.forTitle(title),
    val condition: TriggerCondition? = null,
    val steps: List<RoutineStep>,
    val schedule: ScheduleExpression = ScheduleExpression.DAILY,
) {
    init {
        require(steps.isNotEmpty()) { "Steps must not be empty" }
        require(title.isNotEmpty()) { "Title must not be empty" }
    }

    /**
     * Finds a step by its ID.
     */
    fun findStep(stepId: RoutineStepId): RoutineStep? {
        return steps.find { it.id == stepId }
    }
}

sealed class ScheduleExpression(val cronExpression: String) {
    object DAILY : ScheduleExpression("0 0 * * *")
    object WEEKLY : ScheduleExpression("0 0 * * MON")
    object WEEKDAYS : ScheduleExpression("0 0 * * MON-FRI")
    object WEEKENDS : ScheduleExpression("0 0 * * SAT,SUN")
    
    // Individual weekdays
    object MONDAY : ScheduleExpression("0 0 * * MON")
    object TUESDAY : ScheduleExpression("0 0 * * TUE")
    object WEDNESDAY : ScheduleExpression("0 0 * * WED")
    object THURSDAY : ScheduleExpression("0 0 * * THU")
    object FRIDAY : ScheduleExpression("0 0 * * FRI")
    object SATURDAY : ScheduleExpression("0 0 * * SAT")
    object SUNDAY : ScheduleExpression("0 0 * * SUN")
    
    data class Custom(val cron: String) : ScheduleExpression(cron) {
        init {
            require(isValidCron(cron)) { "Invalid cron expression: '$cron'. Expected format: 'sec min hour day month dayOfWeek'" }
        }
        
        companion object {
            private fun isValidCron(cron: String): Boolean {
                // Basic cron validation - 6 parts (including seconds) or 5 parts
                val parts = cron.trim().split("\\s+".toRegex())
                if (parts.size !in 5..6) return false
                
                // More sophisticated validation could be added here
                // For now, just check basic structure - allow numbers, *, -, /, commas, and day names
                return parts.all { part ->
                    part.matches("""[0-9*,\-/]+|[A-Z]{3}([,-][A-Z]{3})*""".toRegex())
                }
            }
        }
    }
    
    companion object {
        /**
         * Creates a ScheduleExpression from a string (for backward compatibility)
         */
        fun fromString(schedule: String): ScheduleExpression {
            return when (schedule.uppercase()) {
                "DAILY" -> DAILY
                "WEEKLY" -> WEEKLY
                "WEEKDAYS" -> WEEKDAYS
                "WEEKENDS" -> WEEKENDS
                "MONDAY" -> MONDAY
                "TUESDAY" -> TUESDAY
                "WEDNESDAY" -> WEDNESDAY
                "THURSDAY" -> THURSDAY
                "FRIDAY" -> FRIDAY
                "SATURDAY" -> SATURDAY
                "SUNDAY" -> SUNDAY
                else -> Custom(schedule) // Will validate the cron expression
            }
        }
    }
}

@JvmInline
value class RoutinePhaseId(val combinedId: String) {
    override fun toString(): String {
        return combinedId
    }

    companion object {
        @JvmStatic
        fun forTitle(title: String) = RoutinePhaseId(
            title.replace(Regex("[^a-zA-Z0-9-_]"), "").trim().plus("-").plus(title.hashCode().toString(16).takeLast(5))
                .lowercase()
        )
    }
}

sealed interface RoutineStep {
    val description: String
    val id: RoutineStepId
    val timeOfDay: TimeOfDay?
}

data class ParameterRequestStep(
    val question: String,
    val parameterKey: String,
    val parameterType: RoutineParameterType,
    override val timeOfDay: TimeOfDay? = null,
    override val description: String = question,
    override val id: RoutineStepId = RoutineStepId.forDescription(description),
) : RoutineStep {
    init {
        require(question.isNotBlank()) { "Question must not be blank" }
        require(parameterKey.isNotBlank()) { "Parameter key must not be blank" }
        require(description.isNotBlank()) { "Description must not be blank" }
    }
    companion object {
        @JvmStatic
        fun setupParameterRequest(parameterKey: String, parameterType: RoutineParameterType, description: String) =
            ParameterRequestStep(description, parameterKey, parameterType)
    }
}

data class ActionRoutineStep(
    val message: String,
    val expectConfirmation: Boolean = false,
    val expectedDurationMinutes: Int? = null,
    override val timeOfDay: TimeOfDay? = null,
    override val description: String = message,
    override val id: RoutineStepId = RoutineStepId.forDescription(description),
) : RoutineStep {
    init {
        require(message.isNotBlank()) { "Message must not be blank" }
        require(description.isNotBlank()) { "Description must not be blank" }
        expectedDurationMinutes?.let { require(it > 0) { "Expected duration must be positive" } }
    }
}

data class MessageRoutineStep(
    val message: String,
    override val timeOfDay: TimeOfDay? = null,
    override val description: String = message,
    override val id: RoutineStepId = RoutineStepId.forDescription(description),
) : RoutineStep {
    init {
        require(message.isNotBlank()) { "Message must not be blank" }
        require(description.isNotBlank()) { "Description must not be blank" }
    }
}

@JvmInline
value class RoutineStepId(val combinedId: String) {
    override fun toString(): String {
        return combinedId
    }

    companion object {
        @JvmStatic
        fun forDescription(description: String) = RoutineStepId(
            description.replace(Regex("[^a-zA-Z0-9-_]"), "").trim()
                .plus("-").plus(description.hashCode().toString(16).takeLast(5))
                .lowercase()
        )
    }
}

sealed interface TimeOfDay

data class TimeOfDayLocalTime(val time: LocalTime) : TimeOfDay
data class TimeOfDayReference(val reference: String) : TimeOfDay {
    init {
        require(reference.isNotBlank()) { "Reference must not be blank" }
    }
}


enum class RoutineParameterType {
    STRING,
    LOCAL_TIME,
    BOOLEAN,
    INT,
    FLOAT,
    DATE;
    
    /**
     * Validates and converts a string value to the appropriate type
     */
    fun parseAndValidate(value: String): Any {
        return when (this) {
            STRING -> value
            LOCAL_TIME -> try {
                LocalTime.parse(value)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid time format: '$value'. Expected format: HH:mm or HH:mm:ss")
            }
            BOOLEAN -> when (value.lowercase().trim()) {
                "true", "yes", "y", "1", "on" -> true
                "false", "no", "n", "0", "off" -> false
                else -> throw IllegalArgumentException("Invalid boolean value: '$value'. Expected: true/false, yes/no, y/n, 1/0, on/off")
            }
            INT -> try {
                value.toInt()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid integer value: '$value'")
            }
            FLOAT -> try {
                value.toFloat()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid float value: '$value'")
            }
            DATE -> try {
                LocalDate.parse(value)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid date format: '$value'. Expected format: yyyy-MM-dd")
            }
        }
    }
    
    /**
     * Gets a human-readable description of the expected format
     */
    fun getFormatDescription(): String {
        return when (this) {
            STRING -> "Any text"
            LOCAL_TIME -> "Time in HH:mm format (e.g., 14:30)"
            BOOLEAN -> "true/false, yes/no, y/n, 1/0, or on/off"
            INT -> "A whole number (e.g., 42)"
            FLOAT -> "A decimal number (e.g., 3.14)"
            DATE -> "Date in yyyy-MM-dd format (e.g., 2024-12-25)"
        }
    }
}

/**
 * A type-safe parameter value that remembers its intended type
 */
data class TypedParameter(
    val value: Any,
    val type: RoutineParameterType
) {
    companion object {
        /**
         * Creates a TypedParameter from a string value and expected type
         */
        fun fromString(stringValue: String, expectedType: RoutineParameterType): TypedParameter {
            val parsedValue = expectedType.parseAndValidate(stringValue)
            return TypedParameter(parsedValue, expectedType)
        }
        
        /**
         * Creates a TypedParameter from a raw value (inferring type)
         */
        fun fromValue(value: Any): TypedParameter {
            val type = when (value) {
                is String -> RoutineParameterType.STRING
                is LocalTime -> RoutineParameterType.LOCAL_TIME
                is Boolean -> RoutineParameterType.BOOLEAN
                is Int -> RoutineParameterType.INT
                is Float -> RoutineParameterType.FLOAT
                is Double -> RoutineParameterType.FLOAT // Convert Double to Float
                is LocalDate -> RoutineParameterType.DATE
                else -> RoutineParameterType.STRING // Fallback to string
            }
            return TypedParameter(if (value is Double) value.toFloat() else value, type)
        }
    }
    
    /**
     * Gets the value as the expected type (with type safety)
     * String access always works by converting to string representation
     */
    inline fun <reified T> getAs(): T {
        return when (T::class) {
            String::class -> value.toString() as T // String conversion always works
            LocalTime::class -> {
                require(type == RoutineParameterType.LOCAL_TIME) { "Parameter type is $type, not LOCAL_TIME" }
                value as T
            }
            Boolean::class -> {
                require(type == RoutineParameterType.BOOLEAN) { "Parameter type is $type, not BOOLEAN" }
                value as T
            }
            Int::class -> {
                require(type == RoutineParameterType.INT) { "Parameter type is $type, not INT" }
                value as T
            }
            Float::class -> {
                require(type == RoutineParameterType.FLOAT) { "Parameter type is $type, not FLOAT" }
                value as T
            }
            LocalDate::class -> {
                require(type == RoutineParameterType.DATE) { "Parameter type is $type, not DATE" }
                value as T
            }
            else -> throw IllegalArgumentException("Unsupported type: ${T::class}")
        }
    }
    
    /**
     * Gets the string representation of the value
     */
    fun getAsString(): String = value.toString()
}

data class RoutineTrigger(
    val condition: TriggerCondition,
    val effect: TriggerEffect,
    val id: TriggerId = TriggerId(),
)

@JvmInline
value class TriggerId(
    private val word: String = WordGenerator().newWord(4).lowercase(),
) {
    override fun toString(): String = word
}

// --- Trigger Conditions ---

sealed interface TriggerCondition

sealed interface TimeBasedTriggerCondition : TriggerCondition

data class AfterDays(
    val value: Int,
) : TimeBasedTriggerCondition {
    init {
        require(value > 0) { "Days value must be positive" }
    }
}

data class AfterDuration(
    val reference: String? = null,
    val duration: Duration,
) : TimeBasedTriggerCondition {
    init {
        require(!duration.isNegative) { "Duration must not be negative" }
        reference?.let { require(it.isNotBlank()) { "Reference must not be blank if provided" } }
    }
}

data class AfterEvent(
    val eventType: RoutineAnchorEvent,
    val phaseTitle: String? = null,
    val duration: Duration? = Duration.ZERO,
) : TimeBasedTriggerCondition {
    init {
        duration?.let { require(!it.isNegative) { "Duration must not be negative" } }
        phaseTitle?.let { require(it.isNotBlank()) { "Phase title must not be blank if provided" } }
    }
}

enum class RoutineAnchorEvent {
    ROUTINE_STARTED, PHASE_ENTERED, PHASE_LEFT
}

data class AfterPhaseCompletions(
    val phaseId: RoutinePhaseId,
    val times: Int,
) : TriggerCondition {
    init {
        require(times > 0) { "Times must be positive" }
    }
}

data class AfterParameterSet(
    val parameterKey: String,
) : TriggerCondition {
    init {
        require(parameterKey.isNotBlank()) { "Parameter key must not be blank" }
    }
}


// --- Trigger Effects ---

sealed interface TriggerEffect

data class SendMessage(
    val message: String,
) : TriggerEffect {
    init {
        require(message.isNotBlank()) { "Message must not be blank" }
    }
}

data class CreateTask(
    val taskDescription: String,
    val parameterKey: String,
    val expiryDate: Instant,
) : TriggerEffect {
    init {
        require(taskDescription.isNotBlank()) { "Task description must not be blank" }
        require(parameterKey.isNotBlank()) { "Parameter key must not be blank" }
        require(expiryDate.isAfter(Instant.now())) { "Expiry date must be in the future" }
    }
}

// --- Runtime State ---

@Document(collection = "routine-instance")
data class RoutineInstance(
    @Id
    val _id: String?,
    val templateId: RoutineTemplateId,
    val friendshipId: FriendshipId,
    val parameters: Map<String, TypedParameter> = mutableMapOf(),
    val currentPhaseId: RoutinePhaseId? = null,
    val progress: RoutineProgress = RoutineProgress(
        if (currentPhaseId != null) listOf(
            PhaseIterationProgress(
                currentPhaseId,
                Instant.now()
            )
        ) else emptyList()
    ),
    val concepts: List<RoutineConcept> = mutableListOf(),
    @Indexed(unique = true)
    val instanceId: RoutineInstanceId = RoutineInstanceId.forInstance(templateId, friendshipId),
) {
    fun withCurrentPhase(phaseId: RoutinePhaseId): RoutineInstance =
        this.copy(
            currentPhaseId = phaseId,
            progress = progress.copy(iterations = listOf(PhaseIterationProgress(phaseId, Instant.now())) + progress.iterations)
        )
    
    /**
     * Starts a new iteration of the current phase
     */
    fun withNewIteration(phaseId: RoutinePhaseId): RoutineInstance =
        this.copy(
            progress = progress.copy(iterations = listOf(PhaseIterationProgress(phaseId, Instant.now())) + progress.iterations)
        )
    
    /**
     * Sets a parameter with type validation
     */
    fun withParameter(key: String, value: String, expectedType: RoutineParameterType): RoutineInstance {
        val typedParameter = TypedParameter.fromString(value, expectedType)
        return this.copy(parameters = parameters + (key to typedParameter))
    }
    
    /**
     * Sets a parameter from a raw value (inferring type)
     */
    fun withParameter(key: String, value: Any): RoutineInstance {
        val typedParameter = TypedParameter.fromValue(value)
        return this.copy(parameters = parameters + (key to typedParameter))
    }
    
    /**
     * Gets a parameter value as a specific type
     */
    inline fun <reified T> getParameter(key: String): T? {
        return parameters[key]?.getAs<T>()
    }
    
    /**
     * Gets a parameter value as a string (always works)
     */
    fun getParameterAsString(key: String): String? {
        return parameters[key]?.getAsString()
    }
    
    /**
     * Checks if a parameter exists and has the expected type
     */
    fun hasParameterOfType(key: String, expectedType: RoutineParameterType): Boolean {
        return parameters[key]?.type == expectedType
    }

}

data class RoutineProgress(
    val iterations: List<PhaseIterationProgress> = emptyList(),
) {
    /**
     * Gets the current (most recent) iteration.
     */
    fun getCurrentIteration(): PhaseIterationProgress? {
        return iterations.firstOrNull()
    }
}

data class PhaseIterationProgress(
    val phaseId: RoutinePhaseId,
    val iterationStart: Instant,
    val completedSteps: List<Completion<RoutineStepId>> = emptyList(),
    val completedAt: Instant? = null,
)

data class Completion<T>(val id: T, val at: Instant = Instant.now())

sealed class RoutineConcept {
    abstract val linkedEntityId: Any
    abstract val linkedStep: RoutineStepId
}

data class TaskRoutineConcept(
    val linkedTaskId: String,
    override val linkedStep: RoutineStepId,
    override val linkedEntityId: Any = linkedTaskId,
) : RoutineConcept()

@JvmInline
value class RoutineInstanceId(val combinedId: String) {
    override fun toString(): String {
        return combinedId
    }

    companion object {
        @JvmStatic
        fun forInstance(routineTemplateId: RoutineTemplateId, friendshipId: FriendshipId) =
            RoutineInstanceId("$routineTemplateId:$friendshipId:${WordGenerator().newWord(5)}".lowercase())
    }
}

@Document(collection = "routine-events")
data class RoutineEventLogEntry(
    val routineInstanceId: RoutineInstanceId,
    val friendshipId: FriendshipId,
    val event: RoutineEventType,
    val timestamp: Instant,
    val metadata: Map<String, Any> = emptyMap(),
)

enum class RoutineEventType {
    ROUTINE_STARTED,
    PHASE_ACTIVATED,
    PHASE_DEACTIVATED,
    STEP_PARAMETER_REQUESTED,
    STEP_PARAMETER_SET,
    STEP_MESSAGE_SENT,
    ACTION_STEP_MESSAGE_SENT,
    ACTION_STEP_CONFIRMED,
    ACTION_STEP_SKIPPED,
    PHASE_COMPLETED,
    ROUTINE_COMPLETED,
    TRIGGER_SCHEDULED,
    STEP_SCHEDULED,
    ROUTINE_STOPPED_FOR_TODAY,
    PHASE_SCHEDULED,
    PHASE_ITERATIONS_SCHEDULED,
    PHASE_ITERATION_STARTED,
}