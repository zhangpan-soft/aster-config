package io.asterconfig.core.spi;

import io.asterconfig.core.model.ConfigScope;

public interface EmbedTokenValidator {

    boolean validate(String token, ConfigScope scope, String action);

    static EmbedTokenValidator permitAll() {
        return (token, scope, action) -> true;
    }

    static EmbedTokenValidator denyAll() {
        return (token, scope, action) -> false;
    }
}
