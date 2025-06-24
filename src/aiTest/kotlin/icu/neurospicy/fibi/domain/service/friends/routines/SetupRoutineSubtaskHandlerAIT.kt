package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.interaction.*
import icu.neurospicy.fibi.domain.service.friends.routines.ParameterRequestStep.Companion.setupParameterRequest
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(MockitoJUnitRunner::class)
class SetupRoutineSubtaskHandlerAIT : BaseAIT() {
    lateinit var setupRoutineSubtaskHandler: SetupRoutineSubtaskHandler

    @Autowired
    lateinit var templateRepository: RoutineTemplateRepository

    @Autowired
    lateinit var extractParamFromConversationService: ExtractParamFromConversationService

    @MockK
    lateinit var setupRoutineMock: SetupRoutine

    @BeforeEach
    fun beforeEach() {
        templateRepository.loadAll().forEach { templateRepository.remove(it._id!!) }
        setupRoutineSubtaskHandler =
            SetupRoutineSubtaskHandler(templateRepository, setupRoutineMock, extractParamFromConversationService)
    }

    @ParameterizedTest
    @MethodSource("conversation for parameters and expectation")
    fun `verify clarifies parameters and uses them to set up routine`(
        parameterQuestion: String,
        answer: String,
        parameterType: RoutineParameterType,
        parameterKey: String,
        expectedValue: Any,
    ) = runBlocking<Unit> {
        val parameterStep = setupParameterRequest(
            description = parameterQuestion, parameterKey = parameterKey, parameterType = parameterType
        )
        val template = templateRepository.save(
            RoutineTemplate(
                title = "Focused start in the day",
                version = "1.0",
                description = "Configurable routine",
                setupSteps = listOf(parameterStep),
                phases = listOf(
                    RoutinePhase(
                        title = "Start", condition = AfterDays(1), steps = listOf()
                    )
                )
            )
        )

        val friendshipId = FriendshipId()
        val subtaskId = SubtaskId.from(
            friendshipId, RoutineIntents.Setup, SignalMessageId(Instant.now().epochSecond)
        )
        val setupRoutineSubtask = Subtask(
            subtaskId, RoutineIntents.Setup, "Set up routine", emptyMap()
        )
        every { setupRoutineMock.execute(any(), any(), any()) } returns mockk(relaxed = true)
        // Act
        val result = setupRoutineSubtaskHandler.tryResolveClarification(
            setupRoutineSubtask, SubtaskClarificationQuestion(
                parameterQuestion, subtaskId
            ), UserMessage(
                SignalMessageId(Instant.now().epochSecond), Instant.now(), answer, Channel.SIGNAL
            ), GoalContext(
                Goal(RoutineIntents.Start),
                subtasks = listOf(setupRoutineSubtask),
                parameters = mapOf("routineTemplateId" to template.templateId)
            ), friendshipId
        )
        // Assert
        coVerify {
            setupRoutineMock.execute(template.templateId, friendshipId, match {
                println("${it[parameterKey]!!.javaClass}: ${it[parameterKey]}")
                when (expectedValue) {
                    is String -> (it[parameterKey] as String).contains(expectedValue as String, ignoreCase = true)
                    else -> it[parameterKey] == expectedValue
                }
            })
        }
    }

    @AfterEach
    fun cleanup() {
        super.tearDown()
        templateRepository.loadAll().forEach { templateRepository.remove(it._id!!) }
    }

    companion object {
        @JvmStatic
        fun `conversation for parameters and expectation`() = listOf(
            Arguments.of(
                "What do you usually eat for breakfast?", "I love british breakfast with ham and eggs",
                RoutineParameterType.STRING, "meal", "ham and eggs"
            ),
            Arguments.of(
                "Do you usually take breakfast?", "I love breakfast, yes.", RoutineParameterType.BOOLEAN,
                "takesBreakfast", true
            ),
            Arguments.of(
                "When do you want to start your morning routine?",
                "I usually wake up early and want to start at 5:15 am",
                RoutineParameterType.LOCAL_TIME,
                "wakeUpTime",
                LocalTime.of(5, 15)
            ),
            Arguments.of(
                "When is your friends next birthday",
                "On 31th of December. It starts at 8 pm",
                RoutineParameterType.DATE,
                "party_datetime",
                LocalTime.of(20, 0).atDate(LocalDate.of(Instant.now().atZone(ZoneOffset.UTC).year, 12, 31))
            ),
            Arguments.of("How long do you want to do workout?", "20 minutes", RoutineParameterType.INT, "duration", 20),
            Arguments.of(
                "How many kilometers do you want to run?",
                "I want to achieve 12.05 km",
                RoutineParameterType.FLOAT,
                "distance",
                12.05f
            )

        )
    }
}