package icu.neurospicy.fibi.outgoing.mongodb

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.CalendarActivityRepository
import icu.neurospicy.fibi.domain.repository.CalendarRegistration
import icu.neurospicy.fibi.domain.repository.Source
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.security.crypto.keygen.KeyGenerators
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CalendarActivityRepositoryUsingMongoDb(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.calendar.key}")
    private val calendarEncryptionKey: String,
) : CalendarActivityRepository {
    override fun startRegistration(calendarRegistration: CalendarRegistration) {
        val salt = KeyGenerators.string().generateKey()
        mongoTemplate.save(encryptCalendarRegistration(calendarRegistration, salt))
    }

    override fun loadActiveRegistration(friendshipId: FriendshipId): CalendarRegistration? {
        return findOne(friendshipId)
    }

    override fun finishRegistration(friendshipId: FriendshipId, finishedAt: Instant) {
        val registration = findOne(friendshipId) ?: return
        val salt = KeyGenerators.string().generateKey()
        mongoTemplate.save(encryptCalendarRegistration(registration.copy(finishedAt = finishedAt), salt))
    }


    override fun cancelRegistration(friendshipId: FriendshipId, cancelledAt: Instant) {
        val registration = findOne(friendshipId) ?: return
        val salt = KeyGenerators.string().generateKey()
        mongoTemplate.save(
            encryptCalendarRegistration(
                registration.copy(
                    finishedAt = cancelledAt,
                    cancelledAt = cancelledAt
                ), salt
            )
        )
    }

    private fun findOne(friendshipId: FriendshipId) =
        mongoTemplate.findOne(
            query(
                criteriaForUnfinishedActivity(friendshipId)
            ), SavedCalendarActivityData::class.java
        )?.let { decryptSavedCalendarActivity(it) }


    private fun criteriaForUnfinishedActivity(friendshipId: FriendshipId) =
        where("friendshipId").`is`(friendshipId.toString()).andOperator(where("finishedAt").`is`(null))

    private fun encryptCalendarRegistration(
        calendarRegistration: CalendarRegistration,
        salt: String
    ) = SavedCalendarActivityData(
        calendarRegistration.id,
        calendarRegistration.friendshipId,
        calendarRegistration.startedBy,
        calendarRegistration.startedAt,
        calendarRegistration.finishedAt,
        calendarRegistration.cancelledAt,
        salt,
    )

    private fun decryptSavedCalendarActivity(savedCalendarActivityData: SavedCalendarActivityData): CalendarRegistration =
        CalendarRegistration(
            savedCalendarActivityData.id,
            savedCalendarActivityData.friendshipId,
            savedCalendarActivityData.startedBy,
            savedCalendarActivityData.startedAt,
            savedCalendarActivityData.finishedAt,
            savedCalendarActivityData.cancelledAt
        )
}

@Document(collection = "calendar_activities")
data class SavedCalendarActivityData(
    @Id
    val id: String?,
    val friendshipId: FriendshipId,
    val startedBy: Source,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val cancelledAt: Instant?,
    val salt: String,
)
