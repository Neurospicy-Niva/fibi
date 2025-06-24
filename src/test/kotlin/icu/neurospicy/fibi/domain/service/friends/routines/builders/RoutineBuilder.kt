package icu.neurospicy.fibi.domain.service.friends.routines.builders

import com.maximeroussy.invitrode.WordGenerator
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.*
import java.time.Instant
import java.util.*

fun aRoutineTemplate(block: RoutineTemplateBuilder.() -> Unit = {}): RoutineTemplate =
    RoutineTemplateBuilder().apply(block).build()

fun aRoutineInstance(block: RoutineInstanceBuilder.() -> Unit = {}): RoutineInstance =
    RoutineInstanceBuilder().apply(block).build()

fun aRoutinePhase(block: RoutinePhaseBuilder.() -> Unit = {}): RoutinePhase =
    RoutinePhaseBuilder().apply(block).build()

fun aParameterRequestStep(block: ParameterRequestStepBuilder.() -> Unit = {}): ParameterRequestStep =
    ParameterRequestStepBuilder().apply(block).build()

fun anActionStep(block: ActionRoutineStepBuilder.() -> Unit = {}): ActionRoutineStep =
    ActionRoutineStepBuilder().apply(block).build()

fun aMessageStep(block: MessageRoutineStepBuilder.() -> Unit = {}): MessageRoutineStep =
    MessageRoutineStepBuilder().apply(block).build()

class RoutineTemplateBuilder {
    var title: String = "Morning Routine " + WordGenerator().newWord(4)
    var version: String = "1.0"
    var description: String = "A simple routine to start the day right."
    var phases: List<RoutinePhase> = listOf(aRoutinePhase())
    var triggers: List<RoutineTrigger> = emptyList()

    fun build(): RoutineTemplate = RoutineTemplate(
        _id = UUID.randomUUID().toString(),
        title = title,
        version = version,
        description = description,
        phases = phases,
        triggers = triggers
    )
}

class RoutineInstanceBuilder {
    var friendshipId: FriendshipId = FriendshipId()
    var template: RoutineTemplate = aRoutineTemplate()
    var currentPhaseId: RoutinePhaseId? = template.phases.first().id
    var parameters: Map<String, Any> = emptyMap()
    var progress: RoutineProgress = RoutineProgress(
        if (currentPhaseId != null) listOf(PhaseIterationProgress(currentPhaseId!!, Instant.now())) else emptyList()
    )
    var concepts: List<RoutineConcept> = emptyList()

    fun build(): RoutineInstance = RoutineInstance(
        _id = UUID.randomUUID().toString(),
        friendshipId = friendshipId,
        templateId = template.templateId,
        currentPhaseId = currentPhaseId,
        parameters = parameters,
        progress = progress,
        concepts = concepts
    )
}

class RoutinePhaseBuilder {
    var title: String = "Get Ready " + WordGenerator().newWord(4)
    var steps: List<RoutineStep> = listOf(aParameterRequestStep())
    var condition: TriggerCondition? = null

    fun build(): RoutinePhase = RoutinePhase(
        title = title,
        steps = steps,
        condition = condition
    )
}

class ParameterRequestStepBuilder {
    var question: String = "What time do you want to wake up? " + WordGenerator().newWord(4)
    var parameterKey: String = "wakeUpTime"
    var parameterType: RoutineParameterType = RoutineParameterType.LOCAL_TIME
    var timeOfDay: TimeOfDay? = null

    fun build(): ParameterRequestStep = ParameterRequestStep(
        question = question,
        parameterKey = parameterKey,
        parameterType = parameterType,
        timeOfDay = timeOfDay
    )
}

class ActionRoutineStepBuilder {
    var message: String = "Time to do something! " + WordGenerator().newWord(4)
    var expectConfirmation: Boolean = false
    var timeOfDay: TimeOfDay? = null

    fun build(): ActionRoutineStep = ActionRoutineStep(
        message = message,
        expectConfirmation = expectConfirmation,
        timeOfDay = timeOfDay
    )
}

class MessageRoutineStepBuilder {
    var message: String = "Just a friendly reminder. " + WordGenerator().newWord(4)
    var timeOfDay: TimeOfDay? = null

    fun build(): MessageRoutineStep = MessageRoutineStep(
        message = message,
        timeOfDay = timeOfDay
    )
} 