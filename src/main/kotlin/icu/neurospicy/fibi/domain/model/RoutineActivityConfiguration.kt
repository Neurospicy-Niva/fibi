package icu.neurospicy.fibi.domain.model

/**
 * Represents an individual activity within a routine.
 */
data class RoutineActivityConfiguration(
    val name: String,
    val order: Int,
)