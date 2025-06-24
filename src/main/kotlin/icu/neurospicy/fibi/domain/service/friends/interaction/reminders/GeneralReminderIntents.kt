package icu.neurospicy.fibi.domain.service.friends.interaction.reminders

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor

object GeneralReminderIntents {
    val Set = Intent("AddRemindingTask")
    val List = Intent("ListReminders")
}

class SetReminderIntentContributor : IntentContributor {
    override fun intent(): Intent = GeneralReminderIntents.Set
    override fun description(): String = "Add a task to get reminded of something"
}

