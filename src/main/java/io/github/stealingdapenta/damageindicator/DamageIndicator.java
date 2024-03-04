package io.github.stealingdapenta.damageindicator;

import io.github.stealingdapenta.damageindicator.command.AreaRemoveCommand;
import io.github.stealingdapenta.damageindicator.command.ReloadConfigCommand;
import io.github.stealingdapenta.damageindicator.listener.CustomNameListener;
import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import io.github.stealingdapenta.damageindicator.listener.HealthBarListener;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public class DamageIndicator extends JavaPlugin {
    private static DamageIndicator instance = null;

    private final DamageIndicatorListener damageIndicatorListener = new DamageIndicatorListener();
    private final HealthBarListener healthBarListener = new HealthBarListener();
    private final ReloadConfigCommand reloadConfigCommand = new ReloadConfigCommand();
    private final AreaRemoveCommand areaRemoveCommand = new AreaRemoveCommand();
    private final CustomNameListener customNamesListener = new CustomNameListener();

    public static DamageIndicator getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        ConfigurationFileManager.getInstance().loadConfig();

        Objects.requireNonNull(this.getCommand("reload")).setExecutor(reloadConfigCommand);
        Objects.requireNonNull(this.getCommand("arearemove"))
               .setExecutor(areaRemoveCommand);


        enableDamageIndicator();
        enableHealthBar();
        enableHolographicCustomNames();

        pluginEnabledLog();
    }


    @Override
    public void onDisable() {
        instance = null;
        pluginDisabledLog();
    }

    private void enableDamageIndicator() {
        if (ConfigurationFileManager.getInstance().getBooleanValue(DefaultConfigValue.ENABLE_DAMAGE_INDICATOR)) {
            Bukkit.getPluginManager().registerEvents(damageIndicatorListener, getInstance());
            getLogger().info("Damage indicator feature enabled. To disable, modify the config.yml.");
        } else {
            getLogger().info("Damage indicator feature not enabled. To enable, modify the config.yml.");
        }
    }

    private void enableHealthBar() {
        if (ConfigurationFileManager.getInstance().getBooleanValue(DefaultConfigValue.ENABLE_HEALTH_BAR)) {
            Bukkit.getPluginManager().registerEvents(healthBarListener, getInstance());
            getLogger().info("Health bar feature enabled. To disable, modify the config.yml.");
        } else {
            getLogger().info("Health bar feature not enabled. To enable, modify the config.yml.");
        }
    }

    private void enableHolographicCustomNames() {
        if (ConfigurationFileManager.getInstance().getBooleanValue(DefaultConfigValue.ENABLE_HOLOGRAPHIC_CUSTOM_NAMES)) {
            if (ConfigurationFileManager.getInstance().getBooleanValue(DefaultConfigValue.ENABLE_HOLOGRAM_HEALTH_BAR)) {
                Bukkit.getPluginManager().registerEvents(customNamesListener, getInstance());
                getLogger().info("Holographic custom names feature enabled. To disable, modify the config.yml.");
            } else {
                getLogger().warning("Holographic custom names feature is enabled in your config, but Holographic Health bars is disabled.");
                getLogger().warning("If you want to use holographic custom names, then the holographic health bars feature should also be enabled.");
            }
        } else {
            getLogger().info("Holographic custom names feature not enabled. To enable, modify the config.yml.");
        }
    }

    private void pluginEnabledLog() {
        getLogger().info("Damage indicator plugin enabled.");
    }

    private void pluginDisabledLog() {
        getLogger().info("Damage indicator is now disabled.");
    }
}