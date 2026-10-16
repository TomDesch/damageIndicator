package io.github.stealingdapenta.damageindicator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.ConfigurationLoadException;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class ReloadConfigCommandTest {

    @Test
    void invalidConfigurationReportsFailureInsteadOfSuccess() {
        when(mockCommandSender.hasPermission("damageindicator.reload")).thenReturn(true);
        DamageIndicator plugin = mock(DamageIndicator.class);
        org.mockito.Mockito.doThrow(new ConfigurationLoadException("Invalid YAML", new IllegalArgumentException()))
                .when(plugin).reloadConfiguration();
        try (MockedStatic<DamageIndicator> singleton = mockStatic(DamageIndicator.class)) {
            singleton.when(DamageIndicator::getInstance).thenReturn(plugin);
            assertTrue(reloadConfigCommand.onCommand(mockCommandSender, mockCommand, LABEL, new String[0]));
        }
        verify(mockCommandSender).sendMessage(
                "Configuration reload failed; the previous configuration is still active. Check the server log.");
        verify(mockCommandSender, org.mockito.Mockito.never()).sendMessage(
                "Successfully reloaded the DamageIndicator configuration file.");
    }

    private static final String LABEL = "label";
    private ReloadConfigCommand reloadConfigCommand;
    private CommandSender mockCommandSender;
    private Command mockCommand;

    @BeforeEach
    void setUp() {
        reloadConfigCommand = new ReloadConfigCommand();

        mockCommandSender = mock(CommandSender.class);
        mockCommand = mock(Command.class);
    }

    @Test
    void onCommand_withoutPermission_returnsTrueWithMessage() {
        when(mockCommandSender.hasPermission(anyString())).thenReturn(false);
        String expectedMessage = "You don't have the required damageindicator.reload to execute this command.";

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        boolean result = reloadConfigCommand.onCommand(mockCommandSender, mockCommand, LABEL, new String[]{});

        assertTrue(result);
        verify(mockCommandSender, times(1)).sendMessage(captor.capture());
        assertEquals(expectedMessage, captor.getValue());
    }


    @Test
    void onCommand_withPermission_successWithMessage() {
        String expectedMessage = "Successfully reloaded the DamageIndicator configuration file.";

        when(mockCommandSender.hasPermission(anyString())).thenReturn(true);

        try (MockedStatic<DamageIndicator> mockedStatic = mockStatic(DamageIndicator.class)) {
            DamageIndicator mockPlugin = mock(DamageIndicator.class);
            mockedStatic.when(DamageIndicator::getInstance)
                        .thenReturn(mockPlugin);

            boolean result = reloadConfigCommand.onCommand(mockCommandSender, mockCommand, LABEL, new String[]{});

            assertTrue(result);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mockPlugin).reloadConfiguration();
            verify(mockCommandSender, times(1)).sendMessage(captor.capture());
            assertEquals(expectedMessage, captor.getValue());
        }
    }

}