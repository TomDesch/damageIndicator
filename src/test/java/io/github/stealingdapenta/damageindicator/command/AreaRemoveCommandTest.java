package io.github.stealingdapenta.damageindicator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.bukkit.persistence.PersistentDataType.BOOLEAN;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import io.github.stealingdapenta.damageindicator.support.MemoryPersistentData;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        boolean result = areaRemoveCommand.onCommand(mockPlayer, mockCommand, LABEL, new String[]{});

        assertTrue(result);
        verify(mockPlayer).sendMessage(captor.capture());
        assertEquals("You don't have the required damageindicator.arearemove to execute this command.", captor.getValue());
    }

    @Test
    void onCommand_with2Enemies_successWithMessage() {
        ArmorStand mockEntity1 = mock(ArmorStand.class);
        ArmorStand mockEntity2 = mock(ArmorStand.class);
        when(mockEntity1.isValid()).thenReturn(true);
        when(mockEntity2.isValid()).thenReturn(true);

        List<Entity> nearbyEntities = Arrays.asList(mockEntity1, mockEntity2);
        when(mockPlayer.getNearbyEntities(50, 50, 50)).thenReturn(nearbyEntities);

        MemoryPersistentData data1 = new MemoryPersistentData();
        MemoryPersistentData data2 = new MemoryPersistentData();
        data1.set(DamageIndicatorListener.getCustomNamespacedKey(), BOOLEAN, true);
        data2.set(DamageIndicatorListener.getCustomNamespacedKey(), BOOLEAN, true);

        when(mockEntity1.getPersistentDataContainer()).thenReturn(data1);
        when(mockEntity2.getPersistentDataContainer()).thenReturn(data2);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        boolean result = areaRemoveCommand.onCommand(mockPlayer, mockCommand, LABEL, new String[]{});
        assertTrue(result);

        verify(mockPlayer).getNearbyEntities(50, 50, 50);
        verify(mockEntity1).getPersistentDataContainer();
        verify(mockEntity2).getPersistentDataContainer();
        verify(mockEntity1).remove();
        verify(mockEntity2).remove();
        verify(mockPlayer).sendMessage(captor.capture());

        assertEquals("Successfully removed 2 nearby entities.", captor.getValue());
    }

    @Test
    void denialDoesNotQueryEntitiesOrRunCleanup() {
        when(mockPlayer.hasPermission("damageindicator.arearemove")).thenReturn(false);
        areaRemoveCommand.onCommand(mockPlayer, mockCommand, LABEL, new String[0]);
        verify(mockPlayer, never()).getNearbyEntities(50, 50, 50);
    }

    @Test
    void untaggedArmorStandsAndTaggedNonArmorStandsAreNotRemoved() {
        ArmorStand unrelated = mock(ArmorStand.class);
        Entity otherType = mock(Entity.class);
        when(unrelated.isValid()).thenReturn(true);
        when(unrelated.getPersistentDataContainer()).thenReturn(new MemoryPersistentData());
        when(mockPlayer.getNearbyEntities(50, 50, 50)).thenReturn(List.of(unrelated, otherType));

        areaRemoveCommand.onCommand(mockPlayer, mockCommand, LABEL, new String[0]);

        verify(unrelated, never()).remove();
        verify(otherType, never()).remove();
        verify(otherType, never()).getPersistentDataContainer();
    }
}
