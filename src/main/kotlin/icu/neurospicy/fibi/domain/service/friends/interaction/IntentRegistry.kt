package icu.neurospicy.fibi.domain.service.friends.interaction

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class IntentRegistry(
    dynamicContributors: List<IntentContributor>
) {
    private val dynamic = dynamicContributors.associateBy { it.intent() }

    private val static = mapOf(
        CoreIntents.Smalltalk to "Small casual conversations",
        CoreIntents.CancelGoal to "Cancel *currently ongoing or just initiated* task (e.g., user changes their mind before completing an action)",
        CoreIntents.FollowUp to "Answer to a question",
        CoreIntents.Unknown to "Could not classify the intent",
    )

    private val all: Map<Intent, String> = static + dynamic.map { it.value.intent() to it.value.description() }.toMap()

    fun getAll(): List<Intent> = all.keys.toList()
    fun getDescriptions(): Map<Intent, String> = all.mapValues { it.value }
    fun contains(intent: Intent): Boolean = all.containsKey(intent)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady(event: ApplicationReadyEvent) {
        LOG.info("Loaded intents: ${all.keys}")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
}