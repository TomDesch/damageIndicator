package io.github.stealingdapenta.damageindicator.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture.Model;
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
        try (ListenerFixture fixture = new ListenerFixture()) {
            fixture.open();
            Model victim = fixture.mob("Health bar");
            victim.visible = true;
            when(player.hasPermission("damageindicator.arearename")).thenReturn(true);
            when(player.getNearbyEntities(50, 50, 50)).thenReturn(List.of(victim.entity));
            AreaRenameCommand command = new AreaRenameCommand(entity -> victim.name = "Restored original");

            command.onCommand(player, mock(Command.class), "arearename", new String[0]);

            assertNull(victim.name);
            assertFalse(victim.visible);
            verify(player).sendMessage("Successfully renamed 1 nearby entities.");
        }
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
