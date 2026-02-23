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

public class TMsgResObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_MSG_RES_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TMsgResObj _TMsgResObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TMsgResObj._TMsgResObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[3], TMsgResObj._sqlType, TMsgResObj._factory);
        }
    }

    public TMsgResObj() {
        this._init_struct(true);
    }

    public TMsgResObj(final Integer completion, final Integer reason, final Integer logId) throws SQLException {
        this._init_struct(true);
        this.setCompletion(completion);
        this.setReason(reason);
        this.setLogId(logId);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_MSG_RES_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TMsgResObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TMsgResObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TMsgResObj._sqlType, TMsgResObj._factory);
        return (ORAData) o;
    }

    public Integer getCompletion() throws SQLException {
        return (Integer) this._struct.getAttribute(0);
    }

    public void setCompletion(final Integer completion) throws SQLException {
        this._struct.setAttribute(0, (Object) completion);
    }

    public Integer getReason() throws SQLException {
        return (Integer) this._struct.getAttribute(1);
    }

    public void setReason(final Integer reason) throws SQLException {
        this._struct.setAttribute(1, (Object) reason);
    }

    public Integer getLogId() throws SQLException {
        return (Integer) this._struct.getAttribute(2);
    }

    public void setLogId(final Integer logId) throws SQLException {
        this._struct.setAttribute(2, (Object) logId);
    }

    static {
        TMsgResObj._sqlType = new int[] {4, 4, 4};
        TMsgResObj._factory = new ORADataFactory[3];
        _TMsgResObjFactory = new TMsgResObj();
    }
}
