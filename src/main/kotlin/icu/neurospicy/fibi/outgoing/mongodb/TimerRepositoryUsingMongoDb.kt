package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.Timer
import icu.neurospicy.fibi.domain.model.events.TimerExpired
import icu.neurospicy.fibi.domain.model.events.TimerSet
import icu.neurospicy.fibi.domain.model.events.TimerStopped
import icu.neurospicy.fibi.domain.model.events.TimerUpdated
import icu.neurospicy.fibi.domain.repository.TimerRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class TimerRepositoryUsingMongoDb(
    private val mongodbTemplate: MongoTemplate,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : TimerRepository {
    override fun save(timer: Timer): Timer {
        val savedTimer = mongodbTemplate.save(timer)
        applicationEventPublisher.publishEvent(TimerSet(this.javaClass, timer.owner, savedTimer))
        return savedTimer
    }

    override fun findByFriendshipId(friendshipId: FriendshipId): List<Timer> {
        return mongodbTemplate.find(query(where("owner").`is`(friendshipId.toString())), Timer::class.java)
    }

    override fun remove(friendshipId: FriendshipId, id: String) {
        mongodbTemplate.remove(
            query(where("owner").`is`(friendshipId.toString()).andOperator(where("_id").`is`(id))), Timer::class.java
        )
        applicationEventPublisher.publishEvent(TimerStopped(this.javaClass, friendshipId, id))
    }

    override fun update(
        friendshipId: FriendshipId, id: String, duration: Duration?, label: String?
    ) {
        mongodbTemplate.findOne(
            query(where("owner").`is`(friendshipId.toString()).andOperator(where("_id").`is`(id))), Timer::class.java
        )?.apply {
            val savedTimer =
                mongodbTemplate.save(this.copy(duration = duration ?: this.duration, label = label ?: this.label))
            applicationEventPublisher.publishEvent(TimerUpdated(this.javaClass, friendshipId, savedTimer))
        }
    }

    override fun expired(owner: FriendshipId, id: String) {
        mongodbTemplate.remove(
            query(where("owner").`is`(owner.toString()).andOperator(where("_id").`is`(id))), Timer::class.java
        )
        applicationEventPublisher.publishEvent(TimerExpired(this.javaClass, owner, id))
    }
}