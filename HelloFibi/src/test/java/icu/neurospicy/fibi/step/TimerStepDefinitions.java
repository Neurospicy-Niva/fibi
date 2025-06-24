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
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.Instant.now;
import static java.time.format.FormatStyle.MEDIUM;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class TimerStepDefinitions {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FibiSignalClient fibiSignalClient;

    @Then("they eventually receive a set timer confirmation")
    public void theyEventuallyReceiveASetReminderConfirmation() {
        String username = userRepository.getCurrentUser().username;
        Pattern pattern = Pattern.compile("^(__)?Added timer .*$");
        await().timeout(Duration.ofSeconds(120)).until(() ->
                fibiSignalClient.lastMessagesByFibiTo(username).stream().filter(m -> m.text.toLowerCase().contains("added timer"))
                        .anyMatch(message -> {
                             return pattern.matcher(message.text).matches();
                        }));
        fibiSignalClient.markMessagesRead(username);
        // Besides the confirmation message, fibi answers the message we send. Wait for it.
        await().timeout(Duration.ofSeconds(120)).until(() -> !fibiSignalClient.lastMessagesByFibiTo(username).isEmpty());
        fibiSignalClient.markMessagesRead(username);
    }
}
