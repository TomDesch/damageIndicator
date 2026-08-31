package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.utils.AttackerVictimPair;
import io.github.stealingdapenta.damageindicator.utils.MergedDamageInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DamageIndicatorListenerTest {

    private DamageIndicatorListener listener;
    private World world;
    private Location testLocation;

    @BeforeEach
    void setUp() {
        listener = new DamageIndicatorListener();
        world = mock(World.class);
        testLocation = new Location(world, 100, 64, 100);
    }

    // ===== Helper Methods =====

    @SuppressWarnings("unchecked")
    private Map<AttackerVictimPair, MergedDamageInfo> getMergedDamageMap() throws Exception {
        Field mapField = DamageIndicatorListener.class.getDeclaredField("mergedDamageMap");
        mapField.setAccessible(true);
        return (Map<AttackerVictimPair, MergedDamageInfo>) mapField.get(listener);
    }

    @SuppressWarnings("unchecked")
    private Map<ArmorStand, BukkitTask> getActiveAnimationTasks() throws Exception {
        Field mapField = DamageIndicatorListener.class.getDeclaredField("activeAnimationTasks");
        mapField.setAccessible(true);
        return (Map<ArmorStand, BukkitTask>) mapField.get(listener);
    }

    private void invokeUpdateMergedDamageDisplay(MergedDamageInfo info) throws Exception {
        Method method = DamageIndicatorListener.class.getDeclaredMethod("updateMergedDamageDisplay", MergedDamageInfo.class);
        method.setAccessible(true);
        method.invoke(listener, info);
    }

    // ===== Tests for displayDamageIndicator =====

    @Test
    void testDisplayDamageIndicator_NonLivingEntity_DoesNothing() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        org.bukkit.entity.Entity item = mock(org.bukkit.entity.Entity.class);

        when(event.getEntity()).thenReturn(item);

        assertDoesNotThrow(() -> listener.displayDamageIndicator(event));
    }


    @Test
    void testGetHitLocation_ReturnsLocationAboveEntity() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getLocation()).thenReturn(testLocation.clone());

        Location hitLocation = listener.getHitLocation(entity);

        assertEquals(testLocation.getX(), hitLocation.getX(), 0.001);
        assertEquals(testLocation.getY() + 1.0, hitLocation.getY(), 0.001);
        assertEquals(testLocation.getZ(), hitLocation.getZ(), 0.001);
    }

    @Test
    void testGetHitLocation_WithEntityAtDifferentLocation_CalculatesCorrectly() {
        Location entityLoc = new Location(world, 100, 70, -50);
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getLocation()).thenReturn(entityLoc);

        Location hitLocation = listener.getHitLocation(entity);

        assertEquals(100, hitLocation.getX(), 0.001);
        assertEquals(71, hitLocation.getY(), 0.001);
        assertEquals(-50, hitLocation.getZ(), 0.001);
    }

    @Test
    void testAnimationSchedulerUsesPositiveTickInterval() throws Exception {
        Field intervalField = DamageIndicatorListener.class.getDeclaredField("ARMOR_STAND_ANIMATION_INTERVAL_TICKS");
        intervalField.setAccessible(true);

        assertEquals(1, intervalField.getInt(null));
    }

    // ===== Tests for updateMergedDamageDisplay =====

    @Test
    void testUpdateMergedDamageDisplay_ValidArmorStand_UpdatesDisplay() throws Exception {
        ArmorStand mockStand = mock(ArmorStand.class);
        when(mockStand.isValid()).thenReturn(true);

        TextColor color = TextColor.color(255, 0, 0);
        MergedDamageInfo info = new MergedDamageInfo(10.5, color, mockStand);
        info.addDamage(5.25);

        invokeUpdateMergedDamageDisplay(info);

        verify(mockStand, times(1)).setCustomName(any());
    }

    @Test
    void testUpdateMergedDamageDisplay_InvalidArmorStand_DoesNotThrow() {
        ArmorStand mockStand = mock(ArmorStand.class);
        when(mockStand.isValid()).thenReturn(false);

        TextColor color = TextColor.color(255, 0, 0);
        MergedDamageInfo info = new MergedDamageInfo(10.0, color, mockStand);

        assertDoesNotThrow(() -> invokeUpdateMergedDamageDisplay(info));
        verify(mockStand, never()).setCustomName(any());
    }

    @Test
    void testUpdateMergedDamageDisplay_NullArmorStand_DoesNotThrow() {
        TextColor color = TextColor.color(255, 0, 0);
        MergedDamageInfo info = new MergedDamageInfo(10.0, color, null);

        assertDoesNotThrow(() -> invokeUpdateMergedDamageDisplay(info));
    }

    @Test
    void testOnEntityDeath_WithNoMergedDamage_DoesNotThrowException() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getLocation()).thenReturn(testLocation.clone());

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(entity);

        assertDoesNotThrow(() -> listener.onEntityDeath(event));
    }

    @Test
    void testOnEntityDeath_CleansOnlyDamageForInvolvedEntity() throws Exception {
        LivingEntity attacker = mock(LivingEntity.class);
        LivingEntity victim = mock(LivingEntity.class);
        LivingEntity unrelated = mock(LivingEntity.class);
        when(attacker.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(victim.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(unrelated.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        MergedDamageInfo matchingInfo = mock(MergedDamageInfo.class);
        MergedDamageInfo unrelatedInfo = mock(MergedDamageInfo.class);
        Map<AttackerVictimPair, MergedDamageInfo> map = getMergedDamageMap();
        map.put(new AttackerVictimPair(attacker, victim), matchingInfo);
        map.put(new AttackerVictimPair(attacker, unrelated), unrelatedInfo);

        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(victim);
        listener.onEntityDeath(event);

        verify(matchingInfo).cleanup();
        verify(unrelatedInfo, never()).cleanup();
        assertEquals(1, map.size());
    }


    @Test
    void testMergedDamageMap_IsAccessible() throws Exception {
        Map<AttackerVictimPair, MergedDamageInfo> map = getMergedDamageMap();

        assertNotNull(map, "mergedDamageMap should not be null");
        assertEquals(0, map.size(), "Initial map should be empty");
    }

    @Test
    void testMergedDamageMap_IsInitialized() throws Exception {
        Map<AttackerVictimPair, MergedDamageInfo> map = getMergedDamageMap();

        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    void shutdown_CancelsTasksAndRemovesArmorStands() throws Exception {
        ArmorStand armorStand = mock(ArmorStand.class);
        BukkitTask task = mock(BukkitTask.class);
        when(armorStand.isValid()).thenReturn(true);
        when(task.isCancelled()).thenReturn(false);
        getActiveAnimationTasks().put(armorStand, task);

        listener.shutdown();

        verify(task).cancel();
        verify(armorStand).remove();
        assertTrue(getActiveAnimationTasks().isEmpty());
    }

    @Test
    void cleanupLoadedChunks_ProcessesEntitiesLoadedBeforePluginEnable() {
        DamageIndicatorListener listenerSpy = spy(listener);
        Chunk chunk = mock(Chunk.class);
        org.bukkit.entity.Entity entity = mock(org.bukkit.entity.Entity.class);
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[]{entity});
        doNothing().when(listenerSpy).maybeRemoveArmorStandIfTagged(entity);

        try (MockedStatic<Bukkit> bukkitStatic = mockStatic(Bukkit.class)) {
            bukkitStatic.when(Bukkit::getWorlds).thenReturn(List.of(world));
            listenerSpy.cleanupLoadedChunks();
        }

        verify(listenerSpy).maybeRemoveArmorStandIfTagged(entity);
    }

    @Test
    void chunkCleanup_RunsBeforeHologramsCanBeRecreated() throws Exception {
        Method method = DamageIndicatorListener.class.getDeclaredMethod("removeStuckArmorStands", ChunkLoadEvent.class);

        assertEquals(EventPriority.LOWEST, method.getAnnotation(EventHandler.class).priority());
    }
}
