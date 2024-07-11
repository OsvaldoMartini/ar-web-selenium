package com.allinweb.ch.persistence;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class VariableUserDTO {
    private StringProperty id;
    private StringProperty type;
    private StringProperty name;
    private StringProperty botJobId;
    private StringProperty usedVars;

    public VariableUserDTO() {}

    public VariableUserDTO(String id, String type, String name, String botJobId, String usedVars) {
        this.id = new SimpleStringProperty(id);
        this.type = new SimpleStringProperty(type);
        this.name = new SimpleStringProperty(name);
        this.botJobId = new SimpleStringProperty(botJobId);
        this.usedVars = new SimpleStringProperty(usedVars);
    }

    public VariableUserDTO(String id, String type, String name, String botJobId) {
        this.id = new SimpleStringProperty(id);
        this.type = new SimpleStringProperty(type);
        this.name = new SimpleStringProperty(name);
        this.botJobId = new SimpleStringProperty(botJobId);
    }

    public String getId() {
        return id.get();
    }

    public StringProperty idProperty() {
        return id;
    }

    public void setId(String id) {
        this.id.set(id);
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getBotJobId() {
        return botJobId.get();
    }

    public StringProperty botJobIdProperty() {
        return botJobId;
    }

    public void setBotJobId(String botJobId) {
        this.botJobId.set(botJobId);
    }

    public String getUsedVars() {
        return usedVars.get();
    }

    public StringProperty usedVarsProperty() {
        return usedVars;
    }

    public void setUsedVars(String usedVars) {
        this.usedVars.set(usedVars);
    }
}
