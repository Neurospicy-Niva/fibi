package icu.neurospicy.fibi.domain.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Duration
import java.time.Instant

/**
 * Reminder linked to appointments via keyword matching.
 * The reminder is triggered [offset] before/after an appointment
 * if the title matches [matchingTitleKeywords].
 */
@Document(collection = "appointment-reminder")
data class AppointmentReminder(
    @Id val _id: String? = null,
    val owner: FriendshipId,
    val matchingTitleKeywords: Set<String>,
    val text: String,
    val offset: Duration = Duration.ofMinutes(15),
    val remindBeforeAppointment: Boolean = true,
    val relatedAppointmentIds: Set<AppointmentId> = emptySet(),
    val createdAt: Instant = Instant.now(),
) {
    fun matches(title: String): Boolean = matchingTitleKeywords.any { title.contains(it, ignoreCase = true) }
}