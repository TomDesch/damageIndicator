package io.github.stealingdapenta.damageindicator.utils;

import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Objects;

public class HolographUtil {
    private static HolographUtil instance;

    public static HolographUtil getInstance() {
        if (Objects.isNull(instance)) {
            instance = new HolographUtil();
        }
        return instance;
    }

    public ArmorStand createArmorStandHologram(Location initialLocation, Component name) {
        return initialLocation.getWorld().spawn(initialLocation, ArmorStand.class, armorStand -> {
            armorStand.customName(name);
            armorStand.setMarker(true);
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(false);
            armorStand.setCollidable(false);
            armorStand.setInvulnerable(true);
            armorStand.setGravity(false);
            armorStand.setSmall(true);
            armorStand.getPersistentDataContainer().set(DamageIndicatorListener.getCustomNamespacedKey(), PersistentDataType.BOOLEAN, true);
        });
    }

    public Location locationAboveEntity(LivingEntity livingEntity, double position) {
        return livingEntity.getLocation().add(0, livingEntity.getHeight() + position, 0);
    }


    public void cancelHologramFor(LivingEntity livingEntity, Map<LivingEntity, LivingEntityTaskInfo> data) {
        LivingEntityTaskInfo taskInfo = data.get(livingEntity);
        if (Objects.nonNull(taskInfo)) {
            if (Objects.nonNull(taskInfo.getTask()) && (!taskInfo.getTask().isCancelled())) {
                taskInfo.getTask().cancel();
            }
            if (Objects.nonNull(taskInfo.getArmorStand()) && taskInfo.getArmorStand().isValid()) {
                taskInfo.getArmorStand().remove();
            }
        }
        data.remove(livingEntity);
    }
}
