package io.github.stealingdapenta.damageindicator;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ConfigurationFileManager {
    private static final String CONFIG_FILE = "custom.yml";
    private static ConfigurationFileManager instance;
    private FileConfiguration customConfig;
    private File customConfigFile;


    private ConfigurationFileManager() {
    }

    public static ConfigurationFileManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationFileManager();
        }
        return instance;
    }

    public void loadConfig(JavaPlugin plugin) {
        if (customConfig == null) {
            customConfigFile = new File(plugin.getDataFolder(), CONFIG_FILE);
            if (!customConfigFile.exists()) {
                customConfigFile.getParentFile().mkdirs();
                plugin.saveResource(CONFIG_FILE, false);
            }

            customConfig = new YamlConfiguration();
            try {
                customConfig.load(customConfigFile);

                //set default configurations
                for (DefaultConfigValue defaultConfig : DefaultConfigValue.values()) {
                    customConfig.addDefault(defaultConfig.name().toLowerCase(), defaultConfig.getDefaultValue());
                }

            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().severe("Error loading custom.yml: " + e.getMessage());
            }
        }
    }

    public FileConfiguration getCustomConfig() {
        return customConfig;
    }

    public File getCustomConfigFile() {
        return customConfigFile;
    }

    public TextColor getTextColor(String key) {
        String rgbString = customConfig.getString(key);

        if (rgbString != null) {
            String[] rgbValues = rgbString.split(",");

            if (rgbValues.length == 3) {
                try {
                    int red = Integer.parseInt(rgbValues[0]);
                    int green = Integer.parseInt(rgbValues[1]);
                    int blue = Integer.parseInt(rgbValues[2]);

                    return TextColor.color(red, green, blue);
                } catch (NumberFormatException e) {
                    DamageIndicator.getInstance().getLogger().warning("Error parsing RGB values for key " + key);
                }
            } else {
                DamageIndicator.getInstance().getLogger().warning("Invalid RGB format for key " + key);
            }
        }
        return TextColor.color(255, 255, 255); // Default to white in case of error
    }
}
