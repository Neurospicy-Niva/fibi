package icu.neurospicy.fibi.domain.service.friends.interaction

import icu.neurospicy.fibi.domain.model.FriendshipId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository

@Repository
class GoalContextRepositoryBasedOnMongoDb(
    private val mongoTemplate: MongoTemplate,
) : GoalContextRepository {
    override fun saveContext(
        friendshipId: FriendshipId, context: GoalContext
    ) {
        mongoTemplate.remove(
            query(where("friendshipId").`is`(friendshipId.toString())), GoalContextOfFriend::class.java
        )
        mongoTemplate.save(GoalContextOfFriend(friendshipId = friendshipId, goalContext = context))
    }

    override fun loadContext(friendshipId: FriendshipId): GoalContext? {
        return mongoTemplate.findOne(
            query(where("friendshipId").`is`(friendshipId.toString())), GoalContextOfFriend::class.java
        )?.goalContext
    }
}

@Document(collection = "goal-contexts")
data class GoalContextOfFriend(
    @Id val _id: String? = null,
    @Indexed(unique = true) val friendshipId: FriendshipId,
    val goalContext: GoalContext,
)