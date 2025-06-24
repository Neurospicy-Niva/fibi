package icu.neurospicy.fibi.domain.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

data class CalendarConfigurations(
    val configurations: Set<CalendarConfiguration>
)

data class CalendarConfiguration(
    val friendshipId: FriendshipId,
    val calendarConfigId: CalendarConfigId = CalendarConfigId(),
    val url: String,
    val credential: Credential? = null,
    val lastSyncAt: Instant? = null
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type")
@JsonSubTypes(JsonSubTypes.Type(value = UsernamePasswordCredential::class, name = "usernamepassword"),
    JsonSubTypes.Type(value = ApiKeyCredential::class, name = "apikey"))
interface Credential

data class UsernamePasswordCredential(
    val username: String,
    val password: String
) : Credential

data class ApiKeyCredential(
    val key: String
) : Credential