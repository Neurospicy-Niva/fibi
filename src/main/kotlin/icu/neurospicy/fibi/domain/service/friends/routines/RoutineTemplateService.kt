package icu.neurospicy.fibi.domain.service.friends.routines

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
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
    private val githubRoutineTemplateLoader: GithubRoutineTemplateLoader
) {
    
    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineTemplateService::class.java)
    }
    
    /**
     * Load templates at application startup
     */
    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.info("Loading routine templates at startup...")
        loadTemplatesFromClasspath()
        loadTemplatesFromGithub()
    }
    
    /**
     * Load templates from GitHub daily at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun loadTemplatesScheduled() {
        LOG.info("Loading routine templates from GitHub (scheduled)...")
        loadTemplatesFromGithub()
    }
    
    /**
     * Load templates from classpath resources
     */
    fun loadTemplatesFromClasspath() {
        val resolver = PathMatchingResourcePatternResolver()
        try {
            val resources = resolver.getResources("classpath*:**/*routine*.json")
            
            resources.forEach { resource ->
                try {
                    loadTemplateFromResource(resource)
                } catch (e: Exception) {
                    LOG.error("Failed to load routine template from ${resource.filename}: ${e.message}", e)
                }
            }
            
            LOG.info("Successfully loaded ${resources.size} routine template(s) from classpath")
        } catch (e: Exception) {
            LOG.error("Failed to scan for routine template files: ${e.message}", e)
        }
    }
    
    /**
     * Load templates from GitHub
     */
    fun loadTemplatesFromGithub() {
        try {
            val routineFiles = githubRoutineTemplateLoader.loadRoutineFilesFromGithub()
            
            routineFiles.forEach { routineFile ->
                try {
                    val template = routineTemplateLoader.parseRoutineTemplate(routineFile.jsonContent)
                    saveTemplateIfNew(template, "GitHub:${routineFile.filename}")
                } catch (e: Exception) {
                    LOG.error("Failed to parse routine template from GitHub file ${routineFile.filename}: ${e.message}", e)
                }
            }
            
            LOG.info("Successfully processed ${routineFiles.size} routine template(s) from GitHub")
        } catch (e: Exception) {
            LOG.error("Failed to load routine templates from GitHub: ${e.message}", e)
        }
    }
    
    /**
     * Load template from a specific GitHub URL
     */
    fun loadTemplateFromGithubUrl(url: String): RoutineTemplate? {
        return try {
            val jsonContent = githubRoutineTemplateLoader.loadFromUrl(url)
            if (jsonContent != null) {
                routineTemplateLoader.parseRoutineTemplate(jsonContent)
            } else {
                LOG.warn("Failed to load content from URL: $url")
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to load routine template from URL $url: ${e.message}", e)
            null
        }
    }
    
    /**
     * Load template from classpath resource
     */
    private fun loadTemplateFromResource(resource: Resource) {
        LOG.debug("Loading template from: ${resource.filename}")
        
        val jsonContent = resource.inputStream.bufferedReader().use { it.readText() }
        val template = routineTemplateLoader.parseRoutineTemplate(jsonContent)
        
        saveTemplateIfNew(template, "Classpath:${resource.filename}")
    }
    
    /**
     * Save template if it doesn't already exist
     */
    private fun saveTemplateIfNew(template: RoutineTemplate, source: String) {
        val existingTemplate = templateRepository.findById(template.templateId)
        if (existingTemplate != null) {
            LOG.debug("Template ${template.templateId} already exists, skipping")
            return
        }
        
        templateRepository.save(template)
        LOG.info("Loaded new routine template: ${template.title} (${template.templateId}) from $source")
    }
} 