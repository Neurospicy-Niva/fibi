package icu.neurospicy.fibi.step;

import icu.neurospicy.fibi.service.FibiSignalClient;
import icu.neurospicy.fibi.service.UserRepository;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.time.LocalTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Map.entry;
import static org.awaitility.Awaitility.await;

public class SignalMessengerStepDefinitions {
    private static final Map<String, String> PREDEFINED_TEXTS = Map.ofEntries(entry("initial welcome message", """
                    Hello! I’m Fibi, your personal daily assistant.
                    Great to have you here! I can help you manage tasks, appointments,
                    and routines – especially if ADHD or autism make everyday life challenging.
                    
                    Before we begin, I need your consent to store and process your data
                    in line with our privacy policy. You can learn more here: https://neurospicy.icu/tos.
                    
                    Are you ready to get started with me?"""),
            entry("confirmation message", """
                    Fantastic! I'm excited to help you.
                    Would you prefer to start by connecting your calendar,
                    setting up a short morning routine, or add something to your to-do list?"""),
            entry("confirmation message based on likely consent", """
                    I assume that is consent. Fantastic! I’m excited to help you.
                    Would you prefer to start by connecting your calendar,
                    setting up a short morning routine, or add something to your to-do list?"""),
            entry("denial confirmation message", """
                    No problem at all. I understand.
                    I won't store any of your personal data, except that you declined at this moment.
                    If you change your mind later, just send me a message, and we can start fresh."""),
            entry("calendar activity cancelled message", """
                    No worries! I won’t store any calendar info.
                    If you change your mind later, just let me know and we can set it up."""));
    private static final Map<String, Pattern> PREDEFINED_PATTERNS = Map.ofEntries(
            entry("calendar successfully added message containing appointments",
                    Pattern.compile("Great news—your calendar.? ...? now connected!\\nToday, you have:\\n((- )?\\d\\d:\\d\\d(-\\d\\d:\\d\\d)?:? .+\\n)+Let me know if you'd like reminders or anything else\\.")),
            entry("calendar successfully added message without schedule",
                    Pattern.compile("Great news—your calendar.? ...? now connected!\\nToday, you don't have any appointments.\\nLet me know if you'd like reminders or anything else\\.")),
            entry("request to post a CalDAV URL", Pattern.compile(".*caldav.*", Pattern.DOTALL + Pattern.CASE_INSENSITIVE)),
            entry("calendar activity cancelled message", Pattern.compile(".*(stop|abort|cancel)+.*", Pattern.DOTALL + Pattern.CASE_INSENSITIVE))
    );

    @ParameterType("initial welcome message" +
            "|welcome back message" +
            "|confirmation message" +
            "|denial confirmation message" +
            "|confirmation message based on likely consent"
    )
    public String predefinedText(String text) {
        return PREDEFINED_TEXTS.get(text);
    }

    @ParameterType("calendar successfully added message containing appointments" +
            "|calendar successfully added message without schedule" +
            "|request to post a CalDAV URL" +
            "|calendar activity cancelled message"
    )
    public Pattern predefinedPattern(String text) {
        return PREDEFINED_PATTERNS.get(text);
    }

    @Autowired
    private FibiSignalClient fibiSignalClient;
    @Autowired
    private UserRepository userRepository;

    @Given("a user who is new")
    public void aUserNamedWhoIsNew() {
        userRepository.newUser();
    }

    @Given("a Friend")
    public void aUserNamedWhoAlreadyUsedFibi() {
        UserRepository.User user = userRepository.newUser();
        fibiSignalClient.sendMessageToFibi(user, "Hello");
        await().until(() -> fibiSignalClient.verifyFibiSentTo(user.username));
        fibiSignalClient.sendMessageToFibi(user, "Yes");
        verifyUserReceivedMessageByFibi(PREDEFINED_TEXTS.get("confirmation message"), user);
        fibiSignalClient.clearMessagesFor(user.number);
    }

    @When("they send {string} to Fibi")
    public void theySendMessageToFibi(String message) {
        fibiSignalClient.sendMessageToFibi(userRepository.getCurrentUser(), message);
    }

    @When("they send a wake-up time {int} seconds ahead")
    public void theySendWaUpTime(int secondsAheadOfNow) {
        UserRepository.User user = userRepository.getCurrentUser();
        LocalTime localTimeSecondsAhead = now().plusSeconds(secondsAheadOfNow);
        userRepository.saveWakeUpTime(user.username, localTimeSecondsAhead);
        String message = "I usually wake-up at around " +
                localTimeSecondsAhead.format(ofPattern("H:mm:ss"));
        fibiSignalClient.sendMessageToFibi(user, message);
    }

