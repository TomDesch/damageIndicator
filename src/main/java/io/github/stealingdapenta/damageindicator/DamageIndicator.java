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

    /**
     * Displays already-applied custom HP damage without manufacturing a Bukkit damage event.
     * This Bukkit-only signature is safe for optional reflection callers despite Adventure relocation.
     */
    public void displayVirtualDamage(org.bukkit.entity.LivingEntity victim, org.bukkit.entity.Entity attacker,
                                     double damage, org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Damage indicators require the server thread");
        damageIndicatorListener.displayDamage(victim, attacker, damage, cause);
    }

    @Override
    public void onEnable() {
        instance = this;
        CONFIGURATION_FILE_MANAGER.loadConfig();

        registerCommand("reload", reloadConfigCommand);
        registerCommand("arearemove", areaRemoveCommand);
        registerCommand("arearename", areaRenameCommand);

        registerListener(damageIndicatorListener);
        registerListener(healthBarListener);
        registerListener(customNamesListener);
        logFeatureStates();
        healthBarListener.restorePersistedNames();
        customNamesListener.restorePersistedNames();
        damageIndicatorListener.cleanupLoadedChunks();
        healthBarListener.refreshPersistedHolograms();
        customNamesListener.refreshLoadedEntities();

        getLogger().info("Damage Indicator plugin enabled.");
    }

    public void reloadConfiguration() {
        customNamesListener.shutdown();
        healthBarListener.shutdown();
        damageIndicatorListener.shutdown();
        CONFIGURATION_FILE_MANAGER.reloadConfig();
        logFeatureStates();
        healthBarListener.refreshPersistedHolograms();
        customNamesListener.refreshLoadedEntities();
    }

    private void logFeatureStates() {
        if (ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean()) {
            logEnabledFeature("Damage indicator");
        } else {
            logDisabledFeature("Damage indicator");
        }

        if (ConfigKeys.ENABLE_HEALTH_BAR.asBoolean()) {
            logEnabledFeature("Health bar");
        } else {
            logDisabledFeature("Health bar");
        }

        if (ConfigKeys.ENABLE_HOLOGRAPHIC_CUSTOM_NAMES.asBoolean()) {
            if (ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
                logEnabledFeature("Holographic custom names");
            } else {
                getLogger().warning("Holographic custom names enabled, but holographic health bar is disabled. Enable both for expected behavior.");
            }
        } else {
            logDisabledFeature("Holographic custom names");
        }
    }

    @Override
    public void onDisable() {
        customNamesListener.shutdown();
        healthBarListener.shutdown();
        damageIndicatorListener.shutdown();
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

    private void registerListener(org.bukkit.event.Listener listener) {
        Bukkit.getPluginManager()
              .registerEvents(listener, this);
    }

    private void logEnabledFeature(String featureName) {
        getLogger().info(featureName + " feature enabled.");
    }

    private void logDisabledFeature(String featureName) {
        getLogger().info(featureName + " feature not enabled. You can enable it in config.yml.");
    }
}
