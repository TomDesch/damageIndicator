package io.github.stealingdapenta.damageindicator;

public enum DefaultConfigValue {
    ENABLE_DAMAGE_INDICATOR("true"),
    MAGIC("(95, 10, 95)"),
    MELEE("(100, 100, 100)"),
    POISON("(0, 100, 20)"),
    FIRE("(200, 90, 25)"),
    RANGED("(130, 70, 0)"),
    OTHER("(130, 130, 30)"),
    ENABLE_HEALTH_BAR("true"),
    ENABLE_HOLOGRAM_HEALTH_BAR("true"),
    HEALTH_BAR_DISPLAY_DURATION("5"),
    HEALTH_BAR_ALIVE_COLOR("(0, 255, 0)"),
    HEALTH_BAR_ALIVE_SYMBOL("♥"),
    HEALTH_BAR_DEAD_COLOR("(100, 100, 100)"),
    HEALTH_BAR_DEAD_SYMBOL("♡"),
    HEALTH_BAR_STRIKETHROUGH("true"),
    HEALTH_BAR_BOLD("true"),
    HEALTH_BAR_UNDERLINED("false"),
    HOLOGRAM_POSITION("-0.3");

    private final String defaultValue;

    DefaultConfigValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
