package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.support.BukkitRegistries;
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class HealthBarListenerTest {

    private final Map<UUID, Entity> loaded = new HashMap<>();
    private final List<Model> mobs = new ArrayList<>();
    private final List<Model> stands = new ArrayList<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final YamlConfiguration config = new YamlConfiguration();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<DamageIndicator> singleton;
    private HealthBarListener listener;
    private World world;
    private Logger logger;
    private boolean cancelSpawns;

    @BeforeAll
    static void initializeRegistries() {
        BukkitRegistries.initialize();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        DamageIndicator plugin = mock(DamageIndicator.class);
        logger = mock(Logger.class);
        when(plugin.getName()).thenReturn("DamageIndicator");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        singleton = mockStatic(DamageIndicator.class);
        singleton.when(DamageIndicator::getInstance)
                 .thenReturn(plugin);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler)
              .thenReturn(scheduler);
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class)))
              .thenAnswer(call -> loaded.get(call.getArgument(0)));
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            Scheduled task = new Scheduled(call.getArgument(1));
            scheduled.add(task);
            return task.task;
        });
        world = mock(World.class);
        bukkit.when(Bukkit::getWorlds)
              .thenReturn(List.of(world));
        when(world.getLivingEntities()).thenAnswer(call -> mobs.stream()
                                                               .filter(model -> loaded.containsKey(model.id))
                                                               .map(model -> model.entity)
                                                               .toList());
        when(world.spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class))).thenAnswer(call -> {
            Model stand = new Model(ArmorStand.class, null);
            stands.add(stand);
            ((Consumer<ArmorStand>) call.getArgument(2)).accept((ArmorStand) stand.entity);
            if (cancelSpawns) {
                stand.valid = false;
                loaded.remove(stand.id);
            }
            return stand.entity;
        });
        config.set("enable_health_bar", true);
        config.set("enable_hologram_health_bar", false);
        config.set("health_bar_always_visible", false);
        config.set("health_bar_display_duration", 1);
        config.set("health_bar_length", 10);
        config.set("health_bar_prefix", "");
        config.set("health_bar_suffix", "");
        config.set("health_bar_alive_symbol", "A");
        config.set("health_bar_dead_symbol", "D");
        config.set("hologram_follow_speed", 3);
        listener = new HealthBarListener();
    }

    @AfterEach
    void tearDown() {
        HolographUtil.HOLOGRAPH_UTIL.clearTracking();
        singleton.close();
        bukkit.close();
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
        cancelSpawns = true;
        Model mob = mob("Original");
        damage(mob, 1);
        advance(40);

        assertEquals(1, stands.size());
        assertTrue(scheduled.getFirst().cancelled.get());
        assertEquals("Original", mob.name);
        assertEquals(0, registeredCleanupCount());
        damage(mob, 1);
        advance(40);
        assertEquals(2, stands.size());
        assertEquals(0, registeredCleanupCount());
        listener.clearDisplay(mob.entity, true);
        listener.shutdown();
        assertEquals(0, registeredCleanupCount());
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("spawn was cancelled"));
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
        assertTrue(mob.data.containsKey(key("health-bar-activated")));

        holograms();
        listener.refreshPersistedHolograms();
        assertEquals(1, stands.size());
        listener.onWorldUnload(new WorldUnloadEvent(world));
        loaded.clear();
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertEquals(0, registeredCleanupCount());
        assertTrue(scheduled.getLast().cancelled.get());
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
        assertTrue(scheduled.getLast().cancelled.get());
    }

    @Test
    void legacyInlineActivationSurvivesMigrationAndRestartWithoutCapturingTheOldBar() {
        alwaysVisible();
        Model mob = mob("Old health bar");
        mob.visible = true;
        mob.data.put(key("health-name-overridden"), true);
        mob.data.put(key("health-original-name"), "Saved name");
        mob.data.put(key("health-original-visible"), false);
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
        assertTrue(scheduled.getFirst().cancelled.get());
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
        assertFalse(mob.data.containsKey(key("health-bar-activated")));

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
        mob.data.put(key("health-name-overridden"), true);
        mob.data.put(key("health-original-name"), "Original");
        mob.data.put(key("health-original-visible"), false);
        load(mob);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
        assertFalse(mob.data.containsKey(key("health-name-overridden")));
        assertTrue(scheduled.isEmpty());
    }

    @Test
    void firstDamageResolvesLegacyBackupBeforeCapturingANewOriginal() {
        Model mob = mob("Stuck legacy bar");
        mob.visible = true;
        mob.data.put(key("health-name-overridden"), true);
        mob.data.put(key("health-original-name"), "Original");
        mob.data.put(key("health-original-visible"), false);
        damage(mob, 1);
        advance(1);
        assertEquals("AAAAAAAAAA", mob.name);
        assertEquals("Original", mob.data.get(key("health-original-name")));
        listener.clearDisplay(mob.entity, true);
        assertEquals("Original", mob.name);
        assertFalse(mob.visible);
    }

    @Test
    void canonicalizedTruncatedMulticolourOverlaysNeverReplaceTheOriginalBackup() {
        Model mob = mob("original");
        mob.nameCanonicalizer = value -> value == null || value.isEmpty() ? null
            : value.substring(0, Math.min(value.length(), 256)).toLowerCase(Locale.ROOT);
        config.set("health_bar_prefix", "P".repeat(200));
        config.set("health_bar_length", 100);
        config.set("health_bar_alive_symbol", "&(1,2,3)Alive&(4,5,6)More");
        damage(mob, 1);
        advance(1);
        assertEquals(256, mob.name.length());
        assertEquals(mob.name.toLowerCase(Locale.ROOT), mob.name);
        assertEquals(mob.name, mob.data.get(key("health-last-overlay")));
        advance(3);
        mob.health = 10;
        damage(mob, 1);
        advance(1);
        assertEquals("original", mob.data.get(key("health-original-name")));
        assertEquals(mob.name, mob.data.get(key("health-last-overlay")));
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
        assertEquals("", mob.data.get(key("health-last-overlay")));
        advance(3);
        damage(mob, 1);
        advance(1);
        assertEquals("Original", mob.data.get(key("health-original-name")));
        assertEquals("", mob.data.get(key("health-last-overlay")));
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
        assertTrue(scheduled.getFirst().cancelled.get());
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
        assertTrue(scheduled.getFirst().cancelled.get());
        assertFalse(mob.data.containsKey(key("health-bar-activated")));
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
        when(stand.entity.teleport(any(Location.class))).thenReturn(false);
        advance(5);
        assertFalse(stand.valid);
        assertFalse(HolographUtil.HOLOGRAPH_UTIL.isActive(stand.entity));
        assertEquals(1, stands.size());
        assertTrue(scheduled.getFirst().cancelled.get());
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("Could not move hologram"));
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
        assertTrue(activated.data.containsKey(key("health-bar-activated")));
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
        assertFalse(mob.data.containsKey(key("health-bar-activated")));
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
        mob.data.put(key("always-visible-health-hologram"), true);
        load(mob);
        load(mob);
        assertEquals(1, stands.size());
        assertFalse(mob.data.containsKey(key("always-visible-health-hologram")));
        assertTrue(mob.data.containsKey(key("health-bar-activated")));
        config.set("enable_health_bar", false);
        advance(1);
        assertFalse(stands.getFirst().valid);
        assertFalse(mob.data.containsKey(key("health-bar-activated")));
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
        EntitiesUnloadEvent event = mock(EntitiesUnloadEvent.class);
        when(event.getEntities()).thenReturn(List.of(mob.entity));
        listener.onEntitiesUnload(event);
        loaded.remove(mob.id);
        advance(4);
        assertEquals("Original", mob.name);
        assertTrue(scheduled.getFirst().cancelled.get());
        Model replacement = new Model(mob.id, LivingEntity.class, mob.name, mob.data);
        mobs.add(replacement);
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
        org.mockito.Mockito.clearInvocations(mob.entity);
        EntityRemoveEvent event = mock(EntityRemoveEvent.class);
        when(event.getEntity()).thenReturn(mob.entity);
        listener.onEntityRemove(event);
        loaded.remove(mob.id);
        advance(3);
        verify(mob.entity, never()).setCustomName(any());
        verify(mob.entity, never()).setCustomNameVisible(true);
        assertTrue(scheduled.getFirst().cancelled.get());
        Model replacement = new Model(mob.id, LivingEntity.class, mob.name, mob.data);
        replacement.visible = true;
        mobs.add(replacement);
        load(replacement);
        listener.clearDisplay(replacement.entity, true);
        assertEquals("Original", replacement.name);
    }

    @Test
    void healingAndResurrectionCoalesceAndDeathPreventsRecreation() {
        alwaysVisible();
        Model mob = mob("Original");
        heal(mob, 2);
        EntityResurrectEvent resurrection = mock(EntityResurrectEvent.class);
        when(resurrection.getEntity()).thenReturn(mob.entity);
        listener.updateHealthBarOnResurrection(resurrection);
        mob.health = 1;
        advance(1);
        assertEquals("ADDDDDDDDD", mob.name);
        assertEquals(1, scheduled.size());
        heal(mob, 2);
        EntityDeathEvent death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(mob.entity);
        mob.dead = true;
        listener.handleDeathEvents(death);
        advance(3);
        assertEquals("Original", mob.name);
        assertFalse(mob.data.containsKey(key("health-bar-activated")));
        load(mob);
        assertEquals("Original", mob.name);
    }

    @Test
    void cancelledAndNoOpEventsDoNotActivateBarsAndTemporaryHealDoesNotStartOne() {
        Model mob = mob("Original");
        damage(mob, 0);
        EntityDamageEvent cancelled = damageEvent(mob, 4);
        when(cancelled.isCancelled()).thenReturn(true);
        listener.displayHealthBar(cancelled);
        heal(mob, 2);
        EntityResurrectEvent resurrection = mock(EntityResurrectEvent.class);
        when(resurrection.getEntity()).thenReturn(mob.entity);
        when(resurrection.isCancelled()).thenReturn(true);
        listener.updateHealthBarOnResurrection(resurrection);
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
        assertFalse(mob.data.containsKey(key("health-name-overridden")));
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
        when(mob.maximum.getValue()).thenReturn(Double.NaN);
        damage(mob, 1);
        advance(1);
        assertEquals("Original", mob.name);
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("invalid MAX_HEALTH"));
        assertTrue(scheduled.getLast().cancelled.get());
    }

    @Test
    void inlinePlayersAreNeverRenamedWhileHologramsRecoverOnRejoin() {
        alwaysVisible();
        Model player = new Model(Player.class, "Player plugin name");
        mobs.add(player);
        damage(player, 1);
        heal(player, 1);
        advance(1);
        assertTrue(scheduled.isEmpty());
        assertEquals("Player plugin name", player.name);
        holograms();
        damage(player, 1);
        advance(1);
        assertEquals(1, stands.size());
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn((Player) player.entity);
        listener.onPlayerQuit(quit);
        assertFalse(stands.getFirst().valid);
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn((Player) player.entity);
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
        when(mob.entity.getAttribute(any())).thenReturn(null);
        damage(mob, 1);
        advance(1);
        verify(logger).warning(org.mockito.ArgumentMatchers.contains("missing or invalid MAX_HEALTH"));
        assertTrue(scheduled.getFirst().cancelled.get());
        when(mob.entity.getAttribute(any())).thenReturn(mob.maximum);
        damage(mob, 1);
        mob.valid = false;
        advance(1);
        assertTrue(scheduled.getLast().cancelled.get());
        assertEquals("Original", mob.name);
    }

    private void holograms() {
        config.set("enable_hologram_health_bar", true);
    }

    private void alwaysVisible() {
        config.set("health_bar_always_visible", true);
    }

    private Model mob(String name) {
        Model model = new Model(LivingEntity.class, name);
        mobs.add(model);
        return model;
    }

    private void damage(Model model, double amount) {
        listener.displayHealthBar(damageEvent(model, amount));
    }

    private EntityDamageEvent damageEvent(Model model, double amount) {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(model.entity);
        when(event.getFinalDamage()).thenReturn(amount);
        return event;
    }

    private void heal(Model model, double amount) {
        EntityRegainHealthEvent event = mock(EntityRegainHealthEvent.class);
        when(event.getEntity()).thenReturn(model.entity);
        when(event.getAmount()).thenReturn(amount);
        listener.updateHealthBarOnHeal(event);
    }

    private void load(Model model) {
        EntitiesLoadEvent event = mock(EntitiesLoadEvent.class);
        when(event.getEntities()).thenReturn(List.of(model.entity));
        listener.onEntitiesLoad(event);
    }

    private void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            for (Scheduled task : List.copyOf(scheduled)) {
                if (!task.cancelled.get()) {
                    task.runnable.run();
                }
            }
        }
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("damageindicator", key);
    }

    private static final class Scheduled {

        private final Runnable runnable;
        private final BukkitTask task = mock(BukkitTask.class);
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private Scheduled(Runnable runnable) {
            this.runnable = runnable;
            doAnswer(call -> {
                cancelled.set(true);
                return null;
            }).when(task)
              .cancel();
        }
    }

    private final class Model {

        private final UUID id;
        private final LivingEntity entity;
        private final AttributeInstance maximum = mock(AttributeInstance.class);
        private final Map<NamespacedKey, Object> data;
        private String name;
        private boolean visible;
        private boolean valid = true;
        private boolean dead;
        private double health = 20;
        private UnaryOperator<String> nameCanonicalizer = UnaryOperator.identity();

        private Model(Class<? extends LivingEntity> type, String name) {
            this(UUID.randomUUID(), type, name, new HashMap<>());
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private Model(UUID id, Class<? extends LivingEntity> type, String name, Map<NamespacedKey, Object> data) {
            this.id = id;
            this.name = name;
            this.data = data;
            entity = mock(type);
            PersistentDataContainer pdc = mock(PersistentDataContainer.class);
            when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(call -> data.get(call.getArgument(0)));
            doAnswer(call -> {
                data.put(call.getArgument(0), call.getArgument(2));
                return null;
            }).when(pdc)
              .set(any(NamespacedKey.class), any(PersistentDataType.class), any());
            doAnswer(call -> {
                data.remove(call.getArgument(0));
                return null;
            }).when(pdc)
              .remove(any());
            when(entity.getUniqueId()).thenReturn(id);
            when(entity.getPersistentDataContainer()).thenReturn(pdc);
            when(entity.getHealth()).thenAnswer(call -> health);
            when(entity.isValid()).thenAnswer(call -> valid);
            when(entity.isDead()).thenAnswer(call -> dead);
            when(entity.getCustomName()).thenAnswer(call -> this.name);
            when(entity.isCustomNameVisible()).thenAnswer(call -> visible);
            when(entity.getAttribute(any())).thenReturn(maximum);
            when(maximum.getValue()).thenReturn(20.0);
            when(entity.getLocation()).thenAnswer(call -> new Location(world, 0, 0, 0));
            when(entity.teleport(any(Location.class))).thenReturn(true);
            doAnswer(call -> {
                this.name = nameCanonicalizer.apply(call.getArgument(0));
                return null;
            }).when(entity)
              .setCustomName(any());
            doAnswer(call -> {
                visible = call.getArgument(0);
                return null;
            }).when(entity)
              .setCustomNameVisible(org.mockito.ArgumentMatchers.anyBoolean());
            doAnswer(call -> {
                valid = false;
                loaded.remove(id);
                return null;
            }).when(entity)
              .remove();
            loaded.put(id, entity);
        }
    }
}
