package com.allinweb.ch.component.model;

public class PayloadJson {
    private int id;
    private String name;
    private int data;

    public PayloadJson(int botJobId, String name, int data) {
        this.id = botJobId;
        this.name = name;
        this.data = data;
    }
}
