package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.AppointmentId
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Reminder

interface ReminderRepository {
    fun setReminder(appointmentReminder: AppointmentReminder): AppointmentReminder
    fun setReminder(reminder: Reminder): Reminder
    fun updateRelatedAppointmentIds(reminderId: String?, relatedAppointmentIds: Set<AppointmentId>)
    fun findTimeBasedRemindersBy(friendshipId: FriendshipId): List<Reminder>
    fun findTimeBasedReminderBy(friendshipId: FriendshipId, id: String): Reminder?
    fun removeTimeBasedReminder(owner: FriendshipId, id: String)
    fun findAppointmentRemindersBy(friendshipId: FriendshipId): List<AppointmentReminder>
    fun findAppointmentReminderBy(friendshipId: FriendshipId, id: String): AppointmentReminder?
    fun removeAppointmentReminder(owner: FriendshipId, id: String)
    fun reminderExpired(owner: FriendshipId, id: String)
}
