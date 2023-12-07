package io.github.stealingdapenta.damageindicator;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class ConfigurationFileManager {
    private static ConfigurationFileManager instance;
    private static final String PARSING_ERROR = "Error parsing the value in the config file for %s.";

    private ConfigurationFileManager() {
    }

    public static ConfigurationFileManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationFileManager();
        }
        return instance;
    }

    public void loadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();

        plugin.saveDefaultConfig();
        FileConfiguration configuration = plugin.getConfig();

        //set default configurations
        for (DefaultConfigValue defaultConfig : DefaultConfigValue.values()) {
            configuration.addDefault(defaultConfig.name().toLowerCase(), defaultConfig.getDefaultValue());
        }

        configuration.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public void reloadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();
        plugin.reloadConfig();
    }

    public boolean getBooleanValue(DefaultConfigValue key) {
        return getBooleanValue(key.name().toLowerCase());
    }

    public boolean getBooleanValue(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String valueAsString = plugin.getConfig().getString(key);
        boolean result;
        try {
            result = Boolean.parseBoolean(valueAsString);
        } catch (NumberFormatException | NullPointerException e) {
            DamageIndicator.getInstance().getLogger().warning(PARSING_ERROR.formatted(key));
            result = false;
        }
        return result;
    }

    public String getStringValue(DefaultConfigValue key) {
        return DamageIndicator.getInstance().getConfig().getString(key.name().toLowerCase());
    }

    public double getDouble(DefaultConfigValue key) {
        return getDouble(key.name().toLowerCase());
    }

    private double getDouble(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String valueAsString = plugin.getConfig().getString(key);
        double result;
        if (Objects.isNull(valueAsString)) {
            valueAsString = "0";
        }
        try {
            result = Double.parseDouble(valueAsString);
        } catch (NumberFormatException numberFormatException) {
            DamageIndicator.getInstance().getLogger().warning(PARSING_ERROR.formatted(key));
            result = 0;
        }
        return result;
    }

    public int getInt(DefaultConfigValue key) {
        return getInt(key.name().toLowerCase());
    }

    private int getInt(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String valueAsString = plugin.getConfig().getString(key);
        int result;
        if (Objects.isNull(valueAsString)) {
            valueAsString = "0";
        }
        try {
            result = Integer.parseInt(valueAsString);
        } catch (NumberFormatException numberFormatException) {
            DamageIndicator.getInstance().getLogger().warning(PARSING_ERROR.formatted(key));
            result = 0;
        }
        return result;
    }

    public TextColor getTextColor(DefaultConfigValue key) {
        return getTextColor(key.name().toLowerCase());
    }

    private TextColor getTextColor(String key) {
        JavaPlugin plugin = DamageIndicator.getInstance();
        String rgbString = plugin.getConfig().getString(key);

        if (rgbString != null) {
            rgbString = rgbString.replace("(", "").replace(" ", "").replace(")", "").trim();
            String[] rgbValues = rgbString.split(",");

            if (rgbValues.length == 3) {
                try {
                    int red = Integer.parseInt(rgbValues[0]);
                    int green = Integer.parseInt(rgbValues[1]);
                    int blue = Integer.parseInt(rgbValues[2]);

                    return TextColor.color(red, green, blue);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Error parsing RGB values for key " + key);
                }
            } else {
                plugin.getLogger().warning("Invalid RGB format for key " + key);
            }
        }
        return TextColor.color(255, 255, 255); // Default to white in case of error
    }
}
