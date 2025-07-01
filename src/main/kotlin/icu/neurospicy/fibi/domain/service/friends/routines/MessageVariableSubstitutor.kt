package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant

/**
 * Service for substituting variables in routine messages.
 * Supports both parameter variables (${parameterName}) and system variables (${NOW}, ${FRIEND_NAME}, etc.).
 */
@Service
class MessageVariableSubstitutor(
    private val friendshipLedger: FriendshipLedger,
    private val taskRepository: TaskRepository,
    private val calendarRepository: CalendarRepository,
) {
    
    companion object {
        private val LOG = LoggerFactory.getLogger(MessageVariableSubstitutor::class.java)
        private val VARIABLE_PATTERN = Regex("""\$\{([^}]+)\}""")
        
        // Formatters for different time formats
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }

    /**
     * Substitutes variables in a message with actual values.
     * 
     * @param message The message containing variables to substitute
     * @param instance The routine instance containing parameters
     * @param zoneId The user's timezone
     * @return The message with variables substituted
     */
    fun substituteVariables(message: String, instance: RoutineInstance, zoneId: ZoneId): String {
        return VARIABLE_PATTERN.replace(message) { matchResult ->
            val variableName = matchResult.groupValues[1]
            val substitutedValue = when {
                // Parameter variables
                instance.parameters.containsKey(variableName) -> {
                    instance.getParameterAsString(variableName) ?: matchResult.value
                }
                
                // System time variables
                variableName == "NOW" -> LocalDateTime.now(zoneId).format(DATE_TIME_FORMATTER)
                variableName == "TODAY" -> LocalDate.now(zoneId).format(DATE_FORMATTER)
                variableName == "TIME" -> LocalDateTime.now(zoneId).format(TIME_FORMATTER)
                
                // User information
                variableName == "FRIEND_NAME" -> {
                    val ledgerEntry = friendshipLedger.findBy(instance.friendshipId)
                    ledgerEntry?.signalName ?: "friend"
                }
                
                // Task variables
                variableName == "TODAYS_TASKS" -> getTodaysTasks(instance.friendshipId, zoneId)
                variableName == "UPCOMING_TASKS" -> getUpcomingTasks(instance.friendshipId, 5)
                variableName == "TASK_COUNT" -> getTaskCount(instance.friendshipId).toString()
                
                // Calendar variables
                variableName == "TODAYS_APPOINTMENTS" -> getTodaysAppointments(instance.friendshipId, zoneId)
                variableName == "UPCOMING_APPOINTMENTS" -> getUpcomingAppointments(instance.friendshipId, zoneId, 5)
                variableName == "APPOINTMENT_COUNT" -> getTodaysAppointmentCount(instance.friendshipId, zoneId).toString()
                
                // Routine context variables
                variableName == "ROUTINE_NAME" -> getRoutineName(instance)
                variableName == "PHASE_NAME" -> getCurrentPhaseName(instance)
                variableName == "STEP_NAME" -> getCurrentStepName(instance)
                
                // Unknown variable - keep original
                else -> {
                    LOG.debug("Unknown variable '{}' in message, keeping original", variableName)
                    matchResult.value
                }
            }
            substitutedValue
        }
    }

    private fun getTodaysTasks(friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId, zoneId: ZoneId): String {
        return try {
            val tasks = taskRepository.findByFriendshipId(friendshipId)
                .filter { !it.completed }
                .sortedBy { it.title }
                .take(5)
            
            if (tasks.isEmpty()) {
                "no tasks"
            } else {
                tasks.mapIndexed { index, task -> 
                    "${index + 1}. ${task.title}"
                }.joinToString(", ")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get tasks for variable substitution: {}", e.message)
            "tasks unavailable"
        }
    }

    private fun getUpcomingTasks(friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId, limit: Int): String {
        return try {
            val tasks = taskRepository.findByFriendshipId(friendshipId)
                .filter { !it.completed }
                .sortedBy { it.title }
                .take(limit)
            
            if (tasks.isEmpty()) {
                "no tasks"
            } else {
                tasks.mapIndexed { index, task -> 
                    "${index + 1}. ${task.title}"
                }.joinToString(", ")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get upcoming tasks for variable substitution: {}", e.message)
            "tasks unavailable"
        }
    }

    private fun getTaskCount(friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId): Int {
        return try {
            taskRepository.findByFriendshipId(friendshipId)
                .count { !it.completed }
        } catch (e: Exception) {
            LOG.warn("Failed to get task count for variable substitution: {}", e.message)
            0
        }
    }

    private fun getTodaysAppointments(friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId, zoneId: ZoneId): String {
        return try {
            val startOfDay = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
            val appointments = calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(startOfDay, Duration.ofDays(1)), 
                friendshipId
            ).sortedBy { it.startAt.instant }
            
            if (appointments.isEmpty()) {
                "no appointments"
            } else {
                appointments.take(5).joinToString(", ") { appointment ->
                    "${appointment.startAt.instant.atZone(zoneId).format(TIME_FORMATTER)} ${appointment.summary}"
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get today's appointments for variable substitution: {}", e.message)
            "appointments unavailable"
        }
    }

    private fun getUpcomingAppointments(friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId, zoneId: ZoneId, limit: Int): String {
        return try {
            val now = Instant.now()
            val appointments = calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(now, Duration.ofDays(7)), 
                friendshipId
            ).filter { it.startAt.instant.isAfter(now) }
            .sortedBy { it.startAt.instant }
            .take(limit)
            
            if (appointments.isEmpty()) {
                "no upcoming appointments"
            } else {
                appointments.joinToString(", ") { appointment ->
                    val startDate = appointment.startAt.instant.atZone(zoneId)
                    val dayPrefix = when {
                        startDate.toLocalDate() == LocalDate.now(zoneId) -> "Today"
                        startDate.toLocalDate() == LocalDate.now(zoneId).plusDays(1) -> "Tomorrow"
                        else -> startDate.format(DateTimeFormatter.ofPattern("MMM dd"))
                    }
                    "$dayPrefix ${startDate.format(TIME_FORMATTER)} ${appointment.summary}"
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to get upcoming appointments for variable substitution: {}", e.message)
            "appointments unavailable"
        }
    }

    private fun getTodaysAppointmentCount(friendshipId: icu.neurospicy.fibi.domain.model.FriendshipId, zoneId: ZoneId): Int {
        return try {
            val startOfDay = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
            calendarRepository.loadAppointmentsForTimeRange(
                TimeRange(startOfDay, Duration.ofDays(1)), 
                friendshipId
            ).size
        } catch (e: Exception) {
            LOG.warn("Failed to get appointment count for variable substitution: {}", e.message)
            0
        }
    }

    private fun getRoutineName(instance: RoutineInstance): String {
        // For now, return a placeholder - this would need the template repository
        return "routine"
    }

    private fun getCurrentPhaseName(instance: RoutineInstance): String {
        // For now, return a placeholder - this would need the template repository
        return "current phase"
    }

    private fun getCurrentStepName(instance: RoutineInstance): String {
        // For now, return a placeholder - this would need the template repository
        return "current step"
    }
} 