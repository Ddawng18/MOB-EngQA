package com.example.engqa.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

/** Firestore POJO for {@code centers/{cid}/courses/{id}}. */
@IgnoreExtraProperties
public class Course {

    @DocumentId
    private String id;
    private String title;
    private String description;
    private String level;
    private String schedule;

    public Course() {
    }

    public Course(String title, String description, String level, String schedule) {
        this.title = title;
        this.description = description;
        this.level = level;
        this.schedule = schedule;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
}
