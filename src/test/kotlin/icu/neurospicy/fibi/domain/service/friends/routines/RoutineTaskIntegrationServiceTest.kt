package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.model.events.TaskCompleted
import icu.neurospicy.fibi.domain.model.events.TaskRemoved
import icu.neurospicy.fibi.domain.repository.ChatRepository
import icu.neurospicy.fibi.domain.service.friends.communication.*
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContextRepository
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTaskIntegrationService.Companion.DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTaskIntegrationService.Companion.IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTaskIntegrationService.Companion.IS_USER_STRESSED_QUESTION
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTaskIntegrationService.Companion.WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION
import icu.neurospicy.fibi.domain.service.friends.routines.events.ConfirmedActionStep
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalTime
import java.util.*

class RoutineTaskIntegrationServiceTest {
    private val instanceRepository: RoutineRepository = mockk()
    private val templateRepository: RoutineTemplateRepository = mockk()
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val eventLog: RoutineEventLog = mockk(relaxed = true)
    private val friendStateAnalyzer: FriendStateAnalyzer = mockk()
    private val chatRepository: ChatRepository = mockk()
    private val goalContextRepository: GoalContextRepository = mockk()

    private lateinit var handler: RoutineTaskIntegrationService

    @BeforeEach
    fun setup() {
        handler = RoutineTaskIntegrationService(
            instanceRepository = instanceRepository,
            templateRepository = templateRepository,
            eventPublisher = eventPublisher,
            eventLog = eventLog,
            goalContextRepository = goalContextRepository,
            friendStateAnalyzer = friendStateAnalyzer,
            chatRepository = chatRepository
        )
        clearAllMocks()
    }

    @Test
    fun `marks step as completed and emits confirmation event when matching TaskRoutineConcept exists`() {
        val taskId = UUID.randomUUID().toString()
        val actionRoutineStep = ActionRoutineStep(
            "Buy breakfast for tomorrow", true, timeOfDay = TimeOfDayLocalTime(LocalTime.of(14, 0))
        )
        val phase = RoutinePhase(
            "Breakfast", steps = listOf(actionRoutineStep)
        )
        val friendshipId = FriendshipId()
        val concept = TaskRoutineConcept(taskId, actionRoutineStep.id)
        val template = RoutineTemplate(
            UUID.randomUUID().toString(),
            "Morning Routine",
            "v1",
            description = "desc",
            phases = listOf(phase),
        )
        val instance = instanceWith(template, friendshipId, listOf(concept))
        val event =
            TaskCompleted(this.javaClass, friendshipId, Task(taskId, friendshipId, actionRoutineStep.description))

        every { instanceRepository.findByConceptRelatedToTask(friendshipId, taskId) } returns listOf(instance)
        every { instanceRepository.save(any()) } just Runs
        every { eventPublisher.publishEvent(any()) } just Runs
        every { eventLog.log(any()) } just Runs

        handler.handleTaskCompleted(event)

        verify {
            instanceRepository.findByConceptRelatedToTask(friendshipId, taskId)
            instanceRepository.save(withArg {
                assertThat(it.progress.iterations.first().completedSteps).anySatisfy { c ->
                    assertThat(c.id).isEqualTo(actionRoutineStep.id)
                }
            })
            eventPublisher.publishEvent(match<ConfirmedActionStep> {
                it.friendshipId == friendshipId && it.instanceId == instance.instanceId && it.phaseId == phase.id && it.stepId == actionRoutineStep.id
            })
            eventLog.log(match {
                it.routineInstanceId == instance.instanceId && it.friendshipId == friendshipId && it.event == RoutineEventType.ACTION_STEP_CONFIRMED && it.metadata["stepId"] == actionRoutineStep.id
            })
        }
    }

    @Test
    fun `does nothing when no matching TaskRoutineConcept exists`() {
        val friendshipId = FriendshipId()
        val taskId = UUID.randomUUID().toString()
        val event = TaskCompleted(
            this.javaClass, friendshipId, Task(id = taskId, owner = friendshipId, title = "Buy breakfast for tomorrow")
        )

        every { instanceRepository.findByConceptRelatedToTask(friendshipId, taskId) } returns emptyList()

        handler.handleTaskCompleted(event)

        verify(exactly = 1) { instanceRepository.findByConceptRelatedToTask(friendshipId, taskId) }
        verify(exactly = 0) { instanceRepository.save(any()) }
        verify {
            eventPublisher wasNot Called
            eventLog wasNot Called
        }
    }

