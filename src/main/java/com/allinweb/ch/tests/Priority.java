package com.allinweb.ch.tests;

import java.util.List;

class Priority {
    private List<String> name;
    private String priorityType;
    private int priorityNumber;

    public Priority(List<String> name, String priorityType, int priorityNumber) {
        this.name = name;
        this.priorityType = priorityType;
        this.priorityNumber = priorityNumber;
    }

    public List<String> getName() {
        return name;
    }

    public String getPriorityType() {
        return priorityType;
    }

    public int getPriorityNumber() {
        return priorityNumber;
    }
}
