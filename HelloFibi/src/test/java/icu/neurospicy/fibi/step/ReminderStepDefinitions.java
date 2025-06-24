package icu.neurospicy.fibi.step;


import icu.neurospicy.fibi.service.FibiSignalClient;
import icu.neurospicy.fibi.service.ReminderRepository;
import icu.neurospicy.fibi.service.UserRepository;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.ResolverStyle;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Instant.now;
import static java.time.format.FormatStyle.FULL;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.format.ResolverStyle.SMART;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class ReminderStepDefinitions {
    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FibiSignalClient fibiSignalClient;

    @Then("they eventually receive a set reminder confirmation")
    public void theyEventuallyReceiveASetReminderConfirmation() {
        String username = userRepository.getCurrentUser().username;
        Pattern pattern = Pattern.compile("^Set reminder: (?<formattedDateTime>.+)$");
        await().timeout(Duration.ofSeconds(120)).until(() ->
                fibiSignalClient.lastMessagesByFibiTo(username).stream().filter(m -> m.text.toLowerCase().contains("set reminder"))
                        .anyMatch(message -> {
                            Matcher matcher = pattern.matcher(message.text);
                            if (matcher.matches()) {
                                TemporalAccessor dateTimeAccessor = DateTimeFormatter.ofLocalizedDateTime(MEDIUM).withLocale(Locale.US).withZone(ZoneOffset.UTC).parse(matcher.group("formattedDateTime"));
                                reminderRepository.add(username, Instant.from(dateTimeAccessor));
                                return true;
                            } else {
                                return false;
                            }
                        }));
        fibiSignalClient.markMessagesRead(username);
        // Besides the confirmation message, fibi answers the message we send. Wait for it.
        await().timeout(Duration.ofSeconds(120)).until(() -> !fibiSignalClient.lastMessagesByFibiTo(username).isEmpty());
        fibiSignalClient.markMessagesRead(username);
    }

    @When("wait till the reminder time is reached")
    public void theyWaitTillTheReminderTimeIsReached() {
        UserRepository.User user = userRepository.getCurrentUser();
        long secondsUntilRemindAtIsReached = now().until(reminderRepository.getLastAddedReminderOf(user.username).remindAt(), SECONDS);
        await().timeout(Duration.ofSeconds(secondsUntilRemindAtIsReached + 10)).pollDelay(Duration.ofSeconds(secondsUntilRemindAtIsReached - 5))
                .until(() -> true);
        fibiSignalClient.markMessagesRead(user.username);
    }
}
