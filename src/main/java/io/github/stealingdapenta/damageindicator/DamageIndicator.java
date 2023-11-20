package io.github.stealingdapenta.damageindicator;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;


public class DamageIndicator extends JavaPlugin {
    private static DamageIndicator instance = null;

    // Listeners
    private final DamageIndicatorListener damageIndicatorListener = new DamageIndicatorListener();

    public static DamageIndicator getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        ConfigurationFileManager.getInstance().loadConfig();

        Bukkit.getPluginManager().registerEvents(damageIndicatorListener, getInstance());

        pluginEnabledLog();
    }


    @Override
    public void onDisable() {
        instance = null;
        pluginDisabledLog();
    }

    private void pluginEnabledLog() {
        getLogger().info("Damage indicator enabled.");
    }

    private void pluginDisabledLog() {
        getLogger().info("Damage indicator is now disabled.");
    }
}