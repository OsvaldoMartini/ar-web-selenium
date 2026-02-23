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

public class TMsgGetOptObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_MSG_GET_OPT_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TMsgGetOptObj _TMsgGetOptObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TMsgGetOptObj._TMsgGetOptObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[5], TMsgGetOptObj._sqlType, TMsgGetOptObj._factory);
        }
    }

    public TMsgGetOptObj() {
        this._init_struct(true);
    }

    public TMsgGetOptObj(
            final String netw,
            final String extlMsgNr,
            final Integer timeout,
            final String forceClob,
            final Integer errBehavior)
            throws SQLException {
        this._init_struct(true);
        this.setNetw(netw);
        this.setExtlMsgNr(extlMsgNr);
        this.setTimeout(timeout);
        this.setForceClob(forceClob);
        this.setErrBehavior(errBehavior);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_MSG_GET_OPT_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TMsgGetOptObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TMsgGetOptObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TMsgGetOptObj._sqlType, TMsgGetOptObj._factory);
        return (ORAData) o;
    }

    public String getNetw() throws SQLException {
        return (String) this._struct.getAttribute(0);
    }

    public void setNetw(final String netw) throws SQLException {
        this._struct.setAttribute(0, (Object) netw);
    }

    public String getExtlMsgNr() throws SQLException {
        return (String) this._struct.getAttribute(1);
    }

    public void setExtlMsgNr(final String extlMsgNr) throws SQLException {
        this._struct.setAttribute(1, (Object) extlMsgNr);
    }

    public Integer getTimeout() throws SQLException {
        return (Integer) this._struct.getAttribute(2);
    }

    public void setTimeout(final Integer timeout) throws SQLException {
        this._struct.setAttribute(2, (Object) timeout);
    }

    public String getForceClob() throws SQLException {
        return (String) this._struct.getAttribute(3);
    }

    public void setForceClob(final String forceClob) throws SQLException {
        this._struct.setAttribute(3, (Object) forceClob);
    }

    public Integer getErrBehavior() throws SQLException {
        return (Integer) this._struct.getAttribute(4);
    }

    public void setErrBehavior(final Integer errBehavior) throws SQLException {
        this._struct.setAttribute(4, (Object) errBehavior);
    }

    @Override
    public String toString() {
        try {
            return "TMsgGetOptObj{ Netw=" + this.getNetw() + " ExtlMsgNr=" + this.getExtlMsgNr() + " Timeout="
                    + this.getTimeout() + " ForceClob=" + this.getForceClob() + " ErrBehavior=" + this.getErrBehavior()
                    + '}';
        } catch (SQLException e) {
            return "TMsgGetOptObj: SQLException happened during read of attributes";
        }
    }

    static {
        TMsgGetOptObj._sqlType = new int[] {12, 12, 4, 12, 4};
        TMsgGetOptObj._factory = new ORADataFactory[5];
        _TMsgGetOptObjFactory = new TMsgGetOptObj();
    }
}
