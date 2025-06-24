package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.UserMessage
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.interaction.Goal
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalDeterminator
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment.AppointmentReminderIntents
import icu.neurospicy.fibi.domain.service.friends.interaction.timers.TimerIntents
import icu.neurospicy.fibi.outgoing.ollama.LlmClient
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.ollama.api.OllamaOptions
import org.springframework.stereotype.Component
import java.time.ZoneOffset

@Component
class ReminderGoalDeterminator(
    private val llmClient: LlmClient,
    private val friendshipLedger: FriendshipLedger,
    private val objectMapper: ObjectMapper,
) : GoalDeterminator {
    val reminderIntents = setOf(
        ReminderIntents.Set,
        ReminderIntents.Remove,
        ReminderIntents.Update,
        ReminderIntents.List,
        AppointmentReminderIntents.Set,
        AppointmentReminderIntents.Remove,
        AppointmentReminderIntents.Update,
        AppointmentReminderIntents.List,
        GeneralReminderIntents.Set,
        GeneralReminderIntents.List,
    )

    override fun canHandle(intent: Intent): Boolean = intent in reminderIntents

    override suspend fun determineGoal(
        intent: Intent,
        message: UserMessage,
        friendshipId: FriendshipId
    ): Set<Goal> {
        val json = llmClient.promptReceivingJson(
            listOf(
                SystemMessage(
                    """
You are a helpful assistant helping to determine the intent of the user.
The user intends to interact with their reminders. Your task is to determine how they want to interact and with which kind of reminder.

Interactions:
- Set: Add a reminder
- Remove: Unset a reminder
- Update: Change a reminder
- List: Show reminders

There are two kinds of reminders:
- Time-based reminders 
- Appointment reminders
- Timers

Time-based reminders:
- Reminds the user at a certain point in the future
- Has a specific date and time
- Has a `text` which is send when reminding
- Examples: 
 - Reminder for today 12 am, text: Write letter
 - Reminder for tomorrow 8 am, text: Clean the dishes
 - Reminder for 04.12.2025, text: Buy sweets for the sweet

Appointment reminders:
- Reminds the user with an `offset` `before/after` certain appointments
- Has a `text` which is send when reminding
- Examples:
 - Reminder after doctor appointments, offset: 0 minutes, text: Pick up the prescription
 - Reminder after school pick up, offset: 1 hour, text: Ask for homework
 - Reminder before meetings, offset: 30 minutes, text: Drink water
 
 Timers:
 - Sends a `text` after a certain time period
 - Has a duration of hours, minutes or seconds
 - Examples:
  - In 18 minutes, text: "Look for the pizza"
  - In 2 hours, text: "Clear out the washing machine"
  - In a quarter hour: "Take a break"
 
 Determine based on the message, which kind of reminder the user is referring to and how they want to interact.
 The message may refer to both reminders in general.
 
Output must be a JSON object like this:
 {
    "interactionType": One of `Set`, `Update`, `Remove` or `List`, optional,
    "kind": `TimeBased`, 'Appointment`, `Timer`, `General`
 }
                """.trimIndent()
                ),
                org.springframework.ai.chat.messages.UserMessage(message.text)
            ),
            OllamaOptions.builder().model("qwen2.5").temperature(0.0).topP(0.3).build(),
            friendshipLedger.findBy(friendshipId)?.timeZone ?: ZoneOffset.UTC,
            message.receivedAt
        )
        val result = objectMapper.readValue(json, ReminderDeterminationResult::class.java)
        return when (result.interactionType) {
            InteractionType.List -> when (result.kind) {
                ReminderKind.TimeBased -> setOf(Goal(ReminderIntents.List))
                ReminderKind.Appointment -> setOf(Goal(AppointmentReminderIntents.List))
                ReminderKind.Timer -> setOf(Goal(TimerIntents.List))
                ReminderKind.General -> setOf(Goal(GeneralReminderIntents.List))
            }

            InteractionType.Set -> when (result.kind) {
                ReminderKind.TimeBased -> setOf(Goal(ReminderIntents.Set))
                ReminderKind.Appointment -> setOf(Goal(AppointmentReminderIntents.Set))
                ReminderKind.Timer -> setOf(Goal(TimerIntents.Set))
                ReminderKind.General -> setOf(Goal(ReminderIntents.Set), Goal(AppointmentReminderIntents.Set))
            }

            InteractionType.Update -> when (result.kind) {
                ReminderKind.TimeBased -> setOf(Goal(ReminderIntents.Update))
                ReminderKind.Appointment -> setOf(Goal(AppointmentReminderIntents.Update))
                ReminderKind.Timer -> setOf(Goal(TimerIntents.Update))
                ReminderKind.General -> setOf(Goal(ReminderIntents.Update), Goal(AppointmentReminderIntents.Update))
            }

            InteractionType.Remove -> when (result.kind) {
                ReminderKind.TimeBased -> setOf(Goal(ReminderIntents.Remove))
                ReminderKind.Appointment -> setOf(Goal(AppointmentReminderIntents.Remove))
                ReminderKind.Timer -> setOf(Goal(TimerIntents.Remove))
                ReminderKind.General -> setOf(Goal(ReminderIntents.Remove), Goal(AppointmentReminderIntents.Remove))
            }
        }
    }

    data class ReminderDeterminationResult(
        val interactionType: InteractionType,
        val kind: ReminderKind,
    )

    enum class ReminderKind {
        TimeBased,
        Appointment,
        Timer,
        General,
    }

    enum class InteractionType {
        Set,
        Remove,
        Update,
        List,
    }
}