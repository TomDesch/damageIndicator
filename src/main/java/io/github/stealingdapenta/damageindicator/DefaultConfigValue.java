package io.github.stealingdapenta.damageindicator;

public enum DefaultConfigValue {
    RELOAD_PERMISSION("damageindicator.reload"),
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
    HEALTH_BAR_PREFIX("HP "),
    HEALTH_BAR_PREFIX_COLOR("(0, 255, 0)"),
    HEALTH_BAR_PREFIX_STRIKETHROUGH("true"),
    HEALTH_BAR_PREFIX_BOLD("true"),
    HEALTH_BAR_PREFIX_UNDERLINED("false"),
    HEALTH_BAR_SUFFIX(" <<"),
    HEALTH_BAR_SUFFIX_COLOR("(0, 255, 0)"),
    HEALTH_BAR_SUFFIX_STRIKETHROUGH("true"),
    HEALTH_BAR_SUFFIX_BOLD("true"),
    HEALTH_BAR_SUFFIX_UNDERLINED("false"),
    HEALTH_BAR_ALIVE_COLOR("(0, 255, 0)"),
    HEALTH_BAR_ALIVE_SYMBOL("♥"),
    HEALTH_BAR_DEAD_COLOR("(100, 100, 100)"),
    HEALTH_BAR_DEAD_SYMBOL("♡"),
    HEALTH_BAR_STRIKETHROUGH("true"),
    HEALTH_BAR_BOLD("true"),
    HEALTH_BAR_UNDERLINED("false"),
    HOLOGRAM_POSITION("-0.3"),
    ENABLE_HOLOGRAPHIC_CUSTOM_NAMES("true"),
    HOLOGRAM_NAME_POSITION("0"),
    HOLOGRAM_FOLLOW_SPEED("3");

    private final String defaultValue;

    DefaultConfigValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }
}
