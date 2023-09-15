package io.github.stealingdapenta.idletd;

import io.github.stealingdapenta.idletd.database.DatabaseManager;
import io.github.stealingdapenta.idletd.listener.CustomMobListener;
import io.github.stealingdapenta.idletd.listener.SpawnListener;
import io.github.stealingdapenta.idletd.listener.TrackerListener;
import io.github.stealingdapenta.idletd.service.command.SpawnZombieCommand;
import io.github.stealingdapenta.idletd.service.command.TrackerCommand;
import io.github.stealingdapenta.idletd.service.command.plot.PlotCommand;
import io.github.stealingdapenta.idletd.service.customitem.InventoryHandler;
import io.github.stealingdapenta.idletd.service.customitem.TrackerItem;
import io.github.stealingdapenta.idletd.service.custommob.CustomMobHandler;
import io.github.stealingdapenta.idletd.service.custommob.CustomMobSpawner;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import static io.github.stealingdapenta.idletd.service.utils.Schematic.TOWER_DEFENSE_SCHEMATIC;

public class Idletd extends JavaPlugin {
    private static Idletd instance = null;

    private final InventoryHandler inventoryHandler = new InventoryHandler();
    private final TrackerItem trackerItem = new TrackerItem();
    private final CustomMobHandler customMobHandler = new CustomMobHandler();

    // Commands
    private final TrackerCommand trackerCommand = new TrackerCommand(inventoryHandler, trackerItem);
    private final CustomMobSpawner customMobSpawner = new CustomMobSpawner(customMobHandler);
    private final SpawnZombieCommand spawnZombieCommand = new SpawnZombieCommand(customMobSpawner);
    private final PlotCommand plotCommand = new PlotCommand();

    // Listeners
    private final TrackerListener trackerListener = new TrackerListener(trackerItem, customMobHandler);
    private final SpawnListener spawnListener = new SpawnListener();
    private final CustomMobListener customMobListener = new CustomMobListener();

    public static Idletd getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        this.copyResourcesToDataFolder();

        this.getCommand("zombie").setExecutor(spawnZombieCommand);
        this.getCommand("tracker").setExecutor(trackerCommand);
        this.getCommand("plot").setExecutor(plotCommand);

        Bukkit.getPluginManager().registerEvents(trackerListener, getInstance());
        Bukkit.getPluginManager().registerEvents(customMobListener, getInstance());
        Bukkit.getPluginManager().registerEvents(spawnListener, getInstance());

        try {
            DatabaseManager.createTables();
            DatabaseManager.populateTables();
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().warning(e.getMessage());
        }

        this.pluginEnabledLog();
    }

    @Override
    public void onDisable() {
        instance = null;

        this.pluginDisabledLog();
    }

    private void pluginEnabledLog() {
        getLogger().info("IdleMCTD enabled.");
        getServer().getConsoleSender().sendMessage("IdleMCTD is now loaded!");
        getServer().getConsoleSender().sendMessage("Thank you for using IdleMCTD :)");
    }

    private void pluginDisabledLog() {
        getLogger().info("IdleMCTD is now disabled.");
    }

    public File getIdleTdFolder() {
        File pluginFolder = this.getDataFolder();
        if (!pluginFolder.exists() && (!pluginFolder.mkdirs())) {
            getLogger().warning("Failed to generate idletd data folder!");
        }
        return pluginFolder;
    }

    private void copyResourcesToDataFolder() {
        File dataFolder = getDataFolder();
        File schematicsFolder = new File(dataFolder, "schematics");

        if (!schematicsFolder.exists() && schematicsFolder.mkdirs()) {
            getLogger().info("Schematics folder created.");
        }

        this.copyResource("schematics" + File.separator + TOWER_DEFENSE_SCHEMATIC.getFileName(),
                new File(schematicsFolder, TOWER_DEFENSE_SCHEMATIC.getFileName()));
    }

    private void copyResource(String resourcePath, File targetFile) {
        try (InputStream inputStream = getClass().getResourceAsStream("/" + resourcePath);
             OutputStream outputStream = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[4096];
            int length;
            while (true) {
                assert inputStream != null;
                if ((length = inputStream.read(buffer)) <= 0) break;
                outputStream.write(buffer, 0, length);
            }

            getLogger().info("Copied resource: " + resourcePath);
        } catch (IOException e) {
            getLogger().warning("Failed to copy resource: " + resourcePath);
            e.printStackTrace();
        }
    }
}