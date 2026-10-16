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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Listener responsible for replacing visible custom names on LivingEntities with holographic ArmorStand name tags that track entity movement.
 */
public class CustomNameListener implements Listener {

    private static final NamespacedKey HIDDEN_CUSTOM_NAME_KEY = new NamespacedKey("damageindicator", "custom-name-hidden");
    private static final int DISCOVERY_INTERVAL = 20;
    private static final int DISCOVERY_BATCH_SIZE = 128;
    private final Map<UUID, NameDisplay> activeNames = new HashMap<>();
    private final Set<UUID> candidates = new LinkedHashSet<>();
    private BukkitTask heartbeat;
    private long ticks;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && eligible(entity) && isEnabled()) {
            candidates.add(entity.getUniqueId());
            startHeartbeat();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        event.getEntities().stream().filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).forEach(this::recoverAndReplaceCustomName);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        event.getEntities().stream().filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast).forEach(this::clearDisplay);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        UUID id = event.getEntity().getUniqueId();
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

    private boolean eligible(LivingEntity entity) {
        return !(entity instanceof ArmorStand) && !(entity instanceof Player) && !entity.hasMetadata("NPC");
    }

    void replaceWithHologramIfNeeded(LivingEntity entity) {
        if (!eligible(entity) || !entity.isValid() || !isEnabled()) {
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

        ArmorStand stand = HOLOGRAPH_UTIL.createArmorStandHologram(
                HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_NAME_POSITION.asDouble()),
                TEXT_UTIL.deserializeComponent(name));
        if (!stand.isValid()) {
            DamageIndicator.getInstance().getLogger().warning("Could not create custom-name hologram for " + id + ".");
            return;
        }
        entity.getPersistentDataContainer().set(HIDDEN_CUSTOM_NAME_KEY, PersistentDataType.BOOLEAN, true);
        entity.setCustomNameVisible(false);
        activeNames.put(id, new NameDisplay(stand, name));
        HOLOGRAPH_UTIL.onCleanup(stand, () -> {
            Entity loaded = Bukkit.getEntity(id);
            if (loaded instanceof LivingEntity living) {
                clearDisplay(living);
            } else {
                candidates.remove(id);
                removeDisplay(id);
            }
        });
        startHeartbeat();
    }

    private void startHeartbeat() {
        if (heartbeat == null && !candidates.isEmpty()) {
            heartbeat = Bukkit.getScheduler().runTaskTimer(DamageIndicator.getInstance(), this::tick, 1, 1);
        }
    }

    private void tick() {
        ticks++;
        if (!isEnabled()) {
            shutdown();
            return;
        }
        if (ticks % HOLOGRAM_FOLLOW_SPEED.asIntAtLeast(1) == 0) {
            for (UUID id : new ArrayList<>(activeNames.keySet())) {
                Entity loaded = Bukkit.getEntity(id);
                NameDisplay display = activeNames.get(id);
                if (!(loaded instanceof LivingEntity entity) || !entity.isValid()) {
                    candidates.remove(id);
                    removeDisplay(id);
                } else if (!display.stand.isValid()) {
                    removeDisplay(id);
                    restoreCustomNameVisibility(entity);
                } else if (!eligible(entity)) {
                    clearDisplay(entity);
                } else {
                    if (refreshName(entity, display) && !HOLOGRAPH_UTIL.teleportIfMoved(display.stand,
                            HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_NAME_POSITION.asDouble()))) {
                        clearDisplay(entity);
                    }
                }
            }
        }
        if (ticks % DISCOVERY_INTERVAL == 1) {
            discoverNames();
        }
        if (candidates.isEmpty() && activeNames.isEmpty()) {
            stopHeartbeat();
        }
    }

    private void discoverNames() {
        ArrayList<UUID> batch = new ArrayList<>(Math.min(candidates.size(), DISCOVERY_BATCH_SIZE));
        Iterator<UUID> iterator = candidates.iterator();
        while (iterator.hasNext() && batch.size() < DISCOVERY_BATCH_SIZE) {
            batch.add(iterator.next());
            iterator.remove();
        }
        for (UUID id : batch) {
            Entity loaded = Bukkit.getEntity(id);
            if (loaded instanceof LivingEntity entity && entity.isValid() && eligible(entity)) {
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
        entity.setCustomNameVisible(false);
        return true;
    }

    void restoreCustomNameVisibility(LivingEntity entity) {
        if (Boolean.TRUE.equals(entity.getPersistentDataContainer().get(HIDDEN_CUSTOM_NAME_KEY, PersistentDataType.BOOLEAN))) {
            entity.setCustomNameVisible(true);
            entity.getPersistentDataContainer().remove(HIDDEN_CUSTOM_NAME_KEY);
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
    }

    private boolean isEnabled() {
        return ConfigKeys.ENABLE_HOLOGRAPHIC_CUSTOM_NAMES.asBoolean() && ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean();
    }

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
        Bukkit.getWorlds().forEach(world -> world.getLivingEntities().forEach(this::recoverAndReplaceCustomName));
    }

    public void restorePersistedNames() {
        Bukkit.getWorlds().forEach(world -> world.getLivingEntities().stream()
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