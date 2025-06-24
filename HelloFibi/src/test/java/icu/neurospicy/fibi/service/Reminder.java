package icu.neurospicy.fibi.service;

import java.time.Instant;

public record Reminder(String username, Instant remindAt) {
}
