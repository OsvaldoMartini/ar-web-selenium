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
            if (constructorArgs.length == 0) {
                // No arguments, use default constructor
                return listCellImplementation.getConstructor().newInstance();
            } else {
                // Find the correct constructor
                Class<?>[] argTypes = new Class<?>[constructorArgs.length];
                for (int i = 0; i < constructorArgs.length; i++) {
                    argTypes[i] = constructorArgs[i].getClass();
                }

                Constructor<O> constructor = listCellImplementation.getConstructor(argTypes);
                return constructor.newInstance(constructorArgs);
            }
        } catch (InstantiationException
                | NoSuchMethodException
                | InvocationTargetException
                | IllegalAccessException error) {
            throw new RuntimeException("Error instantiating cell", error);
        }
    }
}
