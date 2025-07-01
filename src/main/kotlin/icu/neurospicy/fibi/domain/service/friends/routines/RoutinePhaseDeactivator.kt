package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.TaskRepository
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContext
import icu.neurospicy.fibi.domain.service.friends.interaction.GoalContextRepository
import icu.neurospicy.fibi.domain.service.friends.routines.events.PhaseDeactivated
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Handles the complete deactivation and cleanup of a routine phase.
 * This is the counterpart to RoutinePhaseActivator - it ensures proper cleanup
 * of all resources associated with the phase being deactivated.
 */
@Service
class RoutinePhaseDeactivator(
    private val scheduler: RoutineScheduler,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventLog: RoutineEventLog,
    private val templateRepository: RoutineTemplateRepository,
    private val goalContextRepository: GoalContextRepository,
    private val taskRepository: TaskRepository,
    private val instanceRepository: RoutineRepository,
) {
    
    /**
     * Completely deactivates a phase, cleaning up all associated resources:
     * - Removes phase iteration schedulers (recurring executions based on phase.schedule)
     * - Removes pending step schedulers for incomplete steps in the current iteration
     * - Publishes deactivation event for other services to clean up tasks/goals
     * - Logs the deactivation
     * 
     * NOTE: Does NOT affect TriggerCondition-based phase schedulers (AfterDays, etc.)
     */
    fun deactivatePhase(instance: RoutineInstance, phaseId: RoutinePhaseId) {
        LOG.info("Deactivating phase {} for routine instance {}", phaseId, instance.instanceId)
        
        try {
            // Remove phase iteration scheduler (phase.schedule-based recurring executions)
            scheduler.removePhaseIterationSchedule(
                instance.friendshipId,
                instance.instanceId,
                phaseId
            )
            
            // Remove step schedulers for incomplete steps in current iteration
            removeIncompleteStepSchedulers(instance, phaseId)
            
            // Clean up ongoing conversations related to this phase
            cleanupOngoingConversations(instance, phaseId)
            
            // Clean up uncompleted tasks created by this phase
            cleanupUncompletedTasks(instance, phaseId)
            
            // Log deactivation event
            eventLog.log(
                RoutineEventLogEntry(
                    routineInstanceId = instance.instanceId,
                    friendshipId = instance.friendshipId,
                    event = RoutineEventType.PHASE_DEACTIVATED,
                    timestamp = Instant.now(),
                    metadata = mapOf("phaseId" to phaseId),
                )
            )
            
            // Publish event so other services can clean up related resources
            // (tasks, goal contexts, etc.)
            eventPublisher.publishEvent(
                PhaseDeactivated(
                    this.javaClass,
                    instance.friendshipId,
                    instance.instanceId,
                    phaseId
                )
            )
            
            LOG.info("Successfully deactivated phase {} for routine instance {}", phaseId, instance.instanceId)
            
        } catch (e: Exception) {
            LOG.error("Error deactivating phase {} for routine instance {}: {}", 
                phaseId, instance.instanceId, e.message, e)
            // Continue with event logging even if cleanup fails
            eventLog.log(
                RoutineEventLogEntry(
                    routineInstanceId = instance.instanceId,
                    friendshipId = instance.friendshipId,
                    event = RoutineEventType.PHASE_DEACTIVATED,
                    timestamp = Instant.now(),
                    metadata = mapOf("phaseId" to phaseId, "error" to (e.message ?: "Unknown error"))
                )
            )
            
            eventPublisher.publishEvent(
                PhaseDeactivated(
                    this.javaClass,
                    instance.friendshipId,
                    instance.instanceId,
                    phaseId
                )
            )
        }
    }
    
    /**
     * Removes schedulers for incomplete steps in the current phase iteration
     */
    private fun removeIncompleteStepSchedulers(instance: RoutineInstance, phaseId: RoutinePhaseId) {
        val template = templateRepository.findById(instance.templateId) ?: run {
            LOG.warn("Cannot remove step schedulers: template {} not found for instance {}", 
                instance.templateId, instance.instanceId)
            return
        }
        
        val phase = template.findPhase(phaseId) ?: run {
            LOG.warn("Cannot remove step schedulers: phase {} not found in template {}", 
                phaseId, instance.templateId)
            return
        }
        
        // Get completed step IDs from current iteration
        val completedStepIds = instance.progress.getCurrentIteration()
            ?.completedSteps
            ?.map { it.id }
            ?.toSet() 
            ?: emptySet()
        
        // Remove schedulers for all incomplete steps
        val incompleteSteps = phase.steps.filterNot { step -> step.id in completedStepIds }
        
        incompleteSteps.forEach { step ->
            try {
                scheduler.removeScheduleFor(
                    instance.friendshipId,
                    instance.instanceId,
                    phaseId,
                    step.id
                )
                LOG.debug("Removed scheduler for incomplete step {} in phase {}", step.id, phaseId)
            } catch (e: Exception) {
                LOG.warn("Failed to remove scheduler for step {} in phase {}: {}", 
                    step.id, phaseId, e.message)
            }
        }
        
        if (incompleteSteps.isNotEmpty()) {
            LOG.info("Removed {} incomplete step schedulers for phase {}", incompleteSteps.size, phaseId)
        }
    }
    
    /**
     * Cleans up ongoing conversations related to this phase:
     * - Parameter request steps that created AnswerQuestion subtasks
     * - Any other routine-related conversations for this phase
     */
    private fun cleanupOngoingConversations(instance: RoutineInstance, phaseId: RoutinePhaseId) {
        try {
            val goalContext = goalContextRepository.loadContext(instance.friendshipId)
            if (goalContext == null) {
                LOG.debug("No goal context found for friendship {}, skipping conversation cleanup", instance.friendshipId)
                return
            }
            
            // Find subtasks related to this routine instance and phase
            val routineRelatedSubtasks = goalContext.subtasks.filter { subtask ->
                val routineInstanceId = subtask.parameters["routineInstanceId"] as? RoutineInstanceId
                val stepPhaseId = getPhaseIdForStep(instance, subtask.parameters["routineStepId"] as? RoutineStepId)
                
                routineInstanceId == instance.instanceId && stepPhaseId == phaseId
            }
            
            if (routineRelatedSubtasks.isNotEmpty()) {
                LOG.info("Cleaning up {} ongoing conversation subtasks for phase {} in routine {}", 
                    routineRelatedSubtasks.size, phaseId, instance.instanceId)
                
                // Remove routine-related subtasks and clarification questions
                val updatedSubtasks = goalContext.subtasks - routineRelatedSubtasks.toSet()
                val updatedClarificationQuestions = goalContext.subtaskClarificationQuestions.filterNot { question ->
                    routineRelatedSubtasks.any { it.id == question.relatedSubtask }
                }
                
                val updatedContext = goalContext.copy(
                    subtasks = updatedSubtasks,
                    subtaskClarificationQuestions = updatedClarificationQuestions
                )
                
                // If no subtasks remain, clear the entire goal context
                val finalContext = if (updatedSubtasks.isEmpty()) {
                    GoalContext.none()
                } else {
                    updatedContext
                }
                
                goalContextRepository.saveContext(instance.friendshipId, finalContext)
                
                LOG.info("Successfully cleaned up conversations for phase {} - removed {} subtasks, {} clarification questions", 
                    phaseId, routineRelatedSubtasks.size, 
                    goalContext.subtaskClarificationQuestions.size - updatedClarificationQuestions.size)
            }
            
        } catch (e: Exception) {
            LOG.error("Error cleaning up conversations for phase {} in routine {}: {}", phaseId, instance.instanceId, e.message, e)
        }
    }
    
    /**
     * Cleans up uncompleted tasks that were created by steps in this phase
     */
    private fun cleanupUncompletedTasks(instance: RoutineInstance, phaseId: RoutinePhaseId) {
        try {
            // Find TaskRoutineConcepts for this phase
            val phaseTaskConcepts = instance.concepts.filterIsInstance<TaskRoutineConcept>().filter { concept ->
                getPhaseIdForStep(instance, concept.linkedStep) == phaseId
            }
            
            if (phaseTaskConcepts.isEmpty()) {
                LOG.debug("No task concepts found for phase {}, skipping task cleanup", phaseId)
                return
            }
            
            LOG.info("Found {} task concepts for phase {} cleanup", phaseTaskConcepts.size, phaseId)
            
            // Get all tasks for this friendship to check completion status
            val allTasks = taskRepository.findByFriendshipId(instance.friendshipId)
            val taskMap = allTasks.associateBy { it.id }
            
            phaseTaskConcepts.forEach { concept ->
                try {
                    val task = taskMap[concept.linkedTaskId]
                    if (task != null && !task.completed) {
                        // Delete uncompleted task
                        taskRepository.removeTask(instance.friendshipId, concept.linkedTaskId)
                        LOG.debug("Deleted uncompleted task {} (linked to step {}) for phase {}", 
                            concept.linkedTaskId, concept.linkedStep, phaseId)
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to cleanup task {} for phase {}: {}", concept.linkedTaskId, phaseId, e.message)
                }
            }
            
            // Remove the task concepts from the routine instance
            val updatedInstance = instance.copy(
                concepts = instance.concepts - phaseTaskConcepts.toSet()
            )
            instanceRepository.save(updatedInstance)
            
            LOG.info("Successfully cleaned up {} task concepts for phase {}", phaseTaskConcepts.size, phaseId)
            
        } catch (e: Exception) {
            LOG.error("Error cleaning up tasks for phase {} in routine {}: {}", phaseId, instance.instanceId, e.message, e)
        }
    }
    
    /**
     * Gets the phase ID that contains a specific step
     */
    private fun getPhaseIdForStep(instance: RoutineInstance, stepId: RoutineStepId?): RoutinePhaseId? {
        if (stepId == null) return null
        
        val template = templateRepository.findById(instance.templateId) ?: return null
        return template.phases.firstOrNull { phase ->
            phase.steps.any { it.id == stepId }
        }?.id
    }
    
    companion object {
        private val LOG = LoggerFactory.getLogger(RoutinePhaseDeactivator::class.java)
    }
} 