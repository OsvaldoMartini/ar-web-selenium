//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

public enum DeliveryStatus {
    ACK(3, "Sent and acknowleded."),
    NACK(4, "Sent and not acknowledged. Message could be resent."),
    ERR(8, "Fatal error. Do not resend message.");

    private int id;
    private String description;

    private DeliveryStatus(final int id, final String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() {
        return this.id;
    }

    public static DeliveryStatus toDeliveryStatus(final int id) {
        for (final DeliveryStatus status : values()) {
            if (status.getId() == id) {
                return status;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "(" + this.id + ") " + this.description;
    }
}
