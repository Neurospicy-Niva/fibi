package icu.neurospicy.fibi.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.springframework.util.FileCopyUtils
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@Component
class PromptsConfiguration {
    @Value("classpath:prompts/processing/day-planning-prompt.txt")
    private lateinit var dayPlanningPromptResource: Resource

    @Value("classpath:prompts/processing/reminder-prompt.txt")
    private lateinit var reminderPromptResource: Resource

    @Value("classpath:prompts/processing/task-management-prompt.txt")
    private lateinit var taskManagementPromptResource: Resource

    @Value("classpath:prompts/processing/timezone-prompt.txt")
    private lateinit var timezonePromptResource: Resource

    @Value("classpath:prompts/processing/calendar-planning-prompt.txt")
    private lateinit var calendarPlanningPromptResource: Resource

    @Value("classpath:prompts/response/task-management-response-prompt.txt")
    private lateinit var taskManagementResponsePromptResource: Resource

    @Value("classpath:prompts/response/reminder-management-response-prompt.txt")
    private lateinit var reminderManagementResponsePromptResource: Resource

    @Value("classpath:prompts/response/day-planning-response-prompt.txt")
    private lateinit var dayPlanningResponsePromptResource: Resource

    @Value("classpath:prompts/response/calendar-planning-response-prompt.txt")
    private lateinit var calendarPlanningResponsePromptResource: Resource

    @Value("classpath:prompts/response/timezone-response-prompt.txt")
    private lateinit var setTimezoneResponsePromptResource: Resource

    @Value("classpath:prompts/response/other-response-prompt.txt")
    private lateinit var otherResponsePromptResource: Resource

    @Value("classpath:prompts/system/fibi-system-prompt.txt")
    private lateinit var fibiSystemPromptResource: Resource

    @Value("classpath:prompts/system/generated-message-prompt.txt")
    private lateinit var generatedMessagePromptResource: Resource

    @Value("classpath:prompts/system/adapted-text-message-prompt.txt")
    private lateinit var adaptedTextMessagePromptResource: Resource

    @Value("classpath:prompts/analysis/intent-recognition-system-prompt.txt")
    private lateinit var intentRecognitionSystemPromptResource: Resource

    @Value("classpath:prompts/analysis/intent-recognition-prompt.txt")
    private lateinit var intentRecognitionPromptResource: Resource

    @Value("classpath:prompts/extraction/information-extraction-system-prompt.txt")
    private lateinit var informationExtractionSystemPromptResource: Resource

    @Value("classpath:prompts/extraction/information-extraction-prompt.txt")
    private lateinit var informationExtractionPromptResource: Resource

    @Value("classpath:prompts/extraction/calendar-credentials-extraction-prompt.txt")
    private lateinit var calendarCredentialsExtractionPromptResource: Resource

    @Value("classpath:prompts/response/calendar-url-support-prompt.txt")
    private lateinit var calendarUrlSupportPromptResource: Resource

    @Value("classpath:prompts/response/calendar-url-support-with-invalid-urls-prompt.txt")
    private lateinit var calendarUrlSupportWithInvalidUrlsPromptResource: Resource

    @Value("classpath:prompts/response/calendar-success-prompt.txt")
    private lateinit var calendarSuccessPromptResource: Resource

    @Value("classpath:prompts/response/morning-routine-help-prompt.txt")
    private lateinit var morningRoutineHelpPromptResource: Resource

    @Value("classpath:prompts/response/appointment-reminder-custom-prompt.txt")
    private lateinit var appointmentReminderCustomPromptResource: Resource

    @Value("classpath:prompts/response/appointment-reminder-unknown-prompt.txt")
    private lateinit var appointmentReminderUnknownPromptResource: Resource

    @Value("classpath:prompts/response/timer-custom-prompt.txt")
    private lateinit var timerCustomPromptResource: Resource

    @Value("classpath:prompts/response/timer-unknown-prompt.txt")
    private lateinit var timerUnknownPromptResource: Resource

    @Value("classpath:prompts/response/acquaintance-feature-question.txt")
    private lateinit var acquaintanceFeatureQuestionResource: Resource

    @Value("classpath:prompts/response/acquaintance-fibi-background.txt")
    private lateinit var acquaintanceFibiBackgroundResource: Resource

    @Value("classpath:prompts/response/acquaintance-tos-confirmation.txt")
    private lateinit var acquaintanceTosConfirmationResource: Resource

    @Value("classpath:prompts/response/acquaintance-tos-question.txt")
    private lateinit var acquaintanceTosQuestionResource: Resource

