package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.*;
import static io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager.CONFIGURATION_FILE_MANAGER;
import static io.github.stealingdapenta.damageindicator.config.ConfigurationTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ConfigKeysTest {

    private DamageIndicator plugin;
    private Logger logger;
    private YamlConfiguration config;
    private MockedStatic<DamageIndicator> instance;

    @BeforeEach
    void setUp() {
        plugin = mock(DamageIndicator.class);
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        instance = mockStatic(DamageIndicator.class);
        instance.when(DamageIndicator::getInstance)
                .thenReturn(plugin);
        freshConfig();
    }

    @AfterEach
    void tearDown() {
        instance.close();
    }

    @Test
    void missingValuesMatchEveryBundledDefault() throws Exception {
        try (var resource = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(resource);
            config.load(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        Map<ConfigKeys, Object> bundledDefaults = new EnumMap<>(ConfigKeys.class);
        for (ConfigKeys key : ConfigKeys.values()) {
            String path = key.getConfigKey();
            assertTrue(config.contains(path, true), "Bundled configuration must explicitly contain " + path);
            Object value = config.get(path, null);
            bundledDefaults.put(key, value);
            assertNativeGetterValue(key, value);
        }
        loadPreloaded();
        Map<ConfigKeys, String> bundled = strings();

        freshConfig();
        loadPreloaded();

        assertEquals(bundled, strings());
        bundledDefaults.forEach(this::assertNativeGetterValue);
        assertTrue(config.getBoolean("enable_damage_indicator"));
        assertEquals(20, config.getInt("hologram_follow_speed"));
        assertFalse(ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean());
        assertFalse(ENABLE_HOLOGRAPHIC_CUSTOM_NAMES.asBoolean());
        assertTrue(HEALTH_BAR_ALWAYS_VISIBLE.asBoolean());
        assertEquals(20, HOLOGRAM_FOLLOW_SPEED.asInt());
        verifyNoInteractions(logger);
    }

    @Test
    void strictBooleansAcceptOnlyBooleansAndTrueFalseStrings() {
        for (Object value : List.of(true, false, "true", "false", " TRUE ", "False")) {
            freshConfig();
            config.set("enable_damage_indicator", value);
            loadPreloaded();
            assertEquals(Boolean.parseBoolean(value.toString()
                                                   .trim()), ENABLE_DAMAGE_INDICATOR.asBoolean());
            verifyNoInteractions(logger);
        }
        for (ConfigKeys key : List.of(ENABLE_DAMAGE_INDICATOR, ENABLE_HOLOGRAM_HEALTH_BAR)) {
            for (Object value : List.of("", "yes", "no", "tru", "0", 1, List.of(true), Map.of("enabled", true))) {
                invalid(key, value);
                assertEquals(Boolean.parseBoolean(key.getDefaultValue()), key.asBoolean(), value.toString());
            }
        }
    }

    @Test
    void emptyAndMalformedYamlBooleanUseDefaultWithDiagnostics() throws Exception {
        for (String yaml : List.of("enable_damage_indicator: ''", "enable_damage_indicator: 'yes'", "enable_damage_indicator: []", "enable_damage_indicator: {enabled: true}")) {
            freshConfig();
            config.loadFromString(yaml);
            loadPreloaded();
            assertTrue(ENABLE_DAMAGE_INDICATOR.asBoolean(), yaml);
            verify(logger).warning(contains("'enable_damage_indicator'"));
        }
    }

    @Test
    void integerBoundsAreAppliedEvenWithoutCallerRangeHelpers() {
        Map<ConfigKeys, Integer> maxima = Map.of(HEALTH_BAR_LENGTH, 100, HEALTH_BAR_DISPLAY_DURATION, 10, HOLOGRAM_FOLLOW_SPEED, 200, DAMAGE_MERGE_JUMP_DURATION, 200);
        for (var entry : maxima.entrySet()) {
            ConfigKeys key = entry.getKey();
            for (int valid : List.of(1, entry.getValue())) {
                freshConfig();
                config.set(key.getConfigKey(), valid);
                loadPreloaded();
                assertEquals(valid, key.asInt());
                assertEquals((double) valid, key.asDouble());
                verifyNoInteractions(logger);
            }
            for (Object value : List.of(-1, 0, entry.getValue() + 1, Integer.MAX_VALUE, Long.MAX_VALUE, "2147483648", "999999999999999999999999999999999999", "", "NaN", "3.0", 2.5, true, List.of(1))) {
                invalid(key, value);
                assertEquals(Integer.parseInt(key.getDefaultValue()), key.asInt(), key + ": " + value);
            }
        }
    }

    @Test
    void decimalBoundariesAllowZeroTimeoutGravityAndNegativeOffsetsVelocity() {
        Map<ConfigKeys, double[]> bounds = new EnumMap<>(ConfigKeys.class);
        bounds.put(DAMAGE_MERGE_TIMEOUT, new double[]{0, 60});
        bounds.put(HOLOGRAM_POSITION, new double[]{-64, 64});
        bounds.put(HOLOGRAM_NAME_POSITION, new double[]{-64, 64});
        bounds.put(HOLOGRAM_VELOCITY_Y, new double[]{-10, 10});
        bounds.put(HOLOGRAM_GRAVITY, new double[]{0, 10});
        for (var entry : bounds.entrySet()) {
            ConfigKeys key = entry.getKey();
            for (double value : entry.getValue()) {
                freshConfig();
                config.set(key.getConfigKey(), value);
                loadPreloaded();
                assertEquals(value, key.asDouble(), key.toString());
                verifyNoInteractions(logger);
            }
            for (Object value : List.of(entry.getValue()[0] - 0.001, entry.getValue()[1] + 0.001, -1e300, 1e300, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "NaN", "Infinity", "-Infinity", "1e309", "", "not a number", true,
                                        List.of(1))) {
                invalid(key, value);
                assertEquals(Double.parseDouble(key.getDefaultValue()), key.asDouble(), key + ": " + value);
            }
        }
    }

    @Test
    void yamlNonfiniteNumbersAndWrongNumericTypesAreDiagnosed() throws Exception {
        for (String yaml : List.of("damage_merge_timeout: .nan", "damage_merge_timeout: .inf", "damage_merge_timeout: -.inf", "damage_merge_timeout: []", "damage_merge_timeout: {seconds: 1}", "damage_merge_timeout: false")) {
            freshConfig();
            config.loadFromString(yaml);
            loadPreloaded();
            assertEquals(1.5, DAMAGE_MERGE_TIMEOUT.asDouble(), yaml);
            verify(logger).warning(contains("'damage_merge_timeout'"));
        }
    }

    @Test
    void quotedNumericScalarsAreTrimmedAndParsed() throws Exception {
        config.loadFromString("hologram_follow_speed: ' 45 '\ndamage_merge_timeout: ' 2.5e1 '");
        loadPreloaded();
        assertEquals(45, HOLOGRAM_FOLLOW_SPEED.asInt());
        assertEquals(25, DAMAGE_MERGE_TIMEOUT.asDouble());
        verifyNoInteractions(logger);
    }

    @Test
    void validColorsUseInclusiveChannelsAndWhitespace() {
        config.set("magic", "&( 0, 255, 123 )");
        loadPreloaded();
        assertEquals(TextColor.color(0, 255, 123), MAGIC.getTextColor());
        assertSame(MAGIC.getTextColor(), MAGIC.getTextColor());
        verifyNoInteractions(logger);
    }

    @Test
    void invalidColorsAndFormattedColorSegmentsAreRejectedRatherThanWrappedOrDisplayedLiterally() {
        for (Object value : List.of("", "red", "&(256,0,0)", "&(999,0,0)", "&(-1,0,0)", "&(1,2)", "&(1,a,3)", "&(1,2,3) trailing", " ".repeat(65), 42, List.of(1, 2, 3))) {
            invalid(MAGIC, value);
            assertEquals(TextColor.color(95, 10, 95), MAGIC.getTextColor(), value.toString());
        }
        for (String value : List.of("HP &(256,0,0)x", "&(1,2)HP", "&(1,a,3)", "HP &(9999,2,3)", "&(1,2,3)valid &(broken)")) {
            invalid(HEALTH_BAR_PREFIX, value);
            assertEquals("&(0,255,0)HP ", HEALTH_BAR_PREFIX.asString());
            assertNotNull(HEALTH_BAR_PREFIX.asFormattedString());
        }
    }

    @Test
    void emptyFormattedTextAndLiteralAmpersandsAreValid() {
        for (ConfigKeys key : HEALTH_BAR_APPEARANCE) {
            freshConfig();
            config.set(key.getConfigKey(), "");
            loadPreloaded();
            assertEquals("", key.asFormattedString()
                                .content());
            verifyNoInteractions(logger);
        }
        freshConfig();
        config.set("health_bar_prefix", "A & Z");
        loadPreloaded();
        assertEquals("A & Z", HEALTH_BAR_PREFIX.asFormattedString()
                                               .content());
        verifyNoInteractions(logger);
    }

    @Test
    void individualTextLimitsAreEnforcedWhenCombinedOutputFits() {
        Map<ConfigKeys, Integer> limits = Map.of(HEALTH_BAR_PREFIX, 256, HEALTH_BAR_SUFFIX, 256, HEALTH_BAR_ALIVE_SYMBOL, 32, HEALTH_BAR_DEAD_SYMBOL, 32);
        for (var entry : limits.entrySet()) {
            ConfigKeys key = entry.getKey();
            int limit = entry.getValue();
            freshConfig();
            for (ConfigKeys appearance : HEALTH_BAR_APPEARANCE) {
                config.set(appearance.getConfigKey(), "");
            }
            config.set(key.getConfigKey(), "x".repeat(limit));
            config.set("health_bar_length", 1);
            loadPreloaded();
            assertEquals(limit, key.asFormattedString()
                                   .content()
                                   .length());
            assertSame(key.asFormattedString(), key.asFormattedString());
            assertEquals(1, HEALTH_BAR_LENGTH.asInt());
            verifyNoInteractions(logger);
            invalid(key, "x".repeat(limit + 1));
            assertEquals(key.getDefaultValue(), key.asString());
            for (Object value : List.of(42, false, List.of("text"), Map.of("text", "value"))) {
                invalid(key, value);
                assertEquals(key.getDefaultValue(), key.asString());
            }
        }
    }

    @Test
    void oversizedCombinedPrefixFallsBackToDefaultAppearanceButRetainsLength() {
        config.set("health_bar_length", 100);
        config.set("health_bar_prefix", "P".repeat(240));
        config.set("health_bar_suffix", "suffix");
        config.set("health_bar_alive_symbol", "A");
        config.set("health_bar_dead_symbol", "D");

        loadPreloaded();

        assertDefaultAppearanceAndBoundedOutput(100);
        assertEquals("P".repeat(240), config.getString("health_bar_prefix"), "Keep user input available for correction");
        verify(logger).warning(contains("256 UTF-16"));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void multicolorRepeatedSymbolFallsBackOnceWithoutHotPathYamlAccess() {
        config.set("health_bar_length", 100);
        config.set("health_bar_prefix", "custom prefix");
        config.set("health_bar_suffix", "custom suffix");
        config.set("health_bar_alive_symbol", "&(1,2,3)A&(4,5,6)B");
        config.set("health_bar_dead_symbol", "D");
        YamlConfiguration tracked = spy(config);
        when(plugin.getConfig()).thenReturn(tracked);

        loadPreloaded();

        assertDefaultAppearanceAndBoundedOutput(100);
        clearInvocations(tracked);
        for (int tick = 0; tick < REPEATED_ACCESS_COUNT; tick++) {
            int alive = tick % (HEALTH_BAR_LENGTH.asInt() + 1);
            assertTrue(serializedBar(alive).length() <= SERIALIZED_HEALTH_BAR_LIMIT);
        }
        verifyNoInteractions(tracked);
        verify(logger).warning(contains("256 UTF-16"));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void intermediateHealthStateCanExceedLimitEvenWhenBothEndpointsFit() {
        config.set("health_bar_length", 20);
        config.set("health_bar_prefix", "P".repeat(234));
        config.set("health_bar_suffix", "");
        config.set("health_bar_alive_symbol", "&bA");
        config.set("health_bar_dead_symbol", "D");
        assertEquals(254, serializedBar(0).length());
        assertEquals(SERIALIZED_HEALTH_BAR_LIMIT, serializedBar(20).length());
        assertTrue(serializedBar(10).length() > SERIALIZED_HEALTH_BAR_LIMIT, "The bold-to-plain reset adds serialized characters");

        loadPreloaded();

        assertDefaultAppearanceAndBoundedOutput(20);
        verify(logger).warning(contains("256 UTF-16"));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void exactCombinedBoundaryIsAcceptedAndCanReplacePreviousFallback() {
        config.set("health_bar_length", 100);
        config.set("health_bar_alive_symbol", "&(1,2,3)A&(4,5,6)B");
        loadPreloaded();
        assertDefaultAppearanceAndBoundedOutput(100);
        clearInvocations(logger);
        int length = 16;
        String suffix = "TAIL";
        String prefix = "P".repeat(SERIALIZED_HEALTH_BAR_LIMIT - length - suffix.length());
        config.set("health_bar_length", length);
        config.set("health_bar_prefix", prefix);
        config.set("health_bar_suffix", suffix);
        config.set("health_bar_alive_symbol", "A");
        config.set("health_bar_dead_symbol", "D");

        loadPreloaded();

        assertEquals(prefix, HEALTH_BAR_PREFIX.asString());
        assertEquals(suffix, HEALTH_BAR_SUFFIX.asString());
        for (int alive = 0; alive <= length; alive++) {
            assertEquals(SERIALIZED_HEALTH_BAR_LIMIT, serializedBar(alive).length());
        }
        verifyNoInteractions(logger);
    }

    @Test
    void loadValidatesEvenUnusedSettingsAndWarnsOnlyOncePerKeyNotPerTick() {
        config.set("magic", "not a color");
        config.set("poison", "&(300,0,0)");
        config.set("health_bar_prefix", "&(1,2) broken");
        loadPreloaded();
        verify(logger, times(3)).warning(anyString());
        for (int tick = 0; tick < REPEATED_ACCESS_COUNT; tick++) {
            MAGIC.getTextColor();
            POISON.getTextColor();
            HEALTH_BAR_PREFIX.asFormattedString();
        }
        verify(logger, times(3)).warning(anyString());
    }

    @Test
    void accessorsUseCachedValuesWithoutAnyYamlReadsAfterLoading() {
        YamlConfiguration tracked = spy(config);
        when(plugin.getConfig()).thenReturn(tracked);
        loadPreloaded();
        clearInvocations(tracked, logger);
        for (int tick = 0; tick < REPEATED_ACCESS_COUNT; tick++) {
            assertTrue(ENABLE_DAMAGE_INDICATOR.asBoolean());
            assertEquals(16, HEALTH_BAR_LENGTH.asIntBetween(1, 100));
            assertEquals(20, HOLOGRAM_FOLLOW_SPEED.asIntAtLeast(1));
            assertEquals(1.5, DAMAGE_MERGE_TIMEOUT.asDoubleAtLeast(0));
            assertEquals(0.15, HOLOGRAM_VELOCITY_Y.asDouble());
            MAGIC.asString();
            MAGIC.getTextColor();
            HEALTH_BAR_PREFIX.asFormattedString();
        }
        verifyNoInteractions(tracked, logger);
    }

    @Test
    void extraAccessorConstraintsFallbackAndWarnOnceForCurrentSnapshot() {
        loadPreloaded();
        for (int tick = 0; tick < REPEATED_ACCESS_COUNT; tick++) {
            assertEquals(30, HOLOGRAM_FOLLOW_SPEED.asIntAtLeast(30));
            assertEquals(10, HEALTH_BAR_LENGTH.asIntBetween(1, 10));
            assertEquals(2, DAMAGE_MERGE_TIMEOUT.asDoubleAtLeast(2));
        }
        verify(logger, times(3)).warning(anyString());
        assertThrows(IllegalArgumentException.class, () -> HEALTH_BAR_LENGTH.asIntBetween(10, 1));
        assertThrows(IllegalArgumentException.class, () -> DAMAGE_MERGE_TIMEOUT.asDoubleAtLeast(Double.NaN));
    }

    @Test
    void changedPluginOrConfigCannotInheritAnOldSnapshot() {
        loadPreloaded();
        config.set("health_bar_length", 42);
        DamageIndicator another = mock(DamageIndicator.class);
        when(another.getConfig()).thenReturn(config);
        when(another.getLogger()).thenReturn(logger);
        instance.when(DamageIndicator::getInstance)
                .thenReturn(another);
        assertEquals(42, HEALTH_BAR_LENGTH.asInt());

        instance.when(DamageIndicator::getInstance)
                .thenReturn(plugin);
        freshConfig();
        config.set("health_bar_length", 24);
        assertEquals(24, HEALTH_BAR_LENGTH.asInt());
    }

    @Test
    void standaloneMockPluginStillReadsItsOwnConfiguration() {
        loadPreloaded();
        freshConfig();
        config.set("enable_hologram_health_bar", "true");
        assertTrue(ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean());
        config.set("enable_hologram_health_bar", "false");
        assertFalse(ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean());
        verifyNoInteractions(logger);
    }

    @Test
    void configurationNamesAreIndependentOfServerLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            config.set("enable_damage_indicator", false);
            loadPreloaded();
            assertFalse(ENABLE_DAMAGE_INDICATOR.asBoolean());
            assertTrue(config.contains("enable_damage_indicator", true));
            assertFalse(config.contains("enable_damage_ındıcator", true));
        } finally {
            Locale.setDefault(original);
        }
    }

    private void assertNativeGetterValue(ConfigKeys key, Object expected) {
        String path = key.getConfigKey();
        Object value = config.get(path);
        assertEquals(expected, value, path);
        switch (key.valueType()) {
            case BOOLEAN -> {
                assertInstanceOf(Boolean.class, value, path);
                assertEquals(expected, config.getBoolean(path), path);
            }
            case INTEGER -> {
                assertInstanceOf(Integer.class, value, path);
                assertEquals(expected, config.getInt(path), path);
            }
            case DECIMAL -> {
                assertInstanceOf(Double.class, value, path);
                assertEquals(expected, config.getDouble(path), path);
            }
            case COLOR, TEXT -> {
                assertInstanceOf(String.class, value, path);
                assertEquals(expected, config.getString(path), path);
            }
        }
    }

    private void freshConfig() {
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        clearInvocations(logger);
    }

    private void invalid(ConfigKeys key, Object value) {
        freshConfig();
        config.set(key.getConfigKey(), value);
        loadPreloaded();
        verify(logger).warning(contains("'" + key.getConfigKey() + "'"));
        verifyNoMoreInteractions(logger);
    }

    private void loadPreloaded() {
        CONFIGURATION_FILE_MANAGER.loadConfig(plugin, plugin.getConfig());
    }

    private void assertDefaultAppearanceAndBoundedOutput(int length) {
        assertEquals(length, HEALTH_BAR_LENGTH.asInt());
        for (ConfigKeys key : HEALTH_BAR_APPEARANCE) {
            assertEquals(key.getDefaultValue(), key.asString(), key.toString());
        }
        for (int alive = 0; alive <= length; alive++) {
            assertTrue(serializedBar(alive).length() <= SERIALIZED_HEALTH_BAR_LIMIT, "Alive count " + alive);
        }
    }
}
