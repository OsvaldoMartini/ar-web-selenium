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

public class TMsgPutOptObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_MSG_PUT_OPT_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TMsgPutOptObj _TMsgPutOptObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TMsgPutOptObj._TMsgPutOptObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[1], TMsgPutOptObj._sqlType, TMsgPutOptObj._factory);
        }
    }

    public TMsgPutOptObj() {
        this._init_struct(true);
    }

    public TMsgPutOptObj(final Integer errBehavior) throws SQLException {
        this._init_struct(true);
        this.setErrBehavior(errBehavior);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_MSG_PUT_OPT_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TMsgPutOptObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TMsgPutOptObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TMsgPutOptObj._sqlType, TMsgPutOptObj._factory);
        return (ORAData) o;
    }

    public Integer getErrBehavior() throws SQLException {
        return (Integer) this._struct.getAttribute(0);
    }

    public void setErrBehavior(final Integer errBehavior) throws SQLException {
        this._struct.setAttribute(0, (Object) errBehavior);
    }

    static {
        TMsgPutOptObj._sqlType = new int[] {4};
        TMsgPutOptObj._factory = new ORADataFactory[1];
        _TMsgPutOptObjFactory = new TMsgPutOptObj();
    }
}
