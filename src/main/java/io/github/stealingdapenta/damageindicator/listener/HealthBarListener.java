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

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import io.github.stealingdapenta.damageindicator.utils.TextUtil;
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

public class HealthBarListener implements Listener {

    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;
    private final HashMap<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHologramBars = new HashMap<>();
    private final HashMap<LivingEntity, Component> originalEntityNames = new HashMap<>();
    private final TextUtil textUtil = TextUtil.getInstance();
    private final HolographUtil holographUtil = HolographUtil.getInstance();

    private double calculateCurrentHealth(EntityDamageEvent event) {
        return Math.max(0, ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage());
    }

    private void displayHolographicHealthBar(LivingEntity livingEntity, Component name) {
        // todo rewrite so it doesn't need to regenerate a new armor stand every time but can update the existing one
        holographUtil.cancelHologramFor(livingEntity, entitiesWithActiveHologramBars);
        LivingEntityTaskInfo newTaskInfo = displayHologramBar(livingEntity, name);
        entitiesWithActiveHologramBars.put(livingEntity, newTaskInfo);
    }

    private void displayCustomNameHealthBar(LivingEntity livingEntity, Component name) {
        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
        if (Objects.nonNull(existingHealthBarTask)) {
            if (!existingHealthBarTask.isCancelled()) {
                existingHealthBarTask.cancel();
            }
            resetEntityName(livingEntity);
            entitiesWithActiveHealthBars.remove(livingEntity);
        }

        BukkitTask resetOriginalNameAfterPresetTimeTask = null;
        if (!HEALTH_BAR_ALWAYS_VISIBLE.getBooleanValue()) {
            resetOriginalNameAfterPresetTimeTask = resetOriginalNameTask(livingEntity);
        }

        entitiesWithActiveHealthBars.put(livingEntity, resetOriginalNameAfterPresetTimeTask);

        // Only restore the original name if it was visible before.
        if (Objects.nonNull(livingEntity.customName())) {
            originalEntityNames.put(livingEntity, livingEntity.customName());
        }

        livingEntity.customName(name);
        livingEntity.setCustomNameVisible(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        double currentHealth = calculateCurrentHealth(event);
        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.MAX_HEALTH))
                                  .getValue();
        Component name = createHealthBar(currentHealth, maxHealth);

        if (ENABLE_HOLOGRAM_HEALTH_BAR.getBooleanValue()) {
            displayHolographicHealthBar(livingEntity, name);
        } else {
            displayCustomNameHealthBar(livingEntity, name);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void restoreNameUponKilling(EntityDamageByEntityEvent event) {
        if (ENABLE_HOLOGRAM_HEALTH_BAR.getBooleanValue()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity) || !(event.getDamager() instanceof LivingEntity killer)) {
            return;
        }

        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(killer);
        if (Objects.nonNull(existingHealthBarTask) && !existingHealthBarTask.isCancelled()) {
            existingHealthBarTask.cancel();
        }
        entitiesWithActiveHealthBars.remove(killer);
        resetEntityName(killer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeathEvents(EntityDeathEvent event) {
        if (ENABLE_HOLOGRAM_HEALTH_BAR.getBooleanValue()) {
            removeHologramsUponDeath(event);
        } else {
            restoreNameUponDeath(event);
        }
    }

    private void restoreNameUponDeath(EntityDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
        if (Objects.nonNull(existingHealthBarTask) && !existingHealthBarTask.isCancelled()) {
            existingHealthBarTask.cancel();
        }
        entitiesWithActiveHealthBars.remove(livingEntity);
        resetEntityName(livingEntity);
    }

    private void removeHologramsUponDeath(EntityDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        holographUtil.cancelHologramFor(livingEntity, entitiesWithActiveHologramBars);
    }

    private LivingEntityTaskInfo displayHologramBar(LivingEntity livingEntity, Component name) {
        final ArmorStand armorStand = holographUtil.createArmorStandHologram(
                holographUtil.locationAboveEntity(livingEntity, HOLOGRAM_POSITION.getDoubleValue()), name);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public synchronized void cancel() throws IllegalStateException {
                if (armorStand.isValid()) {
                    armorStand.remove();
                }

                entitiesWithActiveHologramBars.remove(livingEntity);
                super.cancel();
            }

            @Override
            public void run() {
                if (ticks >= getDisplayDurationInTicks() || !livingEntity.isValid()) {
                    this.cancel();
                    return;
                }

                if (armorStand.isValid()) {
                    armorStand.teleport(holographUtil.locationAboveEntity(livingEntity, HOLOGRAM_POSITION.getDoubleValue()));
                }

                ticks++;
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0, HOLOGRAM_FOLLOW_SPEED.getIntValue());

        return new LivingEntityTaskInfo(task, armorStand);
    }

    private BukkitTask resetOriginalNameTask(LivingEntity entity) {
        return new BukkitRunnable() {
            @Override
            public synchronized void cancel() throws IllegalStateException {
                entitiesWithActiveHealthBars.remove(entity);
                resetEntityName(entity);
                super.cancel();
            }

            @Override
            public void run() {
                entitiesWithActiveHealthBars.remove(entity);
                resetEntityName(entity);
            }
        }.runTaskLater(DamageIndicator.getInstance(), getDisplayDurationInTicks());
    }

    private void resetEntityName(LivingEntity entity) {
        entity.customName(originalEntityNames.get(entity));
        originalEntityNames.remove(entity);
    }

    private int getDisplayDurationInTicks() {
        int displayDuration = HEALTH_BAR_DISPLAY_DURATION.getIntValue();
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(displayDuration, MAX_SECONDS));
    }

    private Component createHealthBar(double currentHealth, double maxHealth) {
        int healthBarLength = HEALTH_BAR_LENGTH.getIntValue();
        double percentDead = 1 - (currentHealth / maxHealth);
        int deadBarLength = Math.max(0, (int) Math.round(percentDead * healthBarLength));
        if (deadBarLength >= healthBarLength) {
            deadBarLength = healthBarLength - 1;
        }
        int aliveBarLength = healthBarLength - deadBarLength;

        TextComponent aliveComponent = buildAliveComponent(aliveBarLength);
        TextComponent deadComponent = buildDeadComponent(deadBarLength);

        return HEALTH_BAR_PREFIX.getFormattedStringValue().append(aliveComponent.append(deadComponent)).append(HEALTH_BAR_SUFFIX.getFormattedStringValue());
    }

    private TextComponent buildAliveComponent(int barLength) {
        return textUtil.repeatTextWithStyles(HEALTH_BAR_ALIVE_SYMBOL.getFormattedStringValue(), barLength);
    }

    private TextComponent buildDeadComponent(int barLength) {
        return textUtil.repeatTextWithStyles(HEALTH_BAR_DEAD_SYMBOL.getFormattedStringValue(), barLength);
    }
}
