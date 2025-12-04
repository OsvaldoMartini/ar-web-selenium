package com.allinweb.ch.component.listCell;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Generic reflection-based factory for creating cell renderers, editors,
 * or any object type in Swing.
 *
 * Replaces the old JavaFX Callback<T,O>.
 *
 * @param <T> Input parameter type (ignored but kept for compatibility)
 * @param <O> Output instance type
 */
public class ARCellFactory<T, O> {

    private final Class<O> implementationClass;
    private final Object[] constructorArgs;

    public ARCellFactory(Class<O> implementationClass, Object... constructorArgs) {
        this.implementationClass = implementationClass;
        this.constructorArgs = constructorArgs;
    }

    /**
     * Creates a new instance of O using reflection.
     *
     * @param param (unused) — kept only to match old signature
     */
    public O create(T param) {
        try {
            for (Constructor<?> constructor : implementationClass.getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (matchesConstructor(parameterTypes, constructorArgs)) {
                    return (O) constructor.newInstance(constructorArgs);
                }
            }
            throw new NoSuchMethodException("No suitable constructor found for " + implementationClass.getName());
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException("Error instantiating object", e);
        }
    }

    /**
     * Check if constructor parameter types match provided arguments.
     */
    private boolean matchesConstructor(Class<?>[] paramTypes, Object[] args) {
        if (paramTypes.length != args.length) {
            return false;
        }

        for (int i = 0; i < paramTypes.length; i++) {
            if (args[i] == null) {
                // null can match anything
                continue;
            }
            if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }
}
