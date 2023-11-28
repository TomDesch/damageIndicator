package io.github.stealingdapenta.damageindicator;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import io.github.stealingdapenta.damageindicator.listener.HealthBarListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public class DamageIndicator extends JavaPlugin {
    private static DamageIndicator instance = null;

    // Listeners
    private final DamageIndicatorListener damageIndicatorListener = new DamageIndicatorListener();
    private final HealthBarListener healthBarListener = new HealthBarListener();

    public static DamageIndicator getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        ConfigurationFileManager.getInstance().loadConfig();

        enableDamageIndicator();
        enableHealthBar();

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

    private void pluginEnabledLog() {
        getLogger().info("Damage indicator plugin enabled.");
    }

    private void pluginDisabledLog() {
        getLogger().info("Damage indicator is now disabled.");
    }
}