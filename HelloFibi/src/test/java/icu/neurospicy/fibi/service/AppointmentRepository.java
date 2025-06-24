package icu.neurospicy.fibi.service;

import org.springframework.stereotype.Repository;
import org.testcontainers.shaded.com.google.common.collect.Lists;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AppointmentRepository {
    private static final Map<String, List<Appointment>> usernameToAppointments = new ConcurrentHashMap<String, List<Appointment>>();

    public Appointment add(String username, Appointment appointment) {
        usernameToAppointments.put(username, Lists.asList(appointment, usernameToAppointments.getOrDefault(username, new ArrayList<>()).toArray(new Appointment[0])));
        return appointment;
    }

    public List<Appointment> getAll(String username) {
        return usernameToAppointments.getOrDefault(username, new ArrayList<>());
    }

    public void addAll(String username, List<Appointment> list) {
        list.forEach(a -> this.add(username, a));
    }

    public static class Appointment {
        public final String title;
        public final Instant startAt;
        public final Instant endAt;

        public Appointment(String title, Instant startAt, Instant endAt) {
            this.title = title;
            this.startAt = startAt;
            this.endAt = endAt;
        }
    }
}
