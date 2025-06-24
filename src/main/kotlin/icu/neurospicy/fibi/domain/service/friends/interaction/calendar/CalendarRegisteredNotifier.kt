package icu.neurospicy.fibi.domain.service.friends.interaction.calendar

import icu.neurospicy.fibi.calendar.sync.CalendarSynchronized
import icu.neurospicy.fibi.config.PromptsConfiguration
import icu.neurospicy.fibi.domain.model.Appointment
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant.now
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter

@Component
class CalendarRegisteredNotifier(
    private val eventPublisher: ApplicationEventPublisher,
    private val calendarActivityRepository: CalendarActivityRepository,
    private val friendshipLedger: FriendshipLedger,
    private val calendarRepository: CalendarRepository,
    private val promptsConfiguration: PromptsConfiguration
) {
    @EventListener
    fun sendTodaysSchedule(event: CalendarSynchronized) {
        // ignore regularly scheduled calendar syncs
        val friendshipId = event.friendshipId
        val calendarRegistration = calendarActivityRepository.loadActiveRegistration(friendshipId) ?: return
        calendarActivityRepository.finishRegistration(friendshipId, now())

        val friendsTimeZone = friendshipLedger.findBy(friendshipId)?.timeZone ?: UTC
        val startOfDay = LocalDate.now().atStartOfDay(friendsTimeZone)
        val todaysAppointments = calendarRepository.loadAppointmentsForTimeRange(
            TimeRange(startOfDay.toInstant(), Duration.ofDays(1)), friendshipId
        )
        val singleOrMultipleCalendarMessage =
            if (todaysAppointments.groupBy { it.calendarId }.keys.distinct().size == 1) "Great news—your calendar is now connected!"
            else "Great news—your calendars are now connected!"

        val appointmentsMessage = if (todaysAppointments.isEmpty()) {
            "Today, you don't have any appointments."
        } else {
            "Today, you have:\n" + if (todaysAppointments.size > 6) {
                "${
                    todaysAppointments.subList(0, 6).joinToString(
                        separator = "\n", transform = appointmentLines(friendsTimeZone)
                    )
                }\n…"
            } else {
                todaysAppointments.joinToString(
                    separator = "\n", transform = appointmentLines(friendsTimeZone)
                )
            }
        }

        val responseChannel = when (calendarRegistration.startedBy) {
            is MessageSource -> calendarRegistration.startedBy.channel
        }
        calendarActivityRepository.finishRegistration(friendshipId, now())
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, friendshipId, OutgoingTextMessage(
                    responseChannel, promptsConfiguration.calendarSuccessPromptTemplate.replace(
                        "\${singleOrMultipleCalendarMessage}", singleOrMultipleCalendarMessage
                    ).replace("\${appointmentsMessage}", appointmentsMessage)
                )
            )
        )
    }


    private fun appointmentLines(friendsTimeZone: ZoneId?): (Appointment) -> String = {
        "- " + it.startAt.instant.atZone(friendsTimeZone).format(
            DateTimeFormatter.ofPattern("HH:mm")
        ) + " " + it.summary
    }
}