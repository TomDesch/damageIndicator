package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_HEALTH_BAR;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.bukkit.event.entity.EntityRegainHealthEvent;
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
    private final Set<BukkitTask> pendingHealTasks = new HashSet<>();

    /**
     * Displays the health bar after an entity takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.MAX_HEALTH))
                                  .getValue();
        double currentHealth = resolveDisplayHealth(livingEntity, event.getFinalDamage(), maxHealth);
        Component name = createHealthBar(currentHealth, maxHealth);

        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            displayHolographicHealthBar(livingEntity, name);
        } else {
            displayCustomNameHealthBar(livingEntity, name);
        }
    }

    /**
     * Updates the health bar when an entity regains health.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void updateHealthBarOnHeal(EntityRegainHealthEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        boolean hasActiveBar = ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean() ? entitiesWithActiveHologramBars.containsKey(livingEntity) : entitiesWithActiveHealthBars.containsKey(livingEntity);

        if (!hasActiveBar && !HEALTH_BAR_ALWAYS_VISIBLE.asBoolean()) {
            return; // respect visibility rules
        }

        // Defer one tick so the healing is fully applied before we read health.
        BukkitTask[] taskHolder = new BukkitTask[1];
        BukkitTask pendingTask = DamageIndicator.getInstance()
                                                .getServer()
                                                .getScheduler()
                                                .runTask(DamageIndicator.getInstance(), () -> {
                                                    try {
                                                        if (!ENABLE_HEALTH_BAR.asBoolean() || !livingEntity.isValid()) {
                                                            return;
                                                        }
                                                        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.MAX_HEALTH))
                                                                                  .getValue();
                                                        double currentHealth = Math.min(livingEntity.getHealth(), maxHealth);
                                                        Component name = createHealthBar(currentHealth, maxHealth);

                                                        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
                                                            displayHolographicHealthBar(livingEntity, name);
                                                        } else {
                                                            displayCustomNameHealthBar(livingEntity, name);
                                                        }
                                                    } finally {
                                                        pendingHealTasks.remove(taskHolder[0]);
                                                    }
                                                });
        taskHolder[0] = pendingTask;
        pendingHealTasks.add(pendingTask);
    }


    /**
     * Restores the attacker's name after they kill another entity.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void restoreNameUponKilling(EntityDamageByEntityEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity victim) || !(event.getDamager() instanceof LivingEntity killer) || !isLethalHit(event, victim)) {
            return;
        }

        BukkitTask existingTask = entitiesWithActiveHealthBars.get(killer);
        if (existingTask != null && !existingTask.isCancelled()) {
            existingTask.cancel();
        }

        entitiesWithActiveHealthBars.remove(killer);
        resetEntityName(killer);
    }

    private boolean isLethalHit(EntityDamageByEntityEvent event, LivingEntity victim) {
        return event.getFinalDamage() >= victim.getHealth();
    }

    /**
     * Handles entity death to clear health bars or restore names.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeathEvents(EntityDeathEvent event) {
        removeHologramsUponDeath(event);
        restoreNameUponDeath(event);
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

        rememberOriginalName(entity);

        entity.setCustomName(TEXT_UTIL.serializeComponent(name));
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
        Component originalName = originalEntityNames.remove(entity);
        if (originalName != null) {
            entity.setCustomName(TEXT_UTIL.serializeComponent(originalName));
            entity.setCustomNameVisible(true);
        } else {
            entity.setCustomName(null);
            entity.setCustomNameVisible(false);
        }
    }

    private void rememberOriginalName(LivingEntity entity) {
        String customName = entity.getCustomName();
        if (!originalEntityNames.containsKey(entity) && customName != null) {
            originalEntityNames.put(entity, TEXT_UTIL.deserializeComponent(customName));
        }
    }

    public void shutdown() {
        new ArrayList<>(pendingHealTasks).forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        pendingHealTasks.clear();

        new ArrayList<>(entitiesWithActiveHealthBars.values()).forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        entitiesWithActiveHealthBars.clear();

        new ArrayList<>(entitiesWithActiveHologramBars.keySet()).forEach(entity -> HOLOGRAPH_UTIL.cancelHologramFor(entity, entitiesWithActiveHologramBars));
        new ArrayList<>(originalEntityNames.keySet()).forEach(this::resetEntityName);
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
        }.runTaskTimer(DamageIndicator.getInstance(), 0, HOLOGRAM_FOLLOW_SPEED.asIntAtLeast(1));

        return new LivingEntityTaskInfo(task, armorStand);
    }

    private int getDisplayDurationInTicks() {
        int seconds = HEALTH_BAR_DISPLAY_DURATION.asInt();
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(seconds, MAX_SECONDS));
    }

    private double resolveDisplayHealth(LivingEntity livingEntity, double finalDamage, double maxHealth) {
        return Math.min(Math.max(0.0, livingEntity.getHealth() - finalDamage), maxHealth);
    }

    private Component createHealthBar(double current, double max) {
        int total = HEALTH_BAR_LENGTH.asIntAtLeast(1);
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
