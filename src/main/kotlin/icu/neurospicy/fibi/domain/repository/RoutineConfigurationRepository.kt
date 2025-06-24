package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.RoutineConfiguration
import icu.neurospicy.fibi.domain.model.RoutineConfigurationId

interface RoutineConfigurationRepository {
    fun save(routineConfiguration: RoutineConfiguration): RoutineConfiguration
    fun findByFriendshipId(friendshipId: FriendshipId): List<RoutineConfiguration>
    fun cancelRegistration(friendshipId: FriendshipId)
    fun finishRegistration(friendshipId: FriendshipId): RoutineConfiguration?
    fun findBy(routineId: RoutineConfigurationId): RoutineConfiguration?
    fun findAll(routineType: String): List<RoutineConfiguration>
}