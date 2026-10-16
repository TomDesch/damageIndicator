package io.github.stealingdapenta.damageindicator.config;

/**
 * A configuration file could not be read or parsed; the previously active configuration remains in use.
 */
public final class ConfigurationLoadException extends RuntimeException {

    public ConfigurationLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
