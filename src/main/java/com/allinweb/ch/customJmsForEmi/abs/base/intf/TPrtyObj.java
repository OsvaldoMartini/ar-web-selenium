//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.base.intf;

import java.sql.Connection;
import java.sql.SQLException;
import oracle.jpub.runtime.MutableStruct;
import oracle.sql.Datum;
import oracle.sql.ORAData;
import oracle.sql.ORADataFactory;
import oracle.sql.STRUCT;

public class TPrtyObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_PRTY_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TPrtyObj _TPrtyObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TPrtyObj._TPrtyObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[2], TPrtyObj._sqlType, TPrtyObj._factory);
        }
    }

    public TPrtyObj() {
        this._init_struct(true);
    }

    public TPrtyObj(final String name, final String val) throws SQLException {
        this._init_struct(true);
        this.setName(name);
        this.setVal(val);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_PRTY_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TPrtyObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TPrtyObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TPrtyObj._sqlType, TPrtyObj._factory);
        return (ORAData) o;
    }

    public String getName() throws SQLException {
        return (String) this._struct.getAttribute(0);
    }

    public void setName(final String name) throws SQLException {
        this._struct.setAttribute(0, (Object) name);
    }

    public String getVal() throws SQLException {
        return (String) this._struct.getAttribute(1);
    }

    public void setVal(final String val) throws SQLException {
        this._struct.setAttribute(1, (Object) val);
    }

    static {
        TPrtyObj._sqlType = new int[] {12, 12};
        TPrtyObj._factory = new ORADataFactory[2];
        _TPrtyObjFactory = new TPrtyObj();
    }
}
