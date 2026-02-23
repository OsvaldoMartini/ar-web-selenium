//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ReasonCode {
    UNIDENTIFIED(-99, "Unidentified reason."),
    RESET_RECOM(-1, "Execution ok, but session reset is recommended."),
    OK(0, "Execution ok."),
    UNKNOWN(1, "Unknown internal error. Check log if available."),
    NULL_NETW(2, "Null network specified."),
    UNKNOWN_NETW(3, "Unknown network specified."),
    NULL_IN_DLV_TYPE(4, "Null or unknown delivery type."),
    NULL_MSG_CONTENT(5, "Null message content."),
    DBL_MSG(6, "Both msg_short and msg_long non null."),
    NULL_PARAM(7, "Null parameter."),
    IMMED_DELAY(8, "Delivery type with immediate processing and delay in future."),
    INVALID_PRIO(9, "Invalid priority. Valid range 0..9 or null."),
    WRONG_NETW(10, "Get message for out dlv type with do_put_extl = false and false netw."),
    TIMEOUT(11, "Timeout occurred while waiting for message."),
    PARSE(12, "Parse error."),
    STORE_INTL(13, "Error storing in internal format."),
    HDL(14, "Error handling."),
    PRCQ(15, "Error inserting into process queue."),
    NULL_MSG(16, "Null message"),
    NULL_PRTY(17, "Null properties"),
    NULL_OPT(18, "Null options"),
    IMMED_PRCQ(19, "Immediate handling and prcq specified."),
    HDL_LOCK(20, "Lock detected in msg in handler"),
    LOCK(21, "Locking error in msg in handler"),
    DEADLOCK(22, "Deadlock error in msg in handler"),
    ILLEG_MSG_STAT(23, "Illegal msg status."),
    UNKNOWN_MSG(24, "Unknonwn msg."),
    CUST_ACK_ERR(25, "Custom ack err."),
    UNKNOWN_TRANSFORM_ERR(26, "Unknown transformation err."),
    NEGATIVE_TIMEOUT(27, "Negative timeout."),
    UNKNOWN_MSG_TYPE(28, "Unknown message type."),
    AFTER_PARSE(29, "Error in after parse block."),
    DUPL(30, "Message is duplicate."),
    BULK_PRCQ(31, "Bulk handling and prcq specified."),
    BULK_NO_META_MSG(32, "OBSOLETE: Replaced by c_rc_bulk_no_on_dlv_block in same cases."),
    BULK_NO_ON_DLV_BLOCK(33, "Bulk handling and no 'on delivery' block defined in the network."),
    NO_INTL_PRCQ(34, "No internal storage and prcq specified.");

    private int id;
    private String description;

    private ReasonCode(final int id, final String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() {
        return this.id;
    }

    public static ReasonCode toReasonCode(final Integer id) {
        final Logger LOGGER = LoggerFactory.getLogger((Class) ReasonCode.class);
        if (id == null) {
            return null;
        }
        for (final ReasonCode reason : values()) {
            if (reason.getId() == id) {
                LOGGER.trace("Converted reason code: " + reason);
                return reason;
            }
        }
        LOGGER.warn("Could not convert reason code id: " + id + ". We use instead code: " + ReasonCode.UNIDENTIFIED);
        return ReasonCode.UNIDENTIFIED;
    }

    @Override
    public String toString() {
        return "(" + this.id + ") " + this.description;
    }
}
