package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTemplate
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTemplateId
import icu.neurospicy.fibi.domain.service.friends.routines.RoutineTemplateRepository
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository

@Repository
class RoutineTemplateRepositoryUsingMongoDb(val mongoTemplate: MongoTemplate) : RoutineTemplateRepository {
    override fun save(template: RoutineTemplate): RoutineTemplate {
        return mongoTemplate.save(template)
    }

    override fun findById(id: RoutineTemplateId): RoutineTemplate? {
        return mongoTemplate.findOne(query(where("templateId").`is`(id.toString())), RoutineTemplate::class.java)
    }

    override fun loadAll(): List<RoutineTemplate> {
        return mongoTemplate.findAll(RoutineTemplate::class.java)
    }

    override fun remove(id: String) {
        mongoTemplate.remove(query(where("_id").`is`(id)), RoutineTemplate::class.java)
    }
}