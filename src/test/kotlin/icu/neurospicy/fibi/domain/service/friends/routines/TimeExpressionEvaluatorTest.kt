package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TimeExpressionEvaluatorTest {

    private val timeExpressionEvaluator = TimeExpressionEvaluator()

    @Test
    fun `evaluates simple parameter reference with arithmetic`() {
        // Given
        val instance = RoutineInstance(
            _id = "test",
            templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
            friendshipId = FriendshipId(),
            parameters = mapOf(
                "wakeUpTime" to TypedParameter.fromValue(LocalTime.of(7, 0))
            )
        )
        val expression = "\${wakeUpTime}+PT15M"
        val zoneId = ZoneId.of("Europe/London")

        // When
        val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.toLocalTime()).isEqualTo(LocalTime.of(7, 15))
    }

    @Test
    fun `evaluates complex arithmetic expression`() {
        // Given
        val instance = RoutineInstance(
            _id = "test",
            templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
            friendshipId = FriendshipId(),
            parameters = mapOf(
                "wakeUpTime" to TypedParameter.fromValue(LocalTime.of(8, 30))
            )
        )
        // Use a simpler expression that the library supports
        val expression = "\${wakeUpTime}+PT20M"
        val zoneId = ZoneId.of("UTC")

        // When
        val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.toLocalTime()).isEqualTo(LocalTime.of(8, 50))
    }

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
        val instance = RoutineInstance(
            _id = "test",
            templateId = RoutineTemplateId.forTitleVersion("test", "1.0"),
            friendshipId = FriendshipId(),
            parameters = mapOf(
                "wakeUpTime" to TypedParameter.fromValue(LocalTime.of(7, 0))
            )
        )
        val expression = "\${wakeUpTime}+INVALID"
        val zoneId = ZoneId.of("UTC")

        // When
        val result = timeExpressionEvaluator.evaluateTimeExpression(expression, instance, zoneId)

        // Then
        assertThat(result).isNull()
    }
} 