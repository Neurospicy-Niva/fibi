package icu.neurospicy.fibi.application.routine

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.OutgoingAdaptedTextMessage
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.RoutineConfigurationRepository
import icu.neurospicy.fibi.domain.service.ConversationContextService
import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Listens for RoutineExecutionEvents and processes those of type MORNING_ROUTINE.
 */
@Component
class MorningRoutineExecutionListener(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val routineConfigurationRepository: RoutineConfigurationRepository,
    private val conversationContextService: ConversationContextService
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(MorningRoutineExecutionListener::class.java)
    }

    @EventListener(condition = "event.routineType == \"Morning routine\"")
    fun onRoutineExecution(event: RoutineExecutionEvent) {
        LOG.info("Processing morning routine for friendshipId=${event.friendshipId}")
        val routineConfiguration = routineConfigurationRepository.findBy(event.routineId)
        conversationContextService.startNewConversation(
            event.friendshipId,
            Intent("Morning routine")
//            PossibleIntent(
//                "Morning routine",
//                "The user is currently doing their morning routine supported by Fibi. They finish activities (such as breakfast or brushing teeth), they ask for information about the day (such as the schedule, tasks, appointments, weather etc.).",
//                "\uD83E\uDDD8\uD83C\uDFFB\u200D♀\uFE0F"
//            )
        )
        applicationEventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.friendshipId, OutgoingAdaptedTextMessage(
                    routineConfiguration?.preferredChannel ?: Channel.SIGNAL,
                    """
                        It's the wake-up time of the user, the morning routine is starting and you shall greet them gently with a warm welcome to the new day.
                        Important: Never invent any tasks, activities, appointments. Don't push the user.
                    """.trimIndent(),
                    "Good morning! Let's have a nice start into the day. How do you feel?",
                    useHistory = false
                )
            )
        )
    }
}