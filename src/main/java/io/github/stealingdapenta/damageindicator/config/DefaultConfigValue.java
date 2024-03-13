package io.github.stealingdapenta.damageindicator.config;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.TextUtil;
import java.util.Objects;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;

public enum DefaultConfigValue {
    ENABLE_DAMAGE_INDICATOR("true"),
    MAGIC("(95,10,95)"),
    MELEE("(100,100,100)"),
    POISON("(0,100,20)"),
    FIRE("(200,90,25)"),
    RANGED("(130,70,0)"),
    OTHER("(130,130,30)"),
    ENABLE_HEALTH_BAR("true"),
    ENABLE_HOLOGRAM_HEALTH_BAR("true"),
    HEALTH_BAR_DISPLAY_DURATION("5"),
    HEALTH_BAR_LENGTH("16"),
    HEALTH_BAR_PREFIX("(0,255,0)HP "),
    HEALTH_BAR_PREFIX_STRIKETHROUGH("true"),
    HEALTH_BAR_PREFIX_BOLD("true"),
    HEALTH_BAR_PREFIX_UNDERLINED("false"),
    HEALTH_BAR_SUFFIX("(0,255,0) <<"),
    HEALTH_BAR_SUFFIX_STRIKETHROUGH("true"),
    HEALTH_BAR_SUFFIX_BOLD("true"),
    HEALTH_BAR_SUFFIX_UNDERLINED("false"),
    HEALTH_BAR_ALIVE_SYMBOL("(0, 255, 0)♥"),
    HEALTH_BAR_DEAD_SYMBOL("(100, 100, 100)♡"),
    HEALTH_BAR_STRIKETHROUGH("true"),
    HEALTH_BAR_BOLD("true"),
    HEALTH_BAR_UNDERLINED("false"),
    HOLOGRAM_POSITION("-0.3"),
    ENABLE_HOLOGRAPHIC_CUSTOM_NAMES("true"),
    HOLOGRAM_NAME_POSITION("0"),
    HOLOGRAM_FOLLOW_SPEED("3");

    private final String defaultValue;
    private static final String PARSING_ERROR = "Error parsing the value in the config file for %s.";

    private static final TextUtil textUtil = TextUtil.getInstance();

    DefaultConfigValue(String defaultValue) {
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
        return getTextColor(name().toLowerCase());
    }

    private TextColor getTextColor(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String rgbString = plugin.getConfig()
                                 .getString(key);

        if (rgbString != null) {
            rgbString = rgbString.replace("(", "")
                                 .replace(" ", "")
                                 .replace(")", "")
                                 .trim();
            String[] rgbValues = rgbString.split(",");

            if (rgbValues.length == 3) {

                int red = Integer.parseInt(rgbValues[0]);
                int green = Integer.parseInt(rgbValues[1]);
                int blue = Integer.parseInt(rgbValues[2]);

                return TextColor.color(red, green, blue);

            } else {
                plugin.getLogger()
                      .warning("Invalid RGB format for key " + key);
            }
        }
        return TextColor.color(255, 255, 255); // Default to white in case of error
    }
}
