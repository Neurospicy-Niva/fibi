package icu.neurospicy.fibi.outgoing.quartz

import icu.neurospicy.fibi.application.routine.RoutinePhaseIterationSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutinePhaseSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutineStepSchedulerJob
import icu.neurospicy.fibi.application.routine.RoutineTriggerSchedulerJob
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.repository.FriendshipLedger
import icu.neurospicy.fibi.domain.service.friends.routines.*
import icu.neurospicy.fibi.domain.service.friends.routines.events.RoutinePhaseScheduled
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
        QuartzRoutineScheduler(
            friendshipLedger,
            quartzSchedulerService,
            eventPublisher,
            routineEventLog,
            timeExpressionEvaluator
        )

    @Nested
    inner class ScheduleTriggerTests {

        @Test
        fun `schedules AfterDays trigger correctly using user's timezone`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterDays(value = 2),
                effect = SendMessage("Plan ahead!")
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
        fun `schedules AtTimeExpression trigger with parameter reference correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val now = Instant.now()
            val referenceKey = "someTime"
            val duration = Duration.ofMinutes(45)
            val expectedTime = now.atZone(zoneId).plus(duration).withSecond(0).withNano(0)

            val instance =
                instanceWith(friendshipId).copy(parameters = mutableMapOf(referenceKey to TypedParameter.fromValue(now)))

            val trigger = RoutineTrigger(
                condition = AtTimeExpression(timeExpression = "\${someTime}+PT45M"),
                effect = SendMessage("Reminder: Continue routine")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "\${someTime}+PT45M",
                    any(),
                    zoneId
                )
            } returns expectedTime.toLocalDateTime()

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
        fun `schedules AtTimeExpression trigger with NOW reference correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val now = ZonedDateTime.now(zoneId)
            val duration = Duration.ofMinutes(30)
            val expectedTime = now.plus(duration)
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AtTimeExpression(timeExpression = "\${NOW}+PT30M"),
                effect = SendMessage("Time to move!")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "\${NOW}+PT30M",
                    any(),
                    zoneId
                )
            } returns expectedTime.toLocalDateTime()

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
        fun `schedules AtTimeExpression trigger with static time correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val staticTime = LocalTime.of(9, 30) // 9:30 AM
            val today = LocalDate.now()
            val expectedTime = today.atTime(staticTime).atZone(zoneId)
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AtTimeExpression(timeExpression = "09:30"),
                effect = SendMessage("Morning check-in!")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "09:30",
                    any(),
                    zoneId
                )
            } returns expectedTime.toLocalDateTime()

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
        fun `schedules AfterEvent trigger with time expression correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val now = ZonedDateTime.now(zoneId)
            val delay = Duration.ofMinutes(15)
            val expectedTime = now.plus(delay)
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterEvent(
                    eventType = RoutineAnchorEvent.ROUTINE_STARTED,
                    phaseTitle = "Morning Phase",
                    timeExpression = "PT15M"
                ),
                effect = SendMessage("15 minutes after routine started!")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "PT15M",
                    any(),
                    zoneId
                )
            } returns expectedTime.toLocalDateTime()

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
        fun `schedules AfterEvent trigger with complex time expression correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/London")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val wakeUpTime = Instant.now().plusSeconds(3600) // 1 hour from now
            val delay = Duration.ofHours(2)
            val expectedTime = wakeUpTime.atZone(zoneId).plus(delay)
            val instance = instanceWith(friendshipId).copy(
                parameters = mutableMapOf("wakeUpTime" to TypedParameter.fromValue(wakeUpTime))
            )
            val trigger = RoutineTrigger(
                condition = AfterEvent(
                    eventType = RoutineAnchorEvent.PHASE_ENTERED,
                    phaseTitle = "Breakfast Phase",
                    timeExpression = "\${wakeUpTime}+PT2H"
                ),
                effect = SendMessage("2 hours after wake up!")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "\${wakeUpTime}+PT2H",
                    any(),
                    zoneId
                )
            } returns expectedTime.toLocalDateTime()

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
        fun `schedules AfterDuration trigger with reference correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val referenceTime = Instant.now().plusSeconds(1800) // 30 minutes from now
            val duration = Duration.ofMinutes(45)
            val expectedTime = referenceTime.atZone(zoneId).plus(duration)
            val instance = instanceWith(friendshipId).copy(
                parameters = mutableMapOf("startTime" to TypedParameter.fromValue(referenceTime))
            )
            val trigger = RoutineTrigger(
                condition = AfterDuration(
                    reference = "startTime",
                    duration = duration
                ),
                effect = SendMessage("45 minutes after start!")
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
        fun `schedules AfterDuration trigger without reference correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val duration = Duration.ofHours(2)
            val now = ZonedDateTime.now(zoneId)
            val expectedTime = now.plus(duration)
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterDuration(
                    reference = null,
                    duration = duration
                ),
                effect = SendMessage("2 hours from now!")
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
        fun `does not schedule AfterDuration trigger if reference parameter is missing`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId).copy(parameters = emptyMap())
            val trigger = RoutineTrigger(
                condition = AfterDuration(
                    reference = "missingParameter",
                    duration = Duration.ofMinutes(30)
                ),
                effect = SendMessage("Should not schedule")
            )

            scheduler.scheduleTrigger(instance, trigger)

            verify { quartzSchedulerService wasNot Called }
        }
    }

    @Nested
    inner class ScheduleTriggerNegativeCases {

        @Test
        fun `does not schedule AfterPhaseCompletions condition`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("UTC")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterPhaseCompletions(
                    phaseId = RoutinePhaseId.forTitle("phase1"),
                    times = 2
                ),
                effect = SendMessage("Should not schedule")
            )
            scheduler.scheduleTrigger(instance, trigger)
            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `does not schedule AfterParameterSet condition`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("UTC")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterParameterSet(parameterKey = "ready"),
                effect = SendMessage("Should not schedule")
            )
            scheduler.scheduleTrigger(instance, trigger)
            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `does not schedule AtTimeExpression trigger if time expression evaluation fails`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AtTimeExpression(timeExpression = "invalid-time-expression"),
                effect = SendMessage("Should not schedule")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "invalid-time-expression",
                    any(),
                    zoneId
                )
            } returns null

            scheduler.scheduleTrigger(instance, trigger)

            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `does not schedule AtTimeExpression trigger if parameter reference is missing`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId).copy(parameters = emptyMap())
            val trigger = RoutineTrigger(
                condition = AtTimeExpression(timeExpression = "\${missingParameter}+PT1H"),
                effect = SendMessage("Should not schedule")
            )

            every {
                timeExpressionEvaluator.evaluateAtTimeExpression(
                    "\${missingParameter}+PT1H",
                    any(),
                    zoneId
                )
            } returns null

            scheduler.scheduleTrigger(instance, trigger)

            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `does not schedule AfterEvent trigger if time expression evaluation fails`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId)
            val trigger = RoutineTrigger(
                condition = AfterEvent(
                    eventType = RoutineAnchorEvent.ROUTINE_STARTED,
                    phaseTitle = "Morning Phase",
                    timeExpression = "invalid-duration"
                ),
                effect = SendMessage("Should not schedule")
            )

            every { timeExpressionEvaluator.evaluateAtTimeExpression("invalid-duration", any(), zoneId) } returns null

            scheduler.scheduleTrigger(instance, trigger)

            verify { quartzSchedulerService wasNot Called }
        }
    }


    @Nested
    inner class ScheduleStepTimeOfDayTests {

        @Test
        fun `schedules RoutineStep with TimeOfDayReference parameter correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val wakeUpTime = Instant.now().plusSeconds(3600)
            val instance = instanceWith(friendshipId).copy(
                parameters = mutableMapOf(
                    "wakeUpTime" to TypedParameter.fromValue(wakeUpTime)
                )
            )
            val phaseId = RoutinePhaseId.forTitle("phase1")
            val step = ActionRoutineStep(
                message = "Wake up and stretch",
                timeOfDay = TimeOfDayReference("wakeUpTime")
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
                        val expectedTime = wakeUpTime.atZone(zoneId)
                        assertThat(it.toInstant()).isCloseTo(expectedTime.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutineStepSchedulerJob::class.java,
                    match {
                        it["phaseId"] == phaseId.toString() && it["stepId"] == step.id.toString()
                    },
                    any()
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
        fun `schedules RoutineStep with TimeOfDayLocalTime correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/London")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val now = ZonedDateTime.now(zoneId)
            val staticTime = now.plusMinutes(10).toLocalTime()
            val instance = instanceWith(friendshipId)
            val phaseId = instance.currentPhaseId!!
            val step = ActionRoutineStep(
                message = "Go for a walk",
                timeOfDay = TimeOfDayLocalTime(staticTime)
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

        @Test
        fun `schedules RoutineStep with TimeOfDayExpression correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val wakeUpTime = Instant.now().plusSeconds(1800) // 30 minutes from now
            val delay = Duration.ofHours(1)
            val expectedTime = wakeUpTime.atZone(zoneId).plus(delay)
            val instance = instanceWith(friendshipId).copy(
                parameters = mutableMapOf("wakeUpTime" to TypedParameter.fromValue(wakeUpTime))
            )
            val phaseId = RoutinePhaseId.forTitle("phase1")
            val step = ActionRoutineStep(
                message = "Take medication",
                timeOfDay = TimeOfDayExpression("\${wakeUpTime}+PT1H")
            )

            every {
                timeExpressionEvaluator.evaluateTimeExpression(
                    "\${wakeUpTime}+PT1H",
                    any(),
                    zoneId
                )
            } returns expectedTime.toLocalDateTime()

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
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutineStepSchedulerJob::class.java,
                    match {
                        it["phaseId"] == phaseId.toString() && it["stepId"] == step.id.toString()
                    },
                    any()
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
        fun `does not schedule RoutineStep if TimeOfDayReference parameter is missing`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            val instance = instanceWith(friendshipId).copy(
                parameters = emptyMap(),
            )
            val phaseId = instance.currentPhaseId!!
            val step = ActionRoutineStep(
                message = "Take vitamins",
                timeOfDay = TimeOfDayReference("vitaminTime")
            )
            scheduler.scheduleStep(instance, step, phaseId)
            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `does not schedule RoutineStep if TimeOfDayExpression evaluation fails`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId).copy(
                parameters = mutableMapOf("wakeUpTime" to TypedParameter.fromValue(Instant.now()))
            )
            val phaseId = RoutinePhaseId.forTitle("phase1")
            val step = ActionRoutineStep(
                message = "Take medication",
                timeOfDay = TimeOfDayExpression("invalid-expression")
            )

            every { timeExpressionEvaluator.evaluateTimeExpression("invalid-expression", any(), zoneId) } returns null

            scheduler.scheduleStep(instance, step, phaseId)
            verify { quartzSchedulerService wasNot Called }
        }

        @Test
        fun `schedules RoutineStep without timeOfDay correctly`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("UTC")
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            val instance = instanceWith(friendshipId)
            val phaseId = instance.currentPhaseId!!
            val step = ActionRoutineStep(
                message = "Immediate action",
                timeOfDay = null
            )
            scheduler.scheduleStep(instance, step, phaseId)
            verify { quartzSchedulerService wasNot Called }
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
    inner class `PhaseScheduler schedulePhaseActivation` {
        @Test
        fun `schedules Phase after days`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Asia/Tokyo")
            val phase = RoutinePhase(title = "Breakfast", condition = AfterDays(3), steps = listOf(aMessageStep()))
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId
            // Act
            scheduler.schedulePhaseActivation(
                instanceWith(friendshipId), phase
            )
            verify {
                quartzSchedulerService.scheduleJob(any(), any(), match {
                    it.hour == 0 && it.minute == 0
                }, any(), any(), any())
            }
        }

        @Test
        fun `schedules Phase after duration with reference`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            val referenceTime = Instant.now().plusSeconds(3600) // 1 hour from now
            val duration = Duration.ofMinutes(30)
            val expectedTime = referenceTime.atZone(zoneId).plus(duration)
            val phase = RoutinePhase(
                title = "Workout",
                condition = AfterDuration(reference = "startTime", duration = duration),
                steps = listOf(aMessageStep())
            )
            val instance = instanceWith(friendshipId).copy(
                parameters = mutableMapOf("startTime" to TypedParameter.fromValue(referenceTime))
            )
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            scheduler.schedulePhaseActivation(instance, phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            phase.id.toString()
                        )
                    },
                    "routineJobs",
                    withArg {
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutinePhaseSchedulerJob::class.java,
                    match { it["routineInstanceId"] == instance.instanceId.toString() },
                    any()
                )
                eventPublisher.publishEvent(match { it is RoutinePhaseScheduled && it.routineInstanceId == instance.instanceId })
                routineEventLog.log(match {
                    it.event == RoutineEventType.PHASE_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }

        @Test
        fun `schedules Phase after duration without reference`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("America/New_York")
            val duration = Duration.ofHours(1)
            val now = ZonedDateTime.now(zoneId)
            val expectedTime = now.plus(duration)
            val phase = RoutinePhase(
                title = "Lunch",
                condition = AfterDuration(reference = null, duration = duration),
                steps = listOf(aMessageStep())
            )
            val instance = instanceWith(friendshipId)
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            scheduler.schedulePhaseActivation(instance, phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match {
                        it.contains(friendshipId.toString()) && it.contains(instance.instanceId.toString()) && it.contains(
                            phase.id.toString()
                        )
                    },
                    "routineJobs",
                    withArg {
                        assertThat(expectedTime.toInstant()).isCloseTo(it.toInstant(), within(2, ChronoUnit.MINUTES))
                    },
                    RoutinePhaseSchedulerJob::class.java,
                    match { it["routineInstanceId"] == instance.instanceId.toString() },
                    any()
                )
                eventPublisher.publishEvent(match { it is RoutinePhaseScheduled && it.routineInstanceId == instance.instanceId })
                routineEventLog.log(match {
                    it.event == RoutineEventType.PHASE_SCHEDULED && it.routineInstanceId == instance.instanceId
                })
            }
        }

        @Test
        fun `does not schedule Phase after duration if reference parameter is missing`() {
            val friendshipId = FriendshipId()
            val zoneId = ZoneId.of("Europe/Berlin")
            val phase = RoutinePhase(
                title = "Should not schedule",
                condition = AfterDuration(reference = "missingParameter", duration = Duration.ofMinutes(30)),
                steps = listOf(aMessageStep())
            )
            val instance = instanceWith(friendshipId).copy(parameters = emptyMap())
            every { friendshipLedger.findTimezoneBy(friendshipId) } returns zoneId

            scheduler.schedulePhaseActivation(instance, phase)

            verify { quartzSchedulerService wasNot Called }
        }
    }

    @Nested
    inner class `PhaseScheduler schedulePhaseIterations` {
        @Test
        fun `schedules Phase iterations with DAILY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Daily Exercise",
                schedule = ScheduleExpression.DAILY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }
            // Act
            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)
            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * *"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with WEEKLY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Weekly Review",
                schedule = ScheduleExpression.WEEKLY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * MON"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with WEEKDAYS schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Work Routine",
                schedule = ScheduleExpression.WEEKDAYS,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * MON-FRI"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with WEEKENDS schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Weekend Relaxation",
                schedule = ScheduleExpression.WEEKENDS,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * SAT,SUN"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with MONDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Monday Motivation",
                schedule = ScheduleExpression.MONDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * MON"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with TUESDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Tuesday Tasks",
                schedule = ScheduleExpression.TUESDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * TUE"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with WEDNESDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Wednesday Wellness",
                schedule = ScheduleExpression.WEDNESDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * WED"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with THURSDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Thursday Thoughts",
                schedule = ScheduleExpression.THURSDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * THU"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with FRIDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Friday Finish",
                schedule = ScheduleExpression.FRIDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * FRI"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with SATURDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Saturday Self-Care",
                schedule = ScheduleExpression.SATURDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * SAT"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with SUNDAY schedule`() {
            val friendshipId = FriendshipId()
            val phase = RoutinePhase(
                title = "Sunday Reflection",
                schedule = ScheduleExpression.SUNDAY,
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    "0 0 0 ? * SUN"
                )
            }
        }

        @Test
        fun `schedules Phase iterations with custom cron expression`() {
            val friendshipId = FriendshipId()
            val customCron = "0 30 7 * * MON-FRI" // 7:30 AM on weekdays
            val phase = RoutinePhase(
                title = "Custom Schedule",
                schedule = ScheduleExpression.Custom(customCron),
                steps = listOf(aMessageStep())
            )
            justRun { quartzSchedulerService.scheduleJob(any(), any(), any(), any(), any()) }

            scheduler.schedulePhaseIterations(instanceWith(friendshipId), phase)

            verify {
                quartzSchedulerService.scheduleJob(
                    match { it.contains("phase-iteration") },
                    "routineJobs",
                    RoutinePhaseIterationSchedulerJob::class.java,
                    match { it["phaseId"] == phase.id.toString() },
                    customCron
                )
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
            val instanceId =
                RoutineInstanceId.forInstance(RoutineTemplateId.forTitleVersion("Morning", "1.0"), friendshipId)
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
            val instanceId =
                RoutineInstanceId.forInstance(RoutineTemplateId.forTitleVersion("Morning", "1.0"), friendshipId)
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

    private fun instanceWith(
        friendshipId: FriendshipId,
        parameters: Map<String, TypedParameter> = emptyMap(),
    ): RoutineInstance {
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

    private fun aMessageStep(): RoutineStep = MessageRoutineStep("Test message")
}