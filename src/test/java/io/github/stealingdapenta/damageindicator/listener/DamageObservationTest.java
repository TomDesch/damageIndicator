package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.support.ListenerFixture;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture.Model;
import io.github.stealingdapenta.damageindicator.support.MemoryPersistentData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.BlockProjectileSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DamageObservationTest {

    private static final int MERGE_TIMEOUT_TICKS = 30;
    private static final int FALL_DURATION_TICKS = 15;
    private static final double DISPLAY_Y = ListenerFixture.GROUND_Y + 1;

    private DamageIndicatorListener listener = new DamageIndicatorListener();
    private final ListenerFixture fixture = new ListenerFixture();

    @BeforeEach
    void setUp() {
        fixture.open();
        fixture.onRemove = entity -> listener.onEntityRemove(new EntityRemoveEvent(entity, EntityRemoveEvent.Cause.PLUGIN));
        fixture.config.set("enable_damage_indicator", true);
        fixture.config.set("enable_damage_merge", true);
        fixture.config.set("damage_merge_timeout", 1.5);
        fixture.config.set("damage_merge_jump_duration", FALL_DURATION_TICKS);
        fixture.config.set("hologram_velocity_y", 0.15);
        fixture.config.set("hologram_gravity", 0.01);
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
        HOLOGRAPH_UTIL.clearTracking();
        fixture.close();
    }

    @Test
    void strongerSameTickHitUsesCommittedHpDespiteZeroApiImmunityCounter() {
        Model victim = fixture.mob(null);
        Player attacker = fixture.attacker();
        when(victim.entity.getNoDamageTicks()).thenReturn(0);

        observe(victim, attacker, 4);
        assertTrue(fixture.stands.isEmpty());
        victim.health = 16;
        observe(victim, attacker, 6);
        assertEquals(List.of("4.0"), numbers());
        victim.health = 14;
        fixture.clock.advance(1);

        assertEquals(List.of("6.0"), numbers());
        verify(victim.entity, never()).getNoDamageTicks();
        verify(victim.entity, never()).getLastDamage();
        verify(fixture.clock.scheduler).runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L));
        verify(fixture.clock.scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
    }

    @Test
    void equalSameTickHitRejectedByServerAddsNoDamage() {
        Model victim = fixture.mob(null);
        Player attacker = fixture.attacker();
        observe(victim, attacker, 4);
        victim.health = 16;
        observe(victim, attacker, 4);
        fixture.clock.advance(1);

        assertEquals(List.of("4.0"), numbers());
    }

    @Test
    void twoAttackersOwnTheirSeparatelyObservedHpLoss() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;
        observe(victim, fixture.attacker(), 6);
        victim.health = 14;
        fixture.clock.advance(1);

        assertEquals(List.of("4.0", "2.0"), numbers());
    }

    @Test
    void damageBeforeHealingIsFlushedBeforeHealthReturnsToItsStartingValue() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;

        listener.onEntityRegainHealth(heal(victim, 4));
        victim.health = 20;
        fixture.clock.advance(1);

        assertEquals(List.of("4.0"), numbers());
    }

    @Test
    void healingBeforeDamageBecomesTheNewHitsStartingHealth() {
        Model victim = fixture.mob(null);
        victim.health = 10;
        listener.onEntityRegainHealth(heal(victim, 5));
        victim.health = 15;
        observe(victim, fixture.attacker(), 4);
        victim.health = 11;
        fixture.clock.advance(1);

        assertEquals(List.of("4.0"), numbers());
    }

    @Test
    void cancelledHealingDoesNotConsumeThePendingDamage() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;
        EntityRegainHealthEvent healing = heal(victim, 4);
        healing.setCancelled(true);
        listener.onEntityRegainHealth(healing);
        assertTrue(fixture.stands.isEmpty());
        fixture.clock.advance(1);

        assertEquals(List.of("4.0"), numbers());
    }

    @Test
    void cancelledZeroFinalAndManufacturedDamageWithoutHpLossNeverSpawn() {
        Model victim = fixture.mob(null);
        EntityDamageEvent cancelled = damage(victim, fixture.attacker(), 4);
        cancelled.setCancelled(true);
        listener.displayDamageIndicator(cancelled);
        listener.displayDamageIndicator(damage(victim, fixture.attacker(), 0));
        assertTrue(fixture.clock.tasks.isEmpty());

        observe(victim, fixture.attacker(), 4);
        fixture.clock.advance(1);

        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void lethalObservedHitOnInvalidCorpseUsesConfiguredMergedFallDuration() {
        Model victim = fixture.mob(null);
        victim.health = 3;
        observe(victim, fixture.attacker(), 100);
        victim.health = 0;
        victim.dead = true;
        victim.valid = false;

        listener.onEntityDeath(new EntityDeathEvent(victim.entity, fixture.damageSource, List.of()));
        listener.onEntityRemove(new EntityRemoveEvent(victim.entity, EntityRemoveEvent.Cause.DEATH));

        assertEquals(List.of("3.0"), numbers());
        fixture.clock.advance(FALL_DURATION_TICKS - 1);
        assertTrue(fixture.stands.getFirst().valid);
        fixture.clock.advance(1);
        assertFalse(fixture.stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void successfulTotemFlushesAtZeroHpBeforeOneHpReset() {
        Model victim = fixture.mob(null);
        victim.health = 3;
        observe(victim, fixture.attacker(), 100);
        victim.health = 0;
        EntityResurrectEvent resurrection = new EntityResurrectEvent(victim.entity, EquipmentSlot.HAND);
        resurrection.setCancelled(false);

        listener.onEntityResurrect(resurrection);
        victim.health = 1;
        fixture.clock.advance(1);

        assertEquals(List.of("3.0"), numbers());
        assertTrue(fixture.stands.getFirst().valid);
    }

    @Test
    void cancelledResurrectionLeavesLethalDamageForDeathBoundary() {
        Model victim = fixture.mob(null);
        victim.health = 3;
        observe(victim, fixture.attacker(), 100);
        victim.health = 0;
        EntityResurrectEvent resurrection = new EntityResurrectEvent(victim.entity, EquipmentSlot.HAND);
        resurrection.setCancelled(true);
        listener.onEntityResurrect(resurrection);
        assertTrue(fixture.stands.isEmpty());

        listener.onEntityDeath(new EntityDeathEvent(victim.entity, fixture.damageSource, List.of()));
        assertEquals(List.of("3.0"), numbers());
    }

    @Test
    void mergeDeadlineStartsAtCaptureRatherThanTheDelayedFlush() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;
        fixture.clock.advance(MERGE_TIMEOUT_TICKS - 1);
        assertEquals(DISPLAY_Y, fixture.stands.getFirst().location.getY());

        fixture.clock.advance(1);
        assertTrue(fixture.stands.getFirst().location.getY() > DISPLAY_Y);
        fixture.clock.advance(FALL_DURATION_TICKS - 1);
        assertFalse(fixture.stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void environmentalDamageUsesObservedHealthAndTheExistingAnimation() {
        Model victim = fixture.mob(null);
        listener.displayDamageIndicator(new EntityDamageEvent(victim.entity, EntityDamageEvent.DamageCause.FIRE,
                                                              fixture.damageSource, 6));
        victim.health = 18;
        fixture.clock.advance(1);

        assertEquals(List.of("2.0"), numbers());
        fixture.clock.advance(MERGE_TIMEOUT_TICKS - 1);
        assertFalse(fixture.stands.getFirst().valid);
    }

    @Test
    void pendingInvalidAndMissingVictimsAreDroppedWithoutDisplaysOrLeaks() {
        Model invalid = fixture.mob(null);
        Model missing = fixture.mob(null);
        observe(invalid, fixture.attacker(), 4);
        observe(missing, fixture.attacker(), 4);
        invalid.health = 16;
        missing.health = 16;
        invalid.valid = false;
        fixture.loaded.remove(missing.id);

        fixture.clock.advance(1);

        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void unloadDropsPendingVictimAndReplacementWrapperCannotInheritItsHit() {
        Model original = fixture.mob(null);
        Player attacker = fixture.attacker();
        observe(original, attacker, 4);
        original.health = 16;
        listener.onEntitiesUnload(new EntitiesUnloadEvent(mock(Chunk.class), List.of(original.entity)));
        fixture.loaded.remove(original.id);
        Model replacement = fixture.entity(original.id, LivingEntity.class, null, new MemoryPersistentData());
        fixture.clock.advance(1);
        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());

        observe(replacement, attacker, 2);
        replacement.health = 18;
        fixture.clock.advance(1);
        assertEquals(List.of("2.0"), numbers());
    }

    @Test
    void quitAndDirectVictimRemovalDropPendingDamage() {
        Model player = fixture.entity(Player.class, null);
        Model removed = fixture.mob(null);
        observe(player, fixture.attacker(), 4);
        observe(removed, fixture.attacker(), 4);
        player.health = 16;
        removed.health = 16;

        listener.onPlayerQuit(new PlayerQuitEvent((Player) player.entity, ""));
        listener.onEntityRemove(new EntityRemoveEvent(removed.entity, EntityRemoveEvent.Cause.PLUGIN));
        fixture.clock.advance(1);

        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void worldUnloadDropsPendingVictimsBeforeAttackerRemovalCanSpawnNumbers() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;
        listener.onWorldUnload(new WorldUnloadEvent(fixture.world));
        fixture.loaded.clear();
        fixture.clock.advance(1);

        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void ephemeralProjectileRetainsItsCapturedShooterIdentityAfterRemoval() {
        Model victim = fixture.mob(null);
        Player shooter = fixture.attacker();
        Projectile arrow = arrow();
        when(arrow.getShooter()).thenReturn(shooter);
        observe(victim, arrow, 4);
        victim.health = 16;
        listener.onEntityRemove(new EntityRemoveEvent(arrow, EntityRemoveEvent.Cause.HIT));
        when(arrow.getShooter()).thenReturn(null);
        fixture.clock.advance(1);

        observe(victim, shooter, 2);
        victim.health = 14;
        fixture.clock.advance(1);

        assertEquals(List.of("6.0"), numbers());
    }

    @Test
    void dispenserArrowRemovalFlushesThenReleasesItsPendingNumber() {
        Model victim = fixture.mob(null);
        Projectile arrow = arrow();
        when(arrow.getShooter()).thenReturn(mock(BlockProjectileSource.class));
        observe(victim, arrow, 4);
        victim.health = 16;

        listener.onEntityRemove(new EntityRemoveEvent(arrow, EntityRemoveEvent.Cause.HIT));

        assertEquals(List.of("4.0"), numbers());
        fixture.clock.advance(FALL_DURATION_TICKS - 1);
        assertTrue(fixture.stands.getFirst().valid);
        fixture.clock.advance(1);
        assertFalse(fixture.stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void nonplayerAttackerRemovalAlsoFlushesBeforeReleasing() {
        Model victim = fixture.mob(null);
        Model attacker = fixture.mob(null);
        observe(victim, attacker.entity, 4);
        victim.health = 16;

        listener.onEntityRemove(new EntityRemoveEvent(attacker.entity, EntityRemoveEvent.Cause.EXPLODE));

        assertEquals(List.of("4.0"), numbers());
        fixture.clock.advance(FALL_DURATION_TICKS);
        assertFalse(fixture.stands.getFirst().valid);
    }

    @Test
    void observedDamageIsCappedByFinalDamageWhenExtraUnobservableHealthLossOccurs() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 10;
        fixture.clock.advance(1);

        assertEquals(List.of("4.0"), numbers());
    }

    @Test
    void badFinalDamageAndHealthAreWarnedAboutWithoutSpawning() {
        Model victim = fixture.mob(null);
        listener.displayDamageIndicator(damage(victim, fixture.attacker(), Double.NaN));
        verify(fixture.logger).warning(contains("non-finite final damage"));
        victim.health = Double.POSITIVE_INFINITY;
        observe(victim, fixture.attacker(), 4);
        verify(fixture.logger).warning(contains("invalid health"));

        victim.health = 20;
        observe(victim, fixture.attacker(), 4);
        victim.health = Double.NaN;
        fixture.clock.advance(1);

        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void cancelledHologramSpawnDoesNotRetainAnObservationOrRegisterInvalidDisplay() {
        Model victim = fixture.mob(null);
        fixture.cancelSpawns = true;
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;
        assertDoesNotThrow(() -> fixture.clock.advance(1));

        assertEquals(1, fixture.stands.size());
        assertFalse(fixture.stands.getFirst().valid);
        assertFalse(HOLOGRAPH_UTIL.isActive(fixture.stands.getFirst().entity));
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void shutdownWhileReadingCommittedHpClearsTheRemainingPendingSnapshot() {
        Model first = fixture.mob(null);
        Model second = fixture.mob(null);
        observe(first, fixture.attacker(), 4);
        observe(second, fixture.attacker(), 4);
        first.health = 16;
        second.health = 16;
        for (Model victim : List.of(first, second)) {
            doAnswer(call -> {
                listener.shutdown();
                return victim.health;
            }).when(victim.entity).getHealth();
        }

        assertDoesNotThrow(() -> fixture.clock.advance(2));

        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, fixture.clock.tasks.size());
    }

    @Test
    void shutdownDuringObservedMergeUpdateDoesNotRestartTickerOrRecaptureNextHit() {
        Model victim = fixture.mob(null);
        Player attacker = fixture.attacker();
        observe(victim, attacker, 4);
        victim.health = 16;
        fixture.clock.advance(1);
        observe(victim, attacker, 2);
        victim.health = 14;
        fixture.stands.getFirst().nameCanonicalizer = name -> {
            listener.shutdown();
            return name;
        };

        assertDoesNotThrow(() -> observe(victim, attacker, 1));
        fixture.clock.advance(MERGE_TIMEOUT_TICKS + FALL_DURATION_TICKS);

        assertEquals(1, fixture.stands.size());
        assertFalse(fixture.stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, fixture.clock.tasks.size());
    }

    @Test
    void shutdownDropsPendingHitsButNewHitsAfterReloadCanStartFresh() {
        Model victim = fixture.mob(null);
        observe(victim, fixture.attacker(), 4);
        victim.health = 16;
        listener.shutdown();
        fixture.clock.advance(1);
        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());

        observe(victim, fixture.attacker(), 2);
        victim.health = 14;
        fixture.clock.advance(1);
        assertEquals(List.of("2.0"), numbers());
    }

    @Test
    void healthBarOnlyObservationDeliversActualCommittedDamageWithoutRenderingNumbers() {
        Model victim = fixture.mob(null);
        List<Double> confirmed = new ArrayList<>();
        fixture.config.set("enable_damage_indicator", false);
        fixture.config.set("enable_health_bar", true);
        listener = new DamageIndicatorListener((entity, damage) -> {
            assertSame(victim.entity, entity);
            assertEquals(18, entity.getHealth());
            confirmed.add(damage);
        });

        observe(victim, fixture.attacker(), 6);
        assertTrue(confirmed.isEmpty());
        victim.health = 18;
        fixture.clock.advance(1);

        assertEquals(List.of(2.0), confirmed);
        assertTrue(fixture.stands.isEmpty());
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void cancelledZeroAndSyntheticDamageAndVirtualRenderingNeverInvokeCommittedConsumer() {
        Model victim = fixture.mob(null);
        AtomicInteger callbacks = new AtomicInteger();
        listener = new DamageIndicatorListener((entity, damage) -> callbacks.incrementAndGet());
        EntityDamageEvent cancelled = damage(victim, fixture.attacker(), 4);
        cancelled.setCancelled(true);
        listener.displayDamageIndicator(cancelled);
        observe(victim, fixture.attacker(), 0);
        observe(victim, fixture.attacker(), 4);
        fixture.clock.advance(1);

        assertEquals(0, callbacks.get());
        assertTrue(fixture.stands.isEmpty());
        listener.displayDamage(victim.entity, fixture.attacker(), 3, EntityDamageEvent.DamageCause.CUSTOM);
        assertEquals(List.of("3.0"), numbers());
        assertEquals(0, callbacks.get());
        fixture.clock.advance(MERGE_TIMEOUT_TICKS + FALL_DURATION_TICKS);
        assertEquals(0, callbacks.get());
    }

    @Test
    void synchronousCommittedConsumerShutdownCannotRenderOrRestartThePendingSnapshot() {
        Model first = fixture.mob(null);
        Model second = fixture.mob(null);
        AtomicInteger callbacks = new AtomicInteger();
        listener = new DamageIndicatorListener((entity, damage) -> {
            callbacks.incrementAndGet();
            listener.shutdown();
        });
        listener.displayDamage(first.entity, fixture.attacker(), 1, EntityDamageEvent.DamageCause.CUSTOM);
        observe(first, fixture.attacker(), 4);
        observe(second, fixture.attacker(), 4);
        first.health = 16;
        second.health = 16;

        assertDoesNotThrow(() -> fixture.clock.advance(MERGE_TIMEOUT_TICKS + FALL_DURATION_TICKS));

        assertEquals(1, callbacks.get());
        assertEquals(1, fixture.stands.size());
        assertFalse(fixture.stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, fixture.clock.tasks.size());
    }

    private void observe(Model victim, Entity attacker, double amount) {
        listener.displayDamageIndicator(damage(victim, attacker, amount));
    }

    private EntityDamageEvent damage(Model victim, Entity attacker, double amount) {
        return new EntityDamageByEntityEvent(attacker, victim.entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                                             fixture.damageSource, amount);
    }

    private EntityRegainHealthEvent heal(Model victim, double amount) {
        return new EntityRegainHealthEvent(victim.entity, amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
    }

    private Projectile arrow() {
        Projectile arrow = mock(Projectile.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        return arrow;
    }

    private List<String> numbers() {
        return fixture.stands.stream()
                             .map(Model::number)
                             .toList();
    }
}
