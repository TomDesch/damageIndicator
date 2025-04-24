package io.github.stealingdapenta.damageindicator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class AreaRemoveCommandTest {

    private static final String LABEL = "label";
    private AreaRemoveCommand areaRemoveCommand;
    private CommandSender mockCommandSender;
    private Command mockCommand;
    private Player mockPlayer;

    @BeforeEach
    void setUp() {
        areaRemoveCommand = new AreaRemoveCommand();
        mockCommandSender = mock(CommandSender.class);
        mockCommand = mock(Command.class);
        mockPlayer = mock(Player.class);
        when(mockPlayer.hasPermission(anyString())).thenReturn(true);
    }

    @Test
    void onCommand_withoutPlayer_returnsTrue() {
        boolean result = areaRemoveCommand.onCommand(mockCommandSender, mockCommand, LABEL, new String[]{});
        assertTrue(result);
    }

    @Test
    void onCommand_withoutPermission_returnsTrueWithMessage() {
        when(mockPlayer.hasPermission(anyString())).thenReturn(false);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        boolean result = areaRemoveCommand.onCommand(mockPlayer, mockCommand, LABEL, new String[]{});

        assertTrue(result);
        verify(mockPlayer).sendMessage(captor.capture());
        assertEquals(Component.text("You don't have the required damageindicator.arearemove to execute this command."), captor.getValue());
    }

    @Test
    void onCommand_with2Enemies_successWithMessage() {
        Entity mockEntity1 = mock(Entity.class);
        Entity mockEntity2 = mock(Entity.class);

        List<Entity> nearbyEntities = Arrays.asList(mockEntity1, mockEntity2);
        when(mockPlayer.getNearbyEntities(50, 50, 50)).thenReturn(nearbyEntities);

        PersistentDataContainer mockDataContainer1 = mock(PersistentDataContainer.class);
        PersistentDataContainer mockDataContainer2 = mock(PersistentDataContainer.class);

        when(mockEntity1.getPersistentDataContainer()).thenReturn(mockDataContainer1);
        when(mockEntity2.getPersistentDataContainer()).thenReturn(mockDataContainer2);

        when(mockDataContainer1.getOrDefault(any(), any(), anyBoolean())).thenReturn(true);
        when(mockDataContainer2.getOrDefault(any(), any(), anyBoolean())).thenReturn(true);

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);

        try (MockedStatic<DamageIndicatorListener> mockedStatic = mockStatic(DamageIndicatorListener.class)) {
            mockedStatic.when(DamageIndicatorListener::getCustomNamespacedKey)
                        .thenReturn(mock(NamespacedKey.class));

            boolean result = areaRemoveCommand.onCommand(mockPlayer, mockCommand, LABEL, new String[]{});
            assertTrue(result);
        }

        verify(mockPlayer).getNearbyEntities(50, 50, 50);
        verify(mockEntity1).getPersistentDataContainer();
        verify(mockEntity2).getPersistentDataContainer();
        verify(mockEntity1).remove();
        verify(mockEntity2).remove();
        verify(mockPlayer).sendMessage(captor.capture());

        assertEquals(Component.text("Successfully removed 2 nearby entities."), captor.getValue());
    }
}
