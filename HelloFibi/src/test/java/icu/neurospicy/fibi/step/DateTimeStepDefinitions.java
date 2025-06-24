package icu.neurospicy.fibi.step;

import icu.neurospicy.fibi.service.FibiSignalClient;
import icu.neurospicy.fibi.service.UserRepository;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

public class DateTimeStepDefinitions {
    @Autowired
    private FibiSignalClient fibiSignalClient;
    @Autowired
    private UserRepository userRepository;

    @When("{int} minutes pass")
    @When("{int} minute passes")
    public void waitForMinutes(int minutesToWait) {
        Duration duration = Duration.ofMinutes(minutesToWait);
        waitFor(duration);
    }

    @When("{int} seconds pass")
    public void waitForSeconds(int secondsToWait) {
        Duration duration = Duration.ofSeconds(secondsToWait);
        waitFor(duration);
    }

    private void waitFor(Duration duration) {
        await().timeout(duration.plusSeconds(5)).pollDelay(duration).until(() -> true);
        fibiSignalClient.clearMessagesFor(userRepository.getCurrentUser().username);
    }
}