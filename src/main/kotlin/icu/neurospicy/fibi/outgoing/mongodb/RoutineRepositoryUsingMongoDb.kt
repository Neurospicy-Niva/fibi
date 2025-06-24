package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineInstance
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineInstanceId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineRepository
import icu.neurospicy.fibi.domain.service.friends.routines.TaskRoutineConcept
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository

@Repository
class RoutineRepositoryUsingMongoDb(val mongoTemplate: MongoTemplate) : RoutineRepository {
    override fun save(instance: RoutineInstance) {
        mongoTemplate.save(instance)
    }

    override fun findById(
        friendshipId: FriendshipId,
        instanceId: RoutineInstanceId,
    ): RoutineInstance? {
        return mongoTemplate.findOne(
            query(
                where("instanceId").`is`(instanceId.toString())
                    .andOperator(where("friendshipId").`is`(friendshipId.toString()))
            ), RoutineInstance::class.java
        )
    }

    override fun findByConceptRelatedToTask(friendshipId: FriendshipId, taskId: String): List<RoutineInstance> {
        return mongoTemplate.find(
            query(
                where("friendshipId").`is`(friendshipId.toString())
            ), RoutineInstance::class.java
        ).filter { it.concepts.any { concept -> concept is TaskRoutineConcept && concept.linkedTaskId == taskId } }
    }
}