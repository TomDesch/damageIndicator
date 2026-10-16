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
        normalize(plugin);
    }

    private void normalize(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        for (ConfigKeys key : ConfigKeys.values()) {
            migrateLegacyValue(config, key);
            config.addDefault(key.getConfigKey(), key.getDefaultValue());
        }

        config.options()
              .copyDefaults(true);
        ConfigKeys.install(plugin);
        plugin.saveConfig();
    }

    private void migrateLegacyValue(FileConfiguration config, ConfigKeys key) {
        if (config.contains(key.getConfigKey(), true)) {
            return;
        }

        key.getLegacyKeys()
           .stream()
           .filter(legacy -> config.contains(legacy, true))
           .findFirst()
           .ifPresent(legacyKey -> config.set(key.getConfigKey(), config.get(legacyKey, null)));
    }

    /**
     * Reloads the plugin configuration from disk.
     */
    public void reloadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();
        plugin.reloadConfig();
        normalize(plugin);
    }
}
