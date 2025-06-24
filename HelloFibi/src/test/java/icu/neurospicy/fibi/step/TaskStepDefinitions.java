package icu.neurospicy.fibi.step;

import icu.neurospicy.fibi.service.FibiSignalClient;
import icu.neurospicy.fibi.service.TaskRepository;
import icu.neurospicy.fibi.service.UserRepository;
import icu.neurospicy.fibi.service.UserRepository.User;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;


public class TaskStepDefinitions {
    @Autowired
    private FibiSignalClient fibiSignalClient;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TaskRepository taskRepository;

    @Given("they add a task {string}")
    public void theyAddATask(String title) {
        User user = userRepository.getCurrentUser();
        fibiSignalClient.sendMessageToFibi(user, "Add task: '" + title + "'");
        theyEventuallyReceiveATaskAddedConfirmation();
    }

    @Given("they tell to mark task {string} completed")
    public void theyTellToMarkTaskCompleted(String title) {
        User user = userRepository.getCurrentUser();
        fibiSignalClient.sendMessageToFibi(user, "Mark task '" + title + "' as completed");
        theyEventuallyReceiveATaskCompletedConfirmation();
    }

    @Then("they eventually receive a list of the tasks")
    public void theyEventuallyReceiveAListOfTheTasks() {
        String username = userRepository.getCurrentUser().username;
        await().timeout(Duration.ofSeconds(120)).until(
                () -> fibiSignalClient.lastMessagesByFibiTo(username).stream().anyMatch(
                        message -> taskRepository.getActiveTasks(username).stream().allMatch(
                                task -> message.text.toLowerCase().contains(task.title().toLowerCase()))));
        fibiSignalClient.markMessagesRead(username);
    }

    @Then("they eventually receive a task added confirmation")
    public void theyEventuallyReceiveATaskAddedConfirmation() {
        String username = userRepository.getCurrentUser().username;
        Pattern taskAddedPattern = Pattern.compile("^Added task: (?<title>.+)$");
        await().timeout(Duration.ofSeconds(120)).until(() -> {
            if (fibiSignalClient.lastMessagesByFibiTo(username).stream().filter(m -> m.text.toLowerCase().contains("added"))
                    .anyMatch(message -> {
                        Matcher matcher = taskAddedPattern.matcher(message.text);
                        if (matcher.matches()) {
                            taskRepository.add(username, matcher.group("title"));
                            return true;
                        } else {
                            return false;
                        }
                    })
            ) {
                return true;
            } else {
                // Sometimes fibi asks if they shall set the task completed, we answer with yes here
                answerCheckUpQuestion(username, List.of("add", "?"), "Yes. Add the task.");
                return false;
            }
        });
        fibiSignalClient.markMessagesRead(username);
        // Besides the confirmation message, fibi answers the message we send. Wait for it.
        await().timeout(Duration.ofSeconds(120)).until(() -> !fibiSignalClient.lastMessagesByFibiTo(username).isEmpty());
        fibiSignalClient.markMessagesRead(username);
    }

    @Then("they eventually receive a task completed confirmation")
    public void theyEventuallyReceiveATaskCompletedConfirmation() {
        String username = userRepository.getCurrentUser().username;
        Pattern taskCompletedPattern = Pattern.compile("^(?<title>.+) completed$");
        await().timeout(Duration.ofSeconds(120)).until(() -> {
            if (fibiSignalClient.lastMessagesByFibiTo(username).stream().filter(m -> m.text.toLowerCase().contains("completed"))
                    .anyMatch(message -> {
                        Matcher matcher = taskCompletedPattern.matcher(message.text);
                        if (matcher.matches()) {
                            taskRepository.markComplete(username, matcher.group("title"));
                            return true;
                        } else {
                            return false;
                        }
                    })
            ) {
                return true;
            } else {
                // Sometimes fibi asks if they shall set the task completed, we answer with yes here
                answerCheckUpQuestion(username, List.of("mark", "complete"), "Yes. Mark the task completed.");
                return false;
            }
        });
        fibiSignalClient.markMessagesRead(username);
        // Besides the confirmation message, fibi answers the message we send. Wait for it.
        await().timeout(Duration.ofSeconds(120)).until(() -> !fibiSignalClient.lastMessagesByFibiTo(username).isEmpty());
        fibiSignalClient.markMessagesRead(username);
    }

    @Then("they eventually receive a task renamed confirmation")
    public void theyEventuallyReceiveATaskRenamedConfirmation() {
        String username = userRepository.getCurrentUser().username;
        Pattern taskRenamedPattern = Pattern.compile("^Renamed task (?<title>.+) \\((?<oldTitle>.+)\\)$");
        await().timeout(Duration.ofSeconds(120)).until(() -> {
            if (fibiSignalClient.lastMessagesByFibiTo(username).stream().filter(m -> m.text.toLowerCase().contains("renamed"))
                    .anyMatch(message -> {
                        Matcher matcher = taskRenamedPattern.matcher(message.text);
                        if (matcher.matches()) {
                            taskRepository.rename(username, matcher.group("title"), matcher.group("oldTitle"));
                            return true;
                        } else {
                            return false;
                        }
                    })
            ) {
                return true;
            } else {
                // Sometimes fibi asks if they shall rename the task, we answer with yes here
                answerCheckUpQuestion(username, List.of("mark", "complete"), "Yes. Rename the task completed.");
                return false;
            }
        });
        fibiSignalClient.markMessagesRead(username);
        // Besides the confirmation message, fibi answers the message we send. Wait for it.
        await().timeout(Duration.ofSeconds(120)).until(() -> !fibiSignalClient.lastMessagesByFibiTo(username).isEmpty());
        fibiSignalClient.markMessagesRead(username);
    }

    @Then("they eventually receive a cleaned-up tasks confirmation")
    public void theyEventuallyReceiveACleanedUpTasksConfirmation() {
        String username = userRepository.getCurrentUser().username;
        await().timeout(Duration.ofSeconds(120)).until(() ->
                fibiSignalClient.lastMessagesByFibiTo(username).stream().anyMatch(m -> m.text.toLowerCase().contains("cleaned up tasks")));
        fibiSignalClient.markMessagesRead(username);
    }

    private void answerCheckUpQuestion(String username, List<String> keywordsIndicatingCheckUpQuestion, String text) {
        if (fibiSignalClient.lastMessagesByFibiTo(username).stream().anyMatch(m -> {
            String messageLowerCase = m.text.toLowerCase();
            return (messageLowerCase.contains("would") || messageLowerCase.contains("shall") || messageLowerCase.contains("should"))
                    && keywordsIndicatingCheckUpQuestion.stream().allMatch(messageLowerCase::contains);
        })) {
            fibiSignalClient.markMessagesRead(username);
            fibiSignalClient.sendMessageToFibi(userRepository.getCurrentUser(), text);
        }
    }

    @Then("they eventually receive a tasks cleaned up confirmation")
    public void theyEventuallyReceiveATasksCleanedUpConfirmation() {
        String username = userRepository.getCurrentUser().username;
        await().timeout(Duration.ofSeconds(120)).until(() -> fibiSignalClient.lastMessagesByFibiTo(username).stream().anyMatch(m -> m.text.toLowerCase().contains("cleaned up")));
        fibiSignalClient.markMessagesRead(username);
        // Besides the confirmation message, fibi answers the message we send. Wait for it.
        await().timeout(Duration.ofSeconds(120)).until(() -> !fibiSignalClient.lastMessagesByFibiTo(username).isEmpty());
        fibiSignalClient.markMessagesRead(username);
    }
}
