package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalMessageId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.Subtask
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskClarificationQuestion
import icu.neurospicy.fibi.domain.service.friends.interaction.SubtaskId
import icu.neurospicy.fibi.domain.service.friends.routines.SelectRoutineSubtaskHandler.ClassificationResponse
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aRoutineTemplate
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class SelectRoutineSubtaskHandlerTest {

    private val templateRepository = mockk<RoutineTemplateRepository>(relaxed = true)
    private val llmClient = mockk<LlmClient>(relaxed = true)
    private val friendshipLedger = mockk<FriendshipLedger>(relaxed = true)
    private val objectMapper = mockk<ObjectMapper>(relaxed = true)

    @Test
    fun `should select matching routine and set routineTemplateId`() = runBlocking<Unit> {
        val template = aRoutineTemplate { title = "Morning Focus" }
        coEvery { templateRepository.loadAll() } returns listOf(template)
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns "{}"
        every {
            objectMapper.readValue(
                any<String>(),
                ClassificationResponse::class.java
            )
        } returns ClassificationResponse(template.templateId.toString(), emptyList(), false)

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val friendshipId = FriendshipId()
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf("rawText" to "I want to use the Morning Focus Routine"),
            id = SubtaskId.from(friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, friendshipId)

        assertTrue(result.updatedSubtask.completed())
        assertEquals(template.templateId, result.updatedContextParameters["routineTemplateId"])
        assertTrue(result.successMessageGenerationPrompt!!.contains(template.title))
    }

    @Test
    fun `should store possibleRoutineIds in subtask parameters if clarification needed`() = runBlocking<Unit> {
        val template1 = aRoutineTemplate { title = "Routine A" }
        val template2 = aRoutineTemplate { title = "Routine B" }
        coEvery { templateRepository.loadAll() } returns listOf(template1, template2)
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns "{}"

        val possibleIds = listOf(template1.templateId.toString(), template2.templateId.toString())
        every {
            objectMapper.readValue(
                any<String>(),
                ClassificationResponse::class.java
            )
        } returns ClassificationResponse(null, possibleRoutineIds = possibleIds, clarificationNeeded = true)

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val friendshipId = FriendshipId()
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf("rawText" to "I want a great routine"),
            id = SubtaskId.from(friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, friendshipId)

        assertTrue(result.updatedSubtask.parameters.containsKey("possibleRoutineIds"))
        assertEquals(possibleIds, result.updatedSubtask.parameters["possibleRoutineIds"])
    }

    @Test
    fun `should resolve clarification and set global routineTemplateId`() = runBlocking<Unit> {
        val template = aRoutineTemplate { title = "Focus Routine" }
        coEvery { templateRepository.loadAll() } returns listOf(template)
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns "{}"
        every {
            objectMapper.readValue(
                any<String>(),
                ClassificationResponse::class.java
            )
        } returns ClassificationResponse(template.templateId.toString(), null, false)

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val friendshipId = FriendshipId()
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf(
                "rawText" to "I want to start a routine",
                "possibleRoutineIds" to listOf(template.templateId.toString())
            ),
            id = SubtaskId.from(friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val clarification = SubtaskClarificationQuestion("Which one?", subtask.id)
        val answer =
            UserMessage(SignalMessageId(Instant.now().epochSecond), Instant.now(), "The focus one!", Channel.SIGNAL)
        val context = GoalContext(originalMessage = null)

        val result = handler.tryResolveClarification(subtask, clarification, answer, context, friendshipId)

        assertTrue(result.updatedSubtask.completed())
        assertThat(result.updatedContextParameters["routineTemplateId"]).isEqualTo(template.templateId)
        assertNull(result.subtaskClarificationQuestion)
    }

    @Test
    fun `should return clarification when multiple routines match`() = runBlocking<Unit> {
        val template1 = aRoutineTemplate { title = "Morning Boost" }
        val template2 = aRoutineTemplate { title = "Morning Calm" }
        coEvery { templateRepository.loadAll() } returns listOf(template1, template2)
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns "{}"
        every {
            objectMapper.readValue(
                any<String>(),
                ClassificationResponse::class.java
            )
        } returns ClassificationResponse(
            null,
            listOf(template1.templateId.toString(), template2.templateId.toString()),
            true
        )

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val friendshipId = FriendshipId()
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf("rawText" to "I want to start a morning routine"),
            id = SubtaskId.from(friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, friendshipId)

        assertTrue(result.subtaskClarificationQuestion != null)
        assertTrue(result.updatedSubtask.completed().not())
    }

    @Test
    fun `should return clarification if routineId not in templates`() = runBlocking<Unit> {
        val template = aRoutineTemplate { title = "Morning Focus" }
        coEvery { templateRepository.loadAll() } returns listOf(template)
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns "{}"
        every {
            objectMapper.readValue(
                any<String>(),
                ClassificationResponse::class.java
            )
        } returns ClassificationResponse("non-existent-id", emptyList(), false)

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val friendshipId = FriendshipId()
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf("rawText" to "I want to start a morning routine"),
            id = SubtaskId.from(friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, friendshipId)

        assertTrue(result.subtaskClarificationQuestion != null)
        assertFalse(result.updatedSubtask.completed())
    }

    @Test
    fun `should fail when LLM response is null or blank`() = runBlocking<Unit> {
        val template = aRoutineTemplate { title = "Morning Focus" }
        coEvery { templateRepository.loadAll() } returns listOf(template)
        coEvery { llmClient.promptReceivingJson(any(), any(), any(), any()) } returns null

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val friendshipId = FriendshipId()
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf("rawText" to "Something vague"),
            id = SubtaskId.from(friendshipId, RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, friendshipId)

        assertFalse(result.updatedSubtask.completed())
    }

    @Test
    fun `should fail if no rawText is provided`() = runBlocking<Unit> {
        val template = aRoutineTemplate { title = "Morning Focus" }
        coEvery { templateRepository.loadAll() } returns listOf(template)

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = emptyMap(),
            id = SubtaskId.from(FriendshipId(), RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, FriendshipId())

        assertFalse(result.updatedSubtask.completed())
    }

    @Test
    fun `should fail if no templates are available`() = runBlocking<Unit> {
        coEvery { templateRepository.loadAll() } returns emptyList()

        val handler =
            SelectRoutineSubtaskHandler(templateRepository, llmClient, friendshipLedger, objectMapper, "fibi64")
        val subtask = Subtask(
            intent = RoutineIntents.Select,
            parameters = mapOf("rawText" to "Anything"),
            id = SubtaskId.from(FriendshipId(), RoutineIntents.Select, SignalMessageId(Instant.now().epochSecond))
        )
        val context = GoalContext(originalMessage = null)

        val result = handler.handle(subtask, context, FriendshipId())

        assertFalse(result.updatedSubtask.completed())
    }
}