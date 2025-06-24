package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.AppointmentId
import icu.neurospicy.fibi.domain.model.AppointmentReminder
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Reminder
import icu.neurospicy.fibi.domain.model.events.*
import icu.neurospicy.fibi.domain.repository.ReminderRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository

@Repository
class ReminderRepositoryUsingMongoDb(
    private val mongoTemplate: MongoTemplate,
    private val eventPublisher: ApplicationEventPublisher
) : ReminderRepository {
    override fun setReminder(appointmentReminder: AppointmentReminder): AppointmentReminder {
        val savedAppointmentReminder = mongoTemplate.save(appointmentReminder)
        eventPublisher.publishEvent(
            if (appointmentReminder._id == null) AppointmentReminderSet(
                ReminderRepositoryUsingMongoDb::class.java.javaClass,
                savedAppointmentReminder.owner,
                savedAppointmentReminder
            )
            else AppointmentReminderUpdated(
                ReminderRepositoryUsingMongoDb::class.java.javaClass,
                savedAppointmentReminder.owner,
                savedAppointmentReminder
            )
        )
        return savedAppointmentReminder
    }

    override fun setReminder(reminder: Reminder): Reminder {
        val savedTimeBasedReminder = mongoTemplate.save(reminder)
        eventPublisher.publishEvent(
            if (reminder._id == null) ReminderSet(
                ReminderRepositoryUsingMongoDb::class.java.javaClass,
                savedTimeBasedReminder.owner,
                savedTimeBasedReminder
            )
            else ReminderUpdated(
                ReminderRepositoryUsingMongoDb::class.java.javaClass,
                savedTimeBasedReminder.owner,
                savedTimeBasedReminder
            )
        )
        return savedTimeBasedReminder
    }

    override fun updateRelatedAppointmentIds(reminderId: String?, relatedAppointmentIds: Set<AppointmentId>) {
        mongoTemplate.findOne(query(where("_id").`is`(reminderId)), AppointmentReminder::class.java)
            ?.apply { mongoTemplate.save(this.copy(relatedAppointmentIds = relatedAppointmentIds)) }
    }

    override fun findTimeBasedRemindersBy(friendshipId: FriendshipId): List<Reminder> =
        mongoTemplate.find(query(where("owner").`is`(friendshipId.toString())), Reminder::class.java)

    override fun findTimeBasedReminderBy(friendshipId: FriendshipId, id: String): Reminder? {
        return mongoTemplate.findOne(
            query(where("owner").`is`(friendshipId.toString()).andOperator(where("_id").`is`(id))),
            Reminder::class.java
        )
    }

    override fun findAppointmentRemindersBy(friendshipId: FriendshipId): List<AppointmentReminder> =
        mongoTemplate.find(query(where("owner").`is`(friendshipId.toString())), AppointmentReminder::class.java)

    override fun findAppointmentReminderBy(friendshipId: FriendshipId, id: String): AppointmentReminder? {
        return mongoTemplate.findOne(
            query(where("owner").`is`(friendshipId.toString()).andOperator(where("_id").`is`(id))),
            AppointmentReminder::class.java
        )
    }

    override fun removeAppointmentReminder(owner: FriendshipId, id: String) {
        mongoTemplate.findAndRemove(query(where("_id").`is`(id)), AppointmentReminder::class.java)
            .takeIf { it != null }.apply {
                eventPublisher.publishEvent(
                    AppointmentReminderUnset(
                        ReminderRepositoryUsingMongoDb::class.java.javaClass,
                        owner,
                        id,
                        this!!.relatedAppointmentIds
                    )
                )
            }
    }

    override fun reminderExpired(owner: FriendshipId, id: String) {
        mongoTemplate.findAndRemove(query(where("_id").`is`(id)), Reminder::class.java)
            .takeIf { it != null }.apply {
                eventPublisher.publishEvent(
                    ReminderExpired(
                        ReminderRepositoryUsingMongoDb::class.java.javaClass,
                        owner,
                        id
                    )
                )
            }

    }

    override fun removeTimeBasedReminder(owner: FriendshipId, id: String) {
        mongoTemplate.findAndRemove(query(where("_id").`is`(id)), Reminder::class.java)
            .takeIf { it != null }.apply {
                eventPublisher.publishEvent(
                    ReminderUnset(
                        ReminderRepositoryUsingMongoDb::class.java.javaClass,
                        owner,
                        id
                    )
                )
            }
    }
}