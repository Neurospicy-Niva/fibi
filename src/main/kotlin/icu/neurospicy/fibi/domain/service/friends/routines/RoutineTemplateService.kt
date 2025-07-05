package icu.neurospicy.fibi.domain.service.friends.routines

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Service responsible for orchestrating the loading of routine templates from various sources.
 * This service manages startup and scheduled loading, coordinating between different loaders
 * and the repository.
 */
@Service
class RoutineTemplateService(
    private val templateRepository: RoutineTemplateRepository,
    private val routineTemplateLoader: RoutineTemplateLoader,
    private val githubRoutineTemplateLoader: GithubRoutineTemplateLoader,
    @Value("\${github.routines.repository.url:https://api.github.com/repos/Neurospicy-Niva/routines/contents}")
    private val githubApiUrl: String,
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Load templates at application startup
     */
    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.info("Loading routine templates at startup...")
        loadTemplatesFromGithubUrl(githubApiUrl)
            .forEach { saveTemplateIfNew(it) }
    }

    /**
     * Load templates from GitHub daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun loadTemplatesScheduled() {
        LOG.info("Loading routine templates from GitHub (scheduled)...")
        loadTemplatesFromGithubUrl(githubApiUrl)
            .forEach { saveTemplateIfNew(it) }
    }

    /**
     * Load template from a specific GitHub URL
     */
    fun loadTemplatesFromGithubUrl(url: String): List<RoutineTemplate> {
        return githubRoutineTemplateLoader.loadRoutineFilesFromGithub(url)
            .mapNotNull { file ->
                try {
                    routineTemplateLoader.parseRoutineTemplate(file.jsonContent)
                } catch (e: Exception) {
                    LOG.error("Failed to load routine template ${file.filename} from URL $url: ${e.message}", e)
                    null
                }
            }
    }

    /**
     * Save template if it doesn't already exist
     */
    private fun saveTemplateIfNew(template: RoutineTemplate) {
        val existingTemplate = templateRepository.findById(template.templateId)
        if (existingTemplate != null) {
            LOG.debug("Template ${template.templateId} already exists, skipping")
            return
        }

        templateRepository.save(template)
        LOG.info("Loaded new routine template: ${template.title} (${template.templateId})")
    }
} 