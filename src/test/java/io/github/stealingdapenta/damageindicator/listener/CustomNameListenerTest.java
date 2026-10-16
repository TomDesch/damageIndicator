package io.github.stealingdapenta.damageindicator.listener;

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
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CustomNameListenerTest {

    private final Map<UUID, Entity> loaded = new HashMap<>();
    private final List<StandState> stands = new ArrayList<>();
    private final List<Scheduled> tasks = new ArrayList<>();
    private final DamageIndicator plugin = mock(DamageIndicator.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final World world = mock(World.class);
    private final YamlConfiguration config = new YamlConfiguration();
    private final CustomNameListener listener = new CustomNameListener();
    private MockedStatic<DamageIndicator> singleton;
    private MockedStatic<Bukkit> bukkit;
    private long tick;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        config.set("enable_hologram_health_bar", true);
        config.set("enable_holographic_custom_names", true);
        config.set("hologram_follow_speed", 3);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getName()).thenReturn("DamageIndicator");
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("custom-name-test"));
        singleton = mockStatic(DamageIndicator.class);
        singleton.when(DamageIndicator::getInstance).thenReturn(plugin);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class))).thenAnswer(invocation -> loaded.get(invocation.getArgument(0)));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        when(world.getLivingEntities()).thenAnswer(invocation -> loaded.values().stream()
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList());
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    Scheduled task = new Scheduled(invocation.getArgument(1), tick + invocation.getArgument(2, Long.class),
                            invocation.getArgument(3));
                    tasks.add(task);
                    return task.handle;
                });
        when(world.spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class))).thenAnswer(invocation -> {
            StandState stand = new StandState(invocation.getArgument(0));
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
        HolographUtil.HOLOGRAPH_UTIL.clearTracking();
        bukkit.close();
        singleton.close();
    }

    @Test
    void existingNamesAreReplacedOnceAndSameSessionRecoveryIsIdempotent() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();
        assertFalse(mob.visible.get());
        assertEquals(1, stands.size());

        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(mob.entity)));
        listener.refreshLoadedEntities();

        assertFalse(mob.visible.get());
        assertEquals(1, stands.size());
        assertTrue(mob.data.containsKey("custom-name-hidden"));
    }

    @Test
    void renameDuringFollowUpdatesExistingHologramAndShutdownPreservesTheNewName() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();
        mob.name.set("\u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76Renamed");
        advance(3);

        assertEquals(mob.name.get(), stands.getFirst().name.get());
        assertEquals(1, stands.size());
        listener.shutdown();
        assertTrue(mob.visible.get());
        assertFalse(mob.data.containsKey("custom-name-hidden"));
        assertTrue(mob.name.get().endsWith("Renamed"));
        assertFalse(stands.getFirst().valid.get());
    }

    @Test
    void lateNamesAndLateSpawnMutationsAreDiscoveredWithoutReload() {
        MobState mob = mob(null, false);
        listener.onEntitySpawn(new EntitySpawnEvent(mob.entity));
        advance(1);
        assertEquals(0, stands.size());

        mob.name.set("Assigned later");
        mob.visible.set(true);
        advance(20);

        assertEquals(1, stands.size());
        assertEquals("Assigned later", stands.getFirst().name.get());
        assertFalse(mob.visible.get());
    }

    @Test
    void originallyHiddenNamesAndArmorStandsAreNotReplaced() {
        MobState mob = mob("Hidden", false);
        listener.refreshLoadedEntities();
        advance(40);
        assertEquals(0, stands.size());
        assertFalse(mob.visible.get());
        verify(mob.entity, never()).setCustomNameVisible(true);
    }

    @Test
    void unloadRestoresVisibilityAndReplacementWrapperRecoversOnlyOneDisplay() {
        MobState original = mob("Original", true);
        listener.refreshLoadedEntities();
        listener.onEntitiesUnload(new EntitiesUnloadEvent(mock(Chunk.class), List.of(original.entity)));
        loaded.remove(original.entity.getUniqueId());
        assertTrue(original.visible.get());
        assertFalse(stands.getFirst().valid.get());

        MobState replacement = mob("Original", true, original.entity.getUniqueId(), original.data);
        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(replacement.entity)));
        advance(3);

        assertFalse(replacement.visible.get());
        assertEquals(1, stands.stream().filter(stand -> stand.valid.get()).count());
    }

    @Test
    void crashMarkerIsRestoredWithFeaturesDisabledWithoutRecreatingAStand() {
        MobState mob = mob("Original", false);
        mob.data.put("custom-name-hidden", true);
        config.set("enable_holographic_custom_names", false);

        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(mob.entity)));

        assertTrue(mob.visible.get());
        assertFalse(mob.data.containsKey("custom-name-hidden"));
        assertEquals(0, stands.size());
    }

    @Test
    void worldUnloadRestoresVisibilityBeforeEntitiesBecomeUnavailable() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();
        listener.onWorldUnload(new WorldUnloadEvent(world));
        assertTrue(mob.visible.get());
        assertFalse(mob.data.containsKey("custom-name-hidden"));
        assertFalse(stands.getFirst().valid.get());
        loaded.clear();
        advance(40);
        assertTrue(tasks.stream().allMatch(task -> task.cancelled.get()));
    }

    @Test
    void administrativeCleanupRestoresNativeNameAndStopsRediscoveryUntilRefresh() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();

        HolographUtil.HOLOGRAPH_UTIL.cleanup(stands.getFirst().entity);
        advance(40);

        assertTrue(mob.visible.get());
        assertEquals(1, stands.size());
        assertFalse(stands.getFirst().valid.get());
        listener.refreshLoadedEntities();
        assertEquals(2, stands.size());
        assertFalse(mob.visible.get());
    }

    @Test
    void areaRenameDetachesTheRealNameFollowerBeforeClearingNames() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();
        Player operator = mock(Player.class);
        when(operator.hasPermission("damageindicator.arearename")).thenReturn(true);
        when(operator.getNearbyEntities(50, 50, 50)).thenReturn(List.of(mob.entity, stands.getFirst().entity));
        doAnswer(invocation -> {
            listener.clearDisplay(invocation.getArgument(0));
            return null;
        }).when(plugin).clearNameDisplays(any(LivingEntity.class));

        new io.github.stealingdapenta.damageindicator.command.AreaRenameCommand().onCommand(
                operator, mock(org.bukkit.command.Command.class), "arearename", new String[0]);
        advance(40);

        assertEquals(null, mob.name.get());
        assertFalse(mob.visible.get());
        assertFalse(stands.getFirst().valid.get());
        assertEquals(1, stands.size());
        assertFalse(mob.data.containsKey("custom-name-hidden"));
    }

    @Test
    void externallyRemovedStandCanBeRecreatedWithoutLosingTheName() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();
        stands.getFirst().valid.set(false);
        advance(21);

        assertEquals(2, stands.size());
        assertFalse(mob.visible.get());
        assertEquals("Original", stands.getLast().name.get());
    }

    @Test
    void removalAndDeathReleaseDisplaysAndDiscoveryCandidates() {
        MobState mob = mob("Removed", true);
        listener.refreshLoadedEntities();
        listener.onEntityRemove(new EntityRemoveEvent(mob.entity, EntityRemoveEvent.Cause.PLUGIN));
        loaded.remove(mob.entity.getUniqueId());
        advance(40);
        assertFalse(stands.getFirst().valid.get());
        assertTrue(tasks.stream().allMatch(task -> task.cancelled.get()));

        MobState dying = mob("Dying", true);
        listener.refreshLoadedEntities();
        EntityDeathEvent death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(dying.entity);
        listener.onEntityDeath(death);
        assertTrue(dying.visible.get());
        assertFalse(stands.getLast().valid.get());
    }

    @Test
    void clearingANameRemovesItsHologramAndRestoresTheVisibilityFlag() {
        MobState mob = mob("Original", true);
        listener.refreshLoadedEntities();
        mob.name.set(null);
        advance(3);
        assertFalse(stands.getFirst().valid.get());
        assertTrue(mob.visible.get());
        assertFalse(mob.data.containsKey("custom-name-hidden"));
        mob.name.set("Named again");
        advance(20);
        assertEquals(2, stands.size());
        assertEquals("Named again", stands.getLast().name.get());
        assertFalse(mob.visible.get());
    }

    private MobState mob(String name, boolean visible) {
        return mob(name, visible, UUID.randomUUID(), new HashMap<>());
    }

    private MobState mob(String name, boolean visible, UUID id, Map<String, Object> data) {
        MobState mob = new MobState(name, visible, data);
        when(mob.entity.getUniqueId()).thenReturn(id);
        loaded.put(id, mob.entity);
        return mob;
    }

    private void advance(int count) {
        for (int i = 0; i < count; i++) {
            tick++;
            for (Scheduled task : new ArrayList<>(tasks)) {
                if (!task.cancelled.get() && task.next <= tick) {
                    task.next += task.period;
                    task.callback.run();
                }
            }
        }
    }

    private static PersistentDataContainer persistentData(Map<String, Object> values) {
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN)))
                .thenAnswer(invocation -> values.get(((NamespacedKey) invocation.getArgument(0)).getKey()));
        doAnswer(invocation -> {
            values.put(((NamespacedKey) invocation.getArgument(0)).getKey(), invocation.getArgument(2));
            return null;
        }).when(data).set(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN), anyBoolean());
        doAnswer(invocation -> {
            values.remove(((NamespacedKey) invocation.getArgument(0)).getKey());
            return null;
        }).when(data).remove(any(NamespacedKey.class));
        return data;
    }

    private final class MobState {
        private final LivingEntity entity = mock(LivingEntity.class);
        private final AtomicReference<String> name;
        private final AtomicBoolean visible;
        private final Map<String, Object> data;

        private MobState(String name, boolean visible, Map<String, Object> data) {
            this.name = new AtomicReference<>(name);
            this.visible = new AtomicBoolean(visible);
            this.data = data;
            PersistentDataContainer pdc = persistentData(data);
            when(entity.getPersistentDataContainer()).thenReturn(pdc);
            when(entity.isValid()).thenReturn(true);
            when(entity.getHeight()).thenReturn(2.0);
            when(entity.getLocation()).thenAnswer(invocation -> new Location(world, 0, 64, 0));
            when(entity.getCustomName()).thenAnswer(invocation -> this.name.get());
            doAnswer(invocation -> {
                this.name.set(invocation.getArgument(0));
                return null;
            }).when(entity).setCustomName(any());
            when(entity.isCustomNameVisible()).thenAnswer(invocation -> this.visible.get());
            doAnswer(invocation -> {
                this.visible.set(invocation.getArgument(0));
                return null;
            }).when(entity).setCustomNameVisible(anyBoolean());
        }
    }

    private final class StandState {
        private final ArmorStand entity = mock(ArmorStand.class);
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private final AtomicReference<String> name = new AtomicReference<>();
        private final AtomicReference<Location> location;

        private StandState(Location location) {
            this.location = new AtomicReference<>(location.clone());
            PersistentDataContainer data = persistentData(new HashMap<>());
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
                valid.set(false);
                loaded.remove(entity.getUniqueId());
                return null;
            }).when(entity).remove();
        }
    }

    private static final class Scheduled {
        private final Runnable callback;
        private final long period;
        private final BukkitTask handle = mock(BukkitTask.class);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private long next;

        private Scheduled(Runnable callback, long next, long period) {
            this.callback = callback;
            this.next = next;
            this.period = period;
            when(handle.isCancelled()).thenAnswer(invocation -> cancelled.get());
            doAnswer(invocation -> {
                cancelled.set(true);
                return null;
            }).when(handle).cancel();
        }
    }
}
