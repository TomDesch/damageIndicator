package io.github.stealingdapenta.damageindicator.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class AreaRenameCommandTest {

    @Test
    @SuppressWarnings("unchecked")
    void nonPlayerAndDeniedPlayerHaveNoCleanupSideEffects() {
        Consumer<LivingEntity> cleanup = mock(Consumer.class);
        AreaRenameCommand command = new AreaRenameCommand(cleanup);
        CommandSender console = mock(CommandSender.class);
        Player denied = mock(Player.class);

        command.onCommand(console, mock(Command.class), "arearename", new String[0]);
        command.onCommand(denied, mock(Command.class), "arearename", new String[0]);

        verify(cleanup, never()).accept(any());
        verify(denied, never()).getNearbyEntities(50, 50, 50);
    }

    @Test
    void displayRestorationHappensBeforeTheExplicitNameWipe() {
        Player player = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        AtomicReference<String> name = new AtomicReference<>("Health bar");
        AtomicBoolean visible = new AtomicBoolean(true);
        when(player.hasPermission("damageindicator.arearename")).thenReturn(true);
        when(player.getNearbyEntities(50, 50, 50)).thenReturn(List.of(victim));
        when(victim.isValid()).thenReturn(true);
        when(victim.getCustomName()).thenAnswer(invocation -> name.get());
        doAnswer(invocation -> {
            name.set(invocation.getArgument(0));
            return null;
        }).when(victim).setCustomName(any());
        doAnswer(invocation -> {
            visible.set(invocation.getArgument(0));
            return null;
        }).when(victim).setCustomNameVisible(anyBoolean());
        AreaRenameCommand command = new AreaRenameCommand(entity -> name.set("Restored original"));

        command.onCommand(player, mock(Command.class), "arearename", new String[0]);

        assertNull(name.get());
        assertFalse(visible.get());
        verify(player).sendMessage("Successfully renamed 1 nearby entities.");
    }

    @Test
    void unnamedEntitiesAlsoHavePendingDisplayWorkDetached() {
        Player player = mock(Player.class);
        LivingEntity unnamed = mock(LivingEntity.class);
        Entity namedOtherType = mock(Entity.class);
        when(player.hasPermission("damageindicator.arearename")).thenReturn(true);
        when(player.getNearbyEntities(50, 50, 50)).thenReturn(List.of(unnamed, namedOtherType));
        when(unnamed.isValid()).thenReturn(true);
        when(namedOtherType.isValid()).thenReturn(true);
        when(namedOtherType.getCustomName()).thenReturn("Other plugin name");
        @SuppressWarnings("unchecked") Consumer<LivingEntity> cleanup = mock(Consumer.class);

        new AreaRenameCommand(cleanup).onCommand(player, mock(Command.class), "arearename", new String[0]);

        verify(cleanup).accept(unnamed);
        verify(namedOtherType).setCustomName(null);
        verify(namedOtherType).setCustomNameVisible(false);
        verify(player).sendMessage("Successfully renamed 1 nearby entities.");
    }
}
