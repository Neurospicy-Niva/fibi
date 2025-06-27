package icu.neurospicy.fibi.domain.service.friends.routines

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class GithubRoutineTemplateLoaderTest {

    @MockK
    private lateinit var restTemplate: RestTemplate

    private val objectMapper = ObjectMapper().registerModule(kotlinModule())
    private lateinit var githubLoader: GithubRoutineTemplateLoader

    @BeforeEach
    fun setUp() {
        githubLoader = GithubRoutineTemplateLoader(
            restTemplate = restTemplate,
            objectMapper = objectMapper,
        )
    }

    @Test
    fun `loadRoutineFilesFromGithub should return RoutineFile objects`() {
        // given
        val githubFiles = arrayOf(
            GitHubFile("morning-routine.json", "file", "https://example.com/morning-routine.json"),
            GitHubFile("hydration-routine.json", "file", "https://example.com/hydration-routine.json"),
            GitHubFile("README.md", "file", "https://example.com/README.md"),
            GitHubFile("examples", "dir", "")
        )

        val githubApiResponse = objectMapper.writeValueAsString(githubFiles)
        val templateJson = """
        {
          "title": "Test Routine",
          "version": "1.0",
          "description": "Test description"
        }
        """.trimIndent()

        every { restTemplate.getForObject(any<String>(), eq(String::class.java)) } returns githubApiResponse
        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns
                ResponseEntity.ok(templateJson)

        // when
        val result = githubLoader.loadRoutineFilesFromGithub("https://example.com/morning-routine.json")

        // then
        assertEquals(2, result.size) // Only .json files with "routine" in name
        assertTrue(result.any { it.filename == "morning-routine.json" })
        assertTrue(result.any { it.filename == "hydration-routine.json" })
        result.forEach { routineFile ->
            assertTrue(routineFile.jsonContent.contains("Test Routine"))
        }
    }

    @Test
    fun `loadRoutineFilesFromGithub should handle API errors gracefully`() {
        // given
        every { restTemplate.getForObject(any<String>(), eq(String::class.java)) } throws RuntimeException("API error")

        // when
        val result = githubLoader.loadRoutineFilesFromGithub("https://example.com/morning-routine.json")

        // then
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadRoutineFilesFromGithub should filter non-routine files`() {
        // given
        val githubFiles = arrayOf(
            GitHubFile("morning-routine.json", "file", "https://example.com/morning-routine.json"),
            GitHubFile("config.json", "file", "https://example.com/config.json"),      // No "routine" in name
            GitHubFile("routine.txt", "file", "https://example.com/routine.txt"),      // Not .json
            GitHubFile("folder", "dir", "")             // Not a file
        )

        val githubApiResponse = objectMapper.writeValueAsString(githubFiles)
        val templateJson = """{"title": "Test"}"""

        every { restTemplate.getForObject(any<String>(), eq(String::class.java)) } returns githubApiResponse
        every {
            restTemplate.exchange(
                any<String>(),
                eq(HttpMethod.GET),
                any<HttpEntity<String>>(),
                eq(String::class.java)
            )
        } returns
                ResponseEntity.ok(templateJson)

        // when
        val result = githubLoader.loadRoutineFilesFromGithub("https://example.com/morning-routine.json")

        // then
        assertEquals(1, result.size) // Only morning-routine.json should match
        assertEquals("morning-routine.json", result[0].filename)
    }
} 