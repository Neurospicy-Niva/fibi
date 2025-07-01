package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.LedgerEntry
import icu.neurospicy.fibi.domain.model.RelationStatus
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import icu.neurospicy.fibi.domain.model.Appointment
import icu.neurospicy.fibi.domain.model.DateTimeInformation
import icu.neurospicy.fibi.domain.model.Task
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@ExtendWith(MockKExtension::class)
class MessageVariableSubstitutorTest {

    @MockK
    private lateinit var friendshipLedger: FriendshipLedger

    @MockK
    private lateinit var taskRepository: TaskRepository

    @MockK
    private lateinit var calendarRepository: CalendarRepository

    private lateinit var substitutor: MessageVariableSubstitutor

    @Test
    fun `should substitute parameter variables`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId,
            parameters = mapOf(
                "morningBeverage" to TypedParameter.fromValue("coffee"),
                "wakeUpTime" to TypedParameter.fromValue(LocalTime.of(7, 30))
            )
        )
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "Good morning! Time for your \${morningBeverage} at \${wakeUpTime}",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result == "Good morning! Time for your coffee at 07:30")
    }

    @Test
    fun `should substitute system time variables`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId
        )
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "Current time: \${TIME}, Date: \${TODAY}",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result.contains("Current time: "))
        assert(result.contains("Date: "))
        assert(!result.contains("\${TIME}"))
        assert(!result.contains("\${TODAY}"))
    }

    @Test
    fun `should substitute friend name variable`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId
        )
        
        every { friendshipLedger.findBy(friendshipId) } returns
            LedgerEntry(friendshipId = friendshipId, signalName = "Alice", relationStatus = RelationStatus.Friend, acceptedAgreements = emptyList())
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "Hello \${FRIEND_NAME}!",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result == "Hello Alice!")
    }

    @Test
    fun `should substitute task variables`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId
        )
        
        val tasks = listOf(
            Task(owner = friendshipId, title = "Organize books"),
            Task(owner = friendshipId, title = "Go jogging"),
            Task(owner = friendshipId, title = "Read email", completed = true)
        )
        
        every { taskRepository.findByFriendshipId(friendshipId) } returns tasks
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "You have \${TASK_COUNT} tasks: \${TODAYS_TASKS}",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result == "You have 2 tasks: 1. Go jogging, 2. Organize books")
    }

    @Test
    fun `should substitute appointment variables`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId
        )
        
        val now = Instant.now()
        val appointments = listOf(
            Appointment(
                _id = null,
                owner = friendshipId,
                appointmentId = icu.neurospicy.fibi.domain.model.AppointmentId("1"),
                calendarConfigId = icu.neurospicy.fibi.domain.model.CalendarConfigId(),
                calendarId = icu.neurospicy.fibi.domain.model.CalendarId("test"),
                relatedVEvent = "event1",
                summary = "Doctor appointment",
                startAt = DateTimeInformation(now.plusSeconds(3600), ZoneId.of("UTC"), now.plusSeconds(3600).atZone(ZoneId.of("UTC")).toLocalDateTime()),
                endAt = DateTimeInformation(now.plusSeconds(7200), ZoneId.of("UTC"), now.plusSeconds(7200).atZone(ZoneId.of("UTC")).toLocalDateTime()),
                hash = "hash1"
            )
        )
        
        every { calendarRepository.loadAppointmentsForTimeRange(any(), any()) } returns appointments
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "You have \${APPOINTMENT_COUNT} appointments today: \${TODAYS_APPOINTMENTS}",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result.contains("You have 1 appointments today:"))
        assert(result.contains("Doctor appointment"))
    }

    @Test
    fun `should keep unknown variables unchanged`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId
        )
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "Hello \${UNKNOWN_VARIABLE}!",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result == "Hello \${UNKNOWN_VARIABLE}!")
    }

    @Test
    fun `should handle missing parameters gracefully`() {
        // Given
        val friendshipId = FriendshipId()
        val instance = RoutineInstance(
            _id = null,
            templateId = RoutineTemplateId.forTitleVersion("Test Routine", "1.0"),
            friendshipId = friendshipId,
            parameters = emptyMap()
        )
        
        every { friendshipLedger.findBy(friendshipId) } returns null
        
        setupSubstitutor()
        
        // When
        val result = substitutor.substituteVariables(
            "Hello \${FRIEND_NAME}! Your \${missingParameter} is ready.",
            instance,
            ZoneId.of("UTC")
        )
        
        // Then
        assert(result.contains("Hello friend!"))
        assert(result.contains("\${missingParameter}"))
    }

    private fun setupSubstitutor() {
        substitutor = MessageVariableSubstitutor(friendshipLedger, taskRepository, calendarRepository)
    }
} 