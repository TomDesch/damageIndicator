package io.github.stealingdapenta.damageindicator.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HolographUtilTest {

    private HolographUtil holographUtil;

    @BeforeEach
    void setUp() {
        holographUtil = HolographUtil.getInstance();
    }

    @Test
    void locationAboveEntity_entity_returnsLocation() {
        LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.getLocation()).thenReturn(new Location(null, 0, 0, 0));
        when(livingEntity.getHeight()).thenReturn(1.0);

        Location resultLocation = holographUtil.locationAboveEntity(livingEntity, 2.0);

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
        when(taskInfo.getArmorStand()
                     .isValid()).thenReturn(true);

        holographUtil.cancelHologramFor(livingEntity, data);

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
