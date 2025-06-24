package icu.neurospicy.fibi.domain.service.friends.interaction.reminders.appointment

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object AppointmentReminderIntents {
    val Set = Intent("SetAppointmentReminder")
    val Update = Intent("UpdateAppointmentReminder")
    val Remove = Intent("RemoveAppointmentReminder")
    val List = Intent("ListAppointmentReminders")
}

@Component
class SetAppointmentReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = AppointmentReminderIntents.Set
    override fun description(): String = "Set a reminder based on appointments or events"
}

@Component
class UpdateAppointmentReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = AppointmentReminderIntents.Update
    override fun description(): String = "Update an appointment reminder regarding appointments or events"
}

@Component
class RemoveAppointmentReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = AppointmentReminderIntents.Remove
    override fun description(): String = "Remove an appointment reminder regarding appointments or events"
}

@Component
class ListAppointmentRemindersIntentContributor : IntentContributor {
    override fun intent(): Intent = AppointmentReminderIntents.List
    override fun description(): String = "List your reminders regarding appointments or events"
}