package io.github.gergilcan.wirej.core.security;

import java.util.EnumSet;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RoleEntityPermissions {
    private String roleName;
    private Map<String, EnumSet<RbacPermissions>> permissions;

    public RoleEntityPermissions(String roleName) {
        RbacPermissionsRegistry.findByRole(roleName).ifPresent(r -> {
            this.roleName = r.getRoleName();
            this.permissions = r.getPermissions();
        });
    }
}
