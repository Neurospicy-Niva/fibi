package icu.neurospicy.fibi.domain.service.friends.routines

interface RoutineEventLog {
    fun log(entry: RoutineEventLogEntry)
}