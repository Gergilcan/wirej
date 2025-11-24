package io.github.gergilcan.wirej.core;

import java.sql.Date;
import java.time.temporal.Temporal;

public class ClassHelpers {
    private ClassHelpers() {
    }

    public static boolean isCustomObject(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return false;
        }

        // Common simple types
        if (Number.class.isAssignableFrom(clazz)
                || CharSequence.class.isAssignableFrom(clazz) // String, StringBuilder, etc.
                || Boolean.class.equals(clazz)
                || Character.class.equals(clazz)) {
            return false;
        }

        // Date/time classes
        if (Date.class.isAssignableFrom(clazz)
                || Temporal.class.isAssignableFrom(clazz) // java.time.*
                || java.sql.Date.class.isAssignableFrom(clazz)
                || java.sql.Timestamp.class.isAssignableFrom(clazz)
                || java.util.Date.class.isAssignableFrom(clazz)
                || java.time.Instant.class.isAssignableFrom(clazz)
                || java.time.LocalDate.class.isAssignableFrom(clazz)
                || java.time.LocalDateTime.class.isAssignableFrom(clazz)) {
            return false;
        }

        // Arrays and enums are not custom objects
        if (clazz.isArray() || clazz.isEnum()) {
            return false;
        }

        // At this point, we assume it's a "custom object"
        return true;
    }
}
