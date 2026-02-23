//
// Decompiled by Procyon v0.5.36
//

package com.allinweb.ch.customJmsForEmi.abs.base.intf;

import java.sql.Connection;
import java.sql.SQLException;
import oracle.jpub.runtime.MutableArray;
import oracle.sql.ARRAY;
import oracle.sql.ArrayDescriptor;
import oracle.sql.Datum;
import oracle.sql.ORAData;
import oracle.sql.ORADataFactory;

public class TPrtyObjVarray256 implements ORAData, ORADataFactory {
    public static final String _SQL_NAME = "K.T_PRTY_OBJ_VARRAY256";
    public static final int _SQL_TYPECODE = 2003;
    MutableArray _array;
    private static final TPrtyObjVarray256 _TPrtyObjVarray256Factory;

    public static ORADataFactory getORADataFactory() {
        return (ORADataFactory) TPrtyObjVarray256._TPrtyObjVarray256Factory;
    }

    public TPrtyObjVarray256() {
        this(null);
    }

    public TPrtyObjVarray256(final TPrtyObj[] a) {
        this._array = new MutableArray(2002, (Object[]) a, TPrtyObj.getORADataFactory());
    }

    public Datum toDatum(final Connection c) throws SQLException {
        return this._array.toDatum(c, "K.T_PRTY_OBJ_VARRAY256");
    }

    public ORAData create(final Datum d, final int sqlType) throws SQLException {
        if (d == null) {
            return null;
        }
        final TPrtyObjVarray256 a = new TPrtyObjVarray256();
        a._array = new MutableArray(2002, (ARRAY) d, TPrtyObj.getORADataFactory());
        return (ORAData) a;
    }

    public int length() throws SQLException {
        return this._array.length();
    }

    public int getBaseType() throws SQLException {
        return this._array.getBaseType();
    }

    public String getBaseTypeName() throws SQLException {
        return this._array.getBaseTypeName();
    }

    public ArrayDescriptor getDescriptor() throws SQLException {
        return this._array.getDescriptor();
    }

    public TPrtyObj[] getArray() throws SQLException {
        return (TPrtyObj[]) this._array.getObjectArray((Object[]) new TPrtyObj[this._array.length()]);
    }

    public TPrtyObj[] getArray(final long index, final int count) throws SQLException {
        return (TPrtyObj[])
                this._array.getObjectArray(index, (Object[]) new TPrtyObj[this._array.sliceLength(index, count)]);
    }

    public void setArray(final TPrtyObj[] a) throws SQLException {
        this._array.setObjectArray((Object[]) a);
    }

    public void setArray(final TPrtyObj[] a, final long index) throws SQLException {
        this._array.setObjectArray((Object[]) a, index);
    }

    public TPrtyObj getElement(final long index) throws SQLException {
        return (TPrtyObj) this._array.getObjectElement(index);
    }

    public void setElement(final TPrtyObj a, final long index) throws SQLException {
        this._array.setObjectElement((Object) a, index);
    }

    static {
        _TPrtyObjVarray256Factory = new TPrtyObjVarray256();
    }
}
