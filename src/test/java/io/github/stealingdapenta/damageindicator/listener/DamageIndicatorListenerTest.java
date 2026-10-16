package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static org.bukkit.persistence.PersistentDataType.BOOLEAN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.support.ListenerFixture;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture.Model;
import io.github.stealingdapenta.damageindicator.support.MemoryPersistentData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DamageIndicatorListenerTest {

    private static final int MERGE_TIMEOUT_TICKS = 30;
    private static final int FALL_DURATION_TICKS = 15;
    private static final int ACTIVE_PAIR_COUNT = 50;
    private static final double DISPLAY_Y = ListenerFixture.GROUND_Y + 1;

    private final DamageIndicatorListener listener = new DamageIndicatorListener();
    private final ListenerFixture fixture = new ListenerFixture();
    private final World world = fixture.world;
    private final BukkitScheduler scheduler = fixture.clock.scheduler;
    private final YamlConfiguration config = fixture.config;
    private final Map<UUID, Entity> loaded = fixture.loaded;
    private final List<Model> stands = fixture.stands;

    @BeforeEach
    void setUp() {
        fixture.open();
        fixture.onRemove = entity -> listener.onEntityRemove(new EntityRemoveEvent(entity, EntityRemoveEvent.Cause.PLUGIN));
        config.set("enable_damage_indicator", true);
        config.set("enable_damage_merge", true);
        config.set("damage_merge_timeout", 1.5);
        config.set("damage_merge_jump_duration", FALL_DURATION_TICKS);
        config.set("hologram_velocity_y", 0.15);
        config.set("hologram_gravity", 0.01);
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
        HOLOGRAPH_UTIL.clearTracking();
        fixture.close();
    }

    @Test
    void nativeEventsCapLethalDamageAndIgnoreCancellation() {
        Model model = fixture.mob(null);
        model.health = 3;
        LivingEntity victim = model.entity;
        Player attacker = attacker();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, fixture.damageSource, 100);

        listener.displayDamageIndicator(event);
        assertEquals(0, stands.size());
        model.health = 0;
        advance(1);
        assertEquals("3.0", stands.getFirst()
                                  .number());
        event.setCancelled(true);
        listener.displayDamageIndicator(event);
        assertEquals(1, stands.size());
        assertEquals("3.0", stands.getFirst()
                                  .number());
    }

    @Test
    void meleeAndProjectilesMergeByShooterButOtherPairsRemainSeparate() {
        LivingEntity victim = victim();
        Player attacker = attacker();
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(attacker);
        hit(victim, attacker, 4);
        hit(victim, arrow, 2.5);
        assertEquals(1, stands.size());
        assertEquals("6.5", stands.getFirst()
                                  .number());
        hit(victim, attacker(), 3);
        hit(victim(), attacker, 1);
        assertEquals(3, stands.size());
        verify(scheduler).runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L));
        verify(scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
    }

    @Test
    void repeatedHitsRefreshOneDeadlineThenAnimationFinishesInConfiguredTicks() {
        LivingEntity victim = victim();
        Player attacker = attacker();
        hit(victim, attacker, 2);
        advance(20);
        hit(victim, attacker, 3);
        advance(MERGE_TIMEOUT_TICKS - 1);
        assertEquals("5.0", stands.getFirst()
                                  .number());
        assertEquals(DISPLAY_Y, stands.getFirst().location.getY());
        advance(1);
        assertTrue(stands.getFirst().location.getY() > DISPLAY_Y);
        advance(FALL_DURATION_TICKS - 1);
        assertFalse(stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void externalRemovalWhileStillValidDoesNotReenterRemoveOrSuppressNextHit() {
        LivingEntity victim = victim();
        Player attacker = attacker();
        hit(victim, attacker, 3);
        stands.getFirst().entity.remove();
        hit(victim, attacker, 2);

        assertEquals(2, stands.size());
        assertEquals("2.0", stands.getLast()
                                  .number());
        assertTrue(stands.getLast().valid);
        verify(stands.getFirst().entity).remove();
    }

    @Test
    void administrativeCleanupSynchronouslyDetachesMergedTracking() {
        LivingEntity victim = victim();
        Player attacker = attacker();
        hit(victim, attacker, 3);
        HOLOGRAPH_UTIL.cleanup((ArmorStand) stands.getFirst().entity);
        hit(victim, attacker, 2);
        assertEquals(2, stands.size());
        assertFalse(stands.getFirst().valid);
        assertEquals("2.0", stands.getLast()
                                  .number());
    }

    @Test
    void lethalMergedNumberSurvivesDeathCleanupAndFalls() {
        LivingEntity victim = victim();
        hit(victim, attacker(), 3);
        EntityDeathEvent death = new EntityDeathEvent(victim, fixture.damageSource, List.of());
        listener.onEntityDeath(death);
        when(victim.isValid()).thenReturn(false);
        listener.onEntityRemove(new EntityRemoveEvent(victim, EntityRemoveEvent.Cause.DEATH));
        advance(FALL_DURATION_TICKS - 1);
        assertTrue(stands.getFirst().valid);
        advance(1);
        assertFalse(stands.getFirst().valid);
    }

    @Test
    void unloadingVictimReleasesTheDisplayAndReplacementWrapperStartsFresh() {
        LivingEntity original = victim();
        Player attacker = attacker();
        hit(original, attacker, 3);
        listener.onEntitiesUnload(new EntitiesUnloadEvent(mock(Chunk.class), List.of(original)));
        loaded.remove(original.getUniqueId());
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());

        LivingEntity replacement = fixture.entity(original.getUniqueId(), LivingEntity.class, null, new MemoryPersistentData()).entity;
        hit(replacement, attacker, 2);
        assertEquals("2.0", stands.getLast()
                                  .number());
    }

    @Test
    void environmentalAndNonMergedNumbersExpireWithoutPerDisplayTasks() {
        config.set("enable_damage_merge", false);
        LivingEntity victim = victim();
        hit(victim, attacker(), 2);
        hit(victim, null, 1);
        assertEquals(2, stands.size());
        advance(MERGE_TIMEOUT_TICKS - 1);
        assertTrue(stands.stream()
                         .allMatch(stand -> stand.valid));
        advance(1);
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
        verify(scheduler).runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L));
    }

    @Test
    void dispenserProjectileHitRemovalReleasesTheNumberInsteadOfDeletingIt() {
        Projectile arrow = mock(Projectile.class);
        var dispenser = mock(BlockProjectileSource.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(dispenser);
        listener.displayDamage(victim(), arrow, 2, EntityDamageEvent.DamageCause.PROJECTILE);

        listener.onEntityRemove(new EntityRemoveEvent(arrow, EntityRemoveEvent.Cause.HIT));

        assertTrue(stands.getFirst().valid);
        advance(FALL_DURATION_TICKS - 1);
        assertTrue(stands.getFirst().valid);
        advance(1);
        assertFalse(stands.getFirst().valid);
    }

    @Test
    void explodingAttackerRemovalDoesNotEraseCompletedNonlethalDamage() {
        Entity creeper = mock(Creeper.class);
        when(creeper.getUniqueId()).thenReturn(UUID.randomUUID());
        hit(victim(), creeper, 2);
        listener.onEntityRemove(new EntityRemoveEvent(creeper, EntityRemoveEvent.Cause.EXPLODE));
        assertTrue(stands.getFirst().valid);
        advance(FALL_DURATION_TICKS);
        assertFalse(stands.getFirst().valid);
    }

    @Test
    void worldUnloadCleansAllNumbersWhileEntitiesAreStillAccessible() {
        hit(victim(), attacker(), 2);
        hit(victim(), null, 1);
        listener.onWorldUnload(new WorldUnloadEvent(world));
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        loaded.clear();
        advance(1);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void loadedEntityCleanupPreservesActiveAndUnownedHolograms() {
        hit(victim(), attacker(), 2);
        Model active = stands.getFirst();
        Model orphan = fixture.entity(ArmorStand.class, null);
        orphan.data.set(DamageIndicatorListener.getCustomNamespacedKey(), BOOLEAN, true);
        Model unrelated = fixture.entity(ArmorStand.class, null);

        listener.removeStuckArmorStands(new EntitiesLoadEvent(mock(Chunk.class), List.of(active.entity, orphan.entity, unrelated.entity)));

        assertTrue(active.valid);
        assertFalse(orphan.valid);
        assertTrue(unrelated.valid);
        listener.cleanupLoadedChunks();
        assertTrue(active.valid);
        verify(world, never()).getLoadedChunks();
    }

    @Test
    void shutdownCancelsOneTickerAndAllDisplaysWithoutRecreation() {
        hit(victim(), attacker(), 2);
        hit(victim(), null, 1);
        listener.shutdown();
        advance(2 * MERGE_TIMEOUT_TICKS);
        assertTrue(fixture.clock.allCancelled());
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertEquals(2, stands.size());
    }

    @Test
    void shutdownDuringMergedDisplayTeleportStopsBothDisplaysWithoutRestartingTicker() {
        Model first = fixture.mob(null);
        Model second = fixture.mob(null);
        hit(first.entity, attacker(), 2);
        hit(second.entity, attacker(), 3);
        assertEquals(2, stands.size());
        first.location.add(1, 0, 0);
        second.location.add(1, 0, 0);
        fixture.onTeleport = entity -> listener.shutdown();

        assertDoesNotThrow(() -> advance(MERGE_TIMEOUT_TICKS + FALL_DURATION_TICKS));

        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, fixture.clock.tasks.size());
        assertEquals(2, stands.size());
    }

    @Test
    void shutdownDuringAnimationTeleportStopsBothDisplaysWithoutRestartingTicker() {
        config.set("enable_damage_merge", false);
        hit(victim(), attacker(), 2);
        hit(victim(), null, 3);
        assertEquals(2, stands.size());
        fixture.onTeleport = entity -> listener.shutdown();

        assertDoesNotThrow(() -> advance(MERGE_TIMEOUT_TICKS + FALL_DURATION_TICKS));

        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, fixture.clock.tasks.size());
        assertEquals(2, stands.size());
    }

    @Test
    void alreadyAppliedVirtualLethalDamageDoesNotMutateVictimOrRequireAValidCorpse() {
        LivingEntity victim = victim();
        when(victim.isValid()).thenReturn(false);
        hit(victim, attacker(), 8.25);
        assertEquals("8.25", stands.getFirst()
                                   .number());
        advance(MERGE_TIMEOUT_TICKS);
        assertFalse(stands.getFirst().valid);
        verify(victim, never()).setHealth(any(Double.class));
        verify(victim, never()).damage(any(Double.class));
    }

    @Test
    void invalidDamageAndDisabledFeatureNeverSpawn() {
        LivingEntity victim = victim();
        for (double damage : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            hit(victim, null, damage);
        }
        config.set("enable_damage_indicator", false);
        hit(victim, null, 2);
        assertEquals(0, stands.size());
    }

    @Test
    void fiftyActivePairsUseOneTickerAndReturnToBaseline() {
        LivingEntity victim = victim();
        for (int i = 0; i < ACTIVE_PAIR_COUNT; i++) {
            hit(victim, attacker(), 1);
        }
        assertEquals(ACTIVE_PAIR_COUNT, stands.size());
        verify(scheduler).runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L));
        advance(MERGE_TIMEOUT_TICKS + FALL_DURATION_TICKS);
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void zeroTimeoutAndOneTickAnimationTerminateWithoutStaleState() {
        config.set("damage_merge_timeout", 0);
        config.set("damage_merge_jump_duration", 1);
        hit(victim(), attacker(), 1);
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
    }

    private void hit(LivingEntity victim, Entity attacker, double damage) {
        listener.displayDamage(victim, attacker, damage, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    }

    private void advance(int ticks) {
        fixture.clock.advance(ticks);
    }

    private LivingEntity victim() {
        return fixture.mob(null).entity;
    }

    private Player attacker() {
        return fixture.attacker();
    }
}
