package icu.neurospicy.fibi.domain.service.onboarding

import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.model.events.IncomingCuriousMessageReceived
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant.now

@ExtendWith(MockKExtension::class)
internal class FirstContactInteractionTest {

    @MockK(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockK(relaxed = true)
    lateinit var friendshipLedger: FriendshipLedger

    @Test
    fun `sends initial welcome message on first contact`() {
        //given
        val firstContactInteraction = FirstContactInteraction(friendshipLedger, applicationEventPublisher)
        val friendshipId = FriendshipId()
        val incomingMessageReceived =
            IncomingCuriousMessageReceived(
                friendshipId, UserMessage(
                    text = "Hello fibi!",
                    messageId = SignalMessageId(now().epochSecond),
                    channel = SIGNAL
                )
            )
        //when
        firstContactInteraction.introduceTo(incomingMessageReceived)
        //then
        verify { applicationEventPublisher.publishEvent(any<SendMessageCmd>()) }
    }

    @Test
    fun `flags as acquaintance on first contact`() {
        //given
        val firstContactInteraction = FirstContactInteraction(friendshipLedger, applicationEventPublisher)
        val friendshipId = FriendshipId()
        val incomingMessageReceived =
            IncomingCuriousMessageReceived(
                friendshipId, UserMessage(
                    text = "Hello fibi!",
                    messageId = SignalMessageId(now().epochSecond),
                    channel = SIGNAL
                )
            )
        //when
        firstContactInteraction.introduceTo(incomingMessageReceived)
        //then
        verify { friendshipLedger.sentTermsOfUseRequest(friendshipId) }
    }
}