package io.github.stealingdapenta.damageindicator.listener;

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
import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Listens to damage and death events and displays dynamic health bars (as floating holograms or custom names) above entities based on plugin config.
 */
public class HealthBarListener implements Listener {

    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;

    private final Map<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHologramBars = new HashMap<>();
    private final Map<LivingEntity, Component> originalEntityNames = new HashMap<>();

    /**
     * Displays the health bar after an entity takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        double currentHealth = Math.max(0, livingEntity.getHealth() - event.getFinalDamage());
        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.MAX_HEALTH))
                                  .getValue();
        Component name = createHealthBar(currentHealth, maxHealth);

        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            displayHolographicHealthBar(livingEntity, name);
        } else {
            displayCustomNameHealthBar(livingEntity, name);
        }
    }

    /**
     * Restores the attacker's name after they kill another entity.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void restoreNameUponKilling(EntityDamageByEntityEvent event) {
        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity) || !(event.getDamager() instanceof LivingEntity killer)) {
            return;
        }

        BukkitTask existingTask = entitiesWithActiveHealthBars.get(killer);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }

        entitiesWithActiveHealthBars.remove(killer);
        resetEntityName(killer);
    }

    /**
     * Handles entity death to clear health bars or restore names.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeathEvents(EntityDeathEvent event) {
        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            removeHologramsUponDeath(event);
        } else {
            restoreNameUponDeath(event);
        }
    }

    private void displayHolographicHealthBar(LivingEntity entity, Component name) {
        HOLOGRAPH_UTIL.cancelHologramFor(entity, entitiesWithActiveHologramBars);
        LivingEntityTaskInfo taskInfo = spawnHologramBar(entity, name);
        entitiesWithActiveHologramBars.put(entity, taskInfo);
    }

    private void displayCustomNameHealthBar(LivingEntity entity, Component name) {
        BukkitTask oldTask = entitiesWithActiveHealthBars.remove(entity);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }

        if (entity.customName() != null) {
            originalEntityNames.put(entity, entity.customName());
        }

        entity.customName(name);
        entity.setCustomNameVisible(true);

        if (!HEALTH_BAR_ALWAYS_VISIBLE.asBoolean()) {
            BukkitTask resetTask = scheduleNameReset(entity);
            entitiesWithActiveHealthBars.put(entity, resetTask);
        }
    }

    private void restoreNameUponDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        BukkitTask task = entitiesWithActiveHealthBars.remove(entity);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        resetEntityName(entity);
    }

    private void removeHologramsUponDeath(EntityDeathEvent event) {
        HOLOGRAPH_UTIL.cancelHologramFor(event.getEntity(), entitiesWithActiveHologramBars);
    }

    private BukkitTask scheduleNameReset(LivingEntity entity) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                resetEntityName(entity);
                entitiesWithActiveHealthBars.remove(entity);
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                resetEntityName(entity);
                entitiesWithActiveHealthBars.remove(entity);
                super.cancel();
            }
        }.runTaskLater(DamageIndicator.getInstance(), getDisplayDurationInTicks());
    }

    private void resetEntityName(LivingEntity entity) {
        entity.customName(originalEntityNames.remove(entity));
    }

    private LivingEntityTaskInfo spawnHologramBar(LivingEntity entity, Component name) {
        ArmorStand armorStand = HOLOGRAPH_UTIL.createArmorStandHologram(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_POSITION.asDouble()), name);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= getDisplayDurationInTicks() || !entity.isValid()) {
                    cancel();
                    return;
                }
                if (armorStand.isValid()) {
                    armorStand.teleport(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_POSITION.asDouble()));
                }
            }

            @Override
            public synchronized void cancel() {
                if (armorStand.isValid()) {
                    armorStand.remove();
                }
                entitiesWithActiveHologramBars.remove(entity);
                super.cancel();
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0, HOLOGRAM_FOLLOW_SPEED.asInt());

        return new LivingEntityTaskInfo(task, armorStand);
    }

    private int getDisplayDurationInTicks() {
        int seconds = HEALTH_BAR_DISPLAY_DURATION.asInt();
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(seconds, MAX_SECONDS));
    }

    private Component createHealthBar(double current, double max) {
        int total = HEALTH_BAR_LENGTH.asInt();
        double percent = 1.0 - (current / max);
        int dead = Math.min(total - 1, Math.max(0, (int) Math.round(percent * total)));
        int alive = total - dead;

        TextComponent alivePart = TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_ALIVE_SYMBOL.asFormattedString(), alive);
        TextComponent deadPart = TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_DEAD_SYMBOL.asFormattedString(), dead);

        return HEALTH_BAR_PREFIX.asFormattedString()
                                .append(alivePart.append(deadPart))
                                .append(HEALTH_BAR_SUFFIX.asFormattedString());
    }
}
