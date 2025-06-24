package icu.neurospicy.fibi.domain.model.events

import icu.neurospicy.fibi.domain.model.AppointmentId
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Reminder
import org.springframework.context.ApplicationEvent

data class AppointmentReminderSet(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminder: AppointmentReminder
) : ApplicationEvent(_source)

data class AppointmentReminderUpdated(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminder: AppointmentReminder
) : ApplicationEvent(_source)

data class AppointmentReminderUnset(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminderId: String,
    val relatedAppointmentIds: Set<AppointmentId>
) : ApplicationEvent(_source)

data class ReminderSet(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminder: Reminder
) : ApplicationEvent(_source)

data class ReminderUpdated(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminder: Reminder
) : ApplicationEvent(_source)

data class ReminderUnset(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminderId: String
) : ApplicationEvent(_source)

data class ReminderExpired(
    val _source: Class<Any>,
    val friendshipId: FriendshipId,
    val reminderId: String
) : ApplicationEvent(_source)
