package io.github.gergilcan.wirej.core.security;

import java.util.EnumSet;
import java.util.Set;

public enum RbacPermissions {
    READ(0),
    WRITE(1),
    DELETE(2);

    private final int value;

    RbacPermissions(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static Set<RbacPermissions> all() {
        return EnumSet.allOf(RbacPermissions.class);
    }

    boolean includes(RbacPermissions value) {
        return this.value == value.getValue();
    }

    boolean includes(RbacPermissions... values) {
        for (RbacPermissions permission : values) {
            if (this.value == permission.getValue()) {
                return true;
            }
        }
        return false;
    }
}