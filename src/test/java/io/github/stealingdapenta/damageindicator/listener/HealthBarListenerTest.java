package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.support.ListenerFixture.key;
import static org.bukkit.persistence.PersistentDataType.BOOLEAN;
import static org.bukkit.persistence.PersistentDataType.STRING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.stealingdapenta.damageindicator.support.ListenerFixture;
import io.github.stealingdapenta.damageindicator.support.ListenerFixture.Model;
import io.github.stealingdapenta.damageindicator.support.TickScheduler.Task;
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthBarListenerTest {

    private static final int BAR_LENGTH = 10;
    private static final int FOLLOW_INTERVAL_TICKS = 3;
    private static final int CANONICAL_NAME_LIMIT = 256;

    private final ListenerFixture fixture = new ListenerFixture();
    private final Map<UUID, Entity> loaded = fixture.loaded;
    private final List<Model> stands = fixture.stands;
    private final List<Task> scheduled = fixture.clock.tasks;
    private final YamlConfiguration config = fixture.config;
    private HealthBarListener listener;
    private final World world = fixture.world;
    private final Logger logger = fixture.logger;

    @BeforeEach
    void setUp() {
        fixture.open();
        config.set("enable_health_bar", true);
        config.set("enable_hologram_health_bar", false);
        config.set("health_bar_always_visible", false);
        config.set("health_bar_display_duration", 1);
        config.set("health_bar_length", BAR_LENGTH);
        config.set("health_bar_prefix", "");
        config.set("health_bar_suffix", "");
        config.set("health_bar_alive_symbol", "A");
        config.set("health_bar_dead_symbol", "D");
        config.set("hologram_follow_speed", FOLLOW_INTERVAL_TICKS);
        listener = new HealthBarListener();
    }

    @AfterEach
    void tearDown() {
        listener.shutdown();
        HolographUtil.HOLOGRAPH_UTIL.clearTracking();
        fixture.close();
    }

    @Test
    void registriesProvideStableTypedAttributeAndDamageTypeEntries() {
        assertSame(Attribute.MAX_HEALTH, Registry.ATTRIBUTE.getOrThrow(NamespacedKey.minecraft("max_health")));
        assertSame(DamageType.GENERIC, Registry.DAMAGE_TYPE.getOrThrow(NamespacedKey.minecraft("generic")));
        assertEquals(NamespacedKey.minecraft("generic"), DamageType.GENERIC.getKeyOrThrow());
        assertTrue(DamageType.GENERIC.isRegistered());
    }

    @Test
    void cancelledHologramSpawnDoesNotRetryOrRetainCleanupHandlers() throws Exception {
        holograms();
        alwaysVisible();
        fixture.cancelSpawns = true;
        Model mob = mob("Original");
        damage(mob, 1);
        advance(40);

        assertEquals(1, stands.size());
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        assertEquals("Original", mob.name);
        assertEquals(0, registeredCleanupCount());
        damage(mob, 1);
        advance(40);
        assertEquals(2, stands.size());
        assertEquals(0, registeredCleanupCount());
        listener.clearDisplay(mob.entity, true);
        listener.shutdown();
        assertEquals(0, registeredCleanupCount());
        verify(logger).warning(contains("spawn was cancelled"));
    }

    @Test
    void worldUnloadRestoresNamesBeforeSaveAndReleasesHolographicOwnership() throws Exception {
        alwaysVisible();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        listener.onWorldUnload(new WorldUnloadEvent(world));
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
        assertTrue(mob.data.has(key("health-bar-activated")));

        holograms();
        listener.refreshPersistedHolograms();
        assertEquals(1, stands.size());
        listener.onWorldUnload(new WorldUnloadEvent(world));
        loaded.clear();
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertEquals(0, registeredCleanupCount());
        assertTrue(scheduled.getLast()
                            .isCancelled());
    }

    @Test
    void unavailableWorldWrappersStillReleaseHologramUuidsAndCallbacks() throws Exception {
        holograms();
        alwaysVisible();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        assertEquals(1, registeredCleanupCount());
        loaded.clear();

        advance(1);

        assertEquals(0, registeredCleanupCount());
        assertFalse(HolographUtil.HOLOGRAPH_UTIL.isActive(stands.getFirst().entity));
        assertTrue(scheduled.getLast()
                            .isCancelled());
    }

    @Test
    void legacyInlineActivationSurvivesMigrationAndRestartWithoutCapturingTheOldBar() {
        alwaysVisible();
        Model mob = mob("Old health bar");
        mob.visible = true;
        mob.data.set(key("health-name-overridden"), BOOLEAN, true);
        mob.data.set(key("health-original-name"), STRING, "Saved name");
        mob.data.set(key("health-original-visible"), BOOLEAN, false);
        listener.restorePersistedNames();
        assertEquals("Saved name", mob.name);
        listener.refreshPersistedHolograms();
        assertEquals("AAAAAAAAAA", mob.name);
        listener.shutdown();
        assertEquals("Saved name", mob.name);
        HealthBarListener restarted = new HealthBarListener();
        restarted.restorePersistedNames();
        restarted.refreshPersistedHolograms();
        assertEquals("AAAAAAAAAA", mob.name);
        restarted.shutdown();
        assertEquals("Saved name", mob.name);
        assertFalse(mob.visible);
    }

    private int registeredCleanupCount() throws Exception {
        var field = HolographUtil.class.getDeclaredField("cleanupHandlers");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(HolographUtil.HOLOGRAPH_UTIL)).size();
    }

    @Test
    void repeatedDamageReadsCommittedHealthOnceAndUsesOneHeartbeat() {
        Model mob = mob("Original");
        damage(mob, 2);
        damage(mob, 3);
        assertEquals("Original", mob.name);
        mob.health = 14;
        advance(1);
        assertEquals("AAAAAAADDD", mob.name);
        assertEquals(1, scheduled.size());
        damage(mob, 1);
        mob.health = 8;
        advance(1);
        assertEquals("AAAADDDDDD", mob.name);
        assertEquals(1, scheduled.size());
        advance(20);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
        assertTrue(scheduled.getFirst()
                            .isCancelled());
    }

    @Test
    void externalNamesBecomeTheNewOriginalBeforeRedrawOrReset() {
        Model mob = mob("Old");
        damage(mob, 2);
        advance(1);
        mob.name = "Name tag";
        mob.visible = false;
        advance(3);
        assertEquals("AAAAAAAAAA", mob.name);
        listener.clearDisplay(mob.entity, true);
        assertEquals("Name tag", mob.name);
        assertFalse(mob.visible);
        assertFalse(mob.data.has(key("health-bar-activated")));

        damage(mob, 2);
        advance(1);
        mob.name = "Other plugin";
        mob.visible = true;
        listener.shutdown();
        assertEquals("Other plugin", mob.name);
        assertTrue(mob.visible);
    }

    @Test
    void changingOnlyVisibilityDoesNotPromoteTheOverlayToAnOriginalName() {
        Model mob = mob("Original");
        mob.visible = true;
        damage(mob, 1);
        advance(1);
        mob.visible = false;
        advance(3);
        listener.clearDisplay(mob.entity, true);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
    }

    @Test
    void loadedLegacyOverlayRestoresStoredOriginalBeforeOwnershipWasRecorded() {
        Model mob = mob("Stuck legacy bar");
        mob.visible = true;
        mob.data.set(key("health-name-overridden"), BOOLEAN, true);
        mob.data.set(key("health-original-name"), STRING, "Original");
        mob.data.set(key("health-original-visible"), BOOLEAN, false);
        load(mob);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
        assertFalse(mob.data.has(key("health-name-overridden")));
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void firstDamageResolvesLegacyBackupBeforeCapturingANewOriginal() {
        Model mob = mob("Stuck legacy bar");
        mob.visible = true;
        mob.data.set(key("health-name-overridden"), BOOLEAN, true);
        mob.data.set(key("health-original-name"), STRING, "Original");
        mob.data.set(key("health-original-visible"), BOOLEAN, false);
        damage(mob, 1);
        advance(1);
        assertEquals("AAAAAAAAAA", mob.name);
        assertEquals("Original", mob.data.get(key("health-original-name"), STRING));
        listener.clearDisplay(mob.entity, true);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
    }

    @Test
    void canonicalizedTruncatedMulticolourOverlaysNeverReplaceTheOriginalBackup() {
        Model mob = mob("original");
        mob.nameCanonicalizer = value -> value == null || value.isEmpty() ? null : value.substring(0, Math.min(value.length(), CANONICAL_NAME_LIMIT))
                                                                                        .toLowerCase(Locale.ROOT);
        config.set("health_bar_prefix", "P".repeat(200));
        config.set("health_bar_length", 100);
        config.set("health_bar_alive_symbol", "&(1,2,3)Alive&(4,5,6)More");
        damage(mob, 1);
        advance(1);
        assertEquals(CANONICAL_NAME_LIMIT, mob.name.length());
        assertEquals(mob.name.toLowerCase(Locale.ROOT), mob.name);
        assertEquals(mob.name, mob.data.get(key("health-last-overlay"), STRING));
        advance(3);
        mob.health = 10;
        damage(mob, 1);
        advance(1);
        assertEquals("original", mob.data.get(key("health-original-name"), STRING));
        assertEquals(mob.name, mob.data.get(key("health-last-overlay"), STRING));
        listener.clearDisplay(mob.entity, true);
        assertEquals("original", mob.name);
        assertFalse(mob.visible);
    }

    @Test
    void emptyOverlayUsesPersistedSentinelAndStillRestoresTheOriginalName() {
        Model mob = mob("Original");
        mob.nameCanonicalizer = value -> value == null || value.isEmpty() ? null : value;
        config.set("health_bar_alive_symbol", "");
        config.set("health_bar_dead_symbol", "");
        damage(mob, 1);
        advance(1);
        assertNull(mob.name);
        assertEquals("", mob.data.get(key("health-last-overlay"), STRING));
        advance(3);
        damage(mob, 1);
        advance(1);
        assertEquals("Original", mob.data.get(key("health-original-name"), STRING));
        assertEquals("", mob.data.get(key("health-last-overlay"), STRING));
        listener.clearDisplay(mob.entity, true);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
    }

    @Test
    void hologramsAreReusedAndExpireInTicksNotFollowInvocations() {
        holograms();
        config.set("hologram_follow_speed", 7);
        Model mob = mob(null);
        damage(mob, 1);
        advance(1);
        damage(mob, 1);
        mob.health = 10;
        advance(1);
        assertEquals(1, stands.size());
        assertEquals("AAAAADDDDD", stands.getFirst().name);
        advance(19);
        assertTrue(stands.getFirst().valid);
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        assertNull(mob.name);
    }

    @Test
    void sharedHologramCleanupCancelsPendingRefreshAndDropsOwnership() {
        alwaysVisible();
        holograms();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        Model stand = stands.getFirst();
        assertTrue(HolographUtil.HOLOGRAPH_UTIL.isActive(stand.entity));
        heal(mob, 1);
        HolographUtil.HOLOGRAPH_UTIL.cleanup((ArmorStand) stand.entity);
        advance(5);
        assertEquals(1, stands.size());
        assertFalse(stand.valid);
        assertFalse(HolographUtil.HOLOGRAPH_UTIL.isActive(stand.entity));
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        assertFalse(mob.data.has(key("health-bar-activated")));
        load(mob);
        assertEquals(1, stands.size());
    }

    @Test
    void failedHologramFollowRemovesTheDisplayWithoutRespawning() {
        alwaysVisible();
        holograms();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        Model stand = stands.getFirst();
        stand.teleportSucceeds = false;
        mob.location.add(1, 0, 0);
        advance(5);
        assertFalse(stand.valid);
        assertFalse(HolographUtil.HOLOGRAPH_UTIL.isActive(stand.entity));
        assertEquals(1, stands.size());
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        verify(logger).warning(contains("Could not move hologram"));
    }

    @Test
    void shutdownDuringFollowTeleportStopsBothBarsWithoutRestartingTicker() {
        alwaysVisible();
        holograms();
        Model first = mob("First original");
        Model second = mob("Second original");
        damage(first, 1);
        damage(second, 1);
        advance(1);
        assertEquals(2, stands.size());
        first.location.add(1, 0, 0);
        second.location.add(1, 0, 0);
        fixture.onTeleport = entity -> listener.shutdown();

        assertDoesNotThrow(() -> advance(2 * FOLLOW_INTERVAL_TICKS));

        assertTrue(stands.stream()
                         .noneMatch(stand -> stand.valid));
        assertTrue(fixture.clock.allCancelled());
        assertEquals(1, scheduled.size());
        assertEquals(2, stands.size());
        assertEquals("First original", first.name);
        assertEquals("Second original", second.name);
    }

    @Test
    void shutdownAndRestartRecoverOnlyActivatedBarsInEitherMode() {
        alwaysVisible();
        Model activated = mob("Original");
        Model untouched = mob("Untouched");
        damage(activated, 1);
        advance(1);
        listener.shutdown();
        assertEquals("Original", activated.name);
        assertTrue(activated.data.has(key("health-bar-activated")));
        HealthBarListener restarted = new HealthBarListener();
        restarted.restorePersistedNames();
        restarted.refreshPersistedHolograms();
        assertEquals("AAAAAAAAAA", activated.name);
        assertEquals("Untouched", untouched.name);
        restarted.refreshPersistedHolograms();
        restarted.shutdown();
        assertEquals("Original", activated.name);

        holograms();
        HealthBarListener switched = new HealthBarListener();
        switched.refreshPersistedHolograms();
        switched.refreshPersistedHolograms();
        assertEquals(1, stands.size());
        switched.shutdown();
    }

    @Test
    void sameListenerResumesAfterReloadButPendingOldTasksCannotRecreateBars() {
        alwaysVisible();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        heal(mob, 3);
        listener.shutdown();
        advance(2);
        assertEquals("Original", mob.name);
        listener.refreshPersistedHolograms();
        assertEquals("AAAAAAAAAA", mob.name);
        damage(mob, 2);
        mob.health = 10;
        advance(1);
        assertEquals("AAAAADDDDD", mob.name);
        listener.shutdown();
        config.set("enable_health_bar", false);
        listener.refreshPersistedHolograms();
        assertEquals("Original", mob.name);
        assertFalse(mob.data.has(key("health-bar-activated")));
    }

    @Test
    void temporaryHealRefreshesAnExistingBarAndResetsItsExpiry() {
        Model mob = mob("Original");
        damage(mob, 1);
        mob.health = 10;
        advance(18);
        heal(mob, 6);
        mob.health = 16;
        advance(1);
        assertEquals("AAAAAAAADD", mob.name);
        advance(19);
        assertEquals("AAAAAAAADD", mob.name);
        advance(1);
        assertEquals("Original", mob.name);
        assertEquals(1, scheduled.size());
    }

    @Test
    void loadMigratesLegacyActivationAndDisabledRecoveryClearsIt() {
        alwaysVisible();
        holograms();
        Model mob = mob(null);
        mob.data.set(key("always-visible-health-hologram"), BOOLEAN, true);
        load(mob);
        load(mob);
        assertEquals(1, stands.size());
        assertFalse(mob.data.has(key("always-visible-health-hologram")));
        assertTrue(mob.data.has(key("health-bar-activated")));
        config.set("enable_health_bar", false);
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertFalse(mob.data.has(key("health-bar-activated")));
        load(mob);
        assertEquals(1, stands.size());
    }

    @Test
    void unloadDropsPendingWorkAndRestoresNameWithoutLosingActivation() {
        alwaysVisible();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        heal(mob, 2);
        listener.onEntitiesUnload(new EntitiesUnloadEvent(mock(Chunk.class), List.of(mob.entity)));
        loaded.remove(mob.id);
        advance(4);
        assertEquals("Original", mob.name);
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        Model replacement = fixture.entity(mob.id, LivingEntity.class, mob.name, mob.data);
        load(replacement);
        assertEquals("AAAAAAAAAA", replacement.name);
        listener.clearDisplay(replacement.entity, true);
        assertEquals("Original", replacement.name);
    }

    @Test
    void removedEntityIsNeverMutatedAndItsPersistedOverlayCanRecover() {
        alwaysVisible();
        Model mob = mob("Original");
        damage(mob, 1);
        advance(1);
        clearInvocations(mob.entity);
        listener.onEntityRemove(new EntityRemoveEvent(mob.entity, EntityRemoveEvent.Cause.PLUGIN));
        loaded.remove(mob.id);
        advance(3);
        verify(mob.entity, never()).setCustomName(any());
        verify(mob.entity, never()).setCustomNameVisible(true);
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        Model replacement = fixture.entity(mob.id, LivingEntity.class, mob.name, mob.data);
        replacement.visible = true;
        load(replacement);
        listener.clearDisplay(replacement.entity, true);
        assertEquals("Original", replacement.name);
    }

    @Test
    void healingAndResurrectionCoalesceAndDeathPreventsRecreation() {
        alwaysVisible();
        Model mob = mob("Original");
        heal(mob, 2);
        EntityResurrectEvent resurrection = new EntityResurrectEvent(mob.entity, EquipmentSlot.HAND);
        resurrection.setCancelled(false);
        listener.updateHealthBarOnResurrection(resurrection);
        mob.health = 1;
        advance(1);
        assertEquals("ADDDDDDDDD", mob.name);
        assertEquals(1, scheduled.size());
        heal(mob, 2);
        EntityDeathEvent death = new EntityDeathEvent(mob.entity, fixture.damageSource, List.of());
        mob.dead = true;
        listener.handleDeathEvents(death);
        advance(3);
        assertEquals("Original", mob.name);
        assertFalse(mob.data.has(key("health-bar-activated")));
        load(mob);
        assertEquals("Original", mob.name);
    }

    @Test
    void cancelledAndNoOpEventsDoNotActivateBarsAndTemporaryHealDoesNotStartOne() throws Exception {
        Model mob = mob("Original");
        damage(mob, 0);
        EntityDamageEvent cancelled = damageEvent(mob, 4);
        cancelled.setCancelled(true);
        listener.displayHealthBar(cancelled);
        heal(mob, 2);
        EntityResurrectEvent resurrection = new EntityResurrectEvent(mob.entity, EquipmentSlot.HAND);
        resurrection.setCancelled(true);
        EventHandler policy = HealthBarListener.class.getMethod("updateHealthBarOnResurrection", EntityResurrectEvent.class)
                                                     .getAnnotation(EventHandler.class);
        RegisteredListener registration = new RegisteredListener(listener, (target, event) -> listener.updateHealthBarOnResurrection(resurrection), policy.priority(), fixture.plugin, policy.ignoreCancelled());
        registration.callEvent(resurrection);
        advance(2);
        assertTrue(scheduled.isEmpty());
        assertEquals("Original", mob.name);
    }

    @Test
    void bukkitRegistrationFiltersCancelledHealing() throws Exception {
        alwaysVisible();
        Model mob = mob("Original");
        EntityRegainHealthEvent healing = new EntityRegainHealthEvent(mob.entity, 2, EntityRegainHealthEvent.RegainReason.CUSTOM);
        healing.setCancelled(true);
        EventHandler policy = HealthBarListener.class.getMethod("updateHealthBarOnHeal", EntityRegainHealthEvent.class)
                                                     .getAnnotation(EventHandler.class);
        RegisteredListener registration = new RegisteredListener(listener, (target, event) -> listener.updateHealthBarOnHeal(healing), policy.priority(), fixture.plugin, policy.ignoreCancelled());
        registration.callEvent(healing);
        advance(2);
        assertTrue(scheduled.isEmpty());
        assertEquals("Original", mob.name);
    }

    @Test
    void pendingRefreshDoesNotReviveAfterShutdownOrInvalidEntity() {
        Model mob = mob("Original");
        damage(mob, 1);
        listener.shutdown();
        advance(2);
        assertEquals("Original", mob.name);
        assertFalse(mob.data.has(key("health-name-overridden")));
        listener.displayHealthBar(damageEvent(mob, 1));
        assertEquals(1, scheduled.size());
    }

    @Test
    void zeroHealthHasNoAliveSymbolsAndInvalidMaximumIsDiagnosed() {
        Model mob = mob("Original");
        damage(mob, 1);
        mob.health = 0;
        advance(1);
        assertEquals("DDDDDDDDDD", mob.name);
        listener.clearDisplay(mob.entity, true);
        mob.health = 5;
        mob.maximumHealth = Double.NaN;
        damage(mob, 1);
        advance(1);
        assertEquals("Original", mob.name);
        verify(logger).warning(contains("invalid MAX_HEALTH"));
        assertTrue(scheduled.getLast()
                            .isCancelled());
    }

    @Test
    void inlinePlayersAreNeverRenamedWhileHologramsRecoverOnRejoin() {
        alwaysVisible();
        Model player = fixture.entity(Player.class, "Player plugin name");
        damage(player, 1);
        heal(player, 1);
        advance(1);
        assertTrue(scheduled.isEmpty());
        assertEquals("Player plugin name", player.name);
        holograms();
        damage(player, 1);
        advance(1);
        assertEquals(1, stands.size());
        PlayerQuitEvent quit = new PlayerQuitEvent((Player) player.entity, null);
        listener.onPlayerQuit(quit);
        assertFalse(stands.getFirst().valid);
        PlayerJoinEvent join = new PlayerJoinEvent((Player) player.entity, null);
        listener.onPlayerJoin(join);
        assertEquals(1, stands.size());
        advance(1);
        assertEquals(2, stands.size());
        assertEquals("Player plugin name", player.name);
        verify(player.entity, never()).setCustomName(any());
    }

    @Test
    void missingMaximumAndUnloadBeforeFirstRefreshCannotLeaveTrackedBars() {
        Model mob = mob("Original");
        mob.hasMaximum = false;
        damage(mob, 1);
        advance(1);
        verify(logger).warning(contains("missing or invalid MAX_HEALTH"));
        assertTrue(scheduled.getFirst()
                            .isCancelled());
        mob.hasMaximum = true;
        damage(mob, 1);
        mob.valid = false;
        advance(1);
        assertTrue(scheduled.getLast()
                            .isCancelled());
        assertEquals("Original", mob.name);
    }

    private void holograms() {
        config.set("enable_hologram_health_bar", true);
    }

    private void alwaysVisible() {
        config.set("health_bar_always_visible", true);
    }

    private Model mob(String name) {
        return fixture.mob(name);
    }

    private void damage(Model model, double amount) {
        listener.displayHealthBar(damageEvent(model, amount));
    }

    private EntityDamageEvent damageEvent(Model model, double amount) {
        return new EntityDamageEvent(model.entity, EntityDamageEvent.DamageCause.CUSTOM, fixture.damageSource, amount);
    }

    private void heal(Model model, double amount) {
        listener.updateHealthBarOnHeal(new EntityRegainHealthEvent(model.entity, amount, EntityRegainHealthEvent.RegainReason.CUSTOM));
    }

    private void load(Model model) {
        listener.onEntitiesLoad(new EntitiesLoadEvent(mock(Chunk.class), List.of(model.entity)));
    }

    private void advance(int ticks) {
        fixture.clock.advance(ticks);
    }
}
