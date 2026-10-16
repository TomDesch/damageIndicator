package io.github.stealingdapenta.damageindicator.config;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public enum ConfigurationFileManager {
    CONFIGURATION_FILE_MANAGER;

    private static final int MAXIMUM_FAILURE_REASON_LENGTH = 1024;

    /**
     * Loads and validates the complete file before changing any active configuration.
     *
     * @throws ConfigurationLoadException if the file cannot be read or parsed
     */
    public void loadConfig() {
        JavaPlugin plugin = DamageIndicator.getInstance();

        plugin.saveDefaultConfig(); // only saves if config doesn't exist
        loadFromDisk(plugin);
    }

    private void loadFromDisk(JavaPlugin plugin) {
        Path path = plugin.getDataFolder()
                          .toPath()
                          .resolve("config.yml");
        YamlConfiguration candidate = readConfiguration(plugin, path);
        normalize(candidate);
        // Validate and format everything before touching the active Bukkit object or snapshot.
        RuntimeConfiguration replacement = RuntimeConfiguration.load(plugin, candidate);
        String normalizedContents = candidate.saveToString();
        FileConfiguration active = plugin.getConfig();
        try {
            // Apply the validated in-memory contents, never Bukkit's permissive disk reload.
            active.loadFromString(normalizedContents);
        } catch (InvalidConfigurationException exception) {
            throw failure(plugin, path, exception);
        }
        active.setDefaults(Objects.requireNonNull(candidate.getDefaults(), "Normalized configuration requires defaults"));
        active.options()
              .copyDefaults(true);
        ConfigKeys.install(replacement.withSource(active));
        plugin.saveConfig();
    }

    private YamlConfiguration readConfiguration(JavaPlugin plugin, Path path) {
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
            return candidate;
        } catch (IOException | InvalidConfigurationException exception) {
            throw failure(plugin, path, exception);
        }
    }

    // Explicit injection for already-parsed configurations; disk lifecycle methods never use this path.
    void loadConfig(JavaPlugin plugin, FileConfiguration preloaded) {
        normalize(preloaded);
        ConfigKeys.install(RuntimeConfiguration.load(plugin, preloaded));
    }

    private void normalize(FileConfiguration config) {
        for (ConfigKeys key : ConfigKeys.values()) {
            migrateLegacyValue(config, key);
            config.addDefault(key.getConfigKey(), key.getDefaultValue());
        }

        config.options()
              .copyDefaults(true);
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
     * Reloads the file transactionally, retaining the previous configuration on read or syntax failure.
     *
     * @throws ConfigurationLoadException if the file cannot be read or parsed
     */
    public void reloadConfig() {
        loadFromDisk(DamageIndicator.getInstance());
    }

    private ConfigurationLoadException failure(JavaPlugin plugin, Path path, Exception cause) {
        String reason = cause.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = cause.getClass()
                          .getSimpleName();
        }
        if (reason.length() > MAXIMUM_FAILURE_REASON_LENGTH) {
            reason = reason.substring(0, MAXIMUM_FAILURE_REASON_LENGTH) + "...";
        }
        String message = "Cannot load configuration '" + path + "': " + reason + ". The active configuration and file were not replaced.";
        plugin.getLogger()
              .severe(message);
        return new ConfigurationLoadException(message, cause);
    }
}