    @Test
    fun `handleTaskCompleted does not duplicate step completion`() {
        val taskId = UUID.randomUUID().toString()
        val actionRoutineStep = ActionRoutineStep(
            "Buy breakfast for tomorrow", true, timeOfDay = TimeOfDayLocalTime(LocalTime.of(14, 0))
        )
        val phase = RoutinePhase(
            "Breakfast", steps = listOf(actionRoutineStep)
        )
        val friendshipId = FriendshipId()
        val concept = TaskRoutineConcept(taskId, actionRoutineStep.id)
        val template = RoutineTemplate(
            UUID.randomUUID().toString(),
            "Morning Routine",
            "v1",
            description = "desc",
            phases = listOf(phase),
        )

        val completedStep = Completion(actionRoutineStep.id, Instant.now().minusSeconds(60))
        val phaseIteration = PhaseIterationProgress(
            phaseId = phase.id,
            iterationStart = Instant.now().minusSeconds(300),
            completedSteps = listOf(completedStep)
        )
        val progress = RoutineProgress(iterations = listOf(phaseIteration))
        val instance = RoutineInstance(
            _id = UUID.randomUUID().toString(),
            templateId = template.templateId,
            friendshipId = friendshipId,
            currentPhaseId = phase.id,
            concepts = listOf(concept),
            progress = progress
        )

        val event =
            TaskCompleted(this.javaClass, friendshipId, Task(taskId, friendshipId, actionRoutineStep.description))

        every { instanceRepository.findByConceptRelatedToTask(friendshipId, taskId) } returns listOf(instance)
        every { instanceRepository.save(any()) } just Runs
        every { eventPublisher.publishEvent(any()) } just Runs
        every { eventLog.log(any()) } just Runs

        handler.handleTaskCompleted(event)

        verify {
            instanceRepository.save(withArg { savedInstance ->
                assertThat(savedInstance.concepts).isEmpty()
                assertThat(savedInstance.progress.iterations.first().completedSteps).hasSize(1)
                assertThat(savedInstance.progress.iterations.first().completedSteps.first().id).isEqualTo(
                    actionRoutineStep.id
                )
            })
        }
    }

    @Test
    fun `onTaskRemoved pauses routine if pauseHelpful is true`() {
        val friendshipId = FriendshipId()
        val step = ActionRoutineStep("Prepare breakfast", expectConfirmation = true)
        val template = routineWith(listOf(step))
        val taskId = UUID.randomUUID().toString()
        val task = Task(taskId, friendshipId, step.description)
        val instance = instanceWith(template, friendshipId, listOf(TaskRoutineConcept(taskId, step.id)))

        runOnTaskRemovedTest(
            task = task,
            instance = instance,
            template = template,
            analysisResult = FriendStateAnalysisResult(
                emotions = listOf(MoodEstimate(Mood.Neutral, 0.5f)), answers = listOf(
                    YesOrNoQuestionToAnswer(DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION, false),
                    YesOrNoQuestionToAnswer(IS_USER_STRESSED_QUESTION, false),
                    YesOrNoQuestionToAnswer(
                        WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace("\$taskTitle", task.title), false
                    ),
                    YesOrNoQuestionToAnswer(IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION, true),
                )
            ),
            expectedMessageContains = "pausing the routine now seems helpful"
        )
    }

    @Test
    fun `onTaskRemoved pauses routine if stressed is true`() {
        val friendshipId = FriendshipId()
        val step = ActionRoutineStep("Prepare breakfast", expectConfirmation = true)
        val template = routineWith(listOf(step))
        val taskId = UUID.randomUUID().toString()
        val task = Task(taskId, friendshipId, step.description)
        val instance = instanceWith(template, friendshipId, listOf(TaskRoutineConcept(taskId, step.id)))

        runOnTaskRemovedTest(
            task = task,
            instance = instance,
            template = template,
            analysisResult = FriendStateAnalysisResult(
                emotions = listOf(MoodEstimate(Mood.Stressed, 0.9f)), answers = listOf(
                    YesOrNoQuestionToAnswer(DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION, false),
                    YesOrNoQuestionToAnswer(IS_USER_STRESSED_QUESTION, true),
                    YesOrNoQuestionToAnswer(
                        WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace("\$taskTitle", task.title), false
                    ),
                    YesOrNoQuestionToAnswer(IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION, false),
                )
            ),
            expectedMessageContains = "seem to be overwhelmed or emotionally burdened"
        )
    }

