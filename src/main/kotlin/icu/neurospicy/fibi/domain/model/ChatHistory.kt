package icu.neurospicy.fibi.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*


@Document
data class ChatHistory(
    @Id
    val _id: String?,
    val friendshipId: FriendshipId,
    val timeline: Stack<Message>
)

