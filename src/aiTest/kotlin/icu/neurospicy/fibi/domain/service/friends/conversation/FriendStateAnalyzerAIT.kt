package icu.neurospicy.fibi.domain.service.friends.conversation

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.service.friends.communication.FriendStateAnalyzer
import icu.neurospicy.fibi.domain.service.friends.communication.Mood
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class FriendStateAnalyzerAIT : BaseAIT() {
    @Autowired
    private lateinit var friendStateAnalyzer: FriendStateAnalyzer

    @ParameterizedTest
    @MethodSource("message to mood")
    fun `should estimate mood correctly when given example messages`(message: String, highestMood: Mood) =
        runBlocking<Unit> {
            val answer = friendStateAnalyzer.analyze(
                listOf(
                    UserMessage(
                        SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
                    )
                ), emptyList()
            )

            println("Expected mood: $highestMood")
            println("Emotions: ${answer.emotions}")
            assertThat(answer.emotions).anyMatch { it.mood == highestMood }
            assertThat(answer.emotions.first { it.mood == highestMood }.confidence).isGreaterThan(0.7f)
            assertThat(answer.emotions.first { it.confidence == answer.emotions.maxOf { it.confidence } }.mood).isEqualTo(
                highestMood
            )
        }

    @ParameterizedTest
    @MethodSource("message and question to expected answer")
    fun `should answer question correctly`(message: String, question: String, expectedAnswer: Boolean) =
        runBlocking<Unit> {
            val answer = friendStateAnalyzer.analyze(
                listOf(
                    UserMessage(
                        SignalMessageId(Instant.now().epochSecond), text = message, channel = Channel.SIGNAL
                    )
                ), listOf(question)
            )

            println("Expected mood: $expectedAnswer")
            println("Emotions: ${answer.emotions}")
            assertThat(answer.answers.first().answer).isEqualTo(expectedAnswer)
        }

    companion object {
        @JvmStatic
        fun `message and question to expected answer`() = listOf(
            Arguments.of(
                "I feel good about skipping the routine. Delete the breakfast task. Let’s move on with my focus.",
                "Did the user intentionally delete the task?",
                true
            ),
            Arguments.of(
                "I feel good about skipping the routine. Delete the breakfast task. Let’s move on.",
                "Did the user intentionally skip the routine?",
                true
            ),
            Arguments.of(
                "Delete the fucking task to do breakfast!",
                "The user deleted a task which is key part of a routine. Did the user explicitly intend to stop the routine?",
                false
            ),
            Arguments.of(
                "I feel good about skipping the routine. Delete the breakfast task. Let’s move on with my focus.",
                "The user deleted a task which is key part of a routine. Did the user explicitly intend to stop the routine?",
                true
            ),
        )

        @JvmStatic
        fun `message to mood`() = listOf(
            Arguments.of(
                "I feel good about skipping the routine. Delete the breakfast task.",
                Mood.Calm
            ),
            Arguments.of("Just delete the task to do breakfast", Mood.Neutral),
            Arguments.of("Yeah! I just had a great breakfast!! Let's delete the related task.", Mood.Energized),
            Arguments.of("Delete the fucking task to do breakfast!", Mood.Angry),
            Arguments.of(
                "Damn, a lot to do today. Delete the task to do breakfast! I can't handle that, too", Mood.Stressed
            ),
            Arguments.of("Oh dear. I am a loser. I can't do breakfast today. Delete the task, please.", Mood.Sad),
        )
    }
}