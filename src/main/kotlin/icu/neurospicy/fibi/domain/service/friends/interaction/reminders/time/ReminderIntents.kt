package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object ReminderIntents {
    val Set = Intent("SetTimeBasedReminder")
    val Update = Intent("UpdateTimeBasedReminder")
    val Remove = Intent("RemoveTimeBasedReminder")
    val List = Intent("ListTimeBasedReminders")
}

@Component
class SetTimeBasedReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = ReminderIntents.Set
    override fun description(): String = "Set a reminder for a specific date and time"
}

@Component
class UpdateTimeBasedReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = ReminderIntents.Update
    override fun description(): String = "Update a reminder scheduled for a specific date and time"
}

@Component
class RemoveTimeBasedReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = ReminderIntents.Remove
    override fun description(): String = "Remove a reminder scheduled for a specific date and time"
}

@Component
class ListTimeBasedRemindersIntentContributor : IntentContributor {
    override fun intent(): Intent = ReminderIntents.List
    override fun description(): String = "List your time-based reminders"
}