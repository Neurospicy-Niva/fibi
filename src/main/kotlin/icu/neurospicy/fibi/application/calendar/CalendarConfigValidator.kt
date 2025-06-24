package icu.neurospicy.fibi.application.calendar

import icu.neurospicy.fibi.outgoing.http.UnvalidatedCalendarConfiguration
import icu.neurospicy.fibi.outgoing.http.ValidationResult

fun interface CalendarConfigValidator {
    fun isValid(config: UnvalidatedCalendarConfiguration): ValidationResult
}