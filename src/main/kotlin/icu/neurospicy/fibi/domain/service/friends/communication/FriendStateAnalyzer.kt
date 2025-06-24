package icu.neurospicy.fibi.domain.service.friends.communication

import icu.neurospicy.fibi.domain.model.Message

interface FriendStateAnalyzer {
    suspend fun analyze(messages: List<Message>, questions: List<String>): FriendStateAnalysisResult
}

data class FriendStateAnalysisResult(
    val answers: List<YesOrNoQuestionToAnswer> = emptyList(),
    val emotions: List<MoodEstimate>,
)

data class YesOrNoQuestionToAnswer(val question: String, val answer: Boolean?) {}
data class MoodEstimate(val mood: Mood, val confidence: Float)
enum class Mood(
    val string: String,
) {
    Neutral("neutral"),
    Calm("calm"),
    Stressed("stressed"),
    Angry("angry"),
    Sad("sad"),
    Energized("energized")
}