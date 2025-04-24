package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_DAMAGE_INDICATOR;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_FOLLOW_SPEED;
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

class ConfigKeysTest {

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
    void asBoolean_true_returnsTrue() {
        when(mockConfig.getString(anyString())).thenReturn("true");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            assertTrue(ENABLE_DAMAGE_INDICATOR.asBoolean());
        }
    }

    @Test
    void asBoolean_false_returnsFalse() {
        when(mockConfig.getString(anyString())).thenReturn("false");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            assertFalse(ENABLE_DAMAGE_INDICATOR.asBoolean());
        }
    }

    @Test
    void asString_returnsConfigValue() {
        String expected = "123465";
        when(mockConfig.getString(anyString())).thenReturn(expected);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            String result = ConfigKeys.HEALTH_BAR_ALIVE_SYMBOL.asString();
            assertEquals(expected, result);
        }
    }

    @Test
    void asDouble_null_returns0() {
        when(mockConfig.getString(anyString())).thenReturn(null);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            double result = HOLOGRAM_FOLLOW_SPEED.asDouble();
            assertEquals(Double.parseDouble(HOLOGRAM_FOLLOW_SPEED.getDefaultValue()), result);
        }
    }

    @Test
    void asDouble_validString_returnsParsedDouble() {
        double expected = 12345D;
        when(mockConfig.getString(anyString())).thenReturn(String.valueOf(expected));

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            double result = HOLOGRAM_FOLLOW_SPEED.asDouble();
            assertEquals(expected, result);
        }
    }

    @Test
    void asDouble_invalidString_logsWarningAndReturns0() {
        when(mockConfig.getString(anyString())).thenReturn("not a double");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            double result = HOLOGRAM_FOLLOW_SPEED.asDouble();
            assertEquals(0.0, result);
            verify(mockPlugin.getLogger(), times(1)).warning("Error parsing the value in the config file for " + HOLOGRAM_FOLLOW_SPEED.name()
                                                                                                                                      .toLowerCase() + ".");
        }
    }

    @Test
    void asInt_validString_returnsParsedInt() {
        int expected = 1234;
        when(mockConfig.getString(anyString())).thenReturn(String.valueOf(expected));

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            int result = HOLOGRAM_FOLLOW_SPEED.asInt();
            assertEquals(expected, result);
        }
    }

    @Test
    void asInt_null_returnsParsedDefaultValue() {
        when(mockConfig.getString(anyString())).thenReturn(null);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            int expected = Integer.parseInt(HOLOGRAM_FOLLOW_SPEED.getDefaultValue());
            int result = HOLOGRAM_FOLLOW_SPEED.asInt();
            assertEquals(expected, result);
        }
    }

    @Test
    void asInt_invalidString_logsWarningAndReturns0() {
        when(mockConfig.getString(anyString())).thenReturn("not an integer");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            int result = HOLOGRAM_FOLLOW_SPEED.asInt();
            assertEquals(0, result);
            verify(mockPlugin.getLogger(), times(1)).warning("Error parsing the value in the config file for " + HOLOGRAM_FOLLOW_SPEED.name()
                                                                                                                                      .toLowerCase() + ".");
        }
    }

    @Test
    void getTextColor_validRGB_returnsParsedColor() {
        when(mockConfig.getString(anyString())).thenReturn("&(123,123,123)");

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            TextColor result = ConfigKeys.MAGIC.getTextColor();
            assertEquals(TextColor.color(123, 123, 123), result);
        }
    }
}
