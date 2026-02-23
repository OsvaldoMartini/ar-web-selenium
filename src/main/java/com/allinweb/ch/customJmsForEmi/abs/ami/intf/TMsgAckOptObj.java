//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

import java.sql.Connection;
import java.sql.SQLException;
import oracle.jpub.runtime.MutableStruct;
import oracle.sql.Datum;
import oracle.sql.ORAData;
import oracle.sql.ORADataFactory;
import oracle.sql.STRUCT;

public class TMsgAckOptObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_MSG_ACK_OPT_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TMsgAckOptObj _TMsgAckOptObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TMsgAckOptObj._TMsgAckOptObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[3], TMsgAckOptObj._sqlType, TMsgAckOptObj._factory);
        }
    }

    public TMsgAckOptObj() {
        this._init_struct(true);
    }

    public TMsgAckOptObj(final String omitCustAck, final String ignCustAckErr, final Integer errBehavior)
            throws SQLException {
        this._init_struct(true);
        this.setOmitCustAck(omitCustAck);
        this.setIgnCustAckErr(ignCustAckErr);
        this.setErrBehavior(errBehavior);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_MSG_ACK_OPT_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TMsgAckOptObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TMsgAckOptObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TMsgAckOptObj._sqlType, TMsgAckOptObj._factory);
        return (ORAData) o;
    }

    public String getOmitCustAck() throws SQLException {
        return (String) this._struct.getAttribute(0);
    }

    public void setOmitCustAck(final String omitCustAck) throws SQLException {
        this._struct.setAttribute(0, (Object) omitCustAck);
    }

    public String getIgnCustAckErr() throws SQLException {
        return (String) this._struct.getAttribute(1);
    }

    public void setIgnCustAckErr(final String ignCustAckErr) throws SQLException {
        this._struct.setAttribute(1, (Object) ignCustAckErr);
    }

    public Integer getErrBehavior() throws SQLException {
        return (Integer) this._struct.getAttribute(2);
    }

    public void setErrBehavior(final Integer errBehavior) throws SQLException {
        this._struct.setAttribute(2, (Object) errBehavior);
    }

    static {
        TMsgAckOptObj._sqlType = new int[] {12, 12, 4};
        TMsgAckOptObj._factory = new ORADataFactory[3];
        _TMsgAckOptObjFactory = new TMsgAckOptObj();
    }
}
