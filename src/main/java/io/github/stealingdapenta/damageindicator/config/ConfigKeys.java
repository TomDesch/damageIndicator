package io.github.stealingdapenta.damageindicator.config;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.TextUtil;
import java.util.Objects;
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
    DRYOUT("&(244,164,96)"),
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
    HOLOGRAM_FOLLOW_SPEED("3");

    private final String defaultValue;
    private static final String PARSING_ERROR = "Error parsing the value in the config file for %s.";

    private static final TextUtil textUtil = TextUtil.getInstance();

    ConfigKeys(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDefaultValue() {
        return defaultValue;
    }


    public boolean getBooleanValue() {
        return getBooleanValue(name().toLowerCase());
    }

    public boolean getBooleanValue(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String valueAsString = plugin.getConfig()
                                     .getString(key);

        return Boolean.parseBoolean(valueAsString);
    }

    public String getStringValue() {
        return DamageIndicator.getInstance()
                              .getConfig()
                              .getString(name().toLowerCase());
    }

    public TextComponent getFormattedStringValue() {
        return textUtil.parseFormattedString(getStringValue());
    }

    public double getDoubleValue() {
        return getDoubleValue(name().toLowerCase());
    }

    private double getDoubleValue(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String valueAsString = plugin.getConfig()
                                     .getString(key);
        double result;
        if (Objects.isNull(valueAsString)) {
            valueAsString = "0";
        }
        try {
            result = Double.parseDouble(valueAsString);
        } catch (NumberFormatException numberFormatException) {
            DamageIndicator.getInstance()
                           .getLogger()
                           .warning(PARSING_ERROR.formatted(key));
            result = 0;
        }
        return result;
    }

    public int getIntValue() {
        return getIntValue(name().toLowerCase());
    }

    private int getIntValue(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String valueAsString = plugin.getConfig()
                                     .getString(key);
        int result;
        if (Objects.isNull(valueAsString)) {
            valueAsString = "0";
        }
        try {
            result = Integer.parseInt(valueAsString);
        } catch (NumberFormatException numberFormatException) {
            DamageIndicator.getInstance()
                           .getLogger()
                           .warning(PARSING_ERROR.formatted(key));
            result = 0;
        }
        return result;
    }

    public TextColor getTextColor() {
        return textUtil.parseRGB(getStringValue());
    }
}
