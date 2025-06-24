package icu.neurospicy.fibi.service;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
public class ReminderRepository {
    private final Deque<Reminder> reminders = new ConcurrentLinkedDeque<>();

    public void add(String username, Instant remindAt) {
        reminders.add(new Reminder(username, remindAt));
    }

    public Reminder getLastAddedReminderOf(String username) {
        return reminders.stream().filter(r -> r.username().equals(username)).findFirst().orElseThrow();
    }

}