    @Test
    fun `onTaskRemoved responds with restore suggestion if mistakenDelete and not intendedStop`() {
        val friendshipId = FriendshipId()
        val step = ActionRoutineStep("Prepare breakfast", expectConfirmation = true)
        val template = routineWith(listOf(step))
        val taskId = UUID.randomUUID().toString()
        val task = Task(taskId, friendshipId, step.description)
        val instance = instanceWith(template, friendshipId, listOf(TaskRoutineConcept(taskId, step.id)))
        every { goalContextRepository.saveContext(friendshipId, any()) } just Runs

        runOnTaskRemovedTest(
            task = task, instance = instance, template = template, analysisResult = FriendStateAnalysisResult(
                emotions = listOf(MoodEstimate(Mood.Neutral, 0.5f)), answers = listOf(
                    YesOrNoQuestionToAnswer(DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION, false),
                    YesOrNoQuestionToAnswer(IS_USER_STRESSED_QUESTION, false),
                    YesOrNoQuestionToAnswer(
                        WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace("\$taskTitle", task.title), true
                    ),
                    YesOrNoQuestionToAnswer(IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION, false),
                )
            ), expectedMessageContains = "the task has been deleted by mistake.", expectedGoalContextCheck = {
                assertThat(goal!!.intent).isEqualTo(RoutineIntents.StopRoutineToday)
                assertThat(subtasks).hasSize(1)
                assertThat(subtaskClarificationQuestions).hasSize(1)
            })
    }

    @Test
    fun `onTaskRemoved asks about reminder if intendedStop and not userStressed`() {
        val friendshipId = FriendshipId()
        val step = ActionRoutineStep("Prepare breakfast", expectConfirmation = true)
        val template = routineWith(listOf(step))
        val taskId = UUID.randomUUID().toString()
        val task = Task(taskId, friendshipId, step.description)
        val instance = instanceWith(template, friendshipId, listOf(TaskRoutineConcept(taskId, step.id)))
        every { goalContextRepository.saveContext(friendshipId, any()) } just Runs

        runOnTaskRemovedTest(
            task = task, instance = instance, template = template, analysisResult = FriendStateAnalysisResult(
                emotions = listOf(MoodEstimate(Mood.Neutral, 0.5f)), answers = listOf(
                    YesOrNoQuestionToAnswer(DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION, true),
                    YesOrNoQuestionToAnswer(IS_USER_STRESSED_QUESTION, false),
                    YesOrNoQuestionToAnswer(
                        WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace("\$taskTitle", task.title), false
                    ),
                    YesOrNoQuestionToAnswer(IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION, false),
                )
            ), expectedMessageContains = "they explicitly intended to stop the routine", expectedGoalContextCheck = {
                assertThat(goal!!.intent).isEqualTo(RoutineIntents.StopRoutineToday)
                assertThat(subtasks).hasSize(1)
                assertThat(subtaskClarificationQuestions).hasSize(1)
                assertThat(
                    subtasks.first().parameters.entries.containsAll(
                        mapOf(
                            "taskId" to taskId, "stepId" to step.id, "routineInstanceId" to instance.instanceId
                        ).entries
                    )
                )
            })
    }

    @Test
    fun `onTaskRemoved asks to recreate task or to remind later if user might not know connection to routine`() {
        val friendshipId = FriendshipId()
        val step = ActionRoutineStep("Prepare breakfast", expectConfirmation = true)
        val template = routineWith(listOf(step))
        val taskId = UUID.randomUUID().toString()
        val task = Task(taskId, friendshipId, step.description)
        val instance = instanceWith(template, friendshipId, listOf(TaskRoutineConcept(taskId, step.id)))
        every { goalContextRepository.saveContext(friendshipId, any()) } just Runs

        runOnTaskRemovedTest(
            task = task,
            instance = instance,
            template = template,
            analysisResult = FriendStateAnalysisResult(
                emotions = listOf(MoodEstimate(Mood.Calm, 0.5f)), answers = listOf(
                    YesOrNoQuestionToAnswer(DID_USER_INTEND_TO_STOP_ROUTINE_QUESTION, false),
                    YesOrNoQuestionToAnswer(IS_USER_STRESSED_QUESTION, false),
                    YesOrNoQuestionToAnswer(
                        WAS_DELETION_OF_TASK_A_MISTAKE_QUESTION.replace("\$taskTitle", task.title), false
                    ),
                    YesOrNoQuestionToAnswer(IS_STOPPING_ROUTINE_HELPFUL_DUE_TO_OVERWHELM_QUESTION, false),
                )
            ),
            expectedMessageContains = "The friend might not know about the connection to the routine",
            expectedGoalContextCheck = {
                assertThat(goal!!.intent).isEqualTo(RoutineIntents.StopRoutineToday)
                assertThat(subtasks).hasSize(1)
                assertThat(subtaskClarificationQuestions).hasSize(1)
            })
    }

