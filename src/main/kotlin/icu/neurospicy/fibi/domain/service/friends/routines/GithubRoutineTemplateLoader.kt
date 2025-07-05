package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

/**
 * Service responsible for fetching routine template files from GitHub repositories.
 * This is a pure GitHub API client that returns raw JSON content.
 */
@Service
class GithubRoutineTemplateLoader(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Load routine files from GitHub repository
     * @return List of RoutineFile objects containing filename and JSON content
     */
    fun loadRoutineFilesFromGithub(url: String): List<RoutineFile> {
        return try {
            val files = fetchRepositoryContents(url)

            files.filter { it.isRoutineFile() }
                .mapNotNull { file ->
                    try {
                        val jsonContent = loadFromUrl(file.downloadUrl!!)
                        if (jsonContent != null) {
                            RoutineFile(file.name, jsonContent)
                        } else {
                            LOG.warn("Failed to load content from ${file.name}")
                            null
                        }
                    } catch (e: Exception) {
                        LOG.error("Failed to load routine file ${file.name}: ${e.message}", e)
                        null
                    }
                }
        } catch (e: Exception) {
            LOG.error("Failed to fetch routine files from GitHub: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Load a single routine file from URL
     * @param url The GitHub raw file URL
     * @return Raw JSON content as string, or null if failed
     */
    private fun loadFromUrl(url: String): String? {
        return try {
            val headers = HttpHeaders()
            headers.set("Accept", "application/vnd.github.v3.raw")
            val entity = HttpEntity<String>(headers)

            val response = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
            response.body
        } catch (e: Exception) {
            LOG.error("Failed to load routine file from URL $url: ${e.message}", e)
            null
        }
    }

    /**
     * Fetch repository contents from GitHub API
     */
    private fun fetchRepositoryContents(githubApiUrl: String): List<GitHubFile> {
        return try {
            val response = restTemplate.getForObject(githubApiUrl, String::class.java)
            val filesArray = objectMapper.readValue(response, Array<GitHubFile>::class.java)
            filesArray.toList()
        } catch (e: Exception) {
            LOG.error("Failed to fetch repository contents: ${e.message}", e)
            emptyList()
        }
    }
}

/**
 * Data class for GitHub API file response
 */
data class GitHubFile(
    val name: String,
    val type: String,
    @JsonProperty("download_url")
    val downloadUrl: String?,
) {
    @JsonIgnore
    fun isRoutineFile(): Boolean {
        return type == "file" &&
                downloadUrl != null &&
                name.endsWith(".json")
    }
}

/**
 * Data class representing a routine file with its content
 */
data class RoutineFile(
    val filename: String,
    val jsonContent: String,
)