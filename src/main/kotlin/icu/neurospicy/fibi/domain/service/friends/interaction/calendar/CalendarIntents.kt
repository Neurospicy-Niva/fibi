package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object CalendarIntents {
    val Register = Intent("RegisterCalendar")
    val Remove = Intent("RemoveCalendar")
    val ListAppointments = Intent("ListAppointments")
}

@Component
class RegisterCalendarIntentContributor : IntentContributor {
    override fun intent(): Intent = CalendarIntents.Register
    override fun description(): String = "Register / Add a new calendar"
}


@Component
class RemoveCalendarIntentContributor : IntentContributor {
    override fun intent(): Intent = CalendarIntents.Remove
    override fun description(): String = "Remove a (registered) calendar"
}

@Component
class ShowCalendarsIntentContributor : IntentContributor {
    override fun intent(): Intent = CalendarIntents.ListAppointments
    override fun description(): String = "Show appointments in the calendar"
}
