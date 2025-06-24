package icu.neurospicy.fibi.domain.service.friends.routines

import com.maximeroussy.invitrode.WordGenerator
import icu.neurospicy.fibi.domain.model.FriendshipId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Duration
import java.time.Instant
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
        phases.let { require(it.isNotEmpty()) { "Phases must not be empty" } }
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
    val schedule: String = "DAILY", // optional cron expression or keyword like "DAILY"
)

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
) : RoutineStep

data class MessageRoutineStep(
    val message: String,
    override val timeOfDay: TimeOfDay? = null,
    override val description: String = message,
    override val id: RoutineStepId = RoutineStepId.forDescription(description),
) : RoutineStep

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
data class TimeOfDayReference(val reference: String) : TimeOfDay


enum class RoutineParameterType {
    STRING,
    LOCAL_TIME,
    BOOLEAN,
    INT,
    FLOAT,
    DATE,
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
) : TimeBasedTriggerCondition

data class AfterDuration(
    val reference: String? = null,
    val duration: Duration,
) : TimeBasedTriggerCondition

data class AfterEvent(
    val eventType: RoutineAnchorEvent,
    val phaseTitle: String? = null,
    val duration: Duration? = Duration.ZERO,
) : TimeBasedTriggerCondition

enum class RoutineAnchorEvent {
    ROUTINE_STARTED, PHASE_ENTERED, PHASE_LEFT
}

data class AfterPhaseCompletions(
    val phaseId: RoutinePhaseId,
    val times: Int,
) : TriggerCondition

data class AfterParameterSet(
    val parameterKey: String,
) : TriggerCondition


// --- Trigger Effects ---

sealed interface TriggerEffect

data class SendMessage(
    val message: String,
) : TriggerEffect

data class CreateTask(
    val taskDescription: String,
    val parameterKey: String,
    val expiryDate: Instant,
) : TriggerEffect

// --- Runtime State ---

@Document(collection = "routine-instance")
data class RoutineInstance(
    @Id
    val _id: String?,
    val templateId: RoutineTemplateId,
    val friendshipId: FriendshipId,
    val parameters: Map<String, Any> = mutableMapOf(),
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
            progress = progress.copy(iterations = progress.iterations + PhaseIterationProgress(phaseId, Instant.now()))
        )
}

data class RoutineProgress(
    val iterations: List<PhaseIterationProgress> = emptyList(),
)

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