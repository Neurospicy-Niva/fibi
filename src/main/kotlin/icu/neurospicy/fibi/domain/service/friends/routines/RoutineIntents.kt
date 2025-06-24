package icu.neurospicy.fibi.domain.service.friends.routines

import icu.neurospicy.fibi.domain.service.friends.interaction.Intent
import icu.neurospicy.fibi.domain.service.friends.interaction.IntentContributor
import org.springframework.stereotype.Component

object RoutineIntents {
    val Start = Intent("StartRoutine")
    val Select = Intent("SelectRoutine")
    val Setup = Intent("SetupRoutine")
    val AnswerQuestion = Intent("AnswerQuestion")
    val StopRoutineToday = Intent("StopRoutineToday")
}

@Component
class StartRoutineIntentContributor : IntentContributor {
    override fun intent(): Intent = RoutineIntents.Start
    override fun description(): String =
        "Select and setup a new routine, e.g., morning routine, diary routine, workout routine"
}