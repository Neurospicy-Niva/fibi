package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.RoutineConfiguration
import icu.neurospicy.fibi.domain.model.RoutineConfigurationId
import icu.neurospicy.fibi.domain.repository.RoutineConfigurationRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class RoutineConfigurationRepositoryUsingMongodb(
    private val mongoTemplate: MongoTemplate
) : RoutineConfigurationRepository {

    override fun save(routineConfiguration: RoutineConfiguration): RoutineConfiguration {
        val config = routineConfiguration.copy(updatedAt = LocalDateTime.now())
        return mongoTemplate.save(config, "routines")
    }

    override fun findByFriendshipId(friendshipId: FriendshipId): List<RoutineConfiguration> {
        return mongoTemplate.find(
            Query.query(where("friendshipId").`is`(friendshipId.toString())),
            RoutineConfiguration::class.java,
            "routines"
        )
    }

    override fun cancelRegistration(friendshipId: FriendshipId) {
        val query = Query.query(where("friendshipId").`is`(friendshipId.toString()))
        val configs = mongoTemplate.find(query, RoutineConfiguration::class.java, "routines")
        configs.forEach { config ->
            val updated = config.copy(enabled = false, updatedAt = LocalDateTime.now())
            mongoTemplate.save(updated, "routines")
        }
    }

    override fun finishRegistration(friendshipId: FriendshipId): RoutineConfiguration {
        val query = Query.query(where("friendshipId").`is`(friendshipId.toString()))
        val configs = mongoTemplate.find(query, RoutineConfiguration::class.java, "routines")
        configs.forEach { config ->
            val updated = config.copy(enabled = true, updatedAt = LocalDateTime.now())
            mongoTemplate.save(updated, "routines")
        }
        return configs.first()
    }

    override fun findBy(routineId: RoutineConfigurationId): RoutineConfiguration? {
        val query = Query.query(where("routineId").`is`(routineId.toString()))
        return mongoTemplate.findOne(query, RoutineConfiguration::class.java, "routines")
    }

    override fun findAll(routineType: String): List<RoutineConfiguration> {
        val query = Query.query(where("routineType").`is`(routineType))
        return mongoTemplate.find(query, RoutineConfiguration::class.java, "routines")
    }
}