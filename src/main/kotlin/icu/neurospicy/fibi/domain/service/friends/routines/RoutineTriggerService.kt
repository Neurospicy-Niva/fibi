package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.OutgoingTextMessage
import icu.neurospicy.fibi.domain.model.Task
import icu.neurospicy.fibi.domain.model.events.SendMessageCmd
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineTriggerFired
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Handles execution of routine triggers and their associated effects.
 * Processes trigger events and executes configured actions like sending messages or creating tasks.
 */
@Service
class RoutineTriggerService(
    private val routineRepository: RoutineRepository,
    private val templateRepository: RoutineTemplateRepository,
    private val taskRepository: TaskRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
) {

    @EventListener
    @Async
    fun onRoutineTriggerFired(event: RoutineTriggerFired) {
        LOG.info("Processing trigger {} fired for routine instance {}", event.triggerId, event.instanceId)

        val instance = routineRepository.findById(event.friendshipId, event.instanceId) ?: run {
            LOG.warn("Could not find routine instance {} for trigger {}", event.instanceId, event.triggerId)
            return
        }

        val template = templateRepository.findById(instance.templateId) ?: run {
            LOG.warn("Could not find template {} for routine instance {}", instance.templateId, event.instanceId)
            return
        }

        val trigger = template.triggers.find { it.id == event.triggerId } ?: run {
            LOG.warn("Could not find trigger {} in template {}", event.triggerId, template.templateId)
            return
        }

        try {
            executeTriggerEffect(trigger.effect, event)
            LOG.info("Successfully executed trigger {} effect for routine {}", event.triggerId, event.instanceId)
        } catch (e: Exception) {
            LOG.error(
                "Failed to execute trigger {} effect for routine {}: {}",
                event.triggerId, event.instanceId, e.message, e
            )
        }
    }

    private fun executeTriggerEffect(effect: TriggerEffect, event: RoutineTriggerFired) {
        when (effect) {
            is SendMessage -> {
                LOG.debug("Sending trigger message: {}", effect.message)
                eventPublisher.publishEvent(
                    SendMessageCmd(
                        this.javaClass,
                        event.friendshipId,
                        OutgoingTextMessage(Channel.SIGNAL, effect.message)
                    )
                )

                eventLog.log(
                    RoutineEventLogEntry(
                        routineInstanceId = event.instanceId,
                        friendshipId = event.friendshipId,
                        event = RoutineEventType.TRIGGER_SCHEDULED, // Reusing existing event type
                        timestamp = java.time.Instant.now(),
                        metadata = mapOf(
                            "triggerId" to event.triggerId.toString(),
                            "effectType" to "SendMessage",
                            "message" to effect.message
                        )
                    )
                )
            }

            is CreateTask -> {
                LOG.debug("Creating trigger task: {}", effect.taskDescription)
                val task = Task(
                    owner = event.friendshipId,
                    title = effect.taskDescription,
                )
                val savedTask = taskRepository.save(task)

                // Optionally link task to routine instance for tracking
                val instance = routineRepository.findById(event.friendshipId, event.instanceId)
                if (instance != null && savedTask.id != null) {
                    val updatedInstance = instance.copy(
                        concepts = instance.concepts + TaskRoutineConcept(
                            linkedTaskId = savedTask.id!!,
                            linkedStep = RoutineStepId.forDescription("trigger-${effect.parameterKey}")
                        )
                    )
                    routineRepository.save(updatedInstance)
                }

                eventLog.log(
                    RoutineEventLogEntry(
                        routineInstanceId = event.instanceId,
                        friendshipId = event.friendshipId,
                        event = RoutineEventType.TRIGGER_SCHEDULED, // Reusing existing event type
                        timestamp = java.time.Instant.now(),
                        metadata = mapOf(
                            "triggerId" to event.triggerId.toString(),
                            "effectType" to "CreateTask",
                            "taskId" to (savedTask.id ?: "unknown"),
                            "taskDescription" to effect.taskDescription,
                            "parameterKey" to effect.parameterKey
                        )
                    )
                )
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }
} 