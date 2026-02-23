//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import oracle.jpub.runtime.MutableStruct;
import oracle.sql.Datum;
import oracle.sql.ORAData;
import oracle.sql.ORADataFactory;
import oracle.sql.STRUCT;

public class TMsgPrtyObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_MSG_PRTY_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TMsgPrtyObj _TMsgPrtyObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TMsgPrtyObj._TMsgPrtyObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[10], TMsgPrtyObj._sqlType, TMsgPrtyObj._factory);
        }
    }

    public TMsgPrtyObj() {
        this._init_struct(true);
    }

    public TMsgPrtyObj(
            final BigDecimal msgId,
            final String netw,
            final String msgType,
            final String extlMsgNr,
            final String addr,
            final Integer prio,
            final Timestamp timestamp,
            final Timestamp delayDate,
            final Timestamp expirDate,
            final Integer attemptCnt)
            throws SQLException {
        this._init_struct(true);
        this.setMsgId(msgId);
        this.setNetw(netw);
        this.setMsgType(msgType);
        this.setExtlMsgNr(extlMsgNr);
        this.setAddr(addr);
        this.setPrio(prio);
        this.setTimestamp(timestamp);
        this.setDelayDate(delayDate);
        this.setExpirDate(expirDate);
        this.setAttemptCnt(attemptCnt);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_MSG_PRTY_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TMsgPrtyObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TMsgPrtyObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TMsgPrtyObj._sqlType, TMsgPrtyObj._factory);
        return (ORAData) o;
    }

    public BigDecimal getMsgId() throws SQLException {
        return (BigDecimal) this._struct.getAttribute(0);
    }

    public void setMsgId(final BigDecimal msgId) throws SQLException {
        this._struct.setAttribute(0, (Object) msgId);
    }

    public String getNetw() throws SQLException {
        return (String) this._struct.getAttribute(1);
    }

    public void setNetw(final String netw) throws SQLException {
        this._struct.setAttribute(1, (Object) netw);
    }

    public String getMsgType() throws SQLException {
        return (String) this._struct.getAttribute(2);
    }

    public void setMsgType(final String msgType) throws SQLException {
        this._struct.setAttribute(2, (Object) msgType);
    }

    public String getExtlMsgNr() throws SQLException {
        return (String) this._struct.getAttribute(3);
    }

    public void setExtlMsgNr(final String extlMsgNr) throws SQLException {
        this._struct.setAttribute(3, (Object) extlMsgNr);
    }

    public String getAddr() throws SQLException {
        return (String) this._struct.getAttribute(4);
    }

    public void setAddr(final String addr) throws SQLException {
        this._struct.setAttribute(4, (Object) addr);
    }

    public Integer getPrio() throws SQLException {
        return (Integer) this._struct.getAttribute(5);
    }

    public void setPrio(final Integer prio) throws SQLException {
        this._struct.setAttribute(5, (Object) prio);
    }

    public Timestamp getTimestamp() throws SQLException {
        return (Timestamp) this._struct.getAttribute(6);
    }

    public void setTimestamp(final Timestamp timestamp) throws SQLException {
        this._struct.setAttribute(6, (Object) timestamp);
    }

    public Timestamp getDelayDate() throws SQLException {
        return (Timestamp) this._struct.getAttribute(7);
    }

    public void setDelayDate(final Timestamp delayDate) throws SQLException {
        this._struct.setAttribute(7, (Object) delayDate);
    }

    public Timestamp getExpirDate() throws SQLException {
        return (Timestamp) this._struct.getAttribute(8);
    }

    public void setExpirDate(final Timestamp expirDate) throws SQLException {
        this._struct.setAttribute(8, (Object) expirDate);
    }

    public Integer getAttemptCnt() throws SQLException {
        return (Integer) this._struct.getAttribute(9);
    }

    public void setAttemptCnt(final Integer attemptCnt) throws SQLException {
        this._struct.setAttribute(9, (Object) attemptCnt);
    }

    static {
        TMsgPrtyObj._sqlType = new int[] {2, 12, 12, 12, 12, 4, 91, 91, 91, 4};
        TMsgPrtyObj._factory = new ORADataFactory[10];
        _TMsgPrtyObjFactory = new TMsgPrtyObj();
    }
}
