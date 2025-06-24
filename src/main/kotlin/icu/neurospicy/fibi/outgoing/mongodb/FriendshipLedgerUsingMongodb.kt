package icu.neurospicy.fibi.outgoing.mongodb

import icu.neurospicy.fibi.domain.model.AcceptedAgreement
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.LedgerEntry
import icu.neurospicy.fibi.domain.model.RelationStatus.*
import icu.neurospicy.fibi.domain.model.SignalId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query.query
import org.springframework.stereotype.Repository
import java.time.Instant.now
import java.time.ZoneId

@Repository
class FriendshipLedgerUsingMongodb(
    private val mongoTemplate: MongoTemplate,
) : FriendshipLedger {
    override fun findBy(signalId: SignalId): LedgerEntry? {
        return mongoTemplate.findAll(
            LedgerEntry::class.java, "friendshipledger"
        ).find { entry -> entry.signalId == signalId }
    }

    override fun findBy(friendshipId: FriendshipId): LedgerEntry? {
        return mongoTemplate.findOne(
            query(where("friendshipId").`is`(friendshipId.toString())), LedgerEntry::class.java, "friendshipledger"
        )
    }

    override fun findTimezoneBy(friendshipId: FriendshipId): ZoneId? {
        return findBy(friendshipId)?.timeZone
    }

    override fun findAllIds(): Set<FriendshipId> =
        mongoTemplate.findAll(LedgerEntry::class.java, "friendshipledger").map { it.friendshipId }.toSet()

    override fun addEntry(signalId: SignalId, sourceName: String?, sourceNumber: String?): LedgerEntry {
        return mongoTemplate.save(
            LedgerEntry(
                friendshipId = FriendshipId(),
                signalId = signalId,
                signalName = sourceName,
                signalNumber = sourceNumber,
                relationStatus = Curious
            ), "friendshipledger"
        )
    }

    override fun sentTermsOfUseRequest(friendshipId: FriendshipId) {
        findBy(friendshipId)?.let { u -> mongoTemplate.save(u.copy(relationStatus = Acquaintance)) }
    }

    override fun acceptTermsOfUse(friendshipId: FriendshipId, acceptedAgreement: AcceptedAgreement) {
        findBy(friendshipId)?.let { u ->
            mongoTemplate.save(
                u.copy(
                    relationStatus = Friend,
                    acceptedAgreements = u.acceptedAgreements.toMutableList().plus(acceptedAgreement)
                )
            )
        }
    }

    override fun startActivity(friendshipId: FriendshipId, activityName: String) {
        findBy(friendshipId)?.let { u ->
            mongoTemplate.save(
                u.copy(
                    activeActivity = activityName
                )
            )
        }
    }

    override fun finishActivity(friendshipId: FriendshipId, activityName: String) {
        findBy(friendshipId)?.takeIf { it.activeActivity == activityName }?.let { u ->
            mongoTemplate.save(
                u.copy(
                    activeActivity = null
                )
            )
        }

    }

    override fun updateSignalInfo(friendshipId: FriendshipId, number: String?, name: String?) {
        findBy(friendshipId)?.let {
            mongoTemplate.save(it.copy(signalNumber = number, signalName = name))
        }
    }

    override fun deniedTermsOfUse(friendshipId: FriendshipId) {
        findBy(friendshipId)?.let {
            mongoTemplate.save(
                it.copy(
                    deniedTermsOfServiceAt = now(),
                    relationStatus = Curious
                )
            )
        }
    }

    override fun updateZoneId(friendshipId: FriendshipId, mostFrequentZoneId: ZoneId) {
        findBy(friendshipId)?.let {
            mongoTemplate.save(
                it.copy(
                    timeZone = mostFrequentZoneId
                )
            )
        }
    }
}
