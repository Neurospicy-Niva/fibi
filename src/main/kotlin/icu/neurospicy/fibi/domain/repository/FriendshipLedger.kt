package icu.neurospicy.fibi.domain.repository

import icu.neurospicy.fibi.domain.model.AcceptedAgreement
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.LedgerEntry
import icu.neurospicy.fibi.domain.model.SignalId
import java.time.ZoneId

interface FriendshipLedger {
    fun findBy(signalId: SignalId): LedgerEntry?
    fun findBy(friendshipId: FriendshipId): LedgerEntry?
    fun findTimezoneBy(friendshipId: FriendshipId): ZoneId?
    fun addEntry(signalId: SignalId, sourceName: String? = null, sourceNumber: String? = null): LedgerEntry
    fun sentTermsOfUseRequest(friendshipId: FriendshipId)
    fun acceptTermsOfUse(friendshipId: FriendshipId, acceptedAgreement: AcceptedAgreement)
    fun updateSignalInfo(friendshipId: FriendshipId, number: String?, name: String?)
    fun deniedTermsOfUse(friendshipId: FriendshipId)
    fun startActivity(friendshipId: FriendshipId, activityName: String)
    fun finishActivity(friendshipId: FriendshipId, activityName: String)
    fun updateZoneId(friendshipId: FriendshipId, mostFrequentZoneId: ZoneId)
    fun findAllIds(): Set<FriendshipId>
}
