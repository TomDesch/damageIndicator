package io.github.stealingdapenta.damageindicator.config;

/**
 * Enum representing plugin permission nodes.
 */
public enum Permission {

    RELOAD("damageindicator.reload"),
    AREA_REMOVE("damageindicator.arearemove");

    private final String node;

    Permission(String node) {
        this.node = node;
    }

    public String getNode() {
        return node;
    }

    @Override
    public String toString() {
        return node;
    }
}
