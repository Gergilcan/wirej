package io.github.gergilcan.wirej.database;

import java.lang.reflect.Field;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.github.gergilcan.wirej.exceptions.WireJException;

public final class ParameterBinder {
    private ParameterBinder() {
    }

    public static void bindObjectFields(Object item, DatabaseStatement<?> databaseStatement) {
        for (var field : item.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                databaseStatement.setParameter(resolveFieldName(field), field.get(item));
            } catch (IllegalAccessException e) {
                throw new WireJException("Could not access field: " + field.getName(), e);
            }
        }
    }

    private static String resolveFieldName(Field field) {
        JsonAlias alias = field.getAnnotation(JsonAlias.class);
        if (alias != null && alias.value().length > 0) {
            return alias.value()[0];
        }
        return field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
