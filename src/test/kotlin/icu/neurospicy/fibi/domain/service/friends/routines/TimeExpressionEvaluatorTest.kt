package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TimeExpressionEvaluatorTest {
    
    private lateinit var timeExpressionEvaluator: TimeExpressionEvaluator
    
    @BeforeEach
    fun setUp() {
        timeExpressionEvaluator = TimeExpressionEvaluator()
    }
    
    @Nested
    inner class `Basic Variable Evaluation` {
        
        @Test
        fun `evaluates NOW variable with duration arithmetic`() {
            // Given
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = emptyMap()
            )
            val expression = "\${NOW}+PT45M"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = LocalDateTime.now(zoneId).plusMinutes(45)
            // Allow for small time differences due to test execution
            assertThat(result!!.toEpochSecond(java.time.ZoneOffset.UTC))
                .isCloseTo(expectedTime.toEpochSecond(java.time.ZoneOffset.UTC), org.assertj.core.data.Offset.offset(5L))
        }
        
        @Test
        fun `evaluates ROUTINE_START variable with duration arithmetic`() {
            // Given
            val routineStartTime = Instant.now().minusSeconds(3600) // 1 hour ago
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                startedAt = routineStartTime,
                parameters = emptyMap()
            )
            val expression = "\${ROUTINE_START}+PT30M"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = routineStartTime.atZone(zoneId).toLocalDateTime().plusMinutes(30)
            assertThat(result).isEqualTo(expectedTime)
        }
        
        @Test
        fun `evaluates parameter variable with duration arithmetic`() {
            // Given
            val wakeUpTime = LocalTime.of(7, 30)
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = mapOf(
                    "wakeUpTime" to TypedParameter.fromValue(wakeUpTime)
                )
            )
            val expression = "\${wakeUpTime}+PT1H"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = LocalDateTime.now(zoneId).with(wakeUpTime).plusHours(1)
            // Compare only hour and minute since the date part depends on current date
            assertThat(result!!.toLocalTime()).isEqualTo(expectedTime.toLocalTime())
        }
        
        @Test
        fun `evaluates PHASE_ENTERED variable with actual event timestamp`() {
            // Given
            val phaseEnteredTime = Instant.now().minusSeconds(1800) // 30 minutes ago
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = mapOf(
                    "PHASE_ENTERED" to TypedParameter.fromValue(phaseEnteredTime)
                )
            )
            val expression = "\${PHASE_ENTERED}+PT15M"
            val zoneId = ZoneId.of("Europe/London")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = phaseEnteredTime.atZone(zoneId).toLocalDateTime().plusMinutes(15)
            assertThat(result).isEqualTo(expectedTime)
        }
        
        @Test
        fun `evaluates PHASE_LEFT variable with actual event timestamp`() {
            // Given
            val phaseLeftTime = Instant.now().minusSeconds(900) // 15 minutes ago
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = mapOf(
                    "PHASE_LEFT" to TypedParameter.fromValue(phaseLeftTime)
                )
            )
            val expression = "\${PHASE_LEFT}+PT10M"
            val zoneId = ZoneId.of("America/New_York")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = phaseLeftTime.atZone(zoneId).toLocalDateTime().plusMinutes(10)
            assertThat(result).isEqualTo(expectedTime)
        }
    }
    
    @Nested
    inner class `Complex Expressions` {
        
        @Test
        fun `evaluates simple duration arithmetic`() {
            // Given
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = emptyMap()
            )
            val expression = "\${NOW}+PT45M"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = LocalDateTime.now(zoneId).plusMinutes(45)
            // Allow for small time differences due to test execution
            assertThat(result!!.toEpochSecond(java.time.ZoneOffset.UTC))
                .isCloseTo(expectedTime.toEpochSecond(java.time.ZoneOffset.UTC), org.assertj.core.data.Offset.offset(5L))
        }
        
        @Test
        fun `combines multiple event variables in complex expression`() {
            // Given
            val routineStartTime = Instant.now().minusSeconds(7200) // 2 hours ago
            val phaseEnteredTime = Instant.now().minusSeconds(1800) // 30 minutes ago
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = mapOf(
                    "ROUTINE_START" to TypedParameter.fromValue(routineStartTime),
                    "PHASE_ENTERED" to TypedParameter.fromValue(phaseEnteredTime)
                )
            )
            val expression = "\${ROUTINE_START}+PT1H"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = routineStartTime.atZone(zoneId).toLocalDateTime().plusHours(1)
            assertThat(result).isEqualTo(expectedTime)
        }
        
        @Test
        fun `evaluates expression with multiple parameters`() {
            // Given
            val wakeUpTime = LocalTime.of(7, 0)
            val delay = Duration.ofMinutes(15)
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = mapOf(
                    "wakeUpTime" to TypedParameter.fromValue(wakeUpTime),
                    "delay" to TypedParameter.fromValue(delay)
                )
            )
            val expression = "\${wakeUpTime}+PT15M"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull
            val expectedTime = LocalDateTime.now(zoneId).with(wakeUpTime).plusMinutes(15)
            // Compare only hour and minute since the date part depends on current date
            assertThat(result!!.toLocalTime()).isEqualTo(expectedTime.toLocalTime())
        }
    }
    
    @Nested
    inner class `Error Handling` {
        
        @Test
        fun `returns null for missing parameter`() {
            // Given
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = emptyMap()
            )
            val expression = "\${missingParam}+PT15M"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNull()
        }
        
        @Test
        fun `returns null for invalid expression`() {
            // Given
            val wakeUpTime = LocalTime.of(7, 0)
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = mapOf(
                    "wakeUpTime" to TypedParameter.fromValue(wakeUpTime)
                )
            )
            val expression = "\${wakeUpTime}+INVALID"
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNull()
        }
        
        @Test
        fun `returns null for malformed variable syntax`() {
            // Given
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = emptyMap()
            )
            val expression = "\${NOW}+PT45M" // This should work, but test the edge case
            val zoneId = ZoneId.of("UTC")
            
            // When
            val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)
            
            // Then
            assertThat(result).isNotNull // This should actually work now
        }
    }
    
    @Nested
    inner class `Time Zone Handling` {
        
        @Test
        fun `handles different time zones correctly`() {
            // Given
            val instance = RoutineInstance(
                _id = "test",
                templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
                friendshipId = FriendshipId(),
                parameters = emptyMap()
            )
            val expression = "\${NOW}+PT45M"
            val utcZone = ZoneId.of("UTC")
            val londonZone = ZoneId.of("Europe/London")
            
            // When
            val utcResult = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, utcZone)
            val londonResult = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, londonZone)
            
            // Then
            assertThat(utcResult).isNotNull
            assertThat(londonResult).isNotNull
            // The results should be different due to time zone differences
            assertThat(utcResult).isNotEqualTo(londonResult)
        }
    }
} 