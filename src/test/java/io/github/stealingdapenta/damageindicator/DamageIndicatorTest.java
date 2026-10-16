package io.github.stealingdapenta.damageindicator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.stealingdapenta.damageindicator.command.AreaRemoveCommand;
import io.github.stealingdapenta.damageindicator.command.AreaRenameCommand;
import io.github.stealingdapenta.damageindicator.command.ReloadConfigCommand;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import io.github.stealingdapenta.damageindicator.config.ConfigurationLoadException;
import io.github.stealingdapenta.damageindicator.listener.CustomNameListener;
import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import io.github.stealingdapenta.damageindicator.listener.HealthBarListener;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class DamageIndicatorTest {

    @TempDir
    Path directory;
    private final DamageIndicator plugin = mock(DamageIndicator.class, CALLS_REAL_METHODS);
    private final DamageIndicatorListener damage = mock(DamageIndicatorListener.class);
    private final HealthBarListener health = mock(HealthBarListener.class);
    private final CustomNameListener names = mock(CustomNameListener.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private MockedStatic<Bukkit> bukkit;
    private Path configFile;

    @BeforeEach
    void setUp() throws Exception {
        configFile = directory.resolve("config.yml");
        Files.writeString(configFile, "enable_damage_indicator: true\n");
        doReturn(config).when(plugin)
                        .getConfig();
        doReturn(directory.toFile()).when(plugin)
                                    .getDataFolder();
        doReturn(Logger.getLogger("plugin-lifecycle-test")).when(plugin)
                                                           .getLogger();
        doReturn(mock(PluginCommand.class)).when(plugin)
                                           .getCommand(anyString());
        doNothing().when(plugin)
                   .saveDefaultConfig();
        doNothing().when(plugin)
                   .saveConfig();
        inject("damageIndicatorListener", damage);
        inject("healthBarListener", health);
        inject("customNamesListener", names);
        inject("reloadConfigCommand", new ReloadConfigCommand());
        inject("areaRemoveCommand", new AreaRemoveCommand());
        inject("areaRenameCommand", new AreaRenameCommand());
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager)
              .thenReturn(pluginManager);
        bukkit.when(Bukkit::getWorlds)
              .thenReturn(List.of());
        bukkit.when(Bukkit::isPrimaryThread)
              .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        plugin.onDisable();
        bukkit.close();
    }

    @Test
    void startupRegistersAllListenersAndRecoversBeforeRecreation() {
        plugin.onEnable();
        verify(pluginManager).registerEvents(damage, plugin);
        verify(pluginManager).registerEvents(health, plugin);
        verify(pluginManager).registerEvents(names, plugin);
        var order = inOrder(health, names, damage);
        order.verify(health)
             .restorePersistedNames();
        order.verify(names)
             .restorePersistedNames();
        order.verify(damage)
             .cleanupLoadedChunks();
        order.verify(health)
             .refreshPersistedHolograms();
        order.verify(names)
             .refreshLoadedEntities();
        assertTrue(ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean());
    }

    @Test
    void successfulReloadPublishesConfigurationBeforeReplacingDisplays() throws Exception {
        plugin.onEnable();
        clearInvocations(health, names, damage);
        Files.writeString(configFile, "enable_damage_indicator: false\n");
        doAnswer(invocation -> {
            assertFalse(ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean());
            return null;
        }).when(names)
          .shutdown();

        plugin.reloadConfiguration();

        var order = inOrder(names, health, damage);
        order.verify(names)
             .shutdown();
        order.verify(health)
             .shutdown();
        order.verify(damage)
             .shutdown();
        order.verify(health)
             .refreshPersistedHolograms();
        order.verify(names)
             .refreshLoadedEntities();
    }

    @Test
    void malformedReloadPreservesLiveConfigurationDisplaysAndFile() throws Exception {
        plugin.onEnable();
        clearInvocations(health, names, damage);
        String invalid = "enable_damage_indicator: [\n";
        Files.writeString(configFile, invalid);

        assertThrows(ConfigurationLoadException.class, plugin::reloadConfiguration);

        assertTrue(ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean());
        assertTrue(config.getBoolean("enable_damage_indicator"));
        assertEquals(invalid, Files.readString(configFile));
        verify(names, never()).shutdown();
        verify(health, never()).shutdown();
        verify(damage, never()).shutdown();
    }

    @Test
    void failedInitialLoadDoesNotRunWorldRestorationDuringDisable() throws Exception {
        Files.writeString(configFile, "enable_damage_indicator: [\n");
        assertThrows(ConfigurationLoadException.class, plugin::onEnable);
        plugin.onDisable();
        verify(pluginManager, never()).registerEvents(any(), any());
        verify(health, never()).shutdown();
        verify(names, never()).shutdown();
        verify(damage, never()).shutdown();
        assertNull(DamageIndicator.getInstance());
    }

    @Test
    void repeatedDisableDoesNotRunCleanupTwice() {
        plugin.onEnable();
        plugin.onDisable();
        plugin.onDisable();
        verify(names).shutdown();
        verify(health).shutdown();
        verify(damage).shutdown();
        assertNull(DamageIndicator.getInstance());
    }

    @Test
    void virtualApiDelegatesWithoutApplyingDamageOrFiringNativeEvents() {
        plugin.onEnable();
        LivingEntity victim = mock(LivingEntity.class);
        Entity attacker = mock(Entity.class);

        plugin.displayVirtualDamage(victim, attacker, 2.5, DamageCause.CUSTOM);

        verify(damage).displayDamage(victim, attacker, 2.5, DamageCause.CUSTOM);
        verify(victim, never()).damage(anyDouble());
        verify(victim, never()).setHealth(anyDouble());
        verify(pluginManager, never()).callEvent(any());
    }

    @Test
    void virtualApiRejectsAsyncAndDisabledCalls() {
        LivingEntity victim = mock(LivingEntity.class);
        assertThrows(IllegalStateException.class, () -> plugin.displayVirtualDamage(victim, null, 1, DamageCause.CUSTOM));
        plugin.onEnable();
        bukkit.when(Bukkit::isPrimaryThread)
              .thenReturn(false);
        assertThrows(IllegalStateException.class, () -> plugin.displayVirtualDamage(victim, null, 1, DamageCause.CUSTOM));
        verify(damage, never()).displayDamage(any(), any(), anyDouble(), any());
    }

    @Test
    void explicitNameWipeDetachesBothDisplayOwners() {
        LivingEntity victim = mock(LivingEntity.class);
        plugin.clearNameDisplays(victim);
        verify(health).clearDisplay(victim, true);
        verify(names).clearDisplay(victim);
    }

    private void inject(String fieldName, Object value) throws Exception {
        // Mockito bypasses field initializers; JavaPlugin's real constructor requires a plugin classloader.
        Field field = DamageIndicator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
