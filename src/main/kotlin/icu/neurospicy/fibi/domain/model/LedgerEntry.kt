package icu.neurospicy.fibi.domain.model

import com.maximeroussy.invitrode.WordGenerator
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.ZoneId
import java.util.*


@Document(collection = "friendshipledger")
data class LedgerEntry(
    @Id
    val _id: String? = null,
    @Indexed(unique = true)
    val friendshipId: FriendshipId = FriendshipId(),
    val signalId: SignalId? = null,
    val signalName: String? = null,
    val signalNumber: String? = null,
    val relationStatus: RelationStatus,
    val deniedTermsOfServiceAt: Instant? = null,
    val acceptedAgreements: List<AcceptedAgreement> = emptyList(),
    val activeActivity: String? = null,
    val timeZone: ZoneId? = null
)

@JvmInline
value class FriendshipId(
    private val word: String = WordGenerator().newWord(4).lowercase() + "_"
            + WordGenerator().newWord(6).lowercase() + "_"
            + WordGenerator().newWord(4).lowercase()
) {
    override fun toString(): String = word
}

@JvmInline
value class SignalId(private val uuid: UUID) {
    override fun toString(): String = uuid.toString()
}

data class AcceptedAgreement(
    val agreementTitle: String,
    val acceptedAt: Instant,
    val acceptanceText: String
)