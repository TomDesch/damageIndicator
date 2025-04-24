package io.github.stealingdapenta.damageindicator.config;

import static io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager.CONFIGURATION_FILE_MANAGER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.FileConfigurationOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ConfigurationFileManagerTest {

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
    void loadConfig_void_saveDefaultConfig() {
        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            doNothing().when(mockPlugin)
                       .saveDefaultConfig();
            doNothing().when(mockConfig)
                       .addDefault(anyString(), any());
            FileConfigurationOptions optionsStub = mock(FileConfigurationOptions.class);
            when(mockConfig.options()).thenReturn(optionsStub);

            CONFIGURATION_FILE_MANAGER.loadConfig();

            verify(mockPlugin, times(1)).saveDefaultConfig();
            verify(mockConfig, times(ConfigKeys.values().length)).addDefault(anyString(), any());
            verify(optionsStub, times(1)).copyDefaults(true);
        }
    }

    @Test
    void loadConfig_void_addsDefaultValues() {
        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            doNothing().when(mockPlugin)
                       .saveDefaultConfig();
            doNothing().when(mockConfig)
                       .addDefault(anyString(), any());
            FileConfigurationOptions optionsStub = mock(FileConfigurationOptions.class);
            when(mockConfig.options()).thenReturn(optionsStub);

            CONFIGURATION_FILE_MANAGER.loadConfig();

            for (ConfigKeys defaultConfig : ConfigKeys.values()) {
                verify(mockConfig, times(1)).addDefault(defaultConfig.name()
                                                                     .toLowerCase(), defaultConfig.getDefaultValue());
            }
        }
    }

    @Test
    void loadConfig_void_copiesDefaultsAndSavesConfig() {
        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);
            doNothing().when(mockPlugin)
                       .saveDefaultConfig();
            doNothing().when(mockConfig)
                       .addDefault(anyString(), any());
            FileConfigurationOptions optionsStub = mock(FileConfigurationOptions.class);
            when(mockConfig.options()).thenReturn(optionsStub);

            CONFIGURATION_FILE_MANAGER.loadConfig();

            verify(mockConfig.options(), times(1)).copyDefaults(true);
            verify(mockPlugin, times(1)).saveConfig();
        }
    }

    @Test
    void reloadConfig_void_reloadsPluginConfig() {
        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            CONFIGURATION_FILE_MANAGER.reloadConfig();
            verify(mockPlugin, times(1)).reloadConfig();
        }
    }
}