    @Value("classpath:messages/acquaintance-welcome.txt")
    private lateinit var acquaintanceWelcomeMessageResource: Resource

    @Value("classpath:messages/acquaintance-welcome-likely.txt")
    private lateinit var acquaintanceWelcomeLikelyMessageResource: Resource

    @Value("classpath:messages/acquaintance-data-deleted.txt")
    private lateinit var acquaintanceDataDeletedMessageResource: Resource

    val dayPlanningPromptTemplate: String by lazy {
        InputStreamReader(dayPlanningPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val reminderPromptTemplate: String by lazy {
        InputStreamReader(reminderPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val taskManagementPromptTemplate: String by lazy {
        InputStreamReader(taskManagementPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val timezonePromptTemplate: String by lazy {
        InputStreamReader(timezonePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val calendarPlanningPromptTemplate: String by lazy {
        InputStreamReader(calendarPlanningPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val taskManagementResponsePromptTemplate: String by lazy {
        InputStreamReader(taskManagementResponsePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val reminderManagementResponsePromptTemplate: String by lazy {
        InputStreamReader(reminderManagementResponsePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val dayPlanningResponsePromptTemplate: String by lazy {
        InputStreamReader(dayPlanningResponsePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val calendarPlanningResponsePromptTemplate: String by lazy {
        InputStreamReader(calendarPlanningResponsePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val setTimezoneResponsePromptTemplate: String by lazy {
        InputStreamReader(setTimezoneResponsePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val otherResponsePromptTemplate: String by lazy {
        InputStreamReader(otherResponsePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val fibiSystemPromptTemplate: String by lazy {
        InputStreamReader(fibiSystemPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val generatedMessagePromptTemplate: String by lazy {
        InputStreamReader(generatedMessagePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val adaptedTextMessagePromptTemplate: String by lazy {
        InputStreamReader(adaptedTextMessagePromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val intentRecognitionSystemPromptTemplate: String by lazy {
        InputStreamReader(intentRecognitionSystemPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val intentRecognitionPromptTemplate: String by lazy {
        InputStreamReader(intentRecognitionPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val informationExtractionSystemPromptTemplate: String by lazy {
        InputStreamReader(informationExtractionSystemPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val informationExtractionPromptTemplate: String by lazy {
        InputStreamReader(informationExtractionPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val calendarCredentialsExtractionPromptTemplate: String by lazy {
        InputStreamReader(
            calendarCredentialsExtractionPromptResource.inputStream, StandardCharsets.UTF_8
        ).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val calendarUrlSupportPromptTemplate: String by lazy {
        InputStreamReader(calendarUrlSupportPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val calendarUrlSupportWithInvalidUrlsPromptTemplate: String by lazy {
        InputStreamReader(
            calendarUrlSupportWithInvalidUrlsPromptResource.inputStream, StandardCharsets.UTF_8
        ).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val calendarSuccessPromptTemplate: String by lazy {
        InputStreamReader(calendarSuccessPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val morningRoutineHelpPromptTemplate: String by lazy {
        InputStreamReader(morningRoutineHelpPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val appointmentReminderCustomPromptTemplate: String by lazy {
        InputStreamReader(appointmentReminderCustomPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val appointmentReminderUnknownPromptTemplate: String by lazy {
        InputStreamReader(appointmentReminderUnknownPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val timerCustomPromptTemplate: String by lazy {
        InputStreamReader(timerCustomPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val timerUnknownPromptTemplate: String by lazy {
        InputStreamReader(timerUnknownPromptResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val acquaintanceFeatureQuestionTemplate: String by lazy {
        InputStreamReader(acquaintanceFeatureQuestionResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
    val acquaintanceFibiBackgroundTemplate: String by lazy {
        InputStreamReader(acquaintanceFibiBackgroundResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val acquaintanceTosConfirmationTemplate: String by lazy {
        InputStreamReader(acquaintanceTosConfirmationResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val acquaintanceTosQuestionTemplate: String by lazy {
        InputStreamReader(acquaintanceTosQuestionResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val acquaintanceWelcomeMessage: String by lazy {
        InputStreamReader(acquaintanceWelcomeMessageResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val acquaintanceWelcomeLikelyMessage: String by lazy {
        InputStreamReader(acquaintanceWelcomeLikelyMessageResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }

    val acquaintanceDataDeletedMessage: String by lazy {
        InputStreamReader(acquaintanceDataDeletedMessageResource.inputStream, StandardCharsets.UTF_8).use { reader ->
            FileCopyUtils.copyToString(reader)
        }
    }
}