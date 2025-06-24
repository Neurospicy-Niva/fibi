package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.AppointmentId
import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.CalendarId
import icu.neurospicy.fibi.domain.model.FriendshipId
import org.springframework.context.ApplicationEvent


data class AppointmentsUpdated(
    val _source: Class<Any>,
    val owner: FriendshipId,
    val calendarConfigId: CalendarConfigId,
    val calendarId: CalendarId,
    val newAppointmentIds: Set<AppointmentId>,
    val changedAppointmentIds: Set<AppointmentId>,
    val deletedAppointmentIds: Set<AppointmentId>,
) : ApplicationEvent(_source)