    @Test
    fun `onTaskRemoved does nothing when task is already completed or no connected routine`() {
        val friendshipId = FriendshipId()
        handler.handleTaskRemoved(
            TaskRemoved(
                this.javaClass, friendshipId, Task(
                    UUID.randomUUID().toString(),
                    friendshipId,
                    "Buy breakfast for tomorrow",
                    completed = true,
                    completedAt = Instant.now()
                )
            )
        )
        every { instanceRepository.findByConceptRelatedToTask(any(), any()) } returns emptyList()
        handler.handleTaskRemoved(
            TaskRemoved(
                this.javaClass, friendshipId, Task(
                    UUID.randomUUID().toString(),
                    friendshipId,
                    "Buy breakfast for tomorrow",
                )
            )
        )
        verify {
            templateRepository wasNot Called
            friendStateAnalyzer wasNot Called
            eventPublisher wasNot Called
        }
    }

    private fun runOnTaskRemovedTest(
        task: Task,
        instance: RoutineInstance,
        template: RoutineTemplate,
        analysisResult: FriendStateAnalysisResult,
        expectedEventType: RoutineEventType? = null,
        expectedMessageContains: String,
        expectedGoalContextCheck: (GoalContext.() -> Unit)? = null,
    ) = runBlocking {
        val friendshipId = task.owner
        val history = Stack<Message>()
        history.push(
            UserMessage(
                SignalMessageId(Instant.now().epochSecond), Instant.now(), "Something about the task...", SIGNAL
            )
        )
        every { chatRepository.findHistory(any()) } returns ChatHistory("history", friendshipId, history)
        every { instanceRepository.findByConceptRelatedToTask(friendshipId, task.id!!) } returns listOf(instance)
        every { templateRepository.findById(template.templateId) } returns template
        coEvery { friendStateAnalyzer.analyze(any(), any()) } returns analysisResult

        handler.handleTaskRemoved(TaskRemoved(this.javaClass, friendshipId, task))

        if (expectedEventType != null) {
            verify {
                eventLog.log(
                    match {
                        it.event == expectedEventType && it.friendshipId == friendshipId && it.routineInstanceId == instance.instanceId
                    })
            }
        }
        verify {
            eventPublisher.publishEvent(
                match<SendMessageCmd> {
                    it.friendshipId == friendshipId && it.outgoingMessage is OutgoingGeneratedMessage && it.outgoingMessage.messageDescription.contains(
                        expectedMessageContains
                    )
                })
        }
        expectedGoalContextCheck?.let { check ->
            verify {
                goalContextRepository.saveContext(friendshipId, match {
                    it.apply(check)
                    true
                })
            }
        }
    }

    private fun instanceWith(
        template: RoutineTemplate,
        friendshipId: FriendshipId,
        concepts: List<TaskRoutineConcept> = emptyList(),
    ): RoutineInstance = RoutineInstance(
        _id = UUID.randomUUID().toString(),
        templateId = template.templateId,
        friendshipId = friendshipId,
        currentPhaseId = template.phases.first().id,
        concepts = concepts
    )

    private fun routineWith(
        steps: List<RoutineStep> = listOf(ActionRoutineStep(message = "Take breakfast")),
    ): RoutineTemplate = RoutineTemplate(
        _id = UUID.randomUUID().toString(),
        title = "Morning routine",
        version = "1.0",
        description = "Start your day",
        phases = listOf(
            RoutinePhase(
                title = "Easy breakfast", condition = AfterDays(1), steps = steps
            )
        )
    )
}
