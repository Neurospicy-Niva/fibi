package icu.neurospicy.fibi.domain.service.friends.interaction.timezones

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object TimezoneIntents {
    val Set = Intent("SetTimezone")
    val SetClock = Intent("SetClock")
    val GeneralTimezoneIntent = Intent("GeneralTimezone")
    val AllTimezoneIntents = arrayOf(SetClock, SetClock, GeneralTimezoneIntent)
}

@Component
class SetTimezoneContributor : IntentContributor {
    override fun intent(): Intent = TimezoneIntents.Set
    override fun description(): String = "Set the user's timezone"
}


@Component
class SetClockContributor : IntentContributor {
    override fun intent(): Intent = TimezoneIntents.SetClock
    override fun description(): String = "Set the user's clock"
}