package icu.neurospicy.fibi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;

@Service
public class FibiSignalClient {

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${signal-mock.base-url}")
    private String signalMockBaseUrl;

    private final ConcurrentSkipListSet<Message> messages = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<MessageReceivedConfirmation> confirmedMessages = new ConcurrentSkipListSet<>();

    @PostConstruct
    private void init() {
        // Subscribe to SSE from the mock server
        WebClient client = WebClient.create(this.signalMockBaseUrl);
        Flux<ServerSentEvent<String>> eventStream = client.get()
                .uri("/all_events")
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .retry();

        eventStream.subscribe(
                this::processNewMessage,
                error -> System.out.println("Error receiving SSE :" + error + "\nTrace:\n" + Arrays.stream(error.getStackTrace()).map(StackTraceElement::toString).collect(Collectors.joining("\n")))
        );
    }

    private void processNewMessage(ServerSentEvent<String> evt) {
        System.out.printf("Processing message %s", evt);
        if (Objects.equals(evt.event(), "send") && evt.data() != null) {
            try {
                JsonNode node = objectMapper.readTree(evt.data());
                UserRepository.User receiver = userRepository.getUserByNumber(node.path("to").asText());
                UserRepository.User fibi = userRepository.getUserByName("Fibi");
                String msgField = node.path("message").asText();
                this.messages.add(new Message(node.path("message").asLong(), new Date(), receiver.number, receiver.uuid, receiver.username, receiver.deviceId, msgField, fibi.number, fibi.uuid, fibi.username, fibi.deviceId));
            } catch (JsonProcessingException ex) {
                System.out.printf("Failed to parse SSE message: %s", evt.data());
            }
        } else if (Objects.equals(evt.event(), "sendReceipt") && evt.data() != null) {
            try {
                JsonNode node = objectMapper.readTree(evt.data());
                UserRepository.User receiver = userRepository.getUserByNumber(node.path("to").asText());
                for (JsonNode timestampNode : node.path("timestamps")) {
                    confirmedMessages.add(new MessageReceivedConfirmation(receiver, new MessageId(timestampNode.asLong())));
                }
            } catch (JsonProcessingException ex) {
                System.out.printf("Failed to parse SSE message: %s", evt.data());
            }
        } else {
            System.out.printf("Ignoring event: %s", evt);
        }
    }

    /**
     * Sends a message to Fibi on behalf of the given userName.
     */
    public MessageId sendMessageToFibi(UserRepository.User user, String text) {
        String url = this.signalMockBaseUrl + "/send";
        text = replacePlaceHolders(text);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Long timestamp = Instant.now().getEpochSecond();

        String jsonBody = String.format(
                """
                        {
                            "to": "%s",
                            "message": "%s",
                            "timestamp": %d,
                            "source": "%s",
                            "sourceUuid": "%s",
                            "sourceName": "%s",
                            "sourceDevice": %s
                        }
                        """,
                userRepository.getUserByName("Fibi").number,
                text,
                timestamp,
                user.number,
                user.uuid,
                user.username,
                user.deviceId
        );
        UserRepository.User fibi = userRepository.getUserByName("Fibi");
        Message message = new Message(timestamp, new Date(), fibi.number, fibi.uuid, fibi.username, fibi.deviceId, text, user.number, user.uuid, user.username, user.deviceId);
        this.messages.add(message);

        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        restTemplate.postForEntity(url, request, String.class);
        return new MessageId(timestamp);
    }

