package icu.neurospicy.fibi.domain.model

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import java.time.Instant

data class Conversation(
    val intent: Intent,
    val messages: List<Message>,
    val lastInteraction: Instant
)