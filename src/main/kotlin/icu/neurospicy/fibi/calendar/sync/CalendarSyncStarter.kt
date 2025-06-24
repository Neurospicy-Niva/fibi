package icu.neurospicy.fibi.calendar.sync

import icu.neurospicy.fibi.application.routine.RoutineSchedulerEventHandler
import icu.neurospicy.fibi.domain.model.*
import icu.neurospicy.fibi.domain.model.events.CalendarRegistrationActivityFinished
import icu.neurospicy.fibi.domain.repository.CalendarConfigurationRepository
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.outgoing.quartz.QuartzSchedulerService
import org.apache.camel.ProducerTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.File

/**
 * CalendarSyncStarter listens for CalendarRegistrationActivityFinished events.
 *
 * When the event indicates successful registration, it:
 *  1. Ensures that the base calendar directory (from app.calendar.root) exists.
 *  2. Creates a friendship-specific subdirectory named after friendshipId.
 *  3. For each CalendarConfiguration, creates a subdirectory (named after calendarId) and writes a
 *     temporary vdirsyncer configuration file.
 *  4. Triggers the vdirsyncer sync via an Apache Camel route using the exec component.
 */
@Component
class CalendarSyncStarter(
    private val quartzSchedulerService: QuartzSchedulerService,
    private val friendshipLedger: FriendshipLedger,
    @Value("\${app.calendar.root}") private val appCalendarRoot: String,
    private val producerTemplate: ProducerTemplate,
    private val calendarConfigurationRepository: CalendarConfigurationRepository
) {
    private val logger = LoggerFactory.getLogger(CalendarSyncStarter::class.java)

    @EventListener
    @Async
    fun onCalendarRegistrationFinished(event: CalendarRegistrationActivityFinished) {
        // Check if registration was successful.
        if (!event.wasSuccessful()) {
            logger.warn("Calendar registration failed for friendship {}. Skipping sync.", event.friendshipId)
            return
        }

        // Ensure base directory exists.
        val baseDir = File(appCalendarRoot)
        if (!baseDir.exists()) {
            baseDir.mkdirs()
            logger.info("Created base calendar directory: {}", baseDir.absolutePath)
        }

        // Create a subdirectory for the friendship.
        val friendshipDir = File(baseDir, event.friendshipId.toString())
        if (!friendshipDir.exists()) {
            friendshipDir.mkdirs()
            logger.info("Created friendship directory: {}", friendshipDir.absolutePath)
        }

        // Process each CalendarConfiguration.
        event.configs?.forEach { config ->
            val (calendarsDir, configFile, discoveryShFile) = createCalendarDirectoryAndConfig(friendshipDir, config)

            // Build the vdirsyncer command for this calendar configuration.
            val command = CalendarSyncRequest(
                config.friendshipId,
                config.calendarConfigId,
                configFile,
                discoveryShFile,
                calendarsDir,
            )
            logger.info("Triggering vdirsyncer discover and sync for calendar ${config.calendarConfigId} with command: $command")

            // Send the command to Camel to start the discover and sync process.
            producerTemplate.sendBody("direct:vdirsyncerDiscoverAndSync", command)
        }
    }

    @EventListener
    fun onApplicationStarted(event: ApplicationStartedEvent) {
        LOG.info("Setting up schedulers for calendar synchronization.")
        val friendshipIds = friendshipLedger.findAllIds()
        val calendarConfigs =
            friendshipIds.map { calendarConfigurationRepository.load(it) }.map { it.configurations }.flatten()
        LOG.info("Scheduling for ${friendshipIds.count()} friends with ${calendarConfigs.count()} calendar configs")
        calendarConfigs.forEach {
            //for each and every private calendar of a friend
            quartzSchedulerService.scheduleCalendarSync(it.friendshipId, it.calendarConfigId)
        }
    }

    @EventListener
    fun onSyncCommand(event: SyncCalendarCmd) {
        val baseDir = File(appCalendarRoot)
        val friendshipDir = File(baseDir, event.friendshipId.toString())
        val calendarConfig =
            calendarConfigurationRepository.load(event.friendshipId).configurations.firstOrNull { it.calendarConfigId == event.calendarConfigId }
                ?: return
        val (calendarsDir, configFile, discoveryShFile) = createCalendarDirectoryAndConfig(
            friendshipDir,
            calendarConfig
        )

        // Build the vdirsyncer command for this calendar configuration.
        val command = CalendarSyncRequest(
            event.friendshipId,
            event.calendarConfigId,
            configFile,
            discoveryShFile,
            calendarsDir,
        )
        logger.info("Triggering vdirsyncer sync for calendar ${event.calendarConfigId} with command: $command")

        // Send the command to Camel to start the sync process.
        producerTemplate.sendBody("direct:vdirsyncerSync", command)
    }

    /**
     * Creates the directory structure for a calendar and writes the vdirsyncer config file.
     * Then it triggers the sync by sending a command to the Camel route.
     */
    private fun createCalendarDirectoryAndConfig(friendshipDir: File, calendarConfig: CalendarConfiguration): SyncDirs {
        // Create a subdirectory for the specific calendar using calendarId.
        val calendarsDir = File(friendshipDir, calendarConfig.calendarConfigId.toString())
        if (!calendarsDir.exists()) {
            calendarsDir.mkdirs()
            logger.info(
                "Created calendar directory for calendar {}: {}",
                calendarConfig.calendarConfigId,
                calendarsDir.absolutePath
            )
        }

        // Create a temporary vdirsyncer configuration file in the calendar directory.
        val configFile = createConfigFile(calendarsDir, friendshipDir, calendarConfig)
        val discoveryShFile = createDiscoverySh(calendarsDir, configFile.absolutePath)
        logger.info("Created vdirsyncer configuration and discovery file for calendar ${calendarConfig.calendarConfigId} at ${configFile.absolutePath}")
        return SyncDirs(calendarsDir.absolutePath, configFile.absolutePath, discoveryShFile.absolutePath)
    }

    private fun createConfigFile(
        calendarDir: File,
        friendshipDir: File,
        calendarConfig: CalendarConfiguration
    ): File {
        val configFile = File(calendarDir, "vdirsyncer.ini")
        val configContent = """
                |[general]
                |status_path = "${friendshipDir.absolutePath}/vdirsyncer_status/"
                |
                |[pair calendar_${calendarConfig.calendarConfigId}]
                |a = "remote_calendar"
                |b = "local_calendar"
                |collections = ["from a"]
                |metadata = ["displayname"]
                |
                |[storage remote_calendar]
                |type = "caldav"
                |url = "${calendarConfig.url}"
                |${
            when (val cred = calendarConfig.credential) {
                is UsernamePasswordCredential -> "username = \"${cred.username}\"\npassword = \"${cred.password}\""
                is ApiKeyCredential -> "apikey = \"${cred.key}\""
                else -> ""
            }
        }
                |
                |[storage local_calendar]
                |type = "filesystem"
                |path = "${calendarDir.absolutePath}"
                |encoding = "utf-8"
                |fileext = ".ics"
            """.trimMargin().trimIndent()

        configFile.writeText(configContent)
        return configFile
    }

    @SuppressWarnings("kotlin:S899")
    private fun createDiscoverySh(
        calendarDir: File,
        confFilePath: String,
    ): File {
        val file = File(calendarDir, "discover.sh")
        val content = """
                |#!/bin/bash
                |vdirsyncer -c $confFilePath discover << EOF
                |y
                |y
                |y
                |y
                |y
                |y
                |y
                |y
                |y
                |y
                |y
                |EOF
            """.trimMargin().trimIndent()

        file.writeText(content)
        file.setExecutable(true)
        return file
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(RoutineSchedulerEventHandler::class.java)
    }
}

data class SyncDirs(
    val calendarsDir: String,
    val configFile: String,
    val discoveryShFile: String
)

data class SyncCalendarCmd(val friendshipId: FriendshipId, val calendarConfigId: CalendarConfigId)