package icu.neurospicy.fibi.outgoing.signal

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId

data class ConfirmSignalMessageReceived(
    val friendshipId: FriendshipId,
    val messageId: SignalMessageId
)
