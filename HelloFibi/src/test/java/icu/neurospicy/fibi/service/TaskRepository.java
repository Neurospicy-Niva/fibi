package icu.neurospicy.fibi.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TaskRepository {
    private final List<Task> list = new ArrayList<>();

    public List<Task> getActiveTasks(String username) {
        return list.stream().filter(t -> t.username.equals(username) && !t.complete).toList();
    }

    public List<Task> getTasks(String username) {
        return list.stream().filter(t -> t.username.equals(username)).toList();
    }

    public void markComplete(String username, String title) {
        List<Task> updatedTasks = getTasks(username).stream().filter(t -> t.title.equals(title)).map(t ->
                new Task(t.username, t.title, true)
        ).toList();
        updatedTasks.forEach(t -> {
            this.list.removeIf(oldTask -> oldTask.title.equals(t.title));
            this.list.add(t);
        });
    }

    public void rename(String username, String newTitle, String oldTitle) {
        List<Task> updatedTasks = getTasks(username).stream().filter(t -> t.title.equals(oldTitle)).map(t ->
                new Task(t.username, newTitle, t.complete)
        ).toList();
        this.list.removeIf(oldTask -> oldTask.title.equals(oldTitle));
        this.list.addAll(updatedTasks);
    }

    public void add(String username, String title) {
        list.add(new Task(username, title, false));
    }

    public record Task(String username, String title, boolean complete) {
    }
}
