package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DamageIndicatorListenerTest {

    private final DamageIndicatorListener listener = new DamageIndicatorListener();
    private final DamageIndicator plugin = mock(DamageIndicator.class);
    private final World world = mock(World.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final BukkitTask task = mock(BukkitTask.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final Map<UUID, Entity> loaded = new HashMap<>();
    private final List<Stand> stands = new ArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private Runnable heartbeat;
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<DamageIndicator> singleton;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        config.set("enable_damage_indicator", true);
        config.set("enable_damage_merge", true);
        config.set("damage_merge_timeout", 1.5);
        config.set("damage_merge_jump_duration", 15);
        config.set("hologram_velocity_y", 0.15);
        config.set("hologram_gravity", 0.01);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("damage-test"));
        singleton = mockStatic(DamageIndicator.class);
        singleton.when(DamageIndicator::getInstance).thenReturn(plugin);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class))).thenAnswer(invocation -> loaded.get(invocation.getArgument(0)));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        when(world.getEntities()).thenAnswer(invocation -> new ArrayList<>(loaded.values()));
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(invocation -> {
            heartbeat = invocation.getArgument(1);
            cancelled.set(false);
            return task;
        });
        when(task.isCancelled()).thenAnswer(invocation -> cancelled.get());
        doAnswer(invocation -> {
            cancelled.set(true);
            return null;
        }).when(task).cancel();
        when(world.spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class))).thenAnswer(invocation -> {
            Stand stand = new Stand(invocation.getArgument(0), true);
            Consumer<ArmorStand> configure = invocation.getArgument(2);
            configure.accept(stand.entity);
            stands.add(stand);
            loaded.put(stand.entity.getUniqueId(), stand.entity);
            return stand.entity;
        });
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
        HOLOGRAPH_UTIL.clearTracking();
        bukkit.close();
        singleton.close();
    }

    @Test
    void nativeEventsCapLethalDamageAndIgnoreCancellation() {
        LivingEntity victim = victim();
        when(victim.getHealth()).thenReturn(3.0);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        Player attacker = attacker();
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getFinalDamage()).thenReturn(100.0);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        listener.displayDamageIndicator(event);
        assertEquals("3.0", stands.getFirst().number());
        when(event.isCancelled()).thenReturn(true);
        listener.displayDamageIndicator(event);
        assertEquals(1, stands.size());
        assertEquals("3.0", stands.getFirst().number());
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
        assertEquals("6.5", stands.getFirst().number());
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
        advance(29);
        assertEquals("5.0", stands.getFirst().number());
        assertEquals(65, stands.getFirst().location.get().getY());
        advance(1);
        assertTrue(stands.getFirst().location.get().getY() > 65);
        advance(14);
        assertFalse(stands.getFirst().valid.get());
        assertTrue(cancelled.get());
    }

    @Test
    void externalRemovalWhileStillValidDoesNotReenterRemoveOrSuppressNextHit() {
        LivingEntity victim = victim();
        Player attacker = attacker();
        hit(victim, attacker, 3);
        stands.getFirst().entity.remove();
        hit(victim, attacker, 2);

        assertEquals(2, stands.size());
        assertEquals("2.0", stands.getLast().number());
        assertTrue(stands.getLast().valid.get());
        verify(stands.getFirst().entity).remove();
    }

    @Test
    void administrativeCleanupSynchronouslyDetachesMergedTracking() {
        LivingEntity victim = victim();
        Player attacker = attacker();
        hit(victim, attacker, 3);
        HOLOGRAPH_UTIL.cleanup(stands.getFirst().entity);
        hit(victim, attacker, 2);
        assertEquals(2, stands.size());
        assertFalse(stands.getFirst().valid.get());
        assertEquals("2.0", stands.getLast().number());
    }

    @Test
    void lethalMergedNumberSurvivesDeathCleanupAndFalls() {
        LivingEntity victim = victim();
        hit(victim, attacker(), 3);
        EntityDeathEvent death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(victim);
        listener.onEntityDeath(death);
        when(victim.isValid()).thenReturn(false);
        listener.onEntityRemove(new EntityRemoveEvent(victim, EntityRemoveEvent.Cause.DEATH));
        advance(14);
        assertTrue(stands.getFirst().valid.get());
        advance(1);
        assertFalse(stands.getFirst().valid.get());
    }

    @Test
    void unloadingVictimReleasesTheDisplayAndReplacementWrapperStartsFresh() {
        LivingEntity original = victim();
        Player attacker = attacker();
        hit(original, attacker, 3);
        listener.onEntitiesUnload(new EntitiesUnloadEvent(mock(Chunk.class), List.of(original)));
        loaded.remove(original.getUniqueId());
        advance(1);
        assertFalse(stands.getFirst().valid.get());
        assertTrue(cancelled.get());

        LivingEntity replacement = victim();
        UUID originalId = original.getUniqueId();
        when(replacement.getUniqueId()).thenReturn(originalId);
        loaded.put(replacement.getUniqueId(), replacement);
        hit(replacement, attacker, 2);
        assertEquals("2.0", stands.getLast().number());
    }

    @Test
    void environmentalAndNonMergedNumbersExpireWithoutPerDisplayTasks() {
        config.set("enable_damage_merge", false);
        LivingEntity victim = victim();
        hit(victim, attacker(), 2);
        hit(victim, null, 1);
        assertEquals(2, stands.size());
        advance(29);
        assertTrue(stands.stream().allMatch(stand -> stand.valid.get()));
        advance(1);
        assertTrue(stands.stream().noneMatch(stand -> stand.valid.get()));
        assertTrue(cancelled.get());
        verify(scheduler).runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L));
    }

    @Test
    void loadedEntityCleanupPreservesActiveAndUnownedHolograms() {
        hit(victim(), attacker(), 2);
        Stand active = stands.getFirst();
        Stand orphan = new Stand(new Location(world, 0, 64, 0), true);
        Stand unrelated = new Stand(new Location(world, 0, 64, 0), false);

        listener.removeStuckArmorStands(new EntitiesLoadEvent(mock(Chunk.class),
                List.of(active.entity, orphan.entity, unrelated.entity)));

        assertTrue(active.valid.get());
        assertFalse(orphan.valid.get());
        assertTrue(unrelated.valid.get());
        listener.cleanupLoadedChunks();
        assertTrue(active.valid.get());
        verify(world, never()).getLoadedChunks();
    }

    @Test
    void shutdownCancelsOneTickerAndAllDisplaysWithoutRecreation() {
        hit(victim(), attacker(), 2);
        hit(victim(), null, 1);
        listener.shutdown();
        advance(60);
        assertTrue(cancelled.get());
        assertTrue(stands.stream().noneMatch(stand -> stand.valid.get()));
        assertEquals(2, stands.size());
    }

    @Test
    void alreadyAppliedVirtualLethalDamageDoesNotMutateVictimOrRequireAValidCorpse() {
        LivingEntity victim = victim();
        when(victim.isValid()).thenReturn(false);
        hit(victim, attacker(), 8.25);
        assertEquals("8.25", stands.getFirst().number());
        advance(30);
        assertFalse(stands.getFirst().valid.get());
        verify(victim, never()).setHealth(any(Double.class));
        verify(victim, never()).damage(any(Double.class));
    }

    @Test
    void invalidDamageAndDisabledFeatureNeverSpawn() {
        LivingEntity victim = victim();
        for (double damage : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            hit(victim, null, damage);
        }
        config.set("enable_damage_indicator", false);
        hit(victim, null, 2);
        assertEquals(0, stands.size());
    }

    @Test
    void fiftyActivePairsUseOneTickerAndReturnToBaseline() {
        LivingEntity victim = victim();
        for (int i = 0; i < 50; i++) {
            hit(victim, attacker(), 1);
        }
        assertEquals(50, stands.size());
        verify(scheduler).runTaskTimer(any(Plugin.class), any(Runnable.class), eq(1L), eq(1L));
        advance(45);
        assertTrue(stands.stream().noneMatch(stand -> stand.valid.get()));
        assertTrue(cancelled.get());
    }

    @Test
    void zeroTimeoutAndOneTickAnimationTerminateWithoutStaleState() {
        config.set("damage_merge_timeout", 0);
        config.set("damage_merge_jump_duration", 1);
        hit(victim(), attacker(), 1);
        advance(1);
        assertFalse(stands.getFirst().valid.get());
        assertTrue(cancelled.get());
    }

    private void hit(LivingEntity victim, Entity attacker, double damage) {
        listener.displayDamage(victim, attacker, damage, EntityDamageEvent.DamageCause.ENTITY_ATTACK);
    }

    private void advance(int ticks) {
        for (int i = 0; i < ticks && !cancelled.get(); i++) {
            if (heartbeat != null) {
                heartbeat.run();
            }
        }
    }

    private LivingEntity victim() {
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(victim.isValid()).thenReturn(true);
        when(victim.getHealth()).thenReturn(20.0);
        when(victim.getLocation()).thenAnswer(invocation -> new Location(world, 0, 64, 0));
        loaded.put(victim.getUniqueId(), victim);
        return victim;
    }

    private Player attacker() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        loaded.put(player.getUniqueId(), player);
        return player;
    }

    private final class Stand {
        private final ArmorStand entity = mock(ArmorStand.class);
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private final AtomicReference<String> name = new AtomicReference<>();
        private final AtomicReference<Location> location;

        private Stand(Location location, boolean owned) {
            this.location = new AtomicReference<>(location.clone());
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN))).thenReturn(owned);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getPersistentDataContainer()).thenReturn(data);
            when(entity.isValid()).thenAnswer(invocation -> valid.get());
            when(entity.getCustomName()).thenAnswer(invocation -> name.get());
            when(entity.getLocation()).thenAnswer(invocation -> this.location.get().clone());
            doAnswer(invocation -> {
                name.set(invocation.getArgument(0));
                return null;
            }).when(entity).setCustomName(any());
            when(entity.teleport(any(Location.class))).thenAnswer(invocation -> {
                this.location.set(invocation.getArgument(0));
                return true;
            });
            doAnswer(invocation -> {
                listener.onEntityRemove(new EntityRemoveEvent(entity, EntityRemoveEvent.Cause.PLUGIN));
                valid.set(false);
                loaded.remove(entity.getUniqueId());
                return null;
            }).when(entity).remove();
        }

        private String number() {
            return name.get().replaceAll("(?i)\u00a7[0-9a-fk-orx]", "");
        }
    }
}
