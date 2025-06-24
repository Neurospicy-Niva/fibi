package icu.neurospicy.fibi.application.task

import icu.neurospicy.fibi.domain.model.Channel.SIGNAL
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.events.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class TaskEventsToSignalSender(
    val eventPublisher: ApplicationEventPublisher
) {
    @EventListener
    @Async
    fun onTaskAdded(event: TaskAdded) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingTextMessage(
                    SIGNAL, "__Added task: ${event.task.title}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTaskUpdated(event: TaskUpdated) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingTextMessage(
                    SIGNAL, "__Updated task ${event.task.title}__ --${event.oldVersion.title}--"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTaskReworded(event: TaskReworded) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingTextMessage(
                    SIGNAL, "__Renamed task ${event.task.title} (--${event.oldVersion.title}--)__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTaskCompleted(event: TaskCompleted) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingTextMessage(
                    SIGNAL,
                    "__${if (event.task.completed) "--${event.task.title}-- completed" else "\"${event.task.title}\" reset to todo"}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTaskCleanedUp(event: TasksCleanedUp) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingTextMessage(
                    SIGNAL, "__Cleaned up tasks ${event.tasks.joinToString { "--${it.title}--" }}__"
                )
            )
        )
    }

    @EventListener
    @Async
    fun onTaskRemoved(event: TaskRemoved) {
        eventPublisher.publishEvent(
            SendMessageCmd(
                this.javaClass, event.owner, OutgoingTextMessage(
                    SIGNAL, "__Removed task --${event.task.title}--__"
                )
            )
        )
    }
}