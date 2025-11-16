package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;

public enum ConfigKeys {

    ENABLE_DAMAGE_INDICATOR("true"),
    MAGIC("&(95,10,95)"),
    POISON("&(0,100,20)"),
    FIRE("&(200,90,25)"),
    FALL_DAMAGE("&(205,92,92)"),
    KILL("&(255,215,0)"),
    WORLD_BORDER("&(128,0,128)"),
    CONTACT("&(205,92,92)"),
    ENTITY_ATTACK("&(100,100,100)"),
    ENTITY_SWEEP_ATTACK("&(100,100,100)"),
    PROJECTILE("&(130,70,0)"),
    SUFFOCATION("&(30,144,255)"),
    FALL("&(0,255,0)"),
    FIRE_TICK("&(200,90,25)"),
    MELTING("&(106,90,205)"),
    LAVA("&(200,90,25)"),
    DROWNING("&(0,255,255)"),
    BLOCK_EXPLOSION("&(255,165,0)"),
    ENTITY_EXPLOSION("&(255,69,0)"),
    VOID("&(128,0,0)"),
    LIGHTNING("&(255,255,0)"),
    SUICIDE("&(255,0,0)"),
    STARVATION("&(220,20,60)"),
    WITHER("&(139,0,139)"),
    FALLING_BLOCK("&(128,128,0)"),
    THORNS("&(100,100,100)"),
    DRAGON_BREATH("&(255,0,255)"),
    CUSTOM("&(75,0,130)"),
    FLY_INTO_WALL("&(128,128,128)"),
    HOT_FLOOR("&(200,90,25)"),
    CRAMMING("&(0,128,0)"),
    DRY_OUT("&(244,164,96)"),
    FREEZE("&(173,216,230)"),
    SONIC_BOOM("&(255,215,0)"),
    OTHER("&(130,130,30)"),
    ENABLE_HEALTH_BAR("true"),
    ENABLE_HOLOGRAM_HEALTH_BAR("true"),
    HEALTH_BAR_DISPLAY_DURATION("5"),
    HEALTH_BAR_LENGTH("16"),
    HEALTH_BAR_PREFIX("&(0,255,0)HP "),
    HEALTH_BAR_SUFFIX("&(0,255,0) <<"),
    HEALTH_BAR_ALIVE_SYMBOL("&(0,255,0)♥"),
    HEALTH_BAR_DEAD_SYMBOL("&(100,100,100)♡"),
    HOLOGRAM_POSITION("-0.3"),
    ENABLE_HOLOGRAPHIC_CUSTOM_NAMES("true"),
    HOLOGRAM_NAME_POSITION("0"),
    HOLOGRAM_FOLLOW_SPEED("3"),
    HEALTH_BAR_ALWAYS_VISIBLE("false"),
    ENABLE_DAMAGE_MERGE("true"),
    DAMAGE_MERGE_TIMEOUT("1.5"),
    DAMAGE_MERGE_JUMP_DURATION("15"),
    HOLOGRAM_VELOCITY_Y("0.15"),
    HOLOGRAM_GRAVITY("0.01");

    private final String defaultValue;
    private static final String PARSING_ERROR = "Error parsing the value in the config file for %s.";

    ConfigKeys(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String asString() {
        String value = getPlugin().getConfig()
                                  .getString(name().toLowerCase());
        return (value != null) ? value : defaultValue;
    }

    public boolean asBoolean() {
        return Boolean.parseBoolean(asString());
    }

    public int asInt() {
        try {
            return Integer.parseInt(asString());
        } catch (NumberFormatException ex) {
            logParseWarning(name().toLowerCase());
            return 0;
        }
    }

    public double asDouble() {
        try {
            return Double.parseDouble(asString());
        } catch (NumberFormatException ex) {
            logParseWarning(name().toLowerCase());
            return 0.0;
        }
    }

    public TextColor getTextColor() {
        return TEXT_UTIL.parseRGB(asString());
    }

    public TextComponent asFormattedString() {
        return TEXT_UTIL.parseFormattedString(asString());
    }

    private void logParseWarning(String key) {
        getPlugin().getLogger()
                   .warning(PARSING_ERROR.formatted(key));
    }

    private JavaPlugin getPlugin() {
        return DamageIndicator.getInstance();
    }
}
