package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.*
import java.time.Duration
import java.time.Instant

interface CalendarRepository {
    fun save(privateCalendar: PrivateCalendar)
    fun replaceCalendarAppointments(
        appointments: List<Appointment>,
        owner: FriendshipId,
        calendarConfigId: CalendarConfigId,
        calendarId: CalendarId
    )

    fun loadAppointmentsForTimeRange(timeRange: TimeRange, friendshipId: FriendshipId): List<Appointment>
    fun loadAppointments(
        owner: FriendshipId,
        calendarConfigId: CalendarConfigId,
        calendarId: CalendarId
    ): List<Appointment>

    fun loadAppointmentsByAppointmentId(owner: FriendshipId, appointmentIds: Set<AppointmentId>): List<Appointment>
    fun loadAppointmentsById(owner: FriendshipId, ids: Set<String>): List<Appointment>
}

data class TimeRange(val startAt: Instant, val duration: Duration)
