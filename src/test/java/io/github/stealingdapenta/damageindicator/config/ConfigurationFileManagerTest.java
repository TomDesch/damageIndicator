package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.*;
import static io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager.CONFIGURATION_FILE_MANAGER;
import static io.github.stealingdapenta.damageindicator.config.ConfigurationTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class ConfigurationFileManagerTest {

    @TempDir
    Path directory;

    private DamageIndicator plugin;
    private Logger logger;
    private YamlConfiguration config;
    private Path file;
    private MockedStatic<DamageIndicator> instance;

    @BeforeEach
    void setUp() throws IOException {
        plugin = mock(DamageIndicator.class);
        logger = mock(Logger.class);
        config = new YamlConfiguration();
        file = directory.resolve("config.yml");
        write("");
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        doAnswer(invocation -> {
            config.save(file.toFile());
            return null;
        }).when(plugin).saveConfig();
        instance = mockStatic(DamageIndicator.class);
        instance.when(DamageIndicator::getInstance).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        instance.close();
    }

    @Test
    void loadCopiesAllAuthoritativeDefaultsAndSavesWithoutWritingInvalidFallbacks() throws Exception {
        write("health_bar_length: -1\nunrelated_setting: preserved");

        CONFIGURATION_FILE_MANAGER.loadConfig();

        verify(plugin).saveDefaultConfig();
        verify(plugin).saveConfig();
        verify(plugin, never()).reloadConfig();
        assertTrue(config.options().copyDefaults());
        for (ConfigKeys key : ConfigKeys.values()) {
            assertTrue(config.contains(key.getConfigKey()), key.toString());
        }
        assertEquals(16, HEALTH_BAR_LENGTH.asInt());
        assertEquals(-1, config.getInt("health_bar_length"));
        assertTrue(Files.readString(file).contains("unrelated_setting: preserved"));
        verify(logger).warning(contains("'health_bar_length'"));
    }

    @Test
    void firstEnableCreatesBundledFileBeforeStrictLoading() throws Exception {
        Files.delete(file);
        doAnswer(invocation -> {
            write("hologram_follow_speed: 20\n# Bundled configuration");
            return null;
        }).when(plugin).saveDefaultConfig();

        CONFIGURATION_FILE_MANAGER.loadConfig();

        assertEquals(20, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertTrue(Files.exists(file));
        verify(plugin).saveDefaultConfig();
        verify(plugin, never()).reloadConfig();
        verifyNoInteractions(logger);
    }

    @Test
    void explicitLegacyValueWinsOverBundledCanonicalDefaultEvenWhenCopyDefaultsIsEnabled() throws Exception {
        write("dryout: '&(1,2,3)'");
        config.addDefault("dry_out", "&(244,164,96)");
        config.options().copyDefaults(true);
        assertFalse(config.contains("dry_out", true));

        CONFIGURATION_FILE_MANAGER.loadConfig();

        assertTrue(config.contains("dry_out", true));
        assertEquals("&(1,2,3)", config.getString("dry_out"));
        assertEquals(TextColor.color(1, 2, 3), DRY_OUT.getTextColor());
        assertTrue(Files.readString(file).contains("dry_out:"));
        verifyNoInteractions(logger);
    }

    @Test
    void explicitCanonicalValueAlwaysWinsIncludingMalformedAndEmptyValues() throws Exception {
        for (String canonical : List.of("&(4,5,6)", "invalid", "")) {
            write("dryout: '&(1,2,3)'\ndry_out: '" + canonical + "'");
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
        write("dryout: 'not a color'");
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
            write(yaml);
            CONFIGURATION_FILE_MANAGER.loadConfig();
            Map<ConfigKeys, String> enabledValues = strings();
            byte[] enabledYaml = Files.readAllBytes(file);
            clearInvocations(plugin, logger);
            write(yaml);

            CONFIGURATION_FILE_MANAGER.reloadConfig();

            assertEquals(enabledValues, strings(), yaml);
            assertArrayEquals(enabledYaml, Files.readAllBytes(file), yaml);
            verify(plugin, never()).reloadConfig();
            verify(plugin).saveConfig();
            verify(plugin, never()).saveDefaultConfig();
        }
    }

    @Test
    void reloadPublishesNewSnapshotAndDiagnosticsOnlyOncePerReload() throws Exception {
        write("hologram_follow_speed: 15\nmagic: 'invalid'");
        CONFIGURATION_FILE_MANAGER.loadConfig();
        assertEquals(15, HOLOGRAM_FOLLOW_SPEED.asInt());
        verify(logger).warning(contains("'magic'"));
        config.set("hologram_follow_speed", 77);
        assertEquals(15, HOLOGRAM_FOLLOW_SPEED.asInt(), "Published snapshots are immutable");
        write("hologram_follow_speed: 45\nmagic: 'still invalid'");

        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertEquals(45, HOLOGRAM_FOLLOW_SPEED.asInt());
        for (int tick = 0; tick < REPEATED_ACCESS_COUNT; tick++) {
            MAGIC.getTextColor();
        }
        verify(logger, times(2)).warning(anyString());
    }

    @Test
    void reloadRebuildsExistingConfigurationObjectWithoutPermissiveBukkitReload() throws Exception {
        CONFIGURATION_FILE_MANAGER.loadConfig();
        write("hologram_follow_speed: 31\ndryout: '&(10,20,30)'");

        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertSame(config, plugin.getConfig());
        assertEquals(31, config.getInt("hologram_follow_speed"));
        assertEquals(31, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertEquals(TextColor.color(10, 20, 30), DRY_OUT.getTextColor());
        verify(plugin, never()).reloadConfig();
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

    @Test
    void malformedYamlReloadPreservesExactFileBytesActiveObjectAndSnapshotThenRecovers() throws Exception {
        write("hologram_follow_speed: 15\nhealth_bar_prefix: 'old prefix'\ndryout: '&(1,2,3)'");
        CONFIGURATION_FILE_MANAGER.loadConfig();
        var oldComponent = HEALTH_BAR_PREFIX.asFormattedString();
        for (String malformed : List.of("# untouched CRLF\r\nhologram_follow_speed: [20\r\n",
                "hologram_follow_speed: 45\nbroken: {unterminated\n", "- not a mapping\n- still not a mapping\n")) {
            byte[] original = malformed.getBytes(StandardCharsets.UTF_8);
            Files.write(file, original);
            clearInvocations(plugin, logger);
            String previousConfig = config.saveToString();

            ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class,
                    CONFIGURATION_FILE_MANAGER::reloadConfig);

            assertInstanceOf(InvalidConfigurationException.class, failure.getCause());
            assertTrue(failure.getMessage().contains("config.yml"));
            assertArrayEquals(original, Files.readAllBytes(file));
            verify(plugin, never()).getConfig();
            verify(plugin, never()).reloadConfig();
            verify(plugin, never()).saveConfig();
            assertSame(config, plugin.getConfig());
            assertEquals(previousConfig, config.saveToString());
            assertEquals(15, HOLOGRAM_FOLLOW_SPEED.asInt());
            assertSame(oldComponent, HEALTH_BAR_PREFIX.asFormattedString());
            assertEquals(TextColor.color(1, 2, 3), DRY_OUT.getTextColor());
            verify(logger).severe(contains("config.yml"));
        }
        write("hologram_follow_speed: 47\nhealth_bar_prefix: 'recovered'\ndryout: '&(4,5,6)'");
        clearInvocations(logger);

        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertEquals(47, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertEquals("recovered", HEALTH_BAR_PREFIX.asString());
        assertEquals(TextColor.color(4, 5, 6), DRY_OUT.getTextColor());
        verifyNoInteractions(logger);
    }

    @Test
    void malformedFirstEnableDoesNotReplaceOrSaveExistingFile() throws Exception {
        byte[] original = "# original bytes\r\nhologram_follow_speed: [\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        config.set("untouched", "previous value");

        assertThrows(ConfigurationLoadException.class, CONFIGURATION_FILE_MANAGER::loadConfig);

        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals("previous value", config.getString("untouched"));
        verify(plugin).saveDefaultConfig();
        verify(plugin, never()).getConfig();
        verify(plugin, never()).saveConfig();
        verify(logger).severe(contains("config.yml"));
    }

    @Test
    void unreadableUtf8BytesAbortReloadWithoutChangingBytesOrRuntime() throws Exception {
        write("hologram_follow_speed: 19");
        CONFIGURATION_FILE_MANAGER.loadConfig();
        byte[] invalidUtf8 = new byte[]{(byte) 0xC3, 0x28, 0x0D, 0x0A};
        Files.write(file, invalidUtf8);
        clearInvocations(plugin, logger);
        String previousConfig = config.saveToString();

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class,
                CONFIGURATION_FILE_MANAGER::reloadConfig);

        assertInstanceOf(IOException.class, failure.getCause());
        assertArrayEquals(invalidUtf8, Files.readAllBytes(file));
        verify(plugin, never()).getConfig();
        verify(plugin, never()).saveConfig();
        assertEquals(previousConfig, config.saveToString());
        assertEquals(19, HOLOGRAM_FOLLOW_SPEED.asInt());
        verify(logger).severe(contains("config.yml"));
    }

    @Test
    void missingReloadFileIsNotRecreatedAndPreviousRuntimeRemainsActive() throws Exception {
        write("hologram_follow_speed: 29");
        CONFIGURATION_FILE_MANAGER.loadConfig();
        Files.delete(file);
        clearInvocations(plugin, logger);

        assertThrows(ConfigurationLoadException.class, CONFIGURATION_FILE_MANAGER::reloadConfig);

        assertFalse(Files.exists(file));
        verify(plugin, never()).getConfig();
        verify(plugin, never()).saveDefaultConfig();
        verify(plugin, never()).saveConfig();
        assertEquals(29, HOLOGRAM_FOLLOW_SPEED.asInt());
        verify(logger).severe(contains("config.yml"));
    }

    @Test
    void validSingleReadContentsAreAppliedInsteadOfReadingDiskAgainDuringCommit() throws Exception {
        CONFIGURATION_FILE_MANAGER.loadConfig();
        write("hologram_follow_speed: 37");
        when(plugin.getConfig()).thenAnswer(invocation -> {
            write("hologram_follow_speed: [broken");
            return config;
        });

        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertEquals(37, config.getInt("hologram_follow_speed"));
        // Avoid the deliberately side-effectful plugin getter while inspecting the committed result.
        doReturn(config).when(plugin).getConfig();
        assertEquals(37, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertTrue(Files.readString(file).contains("hologram_follow_speed: 37"));
        verify(plugin, never()).reloadConfig();
        verifyNoInteractions(logger);
    }

    @Test
    void enableAndReloadApplySameBoundedAppearanceFallbackWhileKeepingConfiguredLength() throws Exception {
        String input = "health_bar_length: 100\nhealth_bar_prefix: 'custom prefix'\nhealth_bar_suffix: 'custom suffix'\n"
                + "health_bar_alive_symbol: '&(1,2,3)A&(4,5,6)B'\nhealth_bar_dead_symbol: 'D'\n";
        write(input);
        CONFIGURATION_FILE_MANAGER.loadConfig();
        List<String> enabledBars = IntStream.rangeClosed(0, 100)
                .mapToObj(ConfigurationTestSupport::serializedBar).toList();
        assertEquals(100, HEALTH_BAR_LENGTH.asInt());
        assertTrue(enabledBars.stream().allMatch(bar -> bar.length() <= SERIALIZED_HEALTH_BAR_LIMIT));
        verify(logger).warning(contains("256 UTF-16"));
        write(input);

        CONFIGURATION_FILE_MANAGER.reloadConfig();

        assertEquals(enabledBars, IntStream.rangeClosed(0, 100)
                .mapToObj(ConfigurationTestSupport::serializedBar).toList());
        assertEquals(100, HEALTH_BAR_LENGTH.asInt());
        assertEquals("&(1,2,3)A&(4,5,6)B", config.getString("health_bar_alive_symbol"));
        assertEquals(HEALTH_BAR_PREFIX.getDefaultValue(), HEALTH_BAR_PREFIX.asString());
        verify(logger, times(2)).warning(anyString());
        verifyNoMoreInteractions(logger);
    }

    private void write(String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

}
