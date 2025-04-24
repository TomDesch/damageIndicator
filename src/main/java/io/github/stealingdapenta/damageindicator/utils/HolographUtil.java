package io.github.stealingdapenta.damageindicator.utils;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enum singleton responsible for creating, positioning, and cleaning up ArmorStand-based holographic displays above entities.
 */
public enum HolographUtil {
    HOLOGRAPH_UTIL;

    /**
     * Creates an invisible, non-colliding ArmorStand with the given name at the specified location.
     *
     * @param location the world position to spawn the ArmorStand
     * @param name     the name to be shown above it
     * @return the configured ArmorStand
     */
    public ArmorStand createArmorStandHologram(Location location, Component name) {
        return location.getWorld()
                       .spawn(location, ArmorStand.class, armorStand -> {
                           armorStand.customName(name);
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(false);
                           armorStand.setMarker(true);
                           armorStand.setGravity(false);
            armorStand.setSmall(true);
                           armorStand.setCollidable(false);
                           armorStand.setInvulnerable(true);

                           armorStand.getPersistentDataContainer()
                                     .set(DamageIndicatorListener.getCustomNamespacedKey(), PersistentDataType.BOOLEAN, true);
                       });
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
            if (info.getTask() != null && !info.getTask()
                                               .isCancelled()) {
                info.getTask()
                    .cancel();
            }
            if (info.getArmorStand() != null && info.getArmorStand()
                                                    .isValid()) {
                info.getArmorStand()
                    .remove();
            }
        }
    }
}
