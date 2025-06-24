package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContextRepository
import icu.neurospicy.fibi.domain.service.friends.routines.builders.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepTriggered
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.util.*

@ExperimentalCoroutinesApi
class RoutineStepExecutorTest {

    private val instanceRepository = mockk<RoutineRepository>()
    private val templateRepository = mockk<RoutineTemplateRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val eventLog = mockk<RoutineEventLog>(relaxed = true)
    private val goalContextRepository = mockk<GoalContextRepository>()
    private val taskRepository = mockk<TaskRepository>()

    private val stepExecutor = RoutineStepExecutor(
        instanceRepository,
        templateRepository,
        eventPublisher,
        eventLog,
        goalContextRepository,
        taskRepository
    )

    @Test
    fun `step is confirmed and logged when confirmation is expected`() {
        val friendshipId = FriendshipId("friend-1")
        val template = aRoutineTemplate {
            this.phases = listOf(
                aRoutinePhase {
                    this.condition = AfterDays(1)
                    this.steps = listOf(
                        anActionStep {
                            this.expectConfirmation = true
                            this.timeOfDay = null
                        }
                    )
                }
            )
        }
        val step = template.phases.first().steps.first() as ActionRoutineStep
        val phase = template.phases.first()
        val taskId: String = UUID.randomUUID().toString()
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.friendshipId = friendshipId
        }
        coEvery { instanceRepository.findById(friendshipId, instance.instanceId) } returns instance
        coEvery { templateRepository.findById(template.templateId) } returns template
        coEvery { taskRepository.save(any()) } returns mockk(relaxed = true) { every { id } returns taskId }
        coEvery { instanceRepository.save(any()) } returns mockk(relaxed = true)

        val event = RoutineStepTriggered(
            _source = this.javaClass,
            friendshipId = friendshipId,
            instanceId = instance.instanceId,
            phaseId = phase.id,
            stepId = step.id
        )
        stepExecutor.executeStep(event)

