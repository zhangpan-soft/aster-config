package io.asterconfig.core.model;

import java.util.Set;

public record UserContext(String userId, Set<String> permissions) {

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
