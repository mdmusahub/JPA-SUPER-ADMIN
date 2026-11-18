package io.github.mdmusahub.jpaadmin.service;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

/**
 * Reads and writes one attribute of a host entity by its JPA name. The host's own getter and setter are used when they exist
 * (so a setter that hashes or normalises a value still does), else the field itself.
 *
 * <p>The attribute name always comes from the metadata, never from a request (SEC-2). No exception text built here holds a
 * value: a failure names the attribute only (SEC-1).
 *
 * <p>Package-private: it is a rule, not API.
 */
final class Members {

    private Members() {
    }

    /** Whether {@code name} of {@code type} can be written: it has a setter that takes one argument, or a non-final field. */
    static boolean writable(Class<?> type, String name) {
        return setter(type, name) != null || field(type, name) != null;
    }

    /** The annotation on the attribute's field or its getter, or {@code null}. */
    static <A extends Annotation> A annotation(Class<?> type, String name, Class<A> annotation) {
        Field field = field(type, name);
        if (field != null && field.isAnnotationPresent(annotation)) {
            return field.getAnnotation(annotation);
        }
        Method getter = getter(type, name);
        return getter != null ? getter.getAnnotation(annotation) : null;
    }

    /** The current value of the attribute. */
    static Object get(Object target, String name) {
        Method getter = getter(target.getClass(), name);
        try {
            if (getter != null) {
                getter.setAccessible(true);
                return getter.invoke(target);
            }
            Field field = field(target.getClass(), name);
            if (field == null) {
                throw new IllegalStateException("The attribute '" + name + "' cannot be read.");
            }
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("The attribute '" + name + "' cannot be read.");
        }
    }

    /** Writes the attribute. A {@code null} for a primitive is refused by the caller before it gets here. */
    static void set(Object target, String name, Object value) {
        Method setter = setter(target.getClass(), name, value);
        try {
            if (setter != null) {
                setter.setAccessible(true);
                setter.invoke(target, value);
                return;
            }
            Field field = field(target.getClass(), name);
            if (field == null) {
                throw new IllegalStateException("The attribute '" + name + "' cannot be written.");
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException failure) {
            throw new IllegalStateException("The attribute '" + name + "' cannot be written.");
        }
    }

    // ---- lookup --------------------------------------------------------------------------------------------------

    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                return Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()) ? null : field;
            } catch (NoSuchFieldException ignored) {
                // Try the superclass: a mapped superclass holds some of the attributes.
            }
        }
        return null;
    }

    private static Method getter(Class<?> type, String name) {
        String capitalized = capitalize(name);
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (String prefix : new String[] {"get", "is"}) {
                try {
                    Method method = current.getDeclaredMethod(prefix + capitalized);
                    if (!Modifier.isStatic(method.getModifiers())) {
                        return method;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Not this one.
                }
            }
        }
        return null;
    }

    /** Any one-argument setter of that name: enough to know the attribute can be written. */
    private static Method setter(Class<?> type, String name) {
        return setter(type, name, null);
    }

    private static Method setter(Class<?> type, String name, Object value) {
        String wanted = "set" + capitalize(name);
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(wanted) && method.getParameterCount() == 1
                        && !Modifier.isStatic(method.getModifiers()) && accepts(method.getParameterTypes()[0], value)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean accepts(Class<?> parameter, Object value) {
        if (value == null) {
            return !parameter.isPrimitive();
        }
        Class<?> boxed = box(parameter);
        return boxed.isInstance(value);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return Character.class;
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
