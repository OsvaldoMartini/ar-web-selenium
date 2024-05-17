package com.allinweb.ch.persistence;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class DatabaseUserDTO {
    private StringProperty id;
    private StringProperty jobs;
    private StringProperty name;
    private StringProperty url;
    private StringProperty priority;
    private StringProperty username;
    private StringProperty password;

    public DatabaseUserDTO(
            String id, String jobs, String name, String url, String priority, String username, String password) {
        this.id = new SimpleStringProperty(id);
        this.jobs = new SimpleStringProperty(jobs);
        this.name = new SimpleStringProperty(name);
        this.url = new SimpleStringProperty(url);
        this.priority = new SimpleStringProperty(priority);
        this.username = new SimpleStringProperty(username);
        this.password = new SimpleStringProperty(password);
    }

    public DatabaseUserDTO(String id, String name, String url, String priority) {
        this.id = new SimpleStringProperty(id);
        this.name = new SimpleStringProperty(name);
        this.url = new SimpleStringProperty(url);
        this.priority = new SimpleStringProperty(priority);
    }

    public String getId() {
        return id.get();
    }

    public void setId(String id) {
        this.id.set(id);
    }

    public StringProperty idProperty() {
        return id;
    }

    public String getJobs() {
        return jobs != null ? jobs.get() : "0";
    }

    public StringProperty jobsProperty() {
        return jobs;
    }

    public void setJobs(String jobs) {
        this.jobs.set(jobs);
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getUrl() {
        return url.get();
    }

    public StringProperty urlProperty() {
        return url;
    }

    public void setUrl(String url) {
        this.url.set(url);
    }

    public String getPriority() {
        return priority != null ? priority.get() : "";
    }

    public StringProperty priorityProperty() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority.set(priority);
    }

    public String getUsername() {
        return username != null ? username.get() : "";
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public String getPassword() {
        return password != null ? password.get() : "";
    }

    public StringProperty passwordProperty() {
        return password;
    }

    public void setPassword(String password) {
        this.password.set(password);
    }
}
