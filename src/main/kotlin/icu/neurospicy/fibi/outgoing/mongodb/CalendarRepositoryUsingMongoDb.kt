package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.AppointmentsUpdated
import icu.neurospicy.fibi.domain.repository.CalendarRepository
import icu.neurospicy.fibi.domain.repository.TimeRange
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository
import java.time.Instant.now

@Repository
class CalendarRepositoryUsingMongoDb(
    val mongoTemplate: MongoTemplate,
    val eventPublisher: ApplicationEventPublisher
) : CalendarRepository {
    override fun save(privateCalendar: PrivateCalendar) {
        mongoTemplate.save(
            mongoTemplate.findOne(
                query(
                    where("calendarConfigId").`is`(privateCalendar.calendarConfigId.toString()).andOperator(
                        where("calendarId").`is`(privateCalendar.calendarId.toString())
                    )
                ),
                PrivateCalendar::class.java
            )?.copy(lastUpdatedAt = now(), appointments = privateCalendar.appointments) ?: privateCalendar
        )
    }

    override fun replaceCalendarAppointments(
        appointments: List<Appointment>,
        owner: FriendshipId,
        calendarConfigId: CalendarConfigId,
        calendarId: CalendarId
    ) {
        val existingAppointments = loadAppointments(owner, calendarConfigId, calendarId)
        val (deletedAppointmentIds, changedAppointmentIds, newAppointmentIds) = detectChangingAppointments(
            existingAppointments,
            appointments
        )
        mongoTemplate.remove(
            query(
                where("owner").`is`(owner.toString())
                    .andOperator(
                        where("calendarConfigId").`is`(calendarConfigId.toString())
                            .andOperator(where("calendarId").`is`(calendarId.toString()))
                    )

            ), Appointment::class.java
        )
        appointments.forEach { mongoTemplate.save(it) }
        eventPublisher.publishEvent(
            AppointmentsUpdated(
                this.javaClass,
                owner,
                calendarConfigId,
                calendarId,
                newAppointmentIds,
                changedAppointmentIds,
                deletedAppointmentIds
            )
        )
    }

    private fun detectChangingAppointments(
        existingAppointments: List<Appointment>,
        newAppointments: List<Appointment>
    ): Triple<Set<AppointmentId>, Set<AppointmentId>, Set<AppointmentId>> {
        val deletedAppointmentIds =
            existingAppointments.map { it.appointmentId }.subtract(newAppointments.map { it.appointmentId }.toSet())
        val newAppointmentIds =
            newAppointments.map { it.appointmentId }.subtract(existingAppointments.map { it.appointmentId }.toSet())
        val changedAppointmentIds =
            existingAppointments.filter { newAppointments.firstOrNull { appointment: Appointment -> it.appointmentId == appointment.appointmentId }?.hash != it.hash }
                .map { it.appointmentId }.toSet()
        return Triple(deletedAppointmentIds, changedAppointmentIds, newAppointmentIds)
    }

    override fun loadAppointmentsForTimeRange(
        timeRange: TimeRange,
        friendshipId: FriendshipId
    ): List<Appointment> {
        val criteria = where("owner").`is`(friendshipId.toString())
        return mongoTemplate.find(
            query(criteria), Appointment::class.java
        ).filter {
            (it.startAt.instant.isBefore(timeRange.startAt) && it.endAt.instant.isAfter(timeRange.startAt)) ||
                    it.startAt.instant.isAfter(timeRange.startAt) && it.startAt.instant.isBefore(
                timeRange.startAt.plus(
                    timeRange.duration
                )
            )
        }
    }

    override fun loadAppointments(
        owner: FriendshipId,
        calendarConfigId: CalendarConfigId,
        calendarId: CalendarId
    ): List<Appointment> {
        return mongoTemplate.find(
            query(
                where("owner").`is`(owner.toString())
                    .andOperator(
                        where("calendarConfigId").`is`(calendarConfigId.toString())
                            .andOperator(where("calendarId").`is`(calendarId.toString()))
                    )
            ), Appointment::class.java
        )
    }

    override fun loadAppointmentsByAppointmentId(
        owner: FriendshipId,
        appointmentIds: Set<AppointmentId>
    ): List<Appointment> {
        return mongoTemplate.find(
            query(
                where("owner").`is`(owner.toString())
                    .andOperator(where("appointmentId").`in`(appointmentIds.map { it.toString() }))
            ), Appointment::class.java
        )

    }

    override fun loadAppointmentsById(owner: FriendshipId, ids: Set<String>): List<Appointment> {
        return mongoTemplate.find(
            query(
                where("owner").`is`(owner.toString())
                    .andOperator(where("_id").`in`(ids))
            ), Appointment::class.java
        )

    }
}