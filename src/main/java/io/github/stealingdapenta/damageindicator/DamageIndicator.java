package io.github.stealingdapenta.damageindicator;

import static io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager.CONFIGURATION_FILE_MANAGER;

import io.github.stealingdapenta.damageindicator.command.AreaRemoveCommand;
import io.github.stealingdapenta.damageindicator.command.AreaRenameCommand;
import io.github.stealingdapenta.damageindicator.command.ReloadConfigCommand;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import io.github.stealingdapenta.damageindicator.listener.CustomNameListener;
import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import io.github.stealingdapenta.damageindicator.listener.HealthBarListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for the DamageIndicator plugin.
 */
public class DamageIndicator extends JavaPlugin {

    private static DamageIndicator instance;

    private final DamageIndicatorListener damageIndicatorListener = new DamageIndicatorListener();
    private final HealthBarListener healthBarListener = new HealthBarListener();
    private final CustomNameListener customNamesListener = new CustomNameListener();
    private final ReloadConfigCommand reloadConfigCommand = new ReloadConfigCommand();
    private final AreaRemoveCommand areaRemoveCommand = new AreaRemoveCommand();
    private final AreaRenameCommand areaRenameCommand = new AreaRenameCommand();

    public static DamageIndicator getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        CONFIGURATION_FILE_MANAGER.loadConfig();

        registerCommand("reload", reloadConfigCommand);
        registerCommand("arearemove", areaRemoveCommand);
        registerCommand("arearename", areaRenameCommand);

        if (ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean()) {
            registerFeature(damageIndicatorListener, "Damage indicator");
        } else {
            logDisabled("Damage indicator");
        }

        if (ConfigKeys.ENABLE_HEALTH_BAR.asBoolean()) {
            registerFeature(healthBarListener, "Health bar");
        } else {
            logDisabled("Health bar");
        }

        if (ConfigKeys.ENABLE_HOLOGRAPHIC_CUSTOM_NAMES.asBoolean()) {
            if (ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
                registerFeature(customNamesListener, "Holographic custom names");
            } else {
                getLogger().warning("Holographic custom names enabled, but holographic health bar is disabled. Enable both for expected behavior.");
            }
        } else {
            logDisabled("Holographic custom names");
        }

        getLogger().info("Damage Indicator plugin enabled.");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("Damage Indicator plugin disabled.");
    }

    private void registerCommand(String commandName, Object executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().warning("Command '" + commandName + "' not found in plugin.yml");
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
    }

    private void registerFeature(Object listener, String featureName) {
        Bukkit.getPluginManager()
              .registerEvents((org.bukkit.event.Listener) listener, this);
        getLogger().info(featureName + " feature enabled.");
    }

    private void logDisabled(String featureName) {
        getLogger().info(featureName + " feature not enabled. You can enable it in config.yml.");
    }
}
