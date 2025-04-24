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
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class ReloadConfigCommandTest {

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

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        boolean result = reloadConfigCommand.onCommand(mockCommandSender, mockCommand, LABEL, new String[]{});

        assertTrue(result);
        verify(mockCommandSender, times(1)).sendMessage(captor.capture());
        assertEquals(Component.text(expectedMessage), captor.getValue());
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

            ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
            verify(mockCommandSender, times(1)).sendMessage(captor.capture());
            assertEquals(Component.text(expectedMessage), captor.getValue());
        }
    }

}