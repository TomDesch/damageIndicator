package io.github.stealingdapenta.damageindicator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DefaultConfigValueTest {

    private DamageIndicator mockPlugin;

    private FileConfiguration mockConfig;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(DamageIndicator.class);
        mockConfig = mock(FileConfiguration.class);

        when(mockPlugin.getConfig()).thenReturn(mockConfig);
        when(mockPlugin.getLogger()).thenReturn(mock(Logger.class));
    }

    @Test
    void getBooleanValue_true_returnsTrue() {
        when(mockConfig.getString(anyString())).thenReturn("true");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            boolean result = DefaultConfigValue.ENABLE_DAMAGE_INDICATOR.getBooleanValue();
            assertTrue(result);
        }
    }

    @Test
    void getBooleanValue_false_returnsFalse() {
        when(mockConfig.getString(anyString())).thenReturn("false");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            boolean result = DefaultConfigValue.ENABLE_DAMAGE_INDICATOR.getBooleanValue();
            assertFalse(result);
        }
    }

    @Test
    void getStringValue_value_returnsAsString() {
        String value = "123465";
        when(mockConfig.getString(anyString())).thenReturn(value);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            String result = DefaultConfigValue.HEALTH_BAR_ALIVE_SYMBOL.getStringValue();
            assertEquals(value, result);
        }
    }

    @Test
    void getDoubleValue_null_returns0() {
        when(mockConfig.getString(anyString())).thenReturn(null);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            double result = DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED.getDoubleValue();
            assertEquals(0, result);
        }
    }

    @Test
    void getDoubleValue_value_returnsAsDouble() {
        double value = 12345D;
        when(mockConfig.getString(anyString())).thenReturn(String.valueOf(value));

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            double result = DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED.getDoubleValue();
            assertEquals(value, result);
        }
    }

    @Test
    void getDoubleValue_notADouble_handleParseError() {
        when(mockConfig.getString(anyString())).thenReturn("not a double");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            double result = DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED.getDoubleValue();
            assertEquals(0, result);
            verify(mockPlugin.getLogger(), times(1)).warning("Error parsing the value in the config file for hologram_follow_speed.");
        }
    }

    @Test
    void getIntValue_value_returnsAsInt() {
        int value = 1234;
        when(mockConfig.getString(anyString())).thenReturn(String.valueOf(value));

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            int result = DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED.getIntValue();
            assertEquals(value, result);
        }
    }

    @Test
    void getIntValue_null_returns0() {
        when(mockConfig.getString(anyString())).thenReturn(null);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            int result = DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED.getIntValue();
            assertEquals(0, result);
        }
    }

    @Test
    void getIntValue_notAnInt_handlesParseError() {
        when(mockConfig.getString(anyString())).thenReturn("not an integer");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            int result = DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED.getIntValue();
            assertEquals(0, result);
            verify(mockPlugin.getLogger(), times(1)).warning("Error parsing the value in the config file for hologram_follow_speed.");
        }
    }

    @Test
    void getTextColor_notRGB_returnsDefaultColorOnError() {
        when(mockConfig.getString(anyString())).thenReturn("invalid_rgb_format");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            TextColor result = DefaultConfigValue.FIRE.getTextColor();
            assertEquals(TextColor.color(255, 255, 255), result);
            verify(mockPlugin.getLogger(), times(1)).warning("Invalid RGB format for key fire");
        }
    }

    @Test
    void getTextColor_validRGB_returnsParsedColor() {
        when(mockConfig.getString(anyString())).thenReturn("(123,123,123)");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            TextColor result = DefaultConfigValue.MAGIC.getTextColor();
            ;
            assertEquals(TextColor.color(123, 123, 123), result);
        }
    }

}