package io.github.stealingdapenta.damageindicator.utils;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enum singleton responsible for creating, positioning, and cleaning up ArmorStand-based holographic displays above entities.
 */
public enum HolographUtil {
    HOLOGRAPH_UTIL;

    private final Set<UUID> activeHolograms = new HashSet<>();
    private final Map<UUID, Runnable> cleanupHandlers = new HashMap<>();
    private final Set<UUID> removing = new HashSet<>();
    private boolean spawnFailureReported;

    /**
     * Creates an invisible, non-colliding ArmorStand with the given name at the specified location.
     *
     * @param location the world position to spawn the ArmorStand
     * @param name     the name to be shown above it
     * @return the configured ArmorStand
     */
    public ArmorStand createArmorStandHologram(Location location, Component name) {
        World world = Objects.requireNonNull(location.getWorld(), "A hologram location must have a world");
        ArmorStand hologram = world.spawn(location, ArmorStand.class, armorStand -> {
            armorStand.setCustomName(TextUtil.TEXT_UTIL.serializeComponent(name));
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(false);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setSmall(true);
            armorStand.setCollidable(false);
            armorStand.setInvulnerable(true);
            armorStand.setPersistent(false);
            armorStand.getPersistentDataContainer()
                      .set(DamageIndicatorListener.getCustomNamespacedKey(), PersistentDataType.BOOLEAN, true);
        });
        track(hologram);
        if (!hologram.isValid() && !spawnFailureReported) {
            spawnFailureReported = true;
            DamageIndicator.getInstance()
                           .getLogger()
                           .warning("Hologram spawn was cancelled or produced an invalid entity; displays cannot be created here.");
        }
        return hologram;
    }

    public void track(ArmorStand hologram) {
        if (hologram.isValid()) {
            activeHolograms.add(hologram.getUniqueId());
        }
    }

    public boolean isActive(Entity entity) {
        return entity instanceof ArmorStand && entity.isValid() && activeHolograms.contains(entity.getUniqueId());
    }

    /**
     * Persistent ownership survives restarts; active ownership is session-local.
     */
    public boolean isTaggedHologram(Entity entity) {
        return entity instanceof ArmorStand && Boolean.TRUE.equals(entity.getPersistentDataContainer()
                                                                         .get(DamageIndicatorListener.getCustomNamespacedKey(), PersistentDataType.BOOLEAN));
    }

    public void forget(Entity entity) {
        forget(entity.getUniqueId());
    }

    /**
     * Drops ownership even when the entity's world is no longer available.
     */
    public void forget(UUID id) {
        activeHolograms.remove(id);
        cleanupHandlers.remove(id);
    }

    /**
     * Registers the owner's detach action; never retains cancelled spawns.
     */
    public void onCleanup(ArmorStand hologram, Runnable cleanup) {
        if (!hologram.isValid()) {
            throw new IllegalArgumentException("Cannot register cleanup for an invalid hologram");
        }
        cleanupHandlers.put(hologram.getUniqueId(), cleanup);
    }

    /**
     * Administrative cleanup detaches the owner before removing the hologram.
     */
    public void cleanup(ArmorStand hologram) {
        Runnable cleanup = cleanupHandlers.remove(hologram.getUniqueId());
        if (cleanup != null) {
            cleanup.run();
        }
        remove(hologram);
    }

    /**
     * Removal events must not remove the same entity recursively.
     */
    public void removed(ArmorStand hologram) {
        UUID id = hologram.getUniqueId();
        Runnable cleanup = cleanupHandlers.remove(id);
        activeHolograms.remove(id);
        removing.add(id);
        try {
            if (cleanup != null) {
                cleanup.run();
            }
        } finally {
            removing.remove(id);
        }
    }

    public void remove(ArmorStand hologram) {
        forget(hologram);
        if (!removing.contains(hologram.getUniqueId()) && hologram.isValid()) {
            hologram.remove();
        }
    }

    /**
     * Called after all display owners have stopped, including on configuration reload.
     */
    public void clearTracking() {
        activeHolograms.clear();
        cleanupHandlers.clear();
        removing.clear();
        spawnFailureReported = false;
    }

    /**
     * Skips redundant teleports; callers detach displays when following fails.
     */
    public boolean teleportIfMoved(ArmorStand hologram, Location target) {
        if (target.equals(hologram.getLocation())) {
            return true;
        }
        if (hologram.teleport(target)) {
            return true;
        }
        DamageIndicator.getInstance()
                       .getLogger()
                       .warning("Could not move hologram " + hologram.getUniqueId() + "; its display will be removed.");
        return false;
    }

    /**
     * Calculates a location above the entity's head for placing a hologram.
     *
     * @param entity  the entity the hologram will follow
     * @param offsetY how high above the entity it should appear
     * @return the calculated location
     */
    public Location locationAboveEntity(LivingEntity entity, double offsetY) {
        return entity.getLocation()
                     .add(0, entity.getHeight() + offsetY, 0);
    }

    /**
     * Cancels the hologram animation task and removes the ArmorStand associated with the entity.
     *
     * @param entity the entity for which to cancel the hologram
     * @param data   a map of active hologram tasks
     */
    public void cancelHologramFor(LivingEntity entity, Map<LivingEntity, LivingEntityTaskInfo> data) {
        LivingEntityTaskInfo info = data.remove(entity);
        if (info != null) {
            if (info.task() != null && !info.task()
                                            .isCancelled()) {
                info.task()
                    .cancel();
            }
            if (info.armorStand() != null) {
                remove(info.armorStand());
            }
        }
    }
}
