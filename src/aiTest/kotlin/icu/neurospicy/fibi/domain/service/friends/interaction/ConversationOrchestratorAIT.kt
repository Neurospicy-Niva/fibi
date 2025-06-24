package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.BaseAIT
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.model.events.IncomingFriendMessageReceived
import icu.neurospicy.fibi.outgoing.signal.SignalMessageSender
import io.mockk.spyk
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class ConversationOrchestratorAIT : BaseAIT() {
    @Autowired
    lateinit var conversationOrchestrator: ConversationOrchestrator

    @Autowired
    lateinit var contextRepository: GoalContextRepository

    @Autowired
    lateinit var signalMessageSender: SignalMessageSender

    @Test
    fun `should handle subtask clarification needed`() {
        val s = spyk(signalMessageSender)
        // Act
        conversationOrchestrator.onMessage(
            incomingFriendMessageReceived(
                Instant.now(),
                "Add task \"Fucking LLMS!\" being done"
            )
        )
        conversationOrchestrator.onMessage(incomingFriendMessageReceived(Instant.now(), "Clean up my tasks"))
        // Assert
        assertThat(contextRepository.loadContext(friendshipId)!!.subtasks).anyMatch { it.needsClarification() }
        assertThat(contextRepository.loadContext(friendshipId)!!.subtaskClarificationQuestions).isNotEmpty
        // Act
        conversationOrchestrator.onMessage(incomingFriendMessageReceived(Instant.now(), "Yes"))
        // Assert
        assertThat(contextRepository.loadContext(friendshipId)!!.goal).isNull()
    }

    private fun incomingFriendMessageReceived(
        receivedAt: Instant,
        text: String,
    ): IncomingFriendMessageReceived = IncomingFriendMessageReceived(
        friendshipId, UserMessage(
            SignalMessageId(receivedAt.toEpochMilli()),
            receivedAt,
            text,
            Channel.SIGNAL
        )
    )
}