package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.*;
import static io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager.CONFIGURATION_FILE_MANAGER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ConfigurationFileManagerTest {

    private DamageIndicator plugin;
    private Logger logger;
    private YamlConfiguration config;
    private MockedStatic<DamageIndicator> instance;

    @BeforeEach
    void setUp() {
        plugin = mock(DamageIndicator.class);
        logger = mock(Logger.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        instance = mockStatic(DamageIndicator.class);
        instance.when(DamageIndicator::getInstance).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        instance.close();
    }

    @Test
    void loadCopiesAllAuthoritativeDefaultsAndSavesWithoutWritingInvalidFallbacks() throws Exception {
        config.loadFromString("health_bar_length: -1\nunrelated_setting: preserved");

        CONFIGURATION_FILE_MANAGER.loadConfig();

        verify(plugin).saveDefaultConfig();
        verify(plugin).saveConfig();
        assertTrue(config.options().copyDefaults());
        for (ConfigKeys key : ConfigKeys.values()) {
            assertTrue(config.contains(key.getConfigKey()), key.toString());
        }
        assertEquals(16, HEALTH_BAR_LENGTH.asInt());
        assertEquals(-1, config.getInt("health_bar_length"));
        assertTrue(config.saveToString().contains("unrelated_setting: preserved"));
        verify(logger).warning(contains("'health_bar_length'"));
    }

    @Test
    void explicitLegacyValueWinsOverBundledCanonicalDefaultEvenWhenCopyDefaultsIsEnabled() throws Exception {
        config.loadFromString("dryout: '&(1,2,3)'");
        config.addDefault("dry_out", "&(244,164,96)");
        config.options().copyDefaults(true);
        assertFalse(config.contains("dry_out", true));

        CONFIGURATION_FILE_MANAGER.loadConfig();

        assertTrue(config.contains("dry_out", true));
        assertEquals("&(1,2,3)", config.getString("dry_out"));
        assertEquals(TextColor.color(1, 2, 3), DRY_OUT.getTextColor());
        assertTrue(config.saveToString().contains("dry_out:"));
        verifyNoInteractions(logger);
    }

    @Test
    void explicitCanonicalValueAlwaysWinsIncludingMalformedAndEmptyValues() throws Exception {
        for (String canonical : List.of("&(4,5,6)", "invalid", "")) {
            config = new YamlConfiguration();
            when(plugin.getConfig()).thenReturn(config);
            config.loadFromString("dryout: '&(1,2,3)'\ndry_out: '" + canonical + "'");
            clearInvocations(logger);

            CONFIGURATION_FILE_MANAGER.loadConfig();

            assertEquals(canonical, config.getString("dry_out"));
            if (canonical.equals("&(4,5,6)")) {
                assertEquals(TextColor.color(4, 5, 6), DRY_OUT.getTextColor());
                verifyNoInteractions(logger);
            } else {
                assertEquals(TextColor.color(244, 164, 96), DRY_OUT.getTextColor());
                verify(logger).warning(contains("'dry_out'"));
            }
        }
    }

    @Test
    void malformedLegacyValueMigratesThenUsesCanonicalDiagnostic() throws Exception {
        config.loadFromString("dryout: 'not a color'");
        CONFIGURATION_FILE_MANAGER.loadConfig();
        assertEquals("not a color", config.getString("dry_out"));
        assertEquals(TextColor.color(244, 164, 96), DRY_OUT.getTextColor());
        verify(logger).warning(contains("'dry_out'"));
    }

    @Test
    void loadAndReloadApplyIdenticalDefaultsMigrationValidationAndPersistence() throws Exception {
        for (String yaml : List.of("", "dryout: '&(1,2,3)'",
                "dryout: '&(1,2,3)'\ndry_out: ''",
                "enable_hologram_health_bar: 'not true'\nhologram_follow_speed: 0",
                "damage_merge_timeout: .inf\nhealth_bar_length: 99999999999",
                "health_bar_prefix: '&(256,0,0)HP'")) {
            config = yaml(yaml);
            when(plugin.getConfig()).thenReturn(config);
            CONFIGURATION_FILE_MANAGER.loadConfig();
            Map<ConfigKeys, String> enabledValues = strings();
            String enabledYaml = config.saveToString();
            clearInvocations(plugin, logger);
            YamlConfiguration fromDisk = yaml(yaml);
            doAnswer(invocation -> {
                when(plugin.getConfig()).thenReturn(fromDisk);
                return null;
            }).when(plugin).reloadConfig();

            CONFIGURATION_FILE_MANAGER.reloadConfig();

            assertEquals(enabledValues, strings(), yaml);
            assertEquals(enabledYaml, fromDisk.saveToString(), yaml);
            verify(plugin).reloadConfig();
            verify(plugin).saveConfig();
            verify(plugin, never()).saveDefaultConfig();
        }
    }

    @Test
    void reloadPublishesNewSnapshotAndDiagnosticsOnlyOncePerReload() throws Exception {
        config.loadFromString("hologram_follow_speed: 15\nmagic: 'invalid'");
        CONFIGURATION_FILE_MANAGER.loadConfig();
        assertEquals(15, HOLOGRAM_FOLLOW_SPEED.asInt());
        verify(logger).warning(contains("'magic'"));
        config.set("hologram_follow_speed", 77);
        assertEquals(15, HOLOGRAM_FOLLOW_SPEED.asInt(), "Published snapshots are immutable");

        YamlConfiguration fromDisk = yaml("hologram_follow_speed: 45\nmagic: 'still invalid'");
        doAnswer(invocation -> {
            when(plugin.getConfig()).thenReturn(fromDisk);
            return null;
        }).when(plugin).reloadConfig();
        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertEquals(45, HOLOGRAM_FOLLOW_SPEED.asInt());
        for (int tick = 0; tick < 200; tick++) {
            MAGIC.getTextColor();
        }
        verify(logger, times(2)).warning(anyString());
    }

    @Test
    void reloadAlsoRebuildsSnapshotWhenPluginReusesSameConfigurationObject() throws Exception {
        CONFIGURATION_FILE_MANAGER.loadConfig();
        doAnswer(invocation -> {
            config.loadFromString("hologram_follow_speed: 31\ndryout: '&(10,20,30)'");
            return null;
        }).when(plugin).reloadConfig();

        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertEquals(31, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertEquals(TextColor.color(10, 20, 30), DRY_OUT.getTextColor());
        verifyNoInteractions(logger);
    }

    @Test
    void inheritedDefaultsCannotOverrideAuthoritativeMissingValueFallbacks() {
        config.addDefault("hologram_follow_speed", 3);
        config.addDefault("enable_hologram_health_bar", true);
        CONFIGURATION_FILE_MANAGER.loadConfig();
        assertEquals(20, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertFalse(ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean());
        verifyNoInteractions(logger);
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration result = new YamlConfiguration();
        result.loadFromString(text);
        return result;
    }

    private Map<ConfigKeys, String> strings() {
        Map<ConfigKeys, String> values = new EnumMap<>(ConfigKeys.class);
        for (ConfigKeys key : ConfigKeys.values()) {
            values.put(key, key.asString());
        }
        return values;
    }
}
