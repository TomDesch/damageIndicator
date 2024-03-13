package io.github.stealingdapenta.damageindicator.config;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigurationFileManager {

    private static ConfigurationFileManager instance;

    private ConfigurationFileManager() {
    }

    public static ConfigurationFileManager getInstance() {
        if (Objects.isNull(instance)) {
            instance = new ConfigurationFileManager();
        }
        return instance;
    }

    public void loadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();

        plugin.saveDefaultConfig();
        FileConfiguration configuration = plugin.getConfig();

        // set default configurations
        for (DefaultConfigValue defaultConfig : DefaultConfigValue.values()) {
            configuration.addDefault(defaultConfig.name()
                                                  .toLowerCase(), defaultConfig.getDefaultValue());
        }

        configuration.options()
                     .copyDefaults(true);
        plugin.saveConfig();
    }

    public void reloadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();
        plugin.reloadConfig();
    }
}
