package icu.neurospicy.fibi.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "time-based-reminder")
data class Reminder(
    @Id
    val _id: String? = null,
    val owner: FriendshipId,
    val trigger: DateTimeBasedTrigger,
    val text: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    fun update(trigger: DateTimeBasedTrigger?, text: String?): Reminder = this.copy(
        trigger = trigger ?: this.trigger,
        text = text ?: this.text,
        updatedAt = Instant.now()
    )
}