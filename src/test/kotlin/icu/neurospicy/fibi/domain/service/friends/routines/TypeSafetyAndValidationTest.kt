package icu.neurospicy.fibi.domain.service.friends.routines

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeSafetyAndValidationTest {

    @Test
    fun `ScheduleExpression validation works correctly`() {
        // Valid cron expressions should work
        val validCron = ScheduleExpression.Custom("0 0 8 * * MON-FRI")
        assertEquals("0 0 8 * * MON-FRI", validCron.cronExpression)
        
        // Invalid cron expressions should throw exceptions
        assertThrows<IllegalArgumentException> {
            ScheduleExpression.Custom("invalid cron")
        }
        
        assertThrows<IllegalArgumentException> {
            ScheduleExpression.Custom("0 0") // Too few parts
        }
        
        // Built-in schedule expressions should work
        assertEquals("0 0 * * *", ScheduleExpression.DAILY.cronExpression)
        assertEquals("0 0 * * MON", ScheduleExpression.MONDAY.cronExpression)
        assertEquals("0 0 * * MON-FRI", ScheduleExpression.WEEKDAYS.cronExpression)
    }
    
    @Test
    fun `ScheduleExpression fromString conversion works`() {
        assertEquals(ScheduleExpression.DAILY, ScheduleExpression.fromString("DAILY"))
        assertEquals(ScheduleExpression.MONDAY, ScheduleExpression.fromString("monday"))
        assertEquals(ScheduleExpression.WEEKDAYS, ScheduleExpression.fromString("weekdays"))
        
        // Custom cron should create Custom instance
        val custom = ScheduleExpression.fromString("0 30 7 * * *")
        assertEquals("0 30 7 * * *", custom.cronExpression)
    }

    @Test
    fun `RoutineParameterType validation works for all types`() {
        // STRING - should always work
        val stringParam = RoutineParameterType.STRING.parseAndValidate("any text")
        assertEquals("any text", stringParam)
        
        // LOCAL_TIME - valid formats
        val timeParam = RoutineParameterType.LOCAL_TIME.parseAndValidate("14:30")
        assertEquals(LocalTime.of(14, 30), timeParam)
        
        val timeParamWithSeconds = RoutineParameterType.LOCAL_TIME.parseAndValidate("14:30:45")
        assertEquals(LocalTime.of(14, 30, 45), timeParamWithSeconds)
        
        // LOCAL_TIME - invalid formats should throw
        assertThrows<IllegalArgumentException> {
            RoutineParameterType.LOCAL_TIME.parseAndValidate("invalid time")
        }
        
        // BOOLEAN - various formats should work
        assertEquals(true, RoutineParameterType.BOOLEAN.parseAndValidate("true"))
        assertEquals(true, RoutineParameterType.BOOLEAN.parseAndValidate("YES"))
        assertEquals(true, RoutineParameterType.BOOLEAN.parseAndValidate("y"))
        assertEquals(true, RoutineParameterType.BOOLEAN.parseAndValidate("1"))
        assertEquals(true, RoutineParameterType.BOOLEAN.parseAndValidate("on"))
        
        assertEquals(false, RoutineParameterType.BOOLEAN.parseAndValidate("false"))
        assertEquals(false, RoutineParameterType.BOOLEAN.parseAndValidate("NO"))
        assertEquals(false, RoutineParameterType.BOOLEAN.parseAndValidate("n"))
        assertEquals(false, RoutineParameterType.BOOLEAN.parseAndValidate("0"))
        assertEquals(false, RoutineParameterType.BOOLEAN.parseAndValidate("off"))
        
        // BOOLEAN - invalid should throw
        assertThrows<IllegalArgumentException> {
            RoutineParameterType.BOOLEAN.parseAndValidate("maybe")
        }
        
        // INT - valid numbers
        assertEquals(42, RoutineParameterType.INT.parseAndValidate("42"))
        assertEquals(-10, RoutineParameterType.INT.parseAndValidate("-10"))
        
        // INT - invalid should throw
        assertThrows<IllegalArgumentException> {
            RoutineParameterType.INT.parseAndValidate("not a number")
        }
        
        // FLOAT - valid numbers
        assertEquals(3.14f, RoutineParameterType.FLOAT.parseAndValidate("3.14"))
        assertEquals(-1.5f, RoutineParameterType.FLOAT.parseAndValidate("-1.5"))
        
        // FLOAT - invalid should throw
        assertThrows<IllegalArgumentException> {
            RoutineParameterType.FLOAT.parseAndValidate("not a float")
        }
        
        // DATE - valid dates
        assertEquals(LocalDate.of(2024, 12, 25), RoutineParameterType.DATE.parseAndValidate("2024-12-25"))
        
        // DATE - invalid should throw
        assertThrows<IllegalArgumentException> {
            RoutineParameterType.DATE.parseAndValidate("invalid date")
        }
    }
    
    @Test
    fun `RoutineParameterType format descriptions are helpful`() {
        assertEquals("Any text", RoutineParameterType.STRING.getFormatDescription())
        assertEquals("Time in HH:mm format (e.g., 14:30)", RoutineParameterType.LOCAL_TIME.getFormatDescription())
        assertEquals("true/false, yes/no, y/n, 1/0, or on/off", RoutineParameterType.BOOLEAN.getFormatDescription())
        assertEquals("A whole number (e.g., 42)", RoutineParameterType.INT.getFormatDescription())
        assertEquals("A decimal number (e.g., 3.14)", RoutineParameterType.FLOAT.getFormatDescription())
        assertEquals("Date in yyyy-MM-dd format (e.g., 2024-12-25)", RoutineParameterType.DATE.getFormatDescription())
    }

    @Test
    fun `TypedParameter creation and access works correctly`() {
        // From string with expected type
        val timeParam = TypedParameter.fromString("14:30", RoutineParameterType.LOCAL_TIME)
        assertEquals(LocalTime.of(14, 30), timeParam.value)
        assertEquals(RoutineParameterType.LOCAL_TIME, timeParam.type)
        assertEquals(LocalTime.of(14, 30), timeParam.getAs<LocalTime>())
        assertEquals("14:30", timeParam.getAsString())
        
        // From raw value (type inference)
        val boolParam = TypedParameter.fromValue(true)
        assertEquals(true, boolParam.value)
        assertEquals(RoutineParameterType.BOOLEAN, boolParam.type)
        assertEquals(true, boolParam.getAs<Boolean>())
        assertEquals("true", boolParam.getAsString())
        
        // Type safety - should throw when accessing wrong type
        assertThrows<IllegalArgumentException> {
            boolParam.getAs<LocalTime>() // Boolean parameter accessed as LocalTime
        }
        
        // String conversion should always work regardless of type
        assertEquals("true", boolParam.getAs<String>())
    }
    
    @Test
    fun `RoutineInstance type-safe parameter methods work`() {
        val instance = RoutineInstance(
            _id = "test",
            templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
            friendshipId = icu.neurospicy.fibi.domain.model.FriendshipId(),
            parameters = emptyMap()
        )
        
        // Setting parameters with validation
        val updatedInstance = instance
            .withParameter("wakeUpTime", "07:30", RoutineParameterType.LOCAL_TIME)
            .withParameter("enableAlarm", true)
            .withParameter("snoozeCount", 3)
        
        // Type-safe access
        assertEquals(LocalTime.of(7, 30), updatedInstance.getParameter<LocalTime>("wakeUpTime"))
        assertEquals(true, updatedInstance.getParameter<Boolean>("enableAlarm"))
        assertEquals(3, updatedInstance.getParameter<Int>("snoozeCount"))
        
        // String access (always works)
        assertEquals("07:30", updatedInstance.getParameterAsString("wakeUpTime"))
        assertEquals("true", updatedInstance.getParameterAsString("enableAlarm"))
        assertEquals("3", updatedInstance.getParameterAsString("snoozeCount"))
        
        // Type checking
        assertEquals(true, updatedInstance.hasParameterOfType("wakeUpTime", RoutineParameterType.LOCAL_TIME))
        assertEquals(false, updatedInstance.hasParameterOfType("wakeUpTime", RoutineParameterType.STRING))
    }
    
    @Test
    fun `Integrity checks prevent invalid routine templates`() {
        // Empty title should fail
        assertThrows<IllegalArgumentException> {
            RoutineTemplate(
                title = "",
                version = "1.0",
                description = "Test",
                phases = listOf(RoutinePhase(title = "Phase", steps = listOf(
                    MessageRoutineStep("Test message")
                )))
            )
        }
        
        // Empty phases should fail
        assertThrows<IllegalArgumentException> {
            RoutineTemplate(
                title = "Test",
                version = "1.0", 
                description = "Test",
                phases = emptyList()
            )
        }
        
        // Empty phase title should fail
        assertThrows<IllegalArgumentException> {
            RoutinePhase(
                title = "",
                steps = listOf(MessageRoutineStep("Test"))
            )
        }
        
        // Empty phase steps should fail
        assertThrows<IllegalArgumentException> {
            RoutinePhase(
                title = "Test Phase",
                steps = emptyList()
            )
        }
        
        // Empty step message should fail
        assertThrows<IllegalArgumentException> {
            MessageRoutineStep(message = "")
        }
        
        // Empty parameter key should fail
        assertThrows<IllegalArgumentException> {
            ParameterRequestStep(
                question = "What time?",
                parameterKey = "",
                parameterType = RoutineParameterType.LOCAL_TIME
            )
        }
    }
} 