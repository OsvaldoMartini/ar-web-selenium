//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.ami.intf;

import com.allinweb.ch.customJmsForEmi.abs.base.intf.TPrtyObjVarray256;
import java.sql.Connection;
import java.sql.SQLException;
import oracle.jpub.runtime.MutableStruct;
import oracle.sql.CLOB;
import oracle.sql.Datum;
import oracle.sql.ORAData;
import oracle.sql.ORADataFactory;
import oracle.sql.STRUCT;

public class TMsgObj implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_MSG_OBJ";
    public static final int _SQL_TYPECODE = 2002;
    protected MutableStruct _struct;
    protected static int[] _sqlType;
    protected static ORADataFactory[] _factory;
    protected static final TMsgObj _TMsgObjFactory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TMsgObj._TMsgObjFactory;
    }

    protected void _init_struct(final boolean init) {
        if (init) {
            this._struct = new MutableStruct(new Object[3], TMsgObj._sqlType, TMsgObj._factory);
        }
    }

    public TMsgObj() {
        this._init_struct(true);
    }

    public TMsgObj(final String msgShort, final CLOB msgLong, final TPrtyObjVarray256 prtyList) throws SQLException {
        this._init_struct(true);
        this.setMsgShort(msgShort);
        this.setMsgLong(msgLong);
        this.setPrtyList(prtyList);
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._struct.toDatum(c, "K.T_MSG_OBJ");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        return this.create(null, d, sqlType);
    }

    protected ORAData create(TMsgObj o, final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        if (o == null) {
            o = new TMsgObj();
        }
        o._struct = new MutableStruct((STRUCT) d, TMsgObj._sqlType, TMsgObj._factory);
        return (ORAData) o;
    }

    public String getMsgShort() throws SQLException {
        return (String) this._struct.getAttribute(0);
    }

    public void setMsgShort(final String msgShort) throws SQLException {
        this._struct.setAttribute(0, (Object) msgShort);
    }

    public CLOB getMsgLong() throws SQLException {
        return (CLOB) this._struct.getOracleAttribute(1);
    }

    public void setMsgLong(final CLOB msgLong) throws SQLException {
        this._struct.setOracleAttribute(1, (Object) msgLong);
    }

    public TPrtyObjVarray256 getPrtyList() throws SQLException {
        return (TPrtyObjVarray256) this._struct.getAttribute(2);
    }

    public void setPrtyList(final TPrtyObjVarray256 prtyList) throws SQLException {
        this._struct.setAttribute(2, (Object) prtyList);
    }

    static {
        TMsgObj._sqlType = new int[] {12, 2005, 2003};
        (TMsgObj._factory = new ORADataFactory[3])[2] = TPrtyObjVarray256.getORADataFactory();
        _TMsgObjFactory = new TMsgObj();
    }
}
