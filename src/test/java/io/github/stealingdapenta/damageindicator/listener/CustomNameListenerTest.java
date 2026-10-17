package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.support.ListenerFixture.key;
import static org.bukkit.persistence.PersistentDataType.BOOLEAN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.command.AreaRenameCommand;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture.Model;
import io.github.stealingdapenta.damageindicator.support.MemoryPersistentData;
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomNameListenerTest {

    private static final int FOLLOW_INTERVAL_TICKS = 3;
    private static final int DISCOVERY_INTERVAL_TICKS = 20;
    private static final int DISCOVERY_BATCH_SIZE = 128;

    private final ListenerFixture fixture = new ListenerFixture();
    private final Map<UUID, Entity> loaded = fixture.loaded;
    private final List<Model> stands = fixture.stands;
    private final DamageIndicator plugin = fixture.plugin;
    private final World world = fixture.world;
    private final YamlConfiguration config = fixture.config;
    private final CustomNameListener listener = new CustomNameListener();

    @BeforeEach
    void setUp() {
        fixture.open();
        config.set("enable_hologram_health_bar", true);
        config.set("enable_holographic_custom_names", true);
        config.set("hologram_follow_speed", FOLLOW_INTERVAL_TICKS);
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
        HolographUtil.HOLOGRAPH_UTIL.clearTracking();
        fixture.close();
    }

    @Test
    void existingNamesAreReplacedOnceAndSameSessionRecoveryIsIdempotent() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();
        assertFalse(mob.visible);
        assertEquals(1, stands.size());

        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(mob.entity)));
        listener.refreshLoadedEntities();

        assertFalse(mob.visible);
        assertEquals(1, stands.size());
        assertTrue(mob.data.has(key("custom-name-hidden")));
    }

    @Test
    void renameDuringFollowUpdatesExistingHologramAndShutdownPreservesTheNewName() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();
        mob.name = "\u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76Renamed";
        advance(FOLLOW_INTERVAL_TICKS);

        assertEquals(mob.name, stands.getFirst().name);
        assertEquals(1, stands.size());
        listener.shutdown();
        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        assertTrue(mob.name.endsWith("Renamed"));
        assertFalse(stands.getFirst().valid);
    }

    @Test
    void lateNamesAndLateSpawnMutationsAreDiscoveredWithoutReload() {
        Model mob = mob(null, false);
        listener.onEntitySpawn(new EntitySpawnEvent(mob.entity));
        advance(1);
        assertEquals(0, stands.size());

        mob.name = "Assigned later";
        mob.visible = true;
        advance(DISCOVERY_INTERVAL_TICKS);

        assertEquals(1, stands.size());
        assertEquals("Assigned later", stands.getFirst().name);
        assertFalse(mob.visible);
    }

    @Test
    void discoveryHonoursItsBudgetWithoutStarvingLaterCandidates() {
        for (int index = 0; index < DISCOVERY_BATCH_SIZE; index++) {
            Model unnamed = mob(null, false);
            listener.onEntitySpawn(new EntitySpawnEvent(unnamed.entity));
        }
        Model last = mob("Last queued name", true);
        listener.onEntitySpawn(new EntitySpawnEvent(last.entity));

        advance(1);
        assertTrue(stands.isEmpty());
        assertTrue(last.visible);

        advance(DISCOVERY_INTERVAL_TICKS);
        assertEquals(1, stands.size());
        assertEquals("Last queued name", stands.getFirst().name);
        assertFalse(last.visible);

        advance(DISCOVERY_INTERVAL_TICKS);
        assertEquals(1, stands.size());
    }

    @Test
    void originallyHiddenNamesAndArmorStandsAreNotReplaced() {
        Model mob = mob("Hidden", false);
        listener.refreshLoadedEntities();
        advance(2 * DISCOVERY_INTERVAL_TICKS);
        assertEquals(0, stands.size());
        assertFalse(mob.visible);
        verify(mob.entity, never()).setCustomNameVisible(true);
    }

    @Test
    void unloadRestoresVisibilityAndReplacementWrapperRecoversOnlyOneDisplay() {
        Model original = mob("Original", true);
        listener.refreshLoadedEntities();
        listener.onEntitiesUnload(new EntitiesUnloadEvent(mock(Chunk.class), List.of(original.entity)));
        loaded.remove(original.entity.getUniqueId());
        assertTrue(original.visible);
        assertFalse(stands.getFirst().valid);

        Model replacement = mob("Original", true, original.entity.getUniqueId(), original.data);
        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(replacement.entity)));
        advance(FOLLOW_INTERVAL_TICKS);

        assertFalse(replacement.visible);
        assertEquals(1, stands.stream()
                              .filter(stand -> stand.valid)
                              .count());
    }

    @Test
    void crashMarkerIsRestoredWithFeaturesDisabledWithoutRecreatingAStand() {
        Model mob = mob("Original", false);
        mob.data.set(key("custom-name-hidden"), BOOLEAN, true);
        config.set("enable_holographic_custom_names", false);

        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(mob.entity)));

        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        assertEquals(0, stands.size());
    }

    @Test
    void worldUnloadRestoresVisibilityBeforeEntitiesBecomeUnavailable() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();
        listener.onWorldUnload(new WorldUnloadEvent(world));
        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        assertFalse(stands.getFirst().valid);
        loaded.clear();
        advance(2 * DISCOVERY_INTERVAL_TICKS);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void shutdownDuringFollowTeleportRestoresBothNamesWithoutRestartingTicker() {
        Model first = mob("First original", true);
        Model second = mob("Second original", true);
        listener.refreshLoadedEntities();
        assertEquals(2, stands.size());
        first.location.add(1, 0, 0);
        second.location.add(1, 0, 0);
        fixture.onTeleport = entity -> listener.shutdown();

        assertDoesNotThrow(() -> advance(2 * DISCOVERY_INTERVAL_TICKS));

        assertTrue(first.visible);
        assertTrue(second.visible);
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, fixture.clock.tasks.size());
        assertEquals(2, stands.size());
        assertFalse(first.data.has(key("custom-name-hidden")));
        assertFalse(second.data.has(key("custom-name-hidden")));
    }

    @Test
    void shutdownDuringHologramSpawnDoesNotHideTheNativeName() {
        Model mob = mob("Original", true);
        fixture.onSpawn = stand -> listener.shutdown();

        listener.replaceWithHologramIfNeeded(mob.entity);

        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void reloadDuringHologramSpawnKeepsOnlyTheRecoveredOwner() {
        Model mob = mob("Original", true);
        AtomicBoolean reloaded = new AtomicBoolean();
        fixture.onSpawn = stand -> {
            if (reloaded.compareAndSet(false, true)) {
                listener.shutdown();
                HolographUtil.HOLOGRAPH_UTIL.clearTracking();
                listener.refreshLoadedEntities();
            }
        };

        listener.replaceWithHologramIfNeeded(mob.entity);

        assertEquals(1, stands.stream()
                              .filter(stand -> stand.valid)
                              .count());
        assertFalse(mob.visible);
        assertTrue(mob.data.has(key("custom-name-hidden")));
        listener.shutdown();
        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void cleanupDuringHologramSpawnDoesNotHideTheDetachedName() {
        Model mob = mob("Original", true);
        fixture.onSpawn = stand -> listener.clearDisplay(mob.entity);

        listener.replaceWithHologramIfNeeded(mob.entity);
        advance(DISCOVERY_INTERVAL_TICKS);

        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void shutdownDuringDiscoveryStopsTheRestOfTheBatch() {
        Model first = mob("First", true);
        Model second = mob("Second", true);
        listener.onEntitySpawn(new EntitySpawnEvent(first.entity));
        listener.onEntitySpawn(new EntitySpawnEvent(second.entity));
        fixture.onSpawn = stand -> listener.shutdown();

        advance(DISCOVERY_INTERVAL_TICKS);

        assertEquals(1, stands.size());
        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(first.visible);
        assertTrue(second.visible);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void shutdownDuringLoadedEntityRefreshDoesNotCreateAnotherDisplay() {
        Model first = mob("First", true);
        Model second = mob("Second", true);
        fixture.onSpawn = stand -> listener.shutdown();

        listener.refreshLoadedEntities();

        assertEquals(1, stands.size());
        assertTrue(stands.stream().noneMatch(stand -> stand.valid));
        assertTrue(first.visible);
        assertTrue(second.visible);
        assertTrue(fixture.clock.allCancelled());
    }

    @Test
    void administrativeCleanupRestoresNativeNameAndStopsRediscoveryUntilRefresh() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();

        HolographUtil.HOLOGRAPH_UTIL.cleanup((ArmorStand) stands.getFirst().entity);
        advance(2 * DISCOVERY_INTERVAL_TICKS);

        assertTrue(mob.visible);
        assertEquals(1, stands.size());
        assertFalse(stands.getFirst().valid);
        listener.refreshLoadedEntities();
        assertEquals(2, stands.size());
        assertFalse(mob.visible);
    }

    @Test
    void areaRenameDetachesTheRealNameFollowerBeforeClearingNames() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();
        Player operator = mock(Player.class);
        when(operator.hasPermission("damageindicator.arearename")).thenReturn(true);
        when(operator.getNearbyEntities(50, 50, 50)).thenReturn(List.of(mob.entity, stands.getFirst().entity));
        doAnswer(invocation -> {
            listener.clearDisplay(invocation.getArgument(0));
            return null;
        }).when(plugin)
          .clearNameDisplays(any(LivingEntity.class));

        new AreaRenameCommand().onCommand(operator, mock(Command.class), "arearename", new String[0]);
        advance(2 * DISCOVERY_INTERVAL_TICKS);

        assertNull(mob.name);
        assertFalse(mob.visible);
        assertFalse(stands.getFirst().valid);
        assertEquals(1, stands.size());
        assertFalse(mob.data.has(key("custom-name-hidden")));
    }

    @Test
    void externallyRemovedStandCanBeRecreatedWithoutLosingTheName() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();
        stands.getFirst().valid = false;
        advance(DISCOVERY_INTERVAL_TICKS + 1);

        assertEquals(2, stands.size());
        assertFalse(mob.visible);
        assertEquals("Original", stands.getLast().name);
    }

    @Test
    void removalAndDeathReleaseDisplaysAndDiscoveryCandidates() {
        Model mob = mob("Removed", true);
        listener.refreshLoadedEntities();
        listener.onEntityRemove(new EntityRemoveEvent(mob.entity, EntityRemoveEvent.Cause.PLUGIN));
        loaded.remove(mob.entity.getUniqueId());
        advance(2 * DISCOVERY_INTERVAL_TICKS);
        assertFalse(stands.getFirst().valid);
        assertTrue(fixture.clock.allCancelled());

        Model dying = mob("Dying", true);
        listener.refreshLoadedEntities();
        EntityDeathEvent death = new EntityDeathEvent(dying.entity, fixture.damageSource, List.of());
        listener.onEntityDeath(death);
        assertTrue(dying.visible);
        assertFalse(stands.getLast().valid);
    }

    @Test
    void clearingANameRemovesItsHologramAndRestoresTheVisibilityFlag() {
        Model mob = mob("Original", true);
        listener.refreshLoadedEntities();
        mob.name = null;
        advance(FOLLOW_INTERVAL_TICKS);
        assertFalse(stands.getFirst().valid);
        assertTrue(mob.visible);
        assertFalse(mob.data.has(key("custom-name-hidden")));
        mob.name = "Named again";
        advance(DISCOVERY_INTERVAL_TICKS);
        assertEquals(2, stands.size());
        assertEquals("Named again", stands.getLast().name);
        assertFalse(mob.visible);
    }

    private Model mob(String name, boolean visible) {
        return mob(name, visible, UUID.randomUUID(), new MemoryPersistentData());
    }

    private Model mob(String name, boolean visible, UUID id, MemoryPersistentData data) {
        Model mob = fixture.entity(id, LivingEntity.class, name, data);
        mob.visible = visible;
        return mob;
    }

    private void advance(int count) {
        fixture.clock.advance(count);
    }
}
