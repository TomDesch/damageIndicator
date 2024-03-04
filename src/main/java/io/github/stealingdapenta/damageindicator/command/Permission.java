package io.github.stealingdapenta.damageindicator.command;

public enum Permission {
    RELOAD("damageindicator.reload");

    private final String node;

    Permission(String node) {
        this.node = node;
    }

    public String getNode() {
        return node;
    }
}
