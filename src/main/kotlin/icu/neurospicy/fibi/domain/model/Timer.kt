package icu.neurospicy.fibi.domain.model

import org.springframework.data.mongodb.core.mapping.Document
import java.time.Duration
import java.time.Instant

@Document(collection = "timers")
data class Timer(
    val _id: String? = null,
    val owner: FriendshipId,
    val label: String,
    val duration: Duration,
    val startedAt: Instant = Instant.now()
)
