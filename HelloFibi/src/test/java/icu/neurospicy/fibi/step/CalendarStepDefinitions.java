package icu.neurospicy.fibi.step;

import com.github.caldav4j.methods.HttpGetMethod;
import com.github.caldav4j.methods.HttpPutMethod;
import com.github.caldav4j.model.request.CalendarRequest;
import icu.neurospicy.fibi.CucumberTestContextConfiguration;
import icu.neurospicy.fibi.service.AppointmentRepository;
import icu.neurospicy.fibi.service.AppointmentRepository.Appointment;
import icu.neurospicy.fibi.service.FibiSignalClient;
import icu.neurospicy.fibi.service.UserRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Name;
import net.fortuna.ical4j.model.property.XProperty;
import net.fortuna.ical4j.util.RandomUidGenerator;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentSkipListSet;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static org.awaitility.Awaitility.await;

public class CalendarStepDefinitions {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private FibiSignalClient fibiSignalClient;

    private final GenericContainer<?> radicaleCalendar = CucumberTestContextConfiguration.radicaleCalendar;
    private final ConcurrentSkipListSet<String> usersWithCalendars = new ConcurrentSkipListSet<>();
    private final Random random = new Random();

    private static final List<String> TEST_EVENT_NAMES = List.of(
            "Birthday", "Meeting", "Grocery Shopping", "Doctor Appointment", "Gym Session",
            "Project Deadline", "Dentist Appointment", "Team Meeting", "Parent-Teacher Conference",
            "Webinar", "Anniversary", "Hairdresser Appointment", "Business Trip", "Assignment Submission",
            "Friends Gathering", "Wedding", "Vacation Planning", "Vet Appointment", "Tax Return Submission",
            "Car Inspection", "Kids' Birthday Party", "Sports Event", "Concert", "Flight Trip",
            "Seminar", "Driving Lesson", "Work Shift", "Library Book Return", "Tutoring Session", "Trade Fair Visit"
    );

    @Given("{string} has a WebDAV calendar")
    public void userWithNameHasACalendar(String username) throws IOException {
        createCalendarForUser(username);
    }

    @Given("they have a WebDAV calendar")
    public void theyHaveACalendar() throws IOException {
        createCalendarForUser(userRepository.getCurrentUser().username);
    }

    @When("they send their CalDAV URL to Fibi")
    public void theySendCalDavConfig() {
        UserRepository.User user = userRepository.getCurrentUser();
        String message = "My CalDAV URL is: '" + user.calDavUrl + "' Username: '" + user.calDavUsername + "' Password: '" + user.calDavPassword + "'";
        fibiSignalClient.sendMessageToFibi(user, message);
    }

    @Given("they have a registered calendar with {int} appointments")
    public void theyHaveARegisteredCalendar(int count) throws IOException {
        theyHaveACalendar();
        theyHaveAppointmentsToday(count);
        UserRepository.User user = userRepository.getCurrentUser();
        String message = "I want to add my calendar. CalDAV URL is: '" + user.calDavUrl + "' Username: '" + user.calDavUsername + "' Password: '" + user.calDavPassword + "'";
        fibiSignalClient.sendMessageToFibi(user, message);
        await().timeout(Duration.ofSeconds(60)).until(() -> fibiSignalClient.verifyFibiSentTextContainingTo("calendar is now connected", user));
    }

    private void createCalendarForUser(String username) throws IOException {
        String usernameLower = username.toLowerCase();

        usersWithCalendars.add(usernameLower);
        radicaleCalendar.stop();
        radicaleCalendar.withCopyToContainer(
                Transferable.of(usersWithCalendars.stream()
                        .map(name -> String.format("%s:%s123", name, name))
                        .collect(joining("\n"))),
                "/radicale-config/users"
        );
        radicaleCalendar.start();

        String calendarUrl = String.format(
                "http://%s:%d/%s",
                radicaleCalendar.getHost(),
                radicaleCalendar.getMappedPort(5232),
                usernameLower
        );

        CloseableHttpClient httpClient = createUserHttpClient(usernameLower, usernameLower + "123");

        try {
            CalendarRequest c = new CalendarRequest(createBaseCalendar(username));
            HttpPutMethod m = new HttpPutMethod(calendarUrl + "/calendar", c, new CalendarOutputter());
            httpClient.execute(m);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create calendar for user " + username, e);
        }
        userRepository.saveCalDavInfo(username, String.format(
                "http://%s:%d/%s/calendar/",
                "radicalecalendar",
                5232,
                usernameLower
        ), usernameLower, usernameLower + "123");
    }

    private CloseableHttpClient createUserHttpClient(String username, String password) {
        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(radicaleCalendar.getHost(), radicaleCalendar.getMappedPort(5232)),
                new UsernamePasswordCredentials(username, password)
        );
        return HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
    }

    @Given("they have {int} appointments today")
    public void theyHaveAppointmentsToday(int count) {
        appointmentRepository.addAll(userRepository.getCurrentUser().username, Arrays.stream(new String[count])
                .map(s -> getRandomEventName()).map(eventName -> {
                    Instant startAt = getRandomTimeToday();
                    return createAppointment(startAt, getRandomTimeTodayAfter(startAt), eventName);
                }).toList());
    }

    @Given("they have the appointment {string} ending in {int} minutes")
    public void theyHaveAnAppointmentEndingInAFewMinutes(String summary, int minutes) {
        appointmentRepository.add(userRepository.getCurrentUser().username,
                createAppointment(now().minus(Duration.ofMinutes(90)), now().plus(Duration.ofMinutes(minutes)), summary));
    }

    @When("the end of the appointment is reached")
    public void theEndOfTheAppointmentIsReached() {
        UserRepository.User user = userRepository.getCurrentUser();
        long secondsUntilAppointmentEnd = now().until(appointmentRepository.getAll(user.username).getFirst().endAt, SECONDS);
        await().timeout(Duration.ofSeconds(secondsUntilAppointmentEnd + 10)).pollDelay(Duration.ofSeconds(secondsUntilAppointmentEnd - 5))
                .until(() -> true);
        fibiSignalClient.markMessagesRead(user.username);
    }

    @Then("they eventually receive a list of their appointments")
    public void theyEventuallyReceiveAListOfAppointments() {
        String username = userRepository.getCurrentUser().username;
        await().timeout(Duration.ofSeconds(120)).until(
                () -> fibiSignalClient.lastMessagesByFibiTo(username).stream().anyMatch(
                        message -> appointmentRepository.getAll(username).stream().allMatch(
                                appointment -> message.text.toLowerCase().contains(appointment.title.toLowerCase()))));
        fibiSignalClient.markMessagesRead(username);
    }

    private Instant getRandomTimeToday() {
        int quarters = random.nextInt(0, (24 * 4));
        return now().truncatedTo(ChronoUnit.DAYS).plus(quarters * 15L, ChronoUnit.MINUTES);
    }

    private Instant getRandomTimeTodayAfter(Instant start) {
        Instant end;
        do {
            end = getRandomTimeToday();
        } while (end.isBefore(start));
        return end;
    }

    private String getRandomEventName() {
        return TEST_EVENT_NAMES.get(random.nextInt(TEST_EVENT_NAMES.size()));
    }


    private Appointment createAppointment(Instant start, Instant end, String eventName) {
        VEvent event = new VEvent(start.atZone(ZoneId.systemDefault()), end.atZone(ZoneId.systemDefault()), eventName).withProperty(new RandomUidGenerator().generateUid()).getFluentTarget();
        String usernameLower = userRepository.getCurrentUser().username.toLowerCase();
        String calendarUrl = String.format(
                "http://%s:%d/%s/calendar/",
                radicaleCalendar.getHost(),
                radicaleCalendar.getMappedPort(5232),
                usernameLower
        );

        try {
            CloseableHttpClient httpClient = createUserHttpClient(usernameLower, usernameLower + "123");
            HttpGetMethod get = new HttpGetMethod(calendarUrl, new CalendarBuilder());
            Calendar calendar = get.getResponseBodyAsCalendar(httpClient.execute(get));
            calendar = calendar.withComponent(event).getFluentTarget();
            HttpPutMethod method = new HttpPutMethod(calendarUrl, new CalendarRequest(calendar), new CalendarOutputter());
            httpClient.execute(method);
            System.out.println("Created appointment: " + eventName + " from " + start + " to " + end);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create appointment: " + eventName, e);
        }
        return new Appointment(eventName, start, end);
    }

    private Calendar createBaseCalendar(String username) {
        String name = username + " private";
        return new Calendar()
                .withProdId("-//Events Calendar//iCal4j 1.0//EN")
                .withDefaults()
                .withProperty(new Name(name))
                .withProperty(new XProperty("X-NAME", name))
                .withProperty(new XProperty("DisplayName", name))
                .withProperty(new Description("The private calendar of " + username))
                .getFluentTarget();
    }
}
