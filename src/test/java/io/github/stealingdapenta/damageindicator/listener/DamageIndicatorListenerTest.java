package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.utils.AttackerVictimPair;
import io.github.stealingdapenta.damageindicator.utils.MergedDamageInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    // ===== Tests for updateMergedDamageDisplay =====

    @Test
    void testUpdateMergedDamageDisplay_ValidArmorStand_UpdatesDisplay() throws Exception {
        ArmorStand mockStand = mock(ArmorStand.class);
        when(mockStand.isValid()).thenReturn(true);

        TextColor color = TextColor.color(255, 0, 0);
        MergedDamageInfo info = new MergedDamageInfo(10.5, color, mockStand);
        info.addDamage(5.25);

        invokeUpdateMergedDamageDisplay(info);

        verify(mockStand, times(1)).customName(any());
    }

    @Test
    void testUpdateMergedDamageDisplay_InvalidArmorStand_DoesNotThrow() {
        ArmorStand mockStand = mock(ArmorStand.class);
        when(mockStand.isValid()).thenReturn(false);

        TextColor color = TextColor.color(255, 0, 0);
        MergedDamageInfo info = new MergedDamageInfo(10.0, color, mockStand);

        assertDoesNotThrow(() -> invokeUpdateMergedDamageDisplay(info));
        verify(mockStand, never()).customName(any());
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
}

