package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_FOLLOW_SPEED;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_NAME_POSITION;
import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Listener responsible for replacing visible custom names on LivingEntities with holographic ArmorStand name tags that track entity movement.
 */
public class CustomNameListener implements Listener {

    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHolographicCustomNames = new HashMap<>();

    /**
     * Handles entity spawn events. If the entity has a visible custom name, it's replaced with a floating ArmorStand hologram.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity livingEntity) {
            replaceWithHologramIfNeeded(livingEntity);
        }
    }

    /**
     * Handles chunk loading to replace custom names for entities already present in the chunk.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Arrays.stream(event.getChunk()
                           .getEntities())
              .filter(LivingEntity.class::isInstance)
              .map(LivingEntity.class::cast)
              .forEach(this::replaceWithHologramIfNeeded);
    }

    /**
     * Cleans up the holographic name when the entity dies.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        HOLOGRAPH_UTIL.cancelHologramFor(event.getEntity(), entitiesWithActiveHolographicCustomNames);
    }

    /**
     * Replaces a visible custom name with a holographic ArmorStand if needed.
     *
     * @param entity the living entity to check
     */
    private void replaceWithHologramIfNeeded(LivingEntity entity) {
        if (entity.hasMetadata("NPC") || entity instanceof ArmorStand) {
            return;
        }
        if (entitiesWithActiveHolographicCustomNames.containsKey(entity)) {
            return;
        }

        if (entity.isCustomNameVisible() && entity.customName() != null) {
            entity.setCustomNameVisible(false);
            LivingEntityTaskInfo taskInfo = spawnHologram(entity, entity.customName());
            entitiesWithActiveHolographicCustomNames.put(entity, taskInfo);
        }
    }

    /**
     * Creates and manages a holographic ArmorStand that follows the entity
     * and displays the entity's original name as a floating tag.
     *
     * @param entity the entity the hologram should follow
     * @param name the name to display above the entity
     * @return task info for the created hologram
     */
    private LivingEntityTaskInfo spawnHologram(LivingEntity entity, Component name) {
        final ArmorStand armorStand = HOLOGRAPH_UTIL.createArmorStandHologram(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_NAME_POSITION.asDouble()), name);

        BukkitTask followTask = new BukkitRunnable() {

            @Override
            public synchronized void cancel() throws IllegalStateException {
                if (armorStand.isValid()) {
                    armorStand.remove();
                } else {
                    DamageIndicator.getInstance()
                                   .getLogger()
                                   .warning("Hologram task canceled: armor stand was already invalid.");
                }
                entitiesWithActiveHolographicCustomNames.remove(entity);
                super.cancel();
            }

            @Override
            public void run() {
                if (!entity.isValid() || !entity.getChunk()
                                                .isEntitiesLoaded()) {
                    cancel();
                    return;
                }

                if (armorStand.isValid()) {
                    armorStand.teleport(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_NAME_POSITION.asDouble()));
                }
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 2, HOLOGRAM_FOLLOW_SPEED.asInt());

        return new LivingEntityTaskInfo(followTask, armorStand);
    }
}