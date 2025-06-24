package icu.neurospicy.fibi.outgoing.mongodb

import com.fasterxml.jackson.databind.ObjectMapper
import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.CalendarConfigurations
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.CalendarConfigurationRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.keygen.KeyGenerators
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CalendarConfigurationRepositoryUsingMongodb(
    private val mongoTemplate: MongoTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${app.calendar.key}") private val calendarEncryptionKey: String,
) : CalendarConfigurationRepository {
    override fun save(friendshipId: FriendshipId, calendarConfigurations: CalendarConfigurations) {
        val salt = KeyGenerators.string().generateKey()
        mongoTemplate.save(
            SavedData(
                friendshipId,
                salt,
                Encryptors.delux(calendarEncryptionKey, salt)
                    .encrypt(objectMapper.writeValueAsString(calendarConfigurations))
            )
        )
    }

    override fun load(friendshipId: FriendshipId): CalendarConfigurations {
        return mongoTemplate.findOne(
            query(where("friendshipId").`is`(friendshipId.toString())), SavedData::class.java
        )?.let { Encryptors.delux(calendarEncryptionKey, it.salt).decrypt(it.blob) }
            ?.let { objectMapper.readValue(it, CalendarConfigurations::class.java) }
            ?: CalendarConfigurations(emptySet())
    }

    override fun synchronized(friendshipId: FriendshipId, calendarConfigId: CalendarConfigId, syncedAt: Instant) {
        save(
            friendshipId, CalendarConfigurations(load(friendshipId).configurations.map {
                if (it.calendarConfigId != calendarConfigId) it else it.copy(lastSyncAt = syncedAt)
            }.toSet())
        )
    }

    override fun remove(
        friendshipId: FriendshipId, calendarConfigId: CalendarConfigId
    ) {
        mongoTemplate.remove(
            query(where("friendshipId").`is`(friendshipId.toString())), SavedData::class.java
        )
    }
}

@Document(collection = "calendarconfiguration")
data class SavedData(
    val friendshipId: FriendshipId, val salt: String, val blob: String
)