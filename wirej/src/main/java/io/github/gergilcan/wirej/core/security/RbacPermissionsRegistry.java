package io.github.gergilcan.wirej.core.security;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Loads RBAC permissions from a classpath resource named
 * {@code rbac-permissions.yaml}
 * and exposes them for static access across the application.
 */
public final class RbacPermissionsRegistry {
    private static final Logger log = LoggerFactory.getLogger(RbacPermissionsRegistry.class);
    private static final String RESOURCE_NAME = "rbac-permissions.yaml";

    // Cached, immutable snapshot
    private static final List<RoleEntityPermissions> ALL_INTERNAL;

    static {
        List<RoleEntityPermissions> loaded = loadFromClasspath();
        ALL_INTERNAL = Collections.unmodifiableList(loaded);
        if (ALL_INTERNAL.isEmpty()) {
            log.debug("No RBAC permissions loaded. Resource '{}' not found or empty.", RESOURCE_NAME);
        } else {
            log.debug("Loaded {} RBAC role definitions from '{}'.", ALL_INTERNAL.size(), RESOURCE_NAME);
        }
    }

    private RbacPermissionsRegistry() {
    }

    /**
     * Returns the cached array of role permissions.
     */
    public static RoleEntityPermissions[] getAll() {
        return ALL_INTERNAL.toArray(new RoleEntityPermissions[0]);
    }

    /** Find a role definition by its role name (case-sensitive match). */
    public static Optional<RoleEntityPermissions> findByRole(String roleName) {
        if (roleName == null)
            return Optional.empty();
        for (RoleEntityPermissions rep : ALL_INTERNAL) {
            if (roleName.equalsIgnoreCase(rep.getRoleName())) {
                return Optional.of(rep);
            }
        }
        return Optional.empty();
    }

    private static List<RoleEntityPermissions> loadFromClasspath() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(RESOURCE_NAME)) {
            if (is == null) {
                return List.of();
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            @SuppressWarnings("unchecked")
            Map<String, Object> rolesMap = mapper.readValue(is, Map.class);
            List<RoleEntityPermissions> result = new ArrayList<>();

            for (Map.Entry<String, Object> roleEntry : rolesMap.entrySet()) {
                String roleName = roleEntry.getKey();
                Map<String, EnumSet<RbacPermissions>> groupPermissions = new LinkedHashMap<>();
                mergeGroupPermissions(roleName, roleEntry.getValue(), groupPermissions);
                RoleEntityPermissions rep = new RoleEntityPermissions();
                rep.setRoleName(roleName);
                rep.setPermissions(groupPermissions);
                result.add(rep);
            }

            return result;
        } catch (Exception ex) {
            log.warn("Failed to load RBAC permissions from '{}': {}", RESOURCE_NAME, ex.getMessage());
            return List.of();
        }
    }

    private static void mergeGroupPermissions(String roleName, Object groups,
            Map<String, EnumSet<RbacPermissions>> groupPermissions) {
        if (groups instanceof Iterable<?> iterable) {
            for (Object item : iterable)
                parseGroupEntry(item, groupPermissions);
        } else if (groups instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e2 : m.entrySet())
                addGroupPermission(groupPermissions, e2.getKey(), e2.getValue());
        } else if (groups != null) {
            log.warn("Unexpected YAML structure for role '{}': {}", roleName, groups.getClass());
        }
    }

    private static void parseGroupEntry(Object item, Map<String, EnumSet<RbacPermissions>> target) {
        if (item instanceof Map<?, ?> m2) {
            for (Map.Entry<?, ?> e2 : m2.entrySet())
                addGroupPermission(target, e2.getKey(), e2.getValue());
        }
    }

    private static void addGroupPermission(Map<String, EnumSet<RbacPermissions>> target, Object key, Object val) {
        String group = String.valueOf(key);
        EnumSet<RbacPermissions> set = parsePermissionSet(String.valueOf(val));
        if (set != null && !set.isEmpty())
            target.put(group, set);
    }

    /** Parses comma-separated tokens into an EnumSet of RbacPermissions. */
    private static EnumSet<RbacPermissions> parsePermissionSet(String value) {
        if (value == null || value.isBlank())
            return EnumSet.noneOf(RbacPermissions.class);
        EnumSet<RbacPermissions> set = EnumSet.noneOf(RbacPermissions.class);
        for (String token : value.split(",")) {
            String t = token.trim();
            if (t.isEmpty())
                continue;
            try {
                set.add(RbacPermissions.valueOf(t.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown RBAC permission token '{}' in '{}'. Skipping.", t, value);
            }
        }
        return set;
    }
}
