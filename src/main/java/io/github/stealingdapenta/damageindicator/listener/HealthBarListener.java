package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_ALIVE_SYMBOL;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_ALWAYS_VISIBLE;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_DEAD_SYMBOL;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_DISPLAY_DURATION;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_LENGTH;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_PREFIX;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_SUFFIX;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_FOLLOW_SPEED;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_POSITION;
import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.NativeDamageAccounting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.scheduler.BukkitTask;

/**
 * Activation-based bars updated from committed health by one next-tick heartbeat. UUID tracking avoids retaining unloaded wrappers; players use holograms only.
 */
public class HealthBarListener implements Listener {

    private static final int TICKS_PER_SECOND = 20;
    private static final long HEARTBEAT_PERIOD_TICKS = 1;
    private static final NamespacedKey ORIGINAL_MARKER = key("health-name-overridden");
    private static final NamespacedKey ORIGINAL_NAME = key("health-original-name");
    private static final NamespacedKey ORIGINAL_VISIBLE = key("health-original-visible");
    private static final NamespacedKey LAST_OVERLAY = key("health-last-overlay");
    private static final NamespacedKey ACTIVATED = key("health-bar-activated");
    private static final NamespacedKey LEGACY_ACTIVATED = key("always-visible-health-hologram");

    private final Map<UUID, ActiveBar> activeBars = new HashMap<>();
    private final Set<UUID> pendingRefreshes = new HashSet<>();
    private final Set<UUID> pendingRecoveries = new HashSet<>();
    private BukkitTask heartbeat;
    private long currentTick;
    private boolean stopped;

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString("damageindicator:" + value));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity entity && NativeDamageAccounting.appliedDamage(event, entity) > 0) {
            queueRefresh(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void updateHealthBarOnHeal(EntityRegainHealthEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean() || !hasPositiveHealing(event)) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity entity && shouldRefreshOnHeal(entity)) {
            queueRefresh(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void updateHealthBarOnResurrection(EntityResurrectEvent event) {
        if (ENABLE_HEALTH_BAR.asBoolean()) {
            queueRefresh(event.getEntity());
        }
    }

    private void queueRefresh(LivingEntity entity) {
        if (stopped || !supportsDisplayMode(entity)) {
            return;
        }
        pendingRefreshes.add(entity.getUniqueId());
        startHeartbeat();
    }

    private boolean hasPositiveHealing(EntityRegainHealthEvent event) {
        return Double.isFinite(event.getAmount()) && event.getAmount() > 0;
    }

    private boolean shouldRefreshOnHeal(LivingEntity entity) {
        return activeBars.containsKey(entity.getUniqueId()) || HEALTH_BAR_ALWAYS_VISIBLE.asBoolean();
    }

    private boolean supportsDisplayMode(LivingEntity entity) {
        return !(entity instanceof Player) || ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean();
    }

    private void startHeartbeat() {
        if (!stopped && heartbeat == null) {
            heartbeat = Bukkit.getScheduler()
                              .runTaskTimer(DamageIndicator.getInstance(), this::heartbeat, HEARTBEAT_PERIOD_TICKS, HEARTBEAT_PERIOD_TICKS);
        }
    }

    private void heartbeat() {
        currentTick++;
        flushRecoveries();
        flushHealthUpdates();
        new ArrayList<>(activeBars.keySet()).forEach(this::updateActiveBar);
        stopHeartbeatIfIdle();
    }

    private void flushRecoveries() {
        for (UUID id : new ArrayList<>(pendingRecoveries)) {
            pendingRecoveries.remove(id);
            if (Bukkit.getEntity(id) instanceof LivingEntity entity) {
                recover(entity);
            }
        }
    }

    /**
     * Multiple damage/heal events in one tick share a single committed-health read.
     */
    private void flushHealthUpdates() {
        for (UUID id : new ArrayList<>(pendingRefreshes)) {
            pendingRefreshes.remove(id);
            if (Bukkit.getEntity(id) instanceof LivingEntity entity && canDisplay(entity)) {
                activate(entity);
            }
        }
    }

    private void updateActiveBar(UUID id) {
        ActiveBar bar = activeBars.get(id);
        if (bar == null) {
            return;
        }
        if (!(Bukkit.getEntity(id) instanceof LivingEntity entity)) {
            detach(id);
        } else if (!ENABLE_HEALTH_BAR.asBoolean()) {
            clearDisplay(entity, true);
        } else if (!canDisplay(entity)) {
            clearDisplay(entity, entity.isDead());
        } else if (hasExpired(bar)) {
            clearDisplay(entity, false);
        } else if (currentTick >= bar.nextFollow) {
            render(entity, bar);
        }
    }

    private boolean hasExpired(ActiveBar bar) {
        return !HEALTH_BAR_ALWAYS_VISIBLE.asBoolean() && currentTick >= bar.expiresAt;
    }

    private boolean canDisplay(LivingEntity entity) {
        if (stopped || !ENABLE_HEALTH_BAR.asBoolean()) {
            return false;
        }
        return entity.isValid() && !entity.isDead() && supportsDisplayMode(entity);
    }

    private void activate(LivingEntity entity) {
        ActiveBar bar = activeBars.computeIfAbsent(entity.getUniqueId(), ignored -> new ActiveBar());
        bar.expiresAt = currentTick + (long) TICKS_PER_SECOND * HEALTH_BAR_DISPLAY_DURATION.asInt();
        entity.getPersistentDataContainer()
              .set(ACTIVATED, PersistentDataType.BOOLEAN, true);
        entity.getPersistentDataContainer()
              .remove(LEGACY_ACTIVATED);
        render(entity, bar);
    }

    private void render(LivingEntity entity, ActiveBar bar) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? Double.NaN : attribute.getValue();
        if (!Double.isFinite(max) || max <= 0) {
            DamageIndicator.getInstance()
                           .getLogger()
                           .warning("Cannot display health bar for " + entity.getUniqueId() + ": missing or invalid MAX_HEALTH (" + max + ").");
            clearDisplay(entity, false);
            return;
        }
        double health = entity.getHealth();
        if (!Double.isFinite(health)) {
            DamageIndicator.getInstance()
                           .getLogger()
                           .warning("Cannot display health bar for " + entity.getUniqueId() + ": non-finite health (" + health + ").");
            clearDisplay(entity, false);
            return;
        }
        String name = TEXT_UTIL.serializeComponent(createHealthBar(Math.clamp(health, 0, max), max));
        bar.nextFollow = currentTick + HOLOGRAM_FOLLOW_SPEED.asInt();
        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            renderHologram(entity, bar, name);
        } else {
            removeHologram(bar);
            writeOwnedName(entity, name);
        }
    }

    private void renderHologram(LivingEntity entity, ActiveBar bar, String name) {
        restoreOwnedName(entity);
        Entity existing = bar.hologram == null ? null : Bukkit.getEntity(bar.hologram);
        if (!(existing instanceof ArmorStand stand) || !stand.isValid()) {
            createHologram(entity, bar, name);
            return;
        }
        if (!Objects.equals(stand.getCustomName(), name)) {
            stand.setCustomName(name);
        }
        if (!HOLOGRAPH_UTIL.teleportIfMoved(stand, HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_POSITION.asDouble()))) {
            clearDisplay(entity, false);
        }
    }

    private void createHologram(LivingEntity entity, ActiveBar bar, String name) {
        ArmorStand created = HOLOGRAPH_UTIL.createArmorStandHologram(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_POSITION.asDouble()), TEXT_UTIL.deserializeComponent(name));
        if (!created.isValid()) {
            clearDisplay(entity, false);
            return;
        }
        bar.hologram = created.getUniqueId();
        UUID ownerId = entity.getUniqueId();
        HOLOGRAPH_UTIL.onCleanup(created, () -> clearHologramOwner(ownerId));
    }

    private void clearHologramOwner(UUID ownerId) {
        if (Bukkit.getEntity(ownerId) instanceof LivingEntity owner) {
            clearDisplay(owner, true);
            return;
        }
        cancelPendingWork(ownerId);
        detach(ownerId);
        stopHeartbeatIfIdle();
    }

    /**
     * External name changes become the new backup before the next overlay is written.
     */
    private void writeOwnedName(LivingEntity entity, String overlay) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (hasLegacyOriginal(data)) {
            restoreOwnedName(entity);
        }
        // A name tag or another plugin can replace our last overlay at any time.
        if (!ownsCurrentName(entity)) {
            data.set(ORIGINAL_MARKER, PersistentDataType.BOOLEAN, true);
            data.set(ORIGINAL_VISIBLE, PersistentDataType.BOOLEAN, entity.isCustomNameVisible());
            String original = entity.getCustomName();
            if (original == null) {
                data.remove(ORIGINAL_NAME);
            } else {
                data.set(ORIGINAL_NAME, PersistentDataType.STRING, original);
            }
        } else if (!entity.isCustomNameVisible()) {
            data.set(ORIGINAL_VISIBLE, PersistentDataType.BOOLEAN, false);
        }
        if (!Objects.equals(entity.getCustomName(), overlay)) {
            entity.setCustomName(overlay);
        }
        // Spigot may canonicalize/truncate the name, or turn an empty string into null.
        data.set(LAST_OVERLAY, PersistentDataType.STRING, normalizedName(entity.getCustomName()));
        if (!entity.isCustomNameVisible()) {
            entity.setCustomNameVisible(true);
        }
    }

    private boolean ownsCurrentName(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String last = data.get(LAST_OVERLAY, PersistentDataType.STRING);
        return last != null && Boolean.TRUE.equals(data.get(ORIGINAL_MARKER, PersistentDataType.BOOLEAN)) && last.equals(normalizedName(entity.getCustomName()));
    }

    private String normalizedName(String name) {
        return name == null ? "" : name;
    }

    private boolean hasLegacyOriginal(PersistentDataContainer data) {
        return Boolean.TRUE.equals(data.get(ORIGINAL_MARKER, PersistentDataType.BOOLEAN)) && data.get(LAST_OVERLAY, PersistentDataType.STRING) == null;
    }

    /**
     * New overlays require a matching fingerprint; legacy records restore their saved original.
     */
    private void restoreOwnedName(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        boolean legacy = hasLegacyOriginal(data);
        if (!(entity instanceof Player) && (legacy || ownsCurrentName(entity))) {
            entity.setCustomName(data.get(ORIGINAL_NAME, PersistentDataType.STRING));
            if (legacy || entity.isCustomNameVisible()) {
                entity.setCustomNameVisible(Boolean.TRUE.equals(data.get(ORIGINAL_VISIBLE, PersistentDataType.BOOLEAN)));
            }
        }
        data.remove(ORIGINAL_MARKER);
        data.remove(ORIGINAL_NAME);
        data.remove(ORIGINAL_VISIBLE);
        data.remove(LAST_OVERLAY);
    }

    /**
     * Detaches pending/active displays before a name wipe; clearing activation also prevents recovery.
     */
    public void clearDisplay(LivingEntity entity, boolean clearActivation) {
        UUID id = entity.getUniqueId();
        cancelPendingWork(id);
        detach(id);
        restoreOwnedName(entity);
        if (clearActivation) {
            entity.getPersistentDataContainer()
                  .remove(ACTIVATED);
            entity.getPersistentDataContainer()
                  .remove(LEGACY_ACTIVATED);
        }
        stopHeartbeatIfIdle();
    }

    private void cancelPendingWork(UUID id) {
        pendingRefreshes.remove(id);
        pendingRecoveries.remove(id);
    }

    private void detach(UUID id) {
        ActiveBar bar = activeBars.remove(id);
        if (bar != null) {
            removeHologram(bar);
        }
    }

    private void removeHologram(ActiveBar bar) {
        UUID id = bar.hologram;
        bar.hologram = null;
        if (id != null) {
            if (Bukkit.getEntity(id) instanceof ArmorStand stand) {
                HOLOGRAPH_UTIL.remove(stand);
            } else {
                HOLOGRAPH_UTIL.forget(id);
            }
        }
    }

    private void stopHeartbeatIfIdle() {
        if (activeBars.isEmpty() && pendingRefreshes.isEmpty() && pendingRecoveries.isEmpty() && heartbeat != null) {
            heartbeat.cancel();
            heartbeat = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeathEvents(EntityDeathEvent event) {
        clearDisplay(event.getEntity(), true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                recover(living);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                clearDisplay(living, !ENABLE_HEALTH_BAR.asBoolean());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        new ArrayList<>(event.getWorld()
                             .getLivingEntities()).forEach(entity -> clearDisplay(entity, !ENABLE_HEALTH_BAR.asBoolean()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        // Bukkit forbids modifying the removed entity; its persisted data is recovered on load.
        UUID id = event.getEntity()
                       .getUniqueId();
        cancelPendingWork(id);
        detach(id);
        stopHeartbeatIfIdle();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearDisplay(event.getPlayer(), !ENABLE_HEALTH_BAR.asBoolean());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!stopped) {
            pendingRecoveries.add(event.getPlayer()
                                       .getUniqueId());
            startHeartbeat();
        }
    }

    private void recover(LivingEntity entity) {
        if (stopped) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        migrateLegacyInlineActivation(data);
        if (Boolean.TRUE.equals(data.get(LEGACY_ACTIVATED, PersistentDataType.BOOLEAN))) {
            data.set(ACTIVATED, PersistentDataType.BOOLEAN, true);
            data.remove(LEGACY_ACTIVATED);
        }
        boolean activated = Boolean.TRUE.equals(data.get(ACTIVATED, PersistentDataType.BOOLEAN));
        if (activeBars.containsKey(entity.getUniqueId())) {
            if (!ENABLE_HEALTH_BAR.asBoolean()) {
                clearDisplay(entity, true);
            }
            return;
        }
        restoreOwnedName(entity);
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            data.remove(ACTIVATED);
        } else if (activated && HEALTH_BAR_ALWAYS_VISIBLE.asBoolean() && canDisplay(entity)) {
            activate(entity);
            startHeartbeat();
        }
    }

    public void shutdown() {
        stopped = true;
        if (heartbeat != null) {
            heartbeat.cancel();
            heartbeat = null;
        }
        pendingRefreshes.clear();
        pendingRecoveries.clear();
        for (UUID id : new ArrayList<>(activeBars.keySet())) {
            if (Bukkit.getEntity(id) instanceof LivingEntity entity) {
                clearDisplay(entity, !ENABLE_HEALTH_BAR.asBoolean());
            } else {
                detach(id);
            }
        }
        restorePersistedNames();
    }

    /**
     * Restores loaded inline overlays without forcing chunk/entity storage to load.
     */
    public void restorePersistedNames() {
        Bukkit.getWorlds()
              .forEach(world -> world.getLivingEntities()
                                     .forEach(entity -> {
                                         migrateLegacyInlineActivation(entity.getPersistentDataContainer());
                                         restoreOwnedName(entity);
                                         if (!ENABLE_HEALTH_BAR.asBoolean()) {
                                             entity.getPersistentDataContainer()
                                                   .remove(ACTIVATED);
                                             entity.getPersistentDataContainer()
                                                   .remove(LEGACY_ACTIVATED);
                                         }
                                     }));
    }

    private void migrateLegacyInlineActivation(PersistentDataContainer data) {
        if (hasLegacyOriginal(data) && ENABLE_HEALTH_BAR.asBoolean() && HEALTH_BAR_ALWAYS_VISIBLE.asBoolean()) {
            data.set(ACTIVATED, PersistentDataType.BOOLEAN, true);
        }
    }

    /**
     * Recovers activated bars in either mode; untouched mobs are never activated by this scan.
     */
    public void refreshPersistedHolograms() {
        stopped = false;
        Bukkit.getWorlds()
              .forEach(world -> world.getLivingEntities()
                                     .forEach(this::recover));
    }

    private Component createHealthBar(double current, double max) {
        int total = HEALTH_BAR_LENGTH.asInt();
        int alive = countAliveSymbols(current, max, total);
        return HEALTH_BAR_PREFIX.asFormattedString()
                                .append(TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_ALIVE_SYMBOL.asFormattedString(), alive))
                                .append(TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_DEAD_SYMBOL.asFormattedString(), total - alive))
                                .append(HEALTH_BAR_SUFFIX.asFormattedString());
    }

    private int countAliveSymbols(double current, double max, int length) {
        if (current <= 0) {
            return 0;
        }
        int rounded = (int) Math.round(current / max * length);
        return Math.clamp(rounded, 1, length);
    }

    private static final class ActiveBar {

        private UUID hologram;
        private long expiresAt;
        private long nextFollow;
    }
}
