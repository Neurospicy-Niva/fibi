package icu.neurospicy.fibi.outgoing.quartz

import icu.neurospicy.fibi.application.routine.RoutineStepSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutineTriggerSchedulerJob
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.routines.*
import icu.neurospicy.fibi.domain.service.friends.routines.builders.aMessageStep
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineStepScheduled
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutineTriggerScheduled
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

class QuartzRoutineSchedulerTest {

    private val quartzSchedulerService = mockk<QuartzSchedulerService>(relaxed = true)
    private val friendshipLedger = mockk<FriendshipLedger>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val routineEventLog = mockk<RoutineEventLog>(relaxed = true)
    private val timeExpressionEvaluator = mockk<TimeExpressionEvaluator>(relaxed = true)
    private val scheduler =
        QuartzRoutineScheduler(friendshipLedger, quartzSchedulerService, eventPublisher, routineEventLog, timeExpressionEvaluator)

    @Nested
    inner class ScheduleTriggerTests {
        @Test
        fun `schedules AfterDays trigger using user's timezone`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterDays(value = 2), effect = SendMessage("Plan ahead!")
            )

            scheduler.scheduleTrigger(instance, trigger)

            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            trigger.id.toString()
                        )
                    },
                    "routineJobs",
                    withArg {
                        val expectedTime = ZonedDateTime.now(zoneId).plusDays(2).withSecond(0).withNano(0)
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutineTriggerSchedulerJob::class.java,
                    match { it["routineInstanceId"] == instance.instanceId.toString() },
                    any()
                )
                eventPublisher.publishEvent(match { it is RoutineTriggerScheduled && it.routineInstanceId == instance.instanceId })
                routineEventLog.log(match {
                    it.event == RoutineEventType.TRIGGER_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }

        @Test
        fun `schedules AfterDuration trigger with reference using user's timezone`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val now = Instant.now()
            val referenceKey = "someTime"
            val duration = Duration.ofMinutes(45)

            val instance = instanceWith(friendshipId).copy(parameters = mutableMapOf(referenceKey to TypedParameter.fromValue(now)))

            val trigger = RoutineTrigger(
                condition = AfterDuration(reference = referenceKey, duration = duration),
                effect = SendMessage("Reminder: Continue routine")
            )

            scheduler.scheduleTrigger(instance, trigger)

            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            trigger.id.toString()
                        )
                    },
                    "routineJobs",
                    withArg {
                        val expectedTime = now.atZone(zoneId).plus(duration).withSecond(0).withNano(0)
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutineTriggerSchedulerJob::class.java,
                    match { it["routineInstanceId"] == instance.instanceId.toString() },
                    any()
                )
                eventPublisher.publishEvent(match { it is RoutineTriggerScheduled && it.routineInstanceId == instance.instanceId })
                routineEventLog.log(match {
                    it.event == RoutineEventType.TRIGGER_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }

        @Test
        fun `schedules AfterDuration trigger without reference using user's timezone`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId)
            val duration = Duration.ofMinutes(30)
            val trigger = RoutineTrigger(
                condition = AfterDuration(duration = duration), effect = SendMessage("Time to move!")
            )

            scheduler.scheduleTrigger(instance, trigger)

            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            trigger.id.toString()
                        )
                    },
                    "routineJobs",
                    withArg {
                        val expectedTime = ZonedDateTime.now(zoneId).plus(duration)
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutineTriggerSchedulerJob::class.java,
                    match { it["routineInstanceId"] == instance.instanceId.toString() },
                    any()
                )
                eventPublisher.publishEvent(match { it is RoutineTriggerScheduled && it.routineInstanceId == instance.instanceId })
                routineEventLog.log(match {
                    it.event == RoutineEventType.TRIGGER_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }
    }

    @Nested
    inner class ScheduleTriggerNegativeCases {
        @Test
        fun `does not schedule AfterDuration trigger if reference parameter is missing`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val referenceKey = "missingTime"
            val duration = Duration.ofHours(1)

            val instance = instanceWith(friendshipId).copy(parameters = emptyMap())

            val trigger = RoutineTrigger(
                condition = AfterDuration(reference = referenceKey, duration = duration),
                effect = SendMessage("This should not be scheduled")
            )

            scheduler.scheduleTrigger(instance, trigger)

            verify { quartzSchedulerService wasNot Called }
        }
    }


    @Nested
    inner class ScheduleStepTimeOfDayTests {
        @Test
        fun `schedules RoutineStep with TimeOfDayReference parameter`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val wakeUpTime = Instant.now().plusSeconds(3600)
            val instance = instanceWith(friendshipId).copy(parameters = mutableMapOf("wakeUpTime" to TypedParameter.fromValue(wakeUpTime)))
            val phaseId = RoutinePhaseId.forTitle("phase1")
            val step = ActionRoutineStep(
                message = "Wake up and stretch", timeOfDay = TimeOfDayReference("wakeUpTime")
            )
            scheduler.scheduleStep(instance, step, phaseId)
            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            phaseId.toString()
                        ) && it.contains(step.id.toString())
                    }, "routineJobs", withArg {
                        val expectedTime = wakeUpTime.atZone(zoneId)
                        assertThat(it.toInstant()).isCloseTo(expectedTime.toInstant(), within(2, ChronoUnit.MINUTES))
                    }, RoutineStepSchedulerJob::class.java, match {
                        it["phaseId"] == phaseId.toString() && it["stepId"] == step.id.toString()
                    }, any()
                )
                eventPublisher.publishEvent(match {
                    it is RoutineStepScheduled && it.routineInstanceId == instance.instanceId && it.stepId == step.id
                })
                routineEventLog.log(match {
                    it.event == RoutineEventType.STEP_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }

        @Test
        fun `does not schedule RoutineStep if TimeOfDayReference parameter is unresolved`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val instance = instanceWith(friendshipId).copy(
                parameters = emptyMap(),
            )
            val phaseId = instance.currentPhaseId!!
            val step = ActionRoutineStep(
                message = "Take vitamins", timeOfDay = TimeOfDayReference("vitaminTime")
            )
            scheduler.scheduleStep(instance, step, phaseId)
            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `schedules RoutineStep with TimeOfDayLocalTime`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/London")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val now = ZonedDateTime.now(zoneId)
            val staticTime = now.plusMinutes(10).toLocalTime()
            val instance = instanceWith(friendshipId)
            val phaseId = instance.currentPhaseId!!
            val step = ActionRoutineStep(
                message = "Go for a walk", timeOfDay = TimeOfDayLocalTime(staticTime)
            )
            scheduler.scheduleStep(instance, step, phaseId)
            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            phaseId.toString()
                        ) && it.contains(step.id.toString())
                    },
                    "routineJobs",
                    withArg {
                        val expectedTime =
                            now.withHour(staticTime.hour).withMinute(staticTime.minute).withSecond(0).withNano(0)
                        val scheduled = it.toInstant()
                        assertThat(expectedTime.toInstant()).isCloseTo(scheduled, within(2, ChronoUnit.MINUTES))
                    },
                    RoutineStepSchedulerJob::class.java,
                    match { it["routineInstanceId"] == instance.instanceId.toString() && it["friendshipId"] == friendshipId.toString() && it["stepId"] == step.id.toString() },
                    any()
                )
            }
            verify {
                eventPublisher.publishEvent(match {
                    it is RoutineStepScheduled && it.routineInstanceId == instance.instanceId && it.stepId == step.id
                })
            }
            verify {
                routineEventLog.log(match {
                    it.event == RoutineEventType.STEP_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }
    }

    @Nested
    inner class RoutineStepParameterSubstitutionTests {
        @Test
        fun `full chain with parameter substitution in description`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val specialActivity = "Yoga"
            val wakeUpTime = Instant.now().plusSeconds(1200)
            val instance = instanceWith(
                friendshipId, mutableMapOf(
                    "wakeUpTime" to TypedParameter.fromValue(wakeUpTime), 
                    "specialMorningActivity" to TypedParameter.fromValue(specialActivity)
                )
            )
            val phaseId = RoutinePhaseId.forTitle("phase1")
            val step = ActionRoutineStep(
                message = "Do your {specialMorningActivity} after waking up",
                timeOfDay = TimeOfDayReference("wakeUpTime")
            )
            scheduler.scheduleStep(instance, step, phaseId)
            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            phaseId.toString()
                        ) && it.contains(step.id.toString())
                    }, "routineJobs", withArg {
                        val expectedTime = wakeUpTime.atZone(zoneId)
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    }, RoutineStepSchedulerJob::class.java, match {
                        it["routineInstanceId"] == instance.instanceId.toString() && it["friendshipId"] == friendshipId.toString() && it["stepId"] == step.id.toString() && it["phaseId"] == phaseId.toString()
                    }, any()
                )
            }
            verify {
                eventPublisher.publishEvent(match {
                    it is RoutineStepScheduled && it.routineInstanceId == instance.instanceId && it.stepId == step.id
                })
            }
            verify {
                routineEventLog.log(match {
                    it.event == RoutineEventType.STEP_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }
    }

    @Nested
    inner class StepNegativeCases {
        @Test
        fun `does not schedule for AfterPhaseCompletions condition`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("UTC")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterPhaseCompletions(
                    phaseId = RoutinePhaseId.forTitle("phase1"), times = 2
                ), effect = SendMessage("Should not schedule")
            )
            scheduler.scheduleTrigger(instance, trigger)
            verify { quartzSchedulerService wasNot Called }
        }
    }

    @Nested
    inner class `PhaseScheduler schedulePhaseActivation` {
        @Test
        fun `schedules Phase after days`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            val phase = RoutinePhase(title = "Breakfast", condition = AfterDays(3), steps = listOf(aMessageStep()))
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any(), any()) }
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            // Act
            scheduler.schedulePhaseActivation(
                instanceWith(friendshipId), phase
            )
            verify {
                quartzSchedulerService.scheduleJob(any(), any(), match {
                    Duration.between(ZonedDateTime.now(zoneId).toLocalDate().plusDays(3).atStartOfDay(), it)
                        .abs() < Duration.ofSeconds(3)
                }, any(), match {
                    it["phaseId"] == phase.id.toString()
                }, any())
            }
        }

        @Test
        fun `schedules Phase after duration`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            val phase = RoutinePhase(
                title = "Breakfast", condition = AfterDuration(duration = Duration.ofHours(5)), steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any(), any()) }
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            // Act
            scheduler.schedulePhaseActivation(
                instanceWith(friendshipId), phase
            )
            verify {
                quartzSchedulerService.scheduleJob(any(), any(), match {
                    Duration.between(ZonedDateTime.now(zoneId).plusHours(5), it).abs() < Duration.ofSeconds(3)
                }, any(), match {
                    it["phaseId"] == phase.id.toString()
                }, any())
            }
        }

        @Test
        fun `schedules Phase after duration with parameter`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            val phase = RoutinePhase(
                title = "Breakfast",
                condition = AfterDuration(reference = "whenToStart", duration = Duration.ofHours(5)),
                steps = listOf(aMessageStep())
            )
            val whenToStart = LocalDate.now().plusWeeks(1).atTime(LocalTime.of(6, 30)).atZone(zoneId).toInstant()
            val parameters = mapOf("whenToStart" to TypedParameter.fromValue(whenToStart))
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any(), any()) }
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            // Act
            scheduler.schedulePhaseActivation(
                instanceWith(friendshipId).copy(parameters = parameters), phase
            )
            verify {
                quartzSchedulerService.scheduleJob(any(), any(), match {
                    Duration.between(whenToStart.atZone(zoneId) + Duration.ofHours(5), it).abs() < Duration.ofSeconds(3)
                }, any(), match {
                    it["phaseId"] == phase.id.toString()
                }, any())
            }
        }

        @Test
        fun `schedules Phase after event + duration`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Breakfast",
                condition = AfterEvent(eventType = RoutineAnchorEvent.ROUTINE_STARTED, duration = Duration.ofHours(5)),
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any(), any()) }
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns ZoneId.of("Asia/Tokyo")
            // Act
            scheduler.schedulePhaseActivation(
                instanceWith(friendshipId), phase
            )
            verify {
                quartzSchedulerService.scheduleJob(any(), any(), match {
                    Duration.between(Instant.now() + Duration.ofHours(5), it.toInstant()).abs() < Duration.ofSeconds(3)
                }, any(), match {
                    it["phaseId"] == phase.id.toString()
                }, any())
            }
        }

        @Test
        fun `does not schedule Phase after duration with missing parameter`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            val phase = RoutinePhase(
                title = "Breakfast",
                condition = AfterDuration(reference = "whenToStart", duration = Duration.ofHours(5)),
                steps = listOf(aMessageStep())
            )
            val parameters = emptyMap<String, TypedParameter>()
            justRun {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase") }, any(), any(), any(), any(), any()
                )
            }
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            // Act
            scheduler.schedulePhaseActivation(
                instanceWith(friendshipId).copy(parameters = parameters), phase
            )
            verify { quartzSchedulerService wasNot Called }
        }
    }

    @Nested
    inner class `PhaseScheduler schedulePhaseIterations` {
        @Test
        fun `schedules phase with cron`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Breakfast", steps = listOf(MessageRoutineStep("Eat your breakfast")), schedule = ScheduleExpression.Custom("0 0 * * *")
            )
            val instance = instanceWith(friendshipId)
            //Act
            scheduler.schedulePhaseIterations(instance, phase)
            verify {
                quartzSchedulerService.scheduleJob(match { it.contains("phase-iteration") }, any(), any(), match {
                    it["phaseId"] == phase.id.toString()
                }, match { it == "0 0 * * *" })
            }
        }

        @Test
        fun `schedules daily phase`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Breakfast", steps = listOf(MessageRoutineStep("Eat your breakfast")), schedule = ScheduleExpression.DAILY
            )
            val instance = instanceWith(friendshipId)
            //Act
            scheduler.schedulePhaseIterations(instance, phase)
            verify {
                quartzSchedulerService.scheduleJob(match { it.contains("phase-iteration") }, any(), any(), match {
                    it["phaseId"] == phase.id.toString()
                }, match { it == "0 0 * * *" })
            }
        }

        @Test
        fun `schedules weekday phase`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Breakfast", steps = listOf(MessageRoutineStep("Eat your breakfast")), schedule = ScheduleExpression.WEDNESDAY
            )
            val instance = instanceWith(friendshipId)
            //Act
            scheduler.schedulePhaseIterations(instance, phase)
            verify {
                quartzSchedulerService.scheduleJob(match { it.contains("phase-iteration") }, any(), any(), match {
                    it["phaseId"] == phase.id.toString()
                }, match { it == "0 0 * * WED" })
            }
        }
    }

    @Nested
    inner class RemoveStepScheduler {
        @Test
        fun `remove scheduler for step deletes job`() {
            val friendshipId = FriendshipId()
            val instanceId =
                RoutineInstanceId.forInstance(RoutineTemplateId.forTitleVersion("Morning", "1.0"), friendshipId)
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            val stepId = RoutineStepId.forDescription("Eat your breakfast")
            justRun { quartzSchedulerService.deleteJob(any(), any()) }
            scheduler.removeScheduleFor(friendshipId, instanceId, phaseId, stepId)
            verify {
                quartzSchedulerService.deleteJob(match {
                    it.contains(friendshipId.toString()) && it.contains(
                        instanceId.toString()
                    ) && it.contains(phaseId.toString()) && it.contains(stepId.toString())
                }, any())
            }
        }
    }

    @Nested
    inner class RemovePhaseActivationSchedulers {
        @Test
        fun `remove phase iteration scheduler deletes correct job`() {
            val friendshipId = FriendshipId()
            val instanceId = RoutineInstanceId.forInstance(RoutineTemplateId.forTitleVersion("Morning", "1.0"), friendshipId)
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            justRun { quartzSchedulerService.deleteJob(any(), any()) }
            
            scheduler.removePhaseIterationSchedule(friendshipId, instanceId, phaseId)
            
            verify {
                quartzSchedulerService.deleteJob(
                    "routine-phase-iteration-${friendshipId}-${instanceId}-${phaseId}",
                    any()
                )
            }
        }

        @Test
        fun `remove phase activation scheduler deletes correct job`() {
            val friendshipId = FriendshipId()
            val instanceId = RoutineInstanceId.forInstance(RoutineTemplateId.forTitleVersion("Morning", "1.0"), friendshipId)
            val phaseId = RoutinePhaseId.forTitle("Breakfast")
            justRun { quartzSchedulerService.deleteJob(any(), any()) }
            
            scheduler.removePhaseActivationSchedule(friendshipId, instanceId, phaseId)
            
            verify {
                quartzSchedulerService.deleteJob(
                    "routine-phase-${friendshipId}-${instanceId}-${phaseId}",
                    any()
                )
            }
        }
    }

    private fun instanceWith(friendshipId: FriendshipId, parameters: Map<String, TypedParameter> = emptyMap()): RoutineInstance {
        return RoutineInstance(
            _id = UUID.randomUUID().toString(),
            templateId = RoutineTemplateId.forTitleVersion("Morning routine", "1.0"),
            friendshipId = friendshipId,
            currentPhaseId = RoutinePhaseId.forTitle(
                "Breakfast"
            ),
            parameters = parameters
        )
    }
}
