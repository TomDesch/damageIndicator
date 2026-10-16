package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_FOLLOW_SPEED;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_NAME_POSITION;
import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Listener responsible for replacing visible custom names on LivingEntities with holographic ArmorStand name tags that track entity movement.
 */
public class CustomNameListener implements Listener {

    private static final NamespacedKey HIDDEN_CUSTOM_NAME_KEY = Objects.requireNonNull(NamespacedKey.fromString("damageindicator:custom-name-hidden"));
    private static final int DISCOVERY_INTERVAL_TICKS = 20;
    private static final int DISCOVERY_BATCH_SIZE = 128;
    private static final long HEARTBEAT_PERIOD_TICKS = 1;
    private static final long FIRST_DISCOVERY_TICK = 1;
    private final Map<UUID, NameDisplay> activeNames = new HashMap<>();
    private final Set<UUID> candidates = new LinkedHashSet<>();
    private BukkitTask heartbeat;
    private long currentTick;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && isEligible(entity) && isEnabled()) {
            candidates.add(entity.getUniqueId());
            startHeartbeat();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities()
             .stream()
             .filter(LivingEntity.class::isInstance)
             .map(LivingEntity.class::cast)
             .forEach(this::recoverAndReplaceCustomName);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        event.getEntities()
             .stream()
             .filter(LivingEntity.class::isInstance)
             .map(LivingEntity.class::cast)
             .forEach(this::clearDisplay);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        new ArrayList<>(event.getWorld()
                             .getLivingEntities()).forEach(this::clearDisplay);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        UUID id = event.getEntity()
                       .getUniqueId();
        candidates.remove(id);
        removeDisplay(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearDisplay(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        clearDisplay(event.getEntity());
    }

    private boolean isEligible(LivingEntity entity) {
        return !(entity instanceof ArmorStand) && !(entity instanceof Player) && !entity.hasMetadata("NPC");
    }

    void replaceWithHologramIfNeeded(LivingEntity entity) {
        if (!isEligible(entity) || !entity.isValid() || !isEnabled()) {
            return;
        }
        UUID id = entity.getUniqueId();
        candidates.add(id);
        NameDisplay existing = activeNames.get(id);
        if (existing != null && existing.stand.isValid()) {
            refreshName(entity, existing);
            return;
        }
        if (existing != null) {
            removeDisplay(id);
            restoreCustomNameVisibility(entity);
        }
        String name = entity.getCustomName();
        if (name == null || !entity.isCustomNameVisible()) {
            startHeartbeat();
            return;
        }

        ArmorStand stand = HOLOGRAPH_UTIL.createArmorStandHologram(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_NAME_POSITION.asDouble()), TEXT_UTIL.deserializeComponent(name));
        if (!stand.isValid()) {
            candidates.remove(id);
            return;
        }
        entity.getPersistentDataContainer()
              .set(HIDDEN_CUSTOM_NAME_KEY, PersistentDataType.BOOLEAN, true);
        entity.setCustomNameVisible(false);
        activeNames.put(id, new NameDisplay(stand, name));
        HOLOGRAPH_UTIL.onCleanup(stand, () -> clearNameOwner(id));
        startHeartbeat();
    }

    private void clearNameOwner(UUID id) {
        Entity loaded = Bukkit.getEntity(id);
        if (loaded instanceof LivingEntity living) {
            clearDisplay(living);
        } else {
            candidates.remove(id);
            removeDisplay(id);
        }
    }

    private void startHeartbeat() {
        if (heartbeat == null && !candidates.isEmpty()) {
            heartbeat = Bukkit.getScheduler()
                              .runTaskTimer(DamageIndicator.getInstance(), this::tick, HEARTBEAT_PERIOD_TICKS, HEARTBEAT_PERIOD_TICKS);
        }
    }

    private void tick() {
        currentTick++;
        if (!isEnabled()) {
            shutdown();
            return;
        }
        if (currentTick % HOLOGRAM_FOLLOW_SPEED.asInt() == 0) {
            followNames();
        }
        if (currentTick % DISCOVERY_INTERVAL_TICKS == FIRST_DISCOVERY_TICK) {
            discoverNames();
        }
        if (candidates.isEmpty() && activeNames.isEmpty()) {
            stopHeartbeat();
        }
    }

    private void followNames() {
        new ArrayList<>(activeNames.keySet()).forEach(this::followName);
    }

    private void followName(UUID id) {
        NameDisplay display = activeNames.get(id);
        if (display == null) {
            return;
        }
        Entity loaded = Bukkit.getEntity(id);
        if (!(loaded instanceof LivingEntity entity) || !entity.isValid()) {
            candidates.remove(id);
            removeDisplay(id);
        } else if (!display.stand.isValid()) {
            removeDisplay(id);
            restoreCustomNameVisibility(entity);
        } else if (!isEligible(entity)) {
            clearDisplay(entity);
        } else {
            refreshAndFollow(entity, display);
        }
    }

    private void refreshAndFollow(LivingEntity entity, NameDisplay display) {
        if (!refreshName(entity, display)) {
            return;
        }
        if (!HOLOGRAPH_UTIL.teleportIfMoved(display.stand, HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_NAME_POSITION.asDouble()))) {
            clearDisplay(entity);
        }
    }

    /**
     * Rotate a bounded batch so late naming never requires a per-tick world scan.
     */
    private void discoverNames() {
        ArrayList<UUID> batch = new ArrayList<>(Math.min(candidates.size(), DISCOVERY_BATCH_SIZE));
        Iterator<UUID> iterator = candidates.iterator();
        while (iterator.hasNext() && batch.size() < DISCOVERY_BATCH_SIZE) {
            batch.add(iterator.next());
            iterator.remove();
        }
        for (UUID id : batch) {
            Entity loaded = Bukkit.getEntity(id);
            if (loaded instanceof LivingEntity entity && entity.isValid() && isEligible(entity)) {
                candidates.add(id);
                replaceWithHologramIfNeeded(entity);
            } else {
                removeDisplay(id);
            }
        }
    }

    private boolean refreshName(LivingEntity entity, NameDisplay display) {
        String name = entity.getCustomName();
        if (name == null) {
            removeDisplay(entity.getUniqueId());
            restoreCustomNameVisibility(entity);
            return false;
        }
        if (!Objects.equals(name, display.name)) {
            display.stand.setCustomName(name);
            display.name = name;
        }
        if (entity.isCustomNameVisible()) {
            entity.setCustomNameVisible(false);
        }
        return true;
    }

    void restoreCustomNameVisibility(LivingEntity entity) {
        if (Boolean.TRUE.equals(entity.getPersistentDataContainer()
                                      .get(HIDDEN_CUSTOM_NAME_KEY, PersistentDataType.BOOLEAN))) {
            entity.setCustomNameVisible(true);
            entity.getPersistentDataContainer()
                  .remove(HIDDEN_CUSTOM_NAME_KEY);
        }
    }

    private void removeDisplay(UUID id) {
        NameDisplay display = activeNames.remove(id);
        if (display != null) {
            HOLOGRAPH_UTIL.remove(display.stand);
        }
    }

    public void clearDisplay(LivingEntity entity) {
        candidates.remove(entity.getUniqueId());
        removeDisplay(entity.getUniqueId());
        restoreCustomNameVisibility(entity);
        if (candidates.isEmpty() && activeNames.isEmpty()) {
            stopHeartbeat();
        }
    }

    private boolean isEnabled() {
        return ConfigKeys.ENABLE_HOLOGRAPHIC_CUSTOM_NAMES.asBoolean() && ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean();
    }

    /**
     * Restore orphaned markers only; same-session displays retain visibility ownership.
     */
    void recoverAndReplaceCustomName(LivingEntity entity) {
        NameDisplay active = activeNames.get(entity.getUniqueId());
        if (active != null && active.stand.isValid()) {
            refreshName(entity, active);
            return;
        }
        removeDisplay(entity.getUniqueId());
        restoreCustomNameVisibility(entity);
        replaceWithHologramIfNeeded(entity);
    }

    public void shutdown() {
        stopHeartbeat();
        for (UUID id : new ArrayList<>(activeNames.keySet())) {
            Entity loaded = Bukkit.getEntity(id);
            if (loaded instanceof LivingEntity entity) {
                restoreCustomNameVisibility(entity);
            }
            removeDisplay(id);
        }
        candidates.clear();
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.cancel();
            heartbeat = null;
        }
    }

    public void refreshLoadedEntities() {
        Bukkit.getWorlds()
              .forEach(world -> world.getLivingEntities()
                                     .forEach(this::recoverAndReplaceCustomName));
    }

    public void restorePersistedNames() {
        Bukkit.getWorlds()
              .forEach(world -> world.getLivingEntities()
                                     .stream()
                                     .filter(entity -> !activeNames.containsKey(entity.getUniqueId()))
                                     .forEach(this::restoreCustomNameVisibility));
    }

    private static final class NameDisplay {

        private final ArmorStand stand;
        private String name;

        private NameDisplay(ArmorStand stand, String name) {
            this.stand = stand;
            this.name = name;
        }
    }
}