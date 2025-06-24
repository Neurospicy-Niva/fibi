package icu.neurospicy.fibi;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalTime;

@RestController
@RequestMapping("/signal/api/v1")
class SignalMessageSender {

    // This mock SSE endpoint returns one line per second
    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessages() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(sequence -> "Mocked signal event at " + LocalTime.now());
    }
}