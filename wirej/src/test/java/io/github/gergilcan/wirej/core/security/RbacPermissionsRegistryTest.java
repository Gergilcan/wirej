package io.github.gergilcan.wirej.core.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class RbacPermissionsRegistryTest {

    @Test
    void loadsRolesFromYamlOnClasspath() {
        RoleEntityPermissions[] all = RbacPermissionsRegistry.getAll();
        // From src/test/resources/rbac-permissions.yaml there are 4 roles
        assertTrue(all.length >= 4, "Expected at least 4 roles from test YAML");

        var coach = RbacPermissionsRegistry.findByRole("Coach").orElse(null);
        assertNotNull(coach, "Coach role should be present");
        var perms = coach.getPermissions();
        assertTrue(perms.containsKey("Biometrics"), "Biometrics group should exist for Coach");
        EnumSet<RbacPermissions> bio = perms.get("Biometrics");
        assertTrue(bio.contains(RbacPermissions.READ));
        assertTrue(bio.contains(RbacPermissions.WRITE));
        assertFalse(bio.contains(RbacPermissions.DELETE), "Coach should not have DELETE on Biometrics in test YAML");
    }
}
