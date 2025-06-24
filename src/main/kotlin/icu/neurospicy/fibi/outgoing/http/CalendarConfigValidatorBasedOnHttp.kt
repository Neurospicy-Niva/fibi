package icu.neurospicy.fibi.outgoing.http

import icu.neurospicy.fibi.application.calendar.CalendarConfigValidator
import icu.neurospicy.fibi.domain.model.*
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class CalendarConfigValidatorBasedOnHttp(
    private val restTemplate: RestTemplate
) : CalendarConfigValidator {

    override fun isValid(config: UnvalidatedCalendarConfiguration): ValidationResult {
        when (val credential = config.credential) {
            is UsernamePasswordCredential -> {
                val rqstBuilder =
                    restTemplate.requestFactory.createRequest(config.url.let { URI.create(it) }, HttpMethod.HEAD)
                rqstBuilder.headers.setBasicAuth(credential.username, credential.password)
                rqstBuilder
            }

            is ApiKeyCredential -> {
                val rqstBuilder =
                    restTemplate.requestFactory.createRequest(config.url.let { URI.create(it) }, HttpMethod.HEAD)
                rqstBuilder.headers.setBearerAuth(credential.key)
                rqstBuilder
            }

            else -> restTemplate.requestFactory.createRequest(config.url.let { URI.create(it) }, HttpMethod.HEAD)
        }.let { client ->
            val response = client.execute()
            val isValid = response.statusCode == HttpStatusCode.valueOf(200)
            return ValidationResult(
                config = if (isValid) CalendarConfiguration(
                    friendshipId = config.friendshipId,
                    url = config.url,
                    credential = config.credential
                ) else null,
                errorMessage = if (isValid) null else "${config.url} with credentials are not valid. Server did not answer with HTTP status OK, but ${response.statusCode}: ${response.statusText}"
            )
        }
    }
}

data class ValidationResult(
    val config: CalendarConfiguration?,
    val errorMessage: String? = null
) {
    val isValid get() = config != null && errorMessage.isNullOrBlank()
}

data class UnvalidatedCalendarConfiguration(
    val friendshipId: FriendshipId,
    val url: String,
    val credential: Credential? = null
)