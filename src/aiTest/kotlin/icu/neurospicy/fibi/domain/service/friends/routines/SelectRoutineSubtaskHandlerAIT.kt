package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class SelectRoutineSubtaskHandlerAIT : BaseAIT() {
    @Autowired
    private lateinit var selectRoutineSubtaskHandler: SelectRoutineSubtaskHandler

    @Autowired
    private lateinit var templateRepository: RoutineTemplateRepository

    @Test
    fun `identifies routine correctly`() {
        runBlocking<Unit> {
            val template = routineWith(
                "Morning routine", "Start a morning routine with a customizable activity", "Drink water"
            )
            templateRepository.save(template)
            val friendshipId = FriendshipId()
            val subtask = subtask(friendshipId, "I want to start a new morning routine")
            val context = GoalContext(Goal(RoutineIntents.Start), subtasks = listOf(subtask))
            //Act
            val (messagePrompt, clarificationQuestion, updatedSubtask, parameters) = selectRoutineSubtaskHandler.handle(
                subtask, context, friendshipId
            )
            // Assert
            assertThat(messagePrompt).containsIgnoringCase("morning routine")
            assertThat(parameters["routineTemplateId"]).isNotNull.isEqualTo(template.templateId)
            assertThat(clarificationQuestion).isNull()
            assertThat(updatedSubtask.completed()).isTrue()
        }
    }

    @Test
    fun `identifies routine among test data`() = runBlocking<Unit> {
        val template = routineWith(
            "Morning routine", "Start a morning routine with a customizable activity", "Drink water"
        )
        templateRepository.save(template)
        testRoutines.forEach { templateRepository.save(it) }
        val friendshipId = FriendshipId()
        val subtask = subtask(friendshipId, "I want to start a new morning routine")
        val context = GoalContext(Goal(RoutineIntents.Start), subtasks = listOf(subtask))
        //Act
        val (messagePrompt, clarificationQuestion, updatedSubtask, parameters) = selectRoutineSubtaskHandler.handle(
            subtask, context, friendshipId
        )
        // Assert
        assertThat(messagePrompt).containsIgnoringCase("morning routine")
        assertThat(parameters["routineTemplateId"]).isNotNull.isEqualTo(template.templateId)
        assertThat(clarificationQuestion).isNull()
        assertThat(updatedSubtask.completed()).isTrue()
    }

    @Test
    fun `asks for clarification when request is vague`() {
        runBlocking<Unit> {
            val template = routineWith(
                "Morning routine", "Start a morning routine with a customizable activity", "Drink water"
            )
            testRoutines.forEach { templateRepository.save(it) }
            templateRepository.save(template)
            val friendshipId = FriendshipId()
            val subtask = subtask(friendshipId, "I want to start a new routine")
            val context = GoalContext(Goal(RoutineIntents.Start), subtasks = listOf(subtask))
            //Act
            val (messagePrompt, clarificationQuestion, updatedSubtask, parameters) = selectRoutineSubtaskHandler.handle(
                subtask, context, friendshipId
            )
            // Assert
            assertThat(clarificationQuestion).isNotNull().extracting { it!!.text }.matches({ question ->
                testRoutines.all { question.contains(it.title, ignoreCase = true) }
            }, "$clarificationQuestion should contain all templates")
            assertThat(messagePrompt).isNull()
            assertThat(parameters["routineTemplateId"]).isNull()
            assertThat(updatedSubtask.completed()).isFalse()
        }
    }

    @Test
    fun `asks differentiation question if matching two similarly`() {
        runBlocking<Unit> {
            val template1 = templateRepository.save(
                routineWith(
                    "Morning routine", "Start a morning routine with a customizable activity", "Drink water"
                )
            )
            testRoutines.forEach { templateRepository.save(it) }
            val template2 = templateRepository.save(
                routineWith(
                    "Healthy morning", "Eat healthy every morning with customizable parts", "Eat the rich?"
                )
            )

            val friendshipId = FriendshipId()
            val subtask = subtask(friendshipId, "I want to start some kind of morning routine")
            val context = GoalContext(Goal(RoutineIntents.Start), subtasks = listOf(subtask))
            //Act
            val (messagePrompt, clarificationQuestion, updatedSubtask, parameters) = selectRoutineSubtaskHandler.handle(
                subtask, context, friendshipId
            )
            println(messagePrompt + "\n" + clarificationQuestion + "\n" + parameters)
            // Assert
            assertThat(clarificationQuestion).isNotNull().extracting { it!!.text }.matches { question ->
                question.contains(template1.title, ignoreCase = true) && question.contains(
                    template2.title, ignoreCase = true
                ) && testRoutines.none { question.contains(it.title, ignoreCase = true) }
            }
            assertThat(messagePrompt).isNull()
            assertThat(parameters["routineTemplateId"]).isNull()
            assertThat(updatedSubtask.completed()).isFalse()
        }
    }

    @Test
    fun `resolves clarification when answer points to one routine`() {
        runBlocking<Unit> {
            val template1 = templateRepository.save(
                routineWith(
                    "Morning routine", "Start a morning routine with a customizable activity", "Drink water"
                )
            )
            testRoutines.forEach { templateRepository.save(it) }
            val template2 = templateRepository.save(
                routineWith(
                    "Healthy morning", "Eat healthy every morning with customizable parts", "Eat the rich?"
                )
            )

            val friendshipId = FriendshipId()
            val subtask = subtask(
                friendshipId,
                "I want to start some kind of morning routine",
                listOf(template1.templateId, template2.templateId)
            )
            val context = GoalContext(Goal(RoutineIntents.Start), subtasks = listOf(subtask))
            val clarifyingAnswer = "The healthy morning routine sounds great."
            //Act
            val (clarificationQuestion, messagePrompt, _, updatedSubtask, parameters) = selectRoutineSubtaskHandler.tryResolveClarification(
                subtask,
                SubtaskClarificationQuestion(
                    "I could not find a routine for your request. These are the predefined routines:\n- ${template1.title}: ${template1.description}\n- ${template2.title}: ${template2.description}",
                    subtask.id
                ),
                UserMessage(SignalMessageId(Instant.now().epochSecond), Instant.now(), clarifyingAnswer, SIGNAL),
                context,
                friendshipId
            )
            println(messagePrompt + "\n" + clarificationQuestion + "\n" + parameters)
            // Assert
            assertThat(clarificationQuestion).isNull()
            assertThat(messagePrompt).containsIgnoringCase(template2.title)
            assertThat(parameters["routineTemplateId"]).isNotNull.isEqualTo(template2.templateId)
            assertThat(updatedSubtask.completed()).isTrue()
        }
    }

    @Test
    fun `asks for clarification again when answer still is unclear`() {
        runBlocking<Unit> {
            val template1 = templateRepository.save(
                routineWith(
                    "Morning routine", "Start a morning routine with a customizable activity", "Drink water"
                )
            )
            testRoutines.forEach { templateRepository.save(it) }
            val template2 = templateRepository.save(
                routineWith(
                    "Healthy morning", "Eat healthy every morning with customizable parts", "Eat the rich?"
                )
            )

            val friendshipId = FriendshipId()
            val subtask = subtask(
                friendshipId,
                "I want to start some kind of morning routine",
                listOf(template1.templateId, template2.templateId)
            )
            val context = GoalContext(Goal(RoutineIntents.Start), subtasks = listOf(subtask))
            val clarifyingAnswer = "Oh, that's a hard decision!"
            //Act
            val (clarificationQuestion, messagePrompt, _, updatedSubtask, parameters) = selectRoutineSubtaskHandler.tryResolveClarification(
                subtask,
                SubtaskClarificationQuestion(
                    "I could not find a routine for your request. These are the predefined routines:\n- ${template1.title}: ${template1.description}\n- ${template2.title}: ${template2.description}",
                    subtask.id
                ),
                UserMessage(SignalMessageId(Instant.now().epochSecond), Instant.now(), clarifyingAnswer, SIGNAL),
                context,
                friendshipId
            )
            println(messagePrompt + "\n" + clarificationQuestion + "\n" + parameters)
            // Assert
            assertThat(clarificationQuestion).isNotNull().extracting { it!!.text }.asString()
                .containsIgnoringCase(template1.title).containsIgnoringCase(template2.title)
            assertThat(parameters["routineTemplateId"]).isNull()
            assertThat(updatedSubtask.completed()).isFalse()
        }
    }


    private fun subtask(
        friendshipId: FriendshipId,
        msg: String,
        possibleRoutineIds: List<RoutineTemplateId>? = null,
    ): Subtask = Subtask(
        SubtaskId.from(
            friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond)
        ), RoutineIntents.Select, parameters = mapOf(
            "rawText" to msg, "possibleRoutineIds" to possibleRoutineIds
        )
    )

    val testRoutines = listOf(
        Triple("Yoga routine", "Learn yoga step by step", "First exercise: sleeping programmer"),
        Triple(
            "Daily diary", "Start your path to self-reflection", "Why do you want to improve yourself all the time?"
        ),
        Triple("Meal prep", "Stop eating shit, eat shit you made yourself", "Prepare lots of food in advance"),
        Triple("Daily exercise", "Start a routine to do your daily exercise", "First task: Bubblesort"),
        Triple("From 0 to 10", "Jogging is fun, start now", "Buy shoes and proteins in my shop"),
    ).map { routineWith(it.first, it.second, it.third) }

    @AfterEach
    fun cleanup() {
        super.tearDown()
        templateRepository.loadAll().forEach { templateRepository.remove(it._id!!) }
    }

    private fun routineWith(
        title: String,
        description: String,
        firstPhaseTitle: String,
    ): RoutineTemplate = RoutineTemplate(
        title = title, version = "1.0", description = description, phases = listOf(
            RoutinePhase(
                title = firstPhaseTitle,
                steps = listOf(ActionRoutineStep(message = firstPhaseTitle))
            )
        )
    )
}