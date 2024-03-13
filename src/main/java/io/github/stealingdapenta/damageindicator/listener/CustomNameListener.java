package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_FOLLOW_SPEED;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_NAME_POSITION;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

public class CustomNameListener implements Listener {

    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHolographicCustomNames = new HashMap<>();
    private final HolographUtil holographUtil = HolographUtil.getInstance();

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void replaceCustomName(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        optionallyReplaceCustomNameWithHolograph(livingEntity);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void replaceCustomName(ChunkLoadEvent event) {
        Arrays.stream(event.getChunk()
                           .getEntities())
              .filter(LivingEntity.class::isInstance)
              .map(LivingEntity.class::cast)
              .forEach(this::optionallyReplaceCustomNameWithHolograph);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void removeHolographicCustomNames(EntityDeathEvent event) {
        holographUtil.cancelHologramFor(event.getEntity(), entitiesWithActiveHolographicCustomNames);
    }

    private void optionallyReplaceCustomNameWithHolograph(LivingEntity livingEntity) {
        if (livingEntity.hasMetadata("NPC")) {
            return;
        }
        if (livingEntity instanceof ArmorStand) {
            return;
        }
        if (entitiesWithActiveHolographicCustomNames.containsKey(livingEntity)) {
            return;
        }

        Component customName = livingEntity.customName();
        boolean isCustomNameVisible = livingEntity.isCustomNameVisible();

        if (isCustomNameVisible && Objects.nonNull(customName)) {
            livingEntity.setCustomNameVisible(false);
            LivingEntityTaskInfo newTaskInfo = displayHologramBar(livingEntity, customName);
            entitiesWithActiveHolographicCustomNames.put(livingEntity, newTaskInfo);
        }
    }


    private LivingEntityTaskInfo displayHologramBar(LivingEntity livingEntity, Component name) {
        final ArmorStand armorStand = holographUtil.createArmorStandHologram(
                holographUtil.locationAboveEntity(livingEntity, HOLOGRAM_NAME_POSITION.getDoubleValue()), name);

        BukkitTask task = new BukkitRunnable() {

            @Override
            public synchronized void cancel() throws IllegalStateException {
                if (armorStand.isValid()) {
                    armorStand.remove();
                    entitiesWithActiveHolographicCustomNames.remove(livingEntity);
                } else {
                    DamageIndicator.getInstance()
                                   .getLogger()
                                   .warning("Cancelling Custom Name task, but invalid armor stand");
                }
                super.cancel();
            }

            @Override
            public void run() {
                if (!livingEntity.isValid() || !livingEntity.getChunk()
                                                            .isEntitiesLoaded()) {
                    this.cancel();
                    return;
                }

                if (armorStand.isValid()) {
                    armorStand.teleport(holographUtil.locationAboveEntity(livingEntity, HOLOGRAM_NAME_POSITION.getDoubleValue()));
                }
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 2, HOLOGRAM_FOLLOW_SPEED.getIntValue());

        return new LivingEntityTaskInfo(task, armorStand);
    }
}