    @When("they send their current time")
    public void theySendCurrentTime() {
        UserRepository.User user = userRepository.getCurrentUser();
        String message = "My current time is " + now().format(ofPattern("H:mm")) + ". Please fix my timezone.";
        fibiSignalClient.sendMessageToFibi(user, message);
    }

    @When("the scheduled wake-up time is reached")
    public void waitUntilWakeUpTime() {
        UserRepository.User user = userRepository.getCurrentUser();
        await().timeout(Duration.ofSeconds(150)).pollDelay(Duration.ofSeconds(Optional.ofNullable(user.wakeUpTime)
                        .map(wakeUpTime -> now().until(wakeUpTime, SECONDS) - 30)
                        .orElse(30L)))
                .until(() -> true);
        fibiSignalClient.markMessagesRead(user.username);
    }

    @Then("they eventually receive {string}")
    public void userEventuallyReceives(String expected) {
        verifyUserReceivedMessageByFibi(expected, userRepository.getCurrentUser());
    }

    @Then("they eventually receive the {predefinedText}")
    public void userEventuallyReceivesPredefinedText(String expected) {
        verifyUserReceivedMessageByFibi(expected, userRepository.getCurrentUser());
    }

    @Then("they eventually receive a {predefinedPattern}")
    public void userEventuallyReceivesPredefinedPattern(Pattern expected) {
        verifyUserReceivedMessageByFibi(expected, userRepository.getCurrentUser());
    }

    private void verifyUserReceivedMessageByFibi(String expected, UserRepository.User user) {
        await()
                .alias(String.format("Verify Fibi sent '%s' to '%s'", expected, user.username))
                .atMost(Duration.ofSeconds(120))
                .until(() -> fibiSignalClient.verifyFibiSentTextTo(expected, user));
    }

    private void verifyUserReceivedMessageByFibi(Pattern pattern, UserRepository.User user) {
        await()
                .alias(String.format("Verify Fibi sent message matching '%s' to '%s'", pattern, user.username))
                .atMost(Duration.ofSeconds(120))
                .until(() -> fibiSignalClient.verifyFibiSentTextLikeTo(pattern, user));
    }

    @Then("they do not receive {string} within {int} seconds")
    public void userDoesNotReceiveWithin(String unexpected, int seconds) {
        verifyUserDoesNotReceiveWithin(unexpected, seconds, userRepository.getCurrentUser());
    }


    @Then("they do not receive the {predefinedText} within {int} seconds")
    public void userDoesNotReceivePredefinedWithin(String unexpected, int seconds) {
        verifyUserDoesNotReceiveWithin(unexpected, seconds, userRepository.getCurrentUser());
    }

    @Then("they eventually receive a message containing {string}")
    public void theyEventuallyReceiveAMessageContaining(String snippet) {
        UserRepository.User user = userRepository.getCurrentUser();
        await()
                .alias(String.format("Verify Fibi sent '%s' to '%s'", snippet, user.username))
                .atMost(Duration.ofSeconds(120))
                .until(() -> fibiSignalClient.verifyFibiSentTextContainingTo(snippet, user));
    }

    @Then("they eventually receive a message not containing {string}")
    public void theyEventuallyReceiveAMessageNotContaining(String snippet) {
        UserRepository.User user = userRepository.getCurrentUser();
        await()
                .alias(String.format("Verify Fibi did not sent '%s' to '%s'", snippet, user.username))
                .timeout(Duration.ofSeconds(120))
                .until(() -> !fibiSignalClient.lastMessagesByFibiTo(user.username).isEmpty() &&
                        fibiSignalClient.lastMessagesByFibiTo(user.username).stream()
                                .noneMatch(m -> m.text.toLowerCase().contains(snippet.toLowerCase())));
    }

    private void verifyUserDoesNotReceiveWithin(String unexpected, int seconds, UserRepository.User user) {
        await()
                .alias(String.format("Verify Fibi did NOT send '%s' to '%s'", unexpected, user.username))
                .pollDelay(Duration.ofSeconds(seconds))
                .timeout(Duration.ofSeconds(seconds + 5L))  // + Buffer
                .until(() -> !fibiSignalClient.verifyFibiSentTextTo(unexpected, user));
    }
}