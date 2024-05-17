package com.allinweb.ch.component.listCell;

import java.lang.reflect.InvocationTargetException;
import javafx.util.Callback;

public class ABRCellFactory<T, O> implements Callback<T, O> {

    private final Class<O> listCellImplementation;

    public ABRCellFactory(Class<O> listCellImplementation) {
        this.listCellImplementation = listCellImplementation;
    }

    @Override
    public O call(T param) {
        try {
            return listCellImplementation.getConstructor().newInstance();
        } catch (InstantiationException
                | NoSuchMethodException
                | InvocationTargetException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
