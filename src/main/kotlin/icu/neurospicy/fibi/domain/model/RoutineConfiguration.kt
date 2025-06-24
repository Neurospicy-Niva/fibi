package icu.neurospicy.fibi.domain.model

import com.maximeroussy.invitrode.WordGenerator
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val MORNING_ROUTINE_TYPE = "Morning routine"

/**
 * Represents a configured routine (e.g., "Morning Routine").
 */
@Document(collection = "routines")
data class RoutineConfiguration(
    @Id
    val id: String? = null,
    val routineId: RoutineConfigurationId = RoutineConfigurationId(),
    val routineType: String = MORNING_ROUTINE_TYPE,
    val name: String = "Morning routine",
    val description: String = "A routine starting after wake-up.",
    val friendshipId: FriendshipId,
    val preferredChannel: Channel = Channel.SIGNAL,
    val trigger: LocalTimeBasedTrigger,
    val enabled: Boolean = false,
    val activities: List<RoutineActivityConfiguration> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)


sealed interface Trigger

data class LocalTimeBasedTrigger(
    val localTime: LocalTime,
    val timezone: ZoneId
) : Trigger

data class DateTimeBasedTrigger(
    val localTime: LocalDateTime,
    val timezone: ZoneId
) : Trigger


@JvmInline
value class RoutineConfigurationId(
    private val word: String = WordGenerator().newWord(4).lowercase() + "_"
            + WordGenerator().newWord(4).lowercase() + "_"
            + WordGenerator().newWord(4).lowercase()
) {
    override fun toString(): String = word
}