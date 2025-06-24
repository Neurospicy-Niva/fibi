package icu.neurospicy.fibi.domain.service.friends.interaction.timers

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object TimerIntents {
    val Set = Intent("SetTimer")
    val Update = Intent("UpdateTimer")
    val Remove = Intent("RemoveTimer")
    val List = Intent("ListTimers")
}

@Component
class SetTimerIntentContributor : IntentContributor {
    override fun intent(): Intent = TimerIntents.Set
    override fun description(): String = "Start a new timer"
}

@Component
class UpdateTimerIntentContributor : IntentContributor {
    override fun intent(): Intent = TimerIntents.Update
    override fun description(): String = "Update an existing timer"
}

@Component
class RemoveTimerIntentContributor : IntentContributor {
    override fun intent(): Intent = TimerIntents.Remove
    override fun description(): String = "Cancel a timer"
}

@Component
class ListTimersIntentContributor : IntentContributor {
    override fun intent(): Intent = TimerIntents.List
    override fun description(): String = "List active timers"
}