    private String replacePlaceHolders(String text) {
        StringBuilder sb = new StringBuilder();
        Pattern timePattern = Pattern.compile("<now(?> plus (?<minutes>\\d+) minute[s]?)?>");
        do {
            Matcher timeMatcher = timePattern.matcher(text);
            if (timeMatcher.find()) {
                sb.append(text, 0, timeMatcher.start());
                sb.append((timeMatcher.namedGroups().isEmpty() ? now(UTC).toLocalTime() :
                        now(UTC).plus(Duration.ofMinutes(Integer.parseInt(timeMatcher.group("minutes")))).toLocalTime())
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                text = text.substring(timeMatcher.end());
            } else {
                sb.append(text);
                text = "";
            }
        } while (!text.isBlank());
        return sb.toString();
    }

    public void reactToMessage(UserRepository.User sourceUser, UserRepository.User targetUser, String messageText, String reactionEmoji) {
        Message targetMessage = this.messages.stream()
                .filter(msg -> msg.toNumber.equals(targetUser.number) && msg.text.equals(messageText))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Message not found to react to."));

        String jsonBody = String.format(
                """
                        {
                            "to": "%s",
                            "from": "%s",
                            "reaction": {
                                "emoji": "%s",
                                "targetAuthor": "%s",
                                "targetSentTimestamp": %d
                            }
                        }
                        """,
                userRepository.getUserByName("Fibi").number,
                sourceUser.number,
                reactionEmoji,
                targetMessage.sourceUuid,
                targetMessage.timestamp
        );

        String url = this.signalMockBaseUrl + "/react";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        restTemplate.postForEntity(url, request, String.class);
    }

    public boolean verifyFibiSentTextTo(String expectedMessage, UserRepository.User user) {
        boolean match = this.messages.stream().filter(m -> !m.processed).anyMatch(
                msg -> msg.toNumber.equals(user.number) && (msg.text.equalsIgnoreCase(expectedMessage))
        );
        if (match) this.messages.forEach(message -> message.processed = true);
        return match;
    }

    public boolean verifyFibiSentTextContainingTo(String textSnippet, UserRepository.User user) {
        boolean match = this.messages.stream().filter(m -> !m.processed).anyMatch(
                msg -> msg.toNumber.equals(user.number) && (msg.text.toLowerCase().contains(textSnippet.toLowerCase()))
        );
        if (match) this.messages.forEach(message -> message.processed = true);
        return match;
    }

    public Boolean verifyFibiSentTextLikeTo(Pattern pattern, UserRepository.User user) {
        boolean match = this.messages.stream().filter(m -> !m.processed).anyMatch(
                msg -> msg.toNumber.equals(user.number) && (pattern.matcher(msg.text).matches())
        );
        if (match) this.messages.forEach(message -> message.processed = true);
        return match;
    }

    public Boolean verifyFibiSentTo(String userName) {
        UserRepository.User user = userRepository.getUserByName(userName);
        if (user == null) {
            return false;
        }
        boolean match = this.messages.stream().filter(m -> !m.processed).anyMatch(
                msg -> msg.toNumber.equals(user.number)
        );
        if (match) this.messages.forEach(message -> message.processed = true);
        return match;
    }

    public Boolean verifyConfirmedMessage(UserRepository.User user, MessageId messageId) {
        return this.confirmedMessages.contains(new MessageReceivedConfirmation(user, messageId));
    }

    /**
     * Clears all stored messages for the given userName.
     */
    public void clearMessagesFor(String userName) {
        UserRepository.User user = userRepository.getUserByName(userName);
        if (user == null) {
            return;
        }

        this.messages.removeIf(m -> m.toNumber.equals(user.number));
    }

    public void markMessagesRead(String username) {
        this.messages.stream().filter(m -> m.toName.equals(username)).forEach(message -> message.processed = true);
    }

    public List<Message> lastMessagesByFibiTo(String username) {
        return this.messages.stream().filter(m -> !m.processed && m.toName.equals(username)).toList();
    }


    public static class Message implements Comparable<Message> {
        public final MessageId id;
        public final Date receivedAt;
        public final String toNumber;
        public final String toUuid;
        public final String toName;
        public final Short toDevice;
        public final String text;
        public final String sourceNumber;
        public final String sourceUuid;
        public final String sourceName;
        public final Short sourceDevice;
        public final long timestamp;
        public boolean processed = false;

        public Message(Long timestamp, Date receivedAt, String toNumber, String toUuid, String toName, Short toDevice, String text, String sourceNumber, String sourceUuid, String sourceName, Short sourceDevice) {
            this.id = new MessageId(timestamp);
            this.receivedAt = receivedAt;
            this.toNumber = toNumber;
            this.toUuid = toUuid;
            this.toName = toName;
            this.toDevice = toDevice;
            this.text = text;
            this.sourceNumber = sourceNumber;
            this.sourceUuid = sourceUuid;
            this.sourceName = sourceName;
            this.sourceDevice = sourceDevice;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(Message other) {
            return other.receivedAt.compareTo(this.receivedAt);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Message that)) {
                return false;
            }
            return this.receivedAt.equals(that.receivedAt);
        }

        @Override
        public int hashCode() {
            return this.receivedAt.hashCode();
        }
    }

    public static class MessageId {
        private final Long timestamp;

        public MessageId(Long timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return this.timestamp.toString();
        }
    }

    public record MessageReceivedConfirmation(UserRepository.User sender,
                                              MessageId messageId) implements Comparable<MessageReceivedConfirmation> {


        @Override
        public int compareTo(@NotNull FibiSignalClient.MessageReceivedConfirmation o) {
            return this.messageId.toString().compareTo(o.messageId.toString());
        }
    }
}