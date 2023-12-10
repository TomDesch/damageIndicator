package io.github.stealingdapenta.damageindicator.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class CustomNameListener implements Listener {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;
    private final HashMap<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHologramBars = new HashMap<>();
    private final HashMap<LivingEntity, Component> originalEntityNames = new HashMap<>();


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
//
//        double currentHealth = Math.max(0, ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage());
//        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
//        Component name = createHealthBar(currentHealth, maxHealth);
//
//        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) {
//            cancelHologramFor(livingEntity);
//            LivingEntityTaskInfo newTaskInfo = displayHologramBar(livingEntity, name);
//            entitiesWithActiveHologramBars.put(livingEntity, newTaskInfo);
//
//        } else {
//            BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
//            if (Objects.nonNull(existingHealthBarTask)) {
//                if (!existingHealthBarTask.isCancelled()) {
//                    existingHealthBarTask.cancel();
//                }
//                resetEntityName(livingEntity);
//                entitiesWithActiveHealthBars.remove(livingEntity);
//            }
//
//            BukkitTask displayBarTask = displayNameBar(livingEntity);
//            entitiesWithActiveHealthBars.put(livingEntity, displayBarTask);
//
//            Component originalName = Objects.nonNull(livingEntity.customName()) ? livingEntity.customName() : livingEntity.name();
//            originalEntityNames.put(livingEntity, originalName);
//
//            livingEntity.customName(name);
//            livingEntity.setCustomNameVisible(true);
//        }
    }
}
