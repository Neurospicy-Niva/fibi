package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId

interface RoutineRepository {
    fun save(instance: RoutineInstance)
    fun findById(friendshipId: FriendshipId, instanceId: RoutineInstanceId): RoutineInstance?
    fun findByConceptRelatedToTask(friendshipId: FriendshipId, taskId: String): List<RoutineInstance>
}

interface RoutineTemplateRepository {
    fun findById(id: RoutineTemplateId): RoutineTemplate?
    fun loadAll(): List<RoutineTemplate>
    fun save(template: RoutineTemplate): RoutineTemplate
    fun remove(id: String)
}

