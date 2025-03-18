package com.allinweb.ch.component.listCell;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import javafx.util.Callback;

public class ARCellFactory<T, O> implements Callback<T, O> {

    private final Class<O> listCellImplementation;
    private final Object[] constructorArgs;

    public ARCellFactory(Class<O> listCellImplementation, Object... constructorArgs) {
        this.listCellImplementation = listCellImplementation;
        this.constructorArgs = constructorArgs;
    }

    @Override
    public O call(T param) {
        try {
            for (Constructor<?> constructor : listCellImplementation.getConstructors()) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (matchesConstructor(paramTypes, constructorArgs)) {
                    return (O) constructor.newInstance(constructorArgs);
                }
            }
            throw new NoSuchMethodException("No suitable constructor found for " + listCellImplementation.getName());
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException("Error instantiating cell", e);
        }
    }

    // Helper method to match constructor parameters
    private boolean matchesConstructor(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < paramTypes.length; i++) {
            if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }
}
