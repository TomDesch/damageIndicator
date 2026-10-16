package io.github.stealingdapenta.damageindicator.config;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.List;
import java.util.Locale;
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
    DRY_OUT("&(244,164,96)", "dryout"),
    FREEZE("&(173,216,230)"),
    SONIC_BOOM("&(255,215,0)"),
    OTHER("&(130,130,30)"),
    ENABLE_HEALTH_BAR("true"),
    ENABLE_HOLOGRAM_HEALTH_BAR("false"),
    HEALTH_BAR_DISPLAY_DURATION("5"),
    HEALTH_BAR_LENGTH("16"),
    HEALTH_BAR_PREFIX("&(0,255,0)HP "),
    HEALTH_BAR_SUFFIX("&(0,255,0) <<"),
    HEALTH_BAR_ALIVE_SYMBOL("&(0,255,0)♥"),
    HEALTH_BAR_DEAD_SYMBOL("&(100,100,100)♡"),
    HOLOGRAM_POSITION("-0.3"),
    ENABLE_HOLOGRAPHIC_CUSTOM_NAMES("false"),
    HOLOGRAM_NAME_POSITION("0"),
    HOLOGRAM_FOLLOW_SPEED("20"),
    HEALTH_BAR_ALWAYS_VISIBLE("true"),
    ENABLE_DAMAGE_MERGE("true"),
    DAMAGE_MERGE_TIMEOUT("1.5"),
    DAMAGE_MERGE_JUMP_DURATION("15"),
    HOLOGRAM_VELOCITY_Y("0.15"),
    HOLOGRAM_GRAVITY("0.01");

    private final String defaultValue;
    private final List<String> legacyKeys;
    private static volatile RuntimeConfiguration runtime;

    ConfigKeys(String defaultValue, String... legacyKeys) {
        this.defaultValue = defaultValue;
        this.legacyKeys = List.of(legacyKeys);
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getConfigKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public List<String> getLegacyKeys() {
        return legacyKeys;
    }

    public String asString() {
        return value().text();
    }

    public boolean asBoolean() {
        return value().bool();
    }

    public int asInt() {
        return value().integer();
    }

    public int asIntAtLeast(int minimum) {
        return asIntBetween(minimum, Integer.MAX_VALUE);
    }

    public int asIntBetween(int minimum, int maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("Minimum must not exceed maximum");
        }
        RuntimeConfiguration snapshot = snapshot();
        int value = snapshot.value(this).integer();
        if (value >= minimum && value <= maximum) {
            return value;
        }
        snapshot.warnRangeOnce(this, minimum, maximum);
        return Math.max(minimum, Math.min(maximum, Integer.parseInt(defaultValue)));
    }

    public double asDouble() {
        return value().number();
    }

    public double asDoubleAtLeast(double minimum) {
        if (!Double.isFinite(minimum)) {
            throw new IllegalArgumentException("Minimum must be finite");
        }
        RuntimeConfiguration snapshot = snapshot();
        double value = snapshot.value(this).number();
        if (value >= minimum) {
            return value;
        }
        snapshot.warnRangeOnce(this, minimum, Double.MAX_VALUE);
        return Math.max(minimum, Double.parseDouble(defaultValue));
    }

    public TextColor getTextColor() {
        return value().color();
    }

    public TextComponent asFormattedString() {
        return value().formatted();
    }

    static void install(RuntimeConfiguration replacement) {
        runtime = replacement;
    }

    private RuntimeConfiguration.Value value() {
        return snapshot().value(this);
    }

    private RuntimeConfiguration snapshot() {
        JavaPlugin plugin = DamageIndicator.getInstance();
        var config = plugin.getConfig();
        RuntimeConfiguration current = runtime;
        if (current != null && current.matches(plugin, config)) {
            return current;
        }
        // Before lifecycle loading, read only this key; standalone callers must not inherit another plugin's snapshot.
        return RuntimeConfiguration.single(plugin, config, this);
    }

    ValueType valueType() {
        return switch (this) {
            case ENABLE_DAMAGE_INDICATOR, ENABLE_HEALTH_BAR, ENABLE_HOLOGRAM_HEALTH_BAR,
                 ENABLE_HOLOGRAPHIC_CUSTOM_NAMES, HEALTH_BAR_ALWAYS_VISIBLE, ENABLE_DAMAGE_MERGE -> ValueType.BOOLEAN;
            case HEALTH_BAR_DISPLAY_DURATION, HEALTH_BAR_LENGTH, HOLOGRAM_FOLLOW_SPEED,
                 DAMAGE_MERGE_JUMP_DURATION -> ValueType.INTEGER;
            case HOLOGRAM_POSITION, HOLOGRAM_NAME_POSITION, DAMAGE_MERGE_TIMEOUT,
                 HOLOGRAM_VELOCITY_Y, HOLOGRAM_GRAVITY -> ValueType.DECIMAL;
            case HEALTH_BAR_PREFIX, HEALTH_BAR_SUFFIX, HEALTH_BAR_ALIVE_SYMBOL, HEALTH_BAR_DEAD_SYMBOL -> ValueType.TEXT;
            default -> ValueType.COLOR;
        };
    }

    double minimum() {
        return switch (this) {
            case HOLOGRAM_POSITION, HOLOGRAM_NAME_POSITION -> -64;
            case HOLOGRAM_VELOCITY_Y -> -10;
            case DAMAGE_MERGE_TIMEOUT, HOLOGRAM_GRAVITY -> 0;
            default -> 1;
        };
    }

    double maximum() {
        return switch (this) {
            case HEALTH_BAR_DISPLAY_DURATION, HOLOGRAM_VELOCITY_Y, HOLOGRAM_GRAVITY -> 10;
            case HEALTH_BAR_LENGTH -> 100;
            case HOLOGRAM_POSITION, HOLOGRAM_NAME_POSITION -> 64;
            case DAMAGE_MERGE_TIMEOUT -> 60;
            default -> 200;
        };
    }

    int maximumTextLength() {
        return switch (this) {
            case HEALTH_BAR_ALIVE_SYMBOL, HEALTH_BAR_DEAD_SYMBOL -> 32;
            case HEALTH_BAR_PREFIX, HEALTH_BAR_SUFFIX -> 256;
            default -> 64;
        };
    }

    enum ValueType {
        BOOLEAN, INTEGER, DECIMAL, COLOR, TEXT
    }
}
