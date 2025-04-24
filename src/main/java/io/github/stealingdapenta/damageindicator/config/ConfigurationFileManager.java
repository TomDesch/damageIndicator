package io.github.stealingdapenta.damageindicator.config;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public enum ConfigurationFileManager {
    CONFIGURATION_FILE_MANAGER;

    /**
     * Loads the configuration file, ensuring all default values from ConfigKeys are registered.
     */
    public void loadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();

        plugin.saveDefaultConfig(); // only saves if config doesn't exist
        FileConfiguration config = plugin.getConfig();

        for (ConfigKeys key : ConfigKeys.values()) {
            config.addDefault(key.name()
                                 .toLowerCase(), key.getDefaultValue());
        }

        config.options()
              .copyDefaults(true);
        plugin.saveConfig();
    }

    /**
     * Reloads the plugin configuration from disk.
     */
    public void reloadConfig() {
        DamageIndicator.getInstance()
                       .reloadConfig();
    }
}
