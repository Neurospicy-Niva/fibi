package icu.neurospicy.fibi.domain.service.friends.interaction.tasks

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object TaskIntents {
    val Add = Intent("AddTask")
    val List = Intent("ListTasks")
    val Complete = Intent("CompleteTask")
    val Update = Intent("UpdateTask")
    val Remove = Intent("RemoveTask")
    val Cleanup = Intent("CleanupTasks")
}

@Component
class AddTaskIntentContributor : IntentContributor {
    override fun intent(): Intent = TaskIntents.Add
    override fun description(): String = "Add a task"
}

@Component
class ListTasksIntentContributor : IntentContributor {
    override fun intent(): Intent = TaskIntents.List
    override fun description(): String = "List existing tasks"
}

@Component
class CompleteTaskIntentContributor : IntentContributor {
    override fun intent(): Intent = TaskIntents.Complete
    override fun description(): String = "Mark tasks as done"
}

@Component
class UpdateTaskIntentContributor : IntentContributor {
    override fun intent(): Intent = TaskIntents.Update
    override fun description(): String = "Update task title or description"
}

@Component
class RemoveTaskIntentContributor : IntentContributor {
    override fun intent(): Intent = TaskIntents.Remove
    override fun description(): String = "Delete an existing task"
}

@Component
class CleanupTasksIntentContributor : IntentContributor {
    override fun intent(): Intent = TaskIntents.Cleanup
    override fun description(): String = "Remove all completed tasks"
}
