package icu.neurospicy.fibi.application

import icu.neurospicy.fibi.outgoing.signal.SignalMessageSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class SetupSignalProfileOnStartup(
    private val signalMessageSender: SignalMessageSender,
    @Value("\${fibi.givenName}") private val givenName: String,
    @Value("\${fibi.familyName}") private val familyName: String,
    @Value("\${fibi.description}") private val description: String,
    @Value("\${fibi.avatar}") private val avatar: String
) {
    @EventListener
    @Async
    fun onApplicationEvent(event: ContextRefreshedEvent?) {
        LOG.info("Setting up signal profile.")
        signalMessageSender.sendProfileUpdate(givenName, familyName, description, avatar)
        System.setProperty("net.fortuna.ical4j.timezone.cache.impl", "net.fortuna.ical4j.util.MapTimeZoneCache")

    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SetupSignalProfileOnStartup::class.java)
    }
}