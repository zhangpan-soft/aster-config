package io.asterconfig.core.spi;

import io.asterconfig.core.model.UserContext;

import java.util.Set;

public interface UserProvider {

    UserContext currentUser();

    static UserProvider system() {
        return () -> new UserContext("system", Set.of("*"));
    }
}
