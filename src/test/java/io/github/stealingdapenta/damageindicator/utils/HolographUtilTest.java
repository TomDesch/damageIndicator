package io.github.stealingdapenta.damageindicator.utils;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.bukkit.World;

class HolographUtilTest {

    @AfterEach
    void clearOwnership() {
        HOLOGRAPH_UTIL.clearTracking();
    }

    @Test
    void activeOwnershipDoesNotTreatAnInvalidWrapperAsAnActiveHologram() {
        ArmorStand stand = mock(ArmorStand.class);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stand.isValid()).thenReturn(true);
        HOLOGRAPH_UTIL.track(stand);
        assertTrue(HOLOGRAPH_UTIL.isActive(stand));

        when(stand.isValid()).thenReturn(false);
        assertFalse(HOLOGRAPH_UTIL.isActive(stand));
        HOLOGRAPH_UTIL.forget(stand);
        when(stand.isValid()).thenReturn(true);
        assertFalse(HOLOGRAPH_UTIL.isActive(stand));
    }

    @Test
    void removingAHologramAlsoReleasesSameSessionOwnership() {
        ArmorStand stand = mock(ArmorStand.class);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stand.isValid()).thenReturn(true);
        HOLOGRAPH_UTIL.track(stand);

        HOLOGRAPH_UTIL.remove(stand);

        verify(stand).remove();
        assertFalse(HOLOGRAPH_UTIL.isActive(stand));
    }

    @Test
    void administrativeCleanupDetachesTrackingBeforeRemovingTheEntity() {
        ArmorStand stand = mock(ArmorStand.class);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stand.isValid()).thenReturn(true);
        Runnable cleanup = mock(Runnable.class);
        HOLOGRAPH_UTIL.track(stand);
        HOLOGRAPH_UTIL.onCleanup(stand, cleanup);

        HOLOGRAPH_UTIL.cleanup(stand);
        HOLOGRAPH_UTIL.cleanup(stand);

        verify(cleanup).run();
        assertFalse(HOLOGRAPH_UTIL.isActive(stand));
    }

    @Test
    void unchangedHologramsDoNotTeleportButMovementStillFollows() {
        ArmorStand stand = mock(ArmorStand.class);
        World world = mock(World.class);
        Location start = new Location(world, 1, 2, 3);
        when(stand.getLocation()).thenReturn(start);
        when(stand.teleport(any(Location.class))).thenReturn(true);

        assertTrue(HOLOGRAPH_UTIL.teleportIfMoved(stand, start.clone()));
        verify(stand, never()).teleport(any(Location.class));
        Location moved = start.clone().add(1, 0, 0);
        assertTrue(HOLOGRAPH_UTIL.teleportIfMoved(stand, moved));
        verify(stand).teleport(moved);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createdHologramsAreTransientAndRegisteredAfterSpawning() {
        World world = mock(World.class);
        ArmorStand stand = mock(ArmorStand.class);
        var data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(stand.getPersistentDataContainer()).thenReturn(data);
        when(stand.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stand.isValid()).thenReturn(true);
        when(world.spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<ArmorStand> configure = invocation.getArgument(2);
            configure.accept(stand);
            return stand;
        });
        try (var keys = org.mockito.Mockito.mockStatic(
                io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener.class)) {
            keys.when(io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener::getCustomNamespacedKey)
                    .thenReturn(new org.bukkit.NamespacedKey("damageindicator", "customnsktag"));
            assertEquals(stand, HOLOGRAPH_UTIL.createArmorStandHologram(new Location(world, 0, 0, 0), Component.text("Name")));
        }
        verify(stand).setPersistent(false);
        assertTrue(HOLOGRAPH_UTIL.isActive(stand));
    }

    @Test
    void locationAboveEntity_entity_returnsLocation() {
        LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(livingEntity.getHeight()).thenReturn(1.0);

        Location resultLocation = HOLOGRAPH_UTIL.locationAboveEntity(livingEntity, 2.0);

        assertNotNull(resultLocation);
        assertEquals(0, resultLocation.getX());
        assertEquals(3.0, resultLocation.getY()); // 1.0 (entity height) + 2.0 (position)
        assertEquals(0, resultLocation.getZ());
    }

    @Test
    void cancelHologramFor_livingEntityAndData_success() {
        LivingEntity livingEntity = mock(LivingEntity.class);
        Map<LivingEntity, LivingEntityTaskInfo> data = new HashMap<>();

        BukkitTaskStub taskStub = new BukkitTaskStub();
        ArmorStand armorStand = mock(ArmorStand.class);
        LivingEntityTaskInfo taskInfo = new LivingEntityTaskInfo(taskStub, armorStand);
        data.put(livingEntity, taskInfo);
        when(taskInfo.armorStand()
                     .isValid()).thenReturn(true);

        HOLOGRAPH_UTIL.cancelHologramFor(livingEntity, data);

        assertTrue(taskStub.isCancelled());
        verify(armorStand).remove();

        assertFalse(data.containsKey(livingEntity));
    }

    private static class BukkitTaskStub implements BukkitTask {

        private boolean cancelled = false;

        @Override
        public synchronized void cancel() throws IllegalStateException {
            this.cancelled = true;
        }

        @Override
        public int getTaskId() {
            return 0;
        }

        @Override
        public @NotNull Plugin getOwner() {
            return mock(Plugin.class);
        }

        @Override
        public boolean isSync() {
            return false;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
