package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.service.friends.routines.RoutineEventLog
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineEventLogEntry
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Repository

@Repository
class RoutineEventLogUsingMongoDb(
    private val mongoTemplate: MongoTemplate,
) : RoutineEventLog {
    override fun log(entry: RoutineEventLogEntry) {
        mongoTemplate.save(entry)
    }
}