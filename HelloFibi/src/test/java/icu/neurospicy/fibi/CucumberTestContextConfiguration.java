package icu.neurospicy.fibi;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.testcontainers.containers.BindMode.READ_WRITE;

@CucumberContextConfiguration
@SpringBootTest(classes = TestConfig.class)
@Testcontainers()
public class CucumberTestContextConfiguration {
    @Container
    public static final GenericContainer<?> signalMock =
            new GenericContainer<>("icu.neurospicy/mock-signal-cli:latest")
                    .withNetworkAliases("signal-cli-mock")
                    .withExposedPorts(8080)
                    //.withLogConsumer(of -> System.out.printf("SignalMock: %s", of.getUtf8String()))
                    .waitingFor(Wait.forLogMessage(".*Running on all addresses.*", 1));
    @Container
    public static final MongoDBContainer mongo = new MongoDBContainer("mongo:5")
            .withNetworkAliases("mongodb")
            //.withLogConsumer(of -> System.out.printf("MONGODB: %s", of.getUtf8String()))
            .withEnv(Map.of(
                    "MONGO_INITDB_DATABASE", "fibi"
            ))
            .withCopyFileToContainer(MountableFile.forClasspathResource("container/mongodb/init.js"), "/docker-entrypoint-initdb.d/init.js")
            .withFileSystemBind("data/mongodb", "/data/db", READ_WRITE)
            .withStartupAttempts(3)
            .waitingFor(Wait.forLogMessage(".*init\\.js.*", 1));
    //@Container
    //final static OllamaContainer ollama = new OllamaContainer("ollama/ollama:latest").withNetworkAliases("ollama");

    @Container
    public static final GenericContainer<?> radicaleCalendar =
            new GenericContainer<>("python:3.13")
                    .withExposedPorts(5232)
                    .withNetworkAliases("radicalecalendar")
                    .withCopyFileToContainer(MountableFile.forClasspathResource("container/calendar/config/config"), "/radicale-config/config")
                    .withCopyFileToContainer(MountableFile.forClasspathResource("container/calendar/config/users"), "/radicale-config/users")
                    .withCopyFileToContainer(MountableFile.forClasspathResource("container/calendar/script/start.sh"), "/radicale-bin/start.sh")
                    .withCommand("/bin/sh /radicale-bin/start.sh")
                    //.withLogConsumer(of -> System.out.printf("RADICALE: %s", of.getUtf8String()))
                    .waitingFor(Wait.forLogMessage(".*Radicale server ready.*", 1))
                    .waitingFor(Wait.forHttp(""));
    @Container
    public static final GenericContainer<?> fibi =
            new GenericContainer<>("icu.neurospicy/fibi:latest")
                    .dependsOn(List.of(signalMock, mongo, radicaleCalendar))
                    .withExposedPorts(8080)
                    .withLogConsumer(of -> System.out.printf("Fibi: %s", of.getUtf8String()))
                    .withFileSystemBind("data/calendar", "/calendars", READ_WRITE)
                    .withEnv(Map.of(
                            "SIGNAL_CLI_API_URL", "http://signal-cli-mock:8080/signal/api/v1",

                            "SPRING_DATA_MONGODB_HOST", "mongodb",
                            "SPRING_DATA_MONGODB_USERNAME", "fibiapp",
                            "SPRING_DATA_MONGODB_PASSWORD", "whydoesitalwaysRAINonme",
                            "SPRING_DATA_MONGODB_DATABASE", "fibi",

                            "SPRING_AI_OLLAMA_BASE_URL", "https://ai.local",
                            "APP_CALENDAR_ROOT", "/calendars",
                            //"LOGGING_LEVEL_ICU_NEUROSPICY_FIBI", "DEBUG"
                            "LOGGING_LEVEL_ICU_NEUROSPICY_FIBI_DOMAIN_SERVICE_FRIENDS", "DEBUG"
                    ))
                    .waitingFor(Wait.forLogMessage(".*Started FibiApplication.*", 1))
                    .withAccessToHost(true);

    static {
        //clean up calendars
        File calendarDir = Path.of("data/calendar").toFile();
        calendarDir.mkdirs();
        File[] oldCalendarDirs = calendarDir.listFiles();
        if (Objects.nonNull(oldCalendarDirs))
            Arrays.stream(oldCalendarDirs).toList().forEach(FileSystemUtils::deleteRecursively);
        //clean up mongodb
        FileSystemUtils.deleteRecursively(Path.of("data/mongodb").toFile());
        //start containers
        Network network = Network.newNetwork();
        signalMock.withNetwork(network).start();
        mongo.withNetwork(network).start();
        //ollama.withNetwork(network).start();
        org.testcontainers.Testcontainers.exposeHostPorts(11434);
        fibi.withNetwork(network).start();
        radicaleCalendar.withNetwork(network).start();
    }

    @DynamicPropertySource
    static void signalMockProperties(DynamicPropertyRegistry registry) {
        registry.add("signal-mock.base-url", () ->
                "http://" + signalMock.getHost() + ":" + signalMock.getMappedPort(8080) + "/signal");
    }
}