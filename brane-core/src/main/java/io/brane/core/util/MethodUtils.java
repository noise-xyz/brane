package io.brane.core.util;

import java.lang.reflect.Method;

/**
 * Utility methods for working with Java reflection Method objects.
 */
public final class MethodUtils {

    private MethodUtils() {
    }

    /**
     * Checks if a method is declared by {@link Object} class.
     *
     * <p>Methods declared by Object (toString, hashCode, equals, etc.) are
     * typically excluded from ABI binding and proxy handling since they are
     * Java-specific and not part of the contract interface.
     *
     * @param method the method to check
     * @return true if the method is declared by Object class
     */
    public static boolean isObjectMethod(final Method method) {
        return method.getDeclaringClass() == Object.class;
    }
}