        // Should log the action step message sent
        coVerify {
            eventLog.log(withArg {
                assertThat(it.routineInstanceId).isEqualTo(instance.instanceId)
                assertThat(it.friendshipId).isEqualTo(friendshipId)
                assertThat(it.event).isEqualTo(RoutineEventType.ACTION_STEP_MESSAGE_SENT)
                assertThat(it.metadata["phaseId"]).isEqualTo(phase.id)
                assertThat(it.metadata["stepId"]).isEqualTo(step.id)
            })
        }
        coVerify {
            taskRepository.save(match {
                it.title == step.description && it.owner == friendshipId
            })
        }
        coVerify {
            instanceRepository.save(match {
                it.concepts.any { concept -> concept.linkedEntityId == taskId && concept.linkedStep == step.id && concept is TaskRoutineConcept } && it.friendshipId == friendshipId
            })
        }
        coVerify {
            eventPublisher.publishEvent(match {
                it is SendMessageCmd && it.friendshipId == friendshipId && it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains(
                    step.description
                )
            })
        }
    }

    @Test
    fun `throws when instance not found`() {
        val friendshipId = FriendshipId("friend-404")
        val instanceId = RoutineInstanceId("nonexistent")
        val phaseId = RoutinePhaseId.forTitle("Nonexistent Phase")
        val stepId = RoutineStepId.forDescription("Nonexistent Step")
        coEvery { instanceRepository.findById(friendshipId, instanceId) } returns null
        val event = RoutineStepTriggered(
            _source = this.javaClass,
            friendshipId = friendshipId,
            instanceId = instanceId,
            phaseId = phaseId,
            stepId = stepId
        )
        try {
            stepExecutor.executeStep(event)
            assertThat(false).`as`("Should throw IllegalStateException").isTrue()
        } catch (ex: IllegalStateException) {
            assertThat(ex.message).contains("Routine instance with id")
        }
        // No log/event published
        coVerify(exactly = 0) { eventLog.log(any()) }
        coVerify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `throws when step not part of current phase`() {
        val friendshipId = FriendshipId("friend-2")
        val template = aRoutineTemplate {
            this.phases = listOf(
                aRoutinePhase {
                    this.condition = AfterDays(1)
                }
            )
        }
        val phase = template.phases.first()
        val instance = aRoutineInstance {
            this.template = template
            this.friendshipId = friendshipId
            this.currentPhaseId = phase.id
        }
        coEvery { instanceRepository.findById(friendshipId, instance.instanceId) } returns instance
        coEvery { templateRepository.findById(template.templateId) } returns template
        val event = RoutineStepTriggered(
            _source = this.javaClass,
            friendshipId = friendshipId,
            instanceId = instance.instanceId,
            phaseId = phase.id,
            stepId = RoutineStepId.forDescription("not-in-phase")
        )
        try {
            stepExecutor.executeStep(event)
            assertThat(false).`as`("Should throw IllegalStateException").isTrue()
        } catch (ex: IllegalStateException) {
            assertThat(ex.message).contains("Step")
        }
        coVerify(exactly = 0) { eventLog.log(any()) }
        coVerify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `message step is sent and logged correctly`() {
        val friendshipId = FriendshipId("friend-3")
        val message = "Remember to smile"
        val template = aRoutineTemplate {
            this.phases = listOf(
                aRoutinePhase {
                    this.condition = AfterDays(1)
                    this.steps = listOf(
                        aMessageStep {
                            this.message = message
                            this.timeOfDay = null
                        }
                    )
                }
            )

        }
        val step = template.phases.first().steps.first() as MessageRoutineStep
        val phase = template.phases.first()
        val instance = aRoutineInstance {
            this.template = template
            this.currentPhaseId = phase.id
            this.friendshipId = friendshipId
        }
        coEvery { instanceRepository.findById(friendshipId, instance.instanceId) } returns instance
        coEvery { templateRepository.findById(template.templateId) } returns template
        coEvery { instanceRepository.save(any()) } just runs

        val event = RoutineStepTriggered(
            _source = this.javaClass,
            friendshipId = friendshipId,
            instanceId = instance.instanceId,
            phaseId = phase.id,
            stepId = step.id
        )
        stepExecutor.executeStep(event)

        coVerify {
            eventLog.log(withArg {
                assertThat(it.event).isEqualTo(RoutineEventType.STEP_MESSAGE_SENT)
                assertThat(it.metadata["stepId"]).isEqualTo(step.id)
            })
            instanceRepository.save(match {
                it.progress.iterations.first().completedSteps.last().id == step.id && Duration.between(
                    Instant.now(), it.progress.iterations.first().completedSteps.last().at
                ).seconds < 2
            })
            eventPublisher.publishEvent(match {
                it is SendMessageCmd && it.friendshipId == friendshipId && it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains(
                    message
                )
            })
        }
    }

    @Test
    fun `parameter request step is logged and parameter is requested`() {
        val friendshipId = FriendshipId("friend-4")
        val template = RoutineTemplate(
            _id = "template-4",
            title = "Setup Example",
            version = "v1",
            description = "desc",
            setupSteps = emptyList(),
            phases = listOf(
                RoutinePhase(
                    title = "Collect Info",
                    id = RoutinePhaseId.forTitle("Collect Info"),
                    condition = AfterDays(1),
                    steps = listOf(
                        ParameterRequestStep(
                            question = "What's your name?",
                            parameterKey = "name",
                            parameterType = RoutineParameterType.STRING,
                            timeOfDay = null
                        )
                    )
                )
            )
        )
        val step = template.phases.first().steps.first() as ParameterRequestStep
        val phase = template.phases.first()
        val instance = RoutineInstance(
            _id = "routine-4", templateId = template.templateId, friendshipId = friendshipId, currentPhaseId = phase.id
        )
        coEvery { instanceRepository.findById(friendshipId, instance.instanceId) } returns instance
        coEvery { templateRepository.findById(template.templateId) } returns template
        coEvery { goalContextRepository.saveContext(any(), any()) } just runs

        val event = RoutineStepTriggered(
            _source = this.javaClass,
            friendshipId = friendshipId,
            instanceId = instance.instanceId,
            phaseId = phase.id,
            stepId = step.id
        )
        stepExecutor.executeStep(event)

        coVerify {
            eventLog.log(withArg {
                assertThat(it.event).isEqualTo(RoutineEventType.STEP_PARAMETER_REQUESTED)
                assertThat(it.metadata["stepId"]).isEqualTo(step.id)
            })
            goalContextRepository.saveContext(friendshipId, match {
                it.goal!!.intent == RoutineIntents.AnswerQuestion && it.subtasks.first().intent == RoutineIntents.AnswerQuestion && it.subtasks.first().parameters["routineStepId"] == step.id && it.subtasks.first().parameters["routineInstanceId"] == instance.instanceId
            })
            eventPublisher.publishEvent(match {
                it is SendMessageCmd && it.friendshipId == friendshipId && it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains(
                    step.description
                )
            })
        }
    }

    @Test
    fun `action step without confirmation sends message and logs only`() {
        val friendshipId = FriendshipId("friend-5")
        val template = RoutineTemplate(
            _id = "template-5",
            title = "Water Reminder",
            version = "v1",
            description = "desc",
            setupSteps = emptyList(),
            phases = listOf(
                RoutinePhase(
                    title = "Hydration",
                    id = RoutinePhaseId.forTitle("Hydration"),
                    condition = AfterDays(1),
                    steps = listOf(
                        ActionRoutineStep(
                            message = "Just drink water!",
                            expectConfirmation = false,
                            expectedDurationMinutes = 1,
                            timeOfDay = null
                        )
                    )
                )
            )
        )
        val step = template.phases.first().steps.first() as ActionRoutineStep
        val phase = template.phases.first()
        val instance = RoutineInstance(
            _id = "routine-5", templateId = template.templateId, friendshipId = friendshipId, currentPhaseId = phase.id
        )
        coEvery { instanceRepository.findById(friendshipId, instance.instanceId) } returns instance
        coEvery { templateRepository.findById(template.templateId) } returns template

        val event = RoutineStepTriggered(
            _source = this.javaClass,
            friendshipId = friendshipId,
            instanceId = instance.instanceId,
            phaseId = phase.id,
            stepId = step.id
        )
        stepExecutor.executeStep(event)

        coVerify {
            eventPublisher.publishEvent(match {
                it is SendMessageCmd && it.friendshipId == friendshipId && it.outgoingMessage is OutgoingTextMessage && it.outgoingMessage.text.contains(
                    step.description
                )
            })
            eventLog.log(withArg {
                assertThat(it.event).isEqualTo(RoutineEventType.ACTION_STEP_MESSAGE_SENT)
                assertThat(it.metadata["stepId"]).isEqualTo(step.id)
            })
        }
    }
} 