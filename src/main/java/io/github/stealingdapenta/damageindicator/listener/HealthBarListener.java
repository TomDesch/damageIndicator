package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.DefaultConfigValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.ENABLE_HOLOGRAM_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_ALIVE_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_ALIVE_SYMBOL;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_DEAD_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_DEAD_SYMBOL;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_STRIKETHROUGH;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_UNDERLINED;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HOLOGRAM_POSITION;

public class HealthBarListener implements Listener {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;
    private final HashMap<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHologramBars = new HashMap<>();
    private final HashMap<LivingEntity, Component> originalEntityNames = new HashMap<>();
    private final ConfigurationFileManager cfm;

    public HealthBarListener() {
        cfm = ConfigurationFileManager.getInstance();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restoreNameUponDeath(EntityDeathEvent event) {
        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) return;

        LivingEntity livingEntity = event.getEntity();
        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
        if (Objects.nonNull(existingHealthBarTask) && !existingHealthBarTask.isCancelled()) {
            existingHealthBarTask.cancel();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void removeHologramsUponDeath(EntityDeathEvent event) {
        if (!cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) return;

        LivingEntity livingEntity = event.getEntity();
        cancelHologramFor(livingEntity);
    }

    private void cancelHologramFor(LivingEntity livingEntity) {
        LivingEntityTaskInfo taskInfo = entitiesWithActiveHologramBars.get(livingEntity);
        if (Objects.nonNull(taskInfo)) {
            if (Objects.nonNull(taskInfo.getTask()) && (!taskInfo.getTask().isCancelled())) {
                taskInfo.getTask().cancel();
            }
            if (Objects.nonNull(taskInfo.getArmorStand()) && taskInfo.getArmorStand().isValid()) {
                taskInfo.getArmorStand().remove();
            }
            entitiesWithActiveHologramBars.remove(livingEntity);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;

        double currentHealth = Math.max(0, ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage());
        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
        Component name = createHealthBar(currentHealth, maxHealth);

        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) {
            cancelHologramFor(livingEntity);
            LivingEntityTaskInfo newTaskInfo = displayHologramBar(livingEntity, name);
            entitiesWithActiveHologramBars.put(livingEntity, newTaskInfo);

        } else {
            BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
            if (Objects.nonNull(existingHealthBarTask) && !existingHealthBarTask.isCancelled()) {
                existingHealthBarTask.cancel();
                entitiesWithActiveHealthBars.remove(livingEntity);
            }

            BukkitTask displayBarTask = getDisplayBarTask(livingEntity);
            entitiesWithActiveHealthBars.put(livingEntity, displayBarTask);

            Component originalName = Objects.nonNull(livingEntity.customName()) ? livingEntity.customName() : livingEntity.name();
            originalEntityNames.put(livingEntity, originalName);

            livingEntity.customName(name);
            livingEntity.setCustomNameVisible(true);
        }
    }

    private LivingEntityTaskInfo displayHologramBar(LivingEntity livingEntity, Component name) {
        final ArmorStand armorStand = createArmorStandHologram(locationAboveEntity(livingEntity), name);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public synchronized void cancel() throws IllegalStateException {
                if (armorStand.isValid()) {
                    DamageIndicator.getInstance().getLogger().info("Cancelling task, and removing valid armor stand");
                    armorStand.remove();
                    entitiesWithActiveHologramBars.remove(livingEntity);
                } else {
                    DamageIndicator.getInstance().getLogger().info("Cancelling task, but invalid armor stand");
                }
                super.cancel();
            }

            @Override
            public void run() {
                if (ticks >= getDisplayDurationInTicks() || !livingEntity.isValid()) {
                    this.cancel();
                    return;
                }

                if (armorStand.isValid()) {
                    armorStand.teleport(locationAboveEntity(livingEntity));
                }

                ticks++;
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0, 1);

        return new LivingEntityTaskInfo(task, armorStand);
    }

    private BukkitTask getDisplayBarTask(LivingEntity entity) {
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

    private Location locationAboveEntity(LivingEntity livingEntity) {
        return livingEntity.getLocation().add(0, livingEntity.getHeight() + cfm.getDouble(HOLOGRAM_POSITION), 0);
    }

    private ArmorStand createArmorStandHologram(Location initialLocation, Component name) {
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

    private void resetEntityName(LivingEntity entity) {
        entity.customName(originalEntityNames.get(entity));
        originalEntityNames.remove(entity);
    }

    private int getDisplayDurationInTicks() {
        int displayDuration = cfm.getInt(DefaultConfigValue.HEALTH_BAR_DISPLAY_DURATION);
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(displayDuration, MAX_SECONDS));
    }

    private Component createHealthBar(double currentHealth, double maxHealth) {
        double percentAlive = currentHealth / maxHealth;
        int aliveBarLength = Math.max(0, (int) (percentAlive * 16));
        int deadBarLength = Math.max(0, 16 - aliveBarLength);

        TextComponent aliveComponent = buildAliveComponent(aliveBarLength);
        TextComponent deadComponent = buildDeadComponent(deadBarLength);

        return applyConfigStyles(aliveComponent.append(deadComponent));
    }

    private TextComponent buildAliveComponent(int barLength) {
        return Component.text(cfm.getStringValue(HEALTH_BAR_ALIVE_SYMBOL).repeat(barLength), cfm.getTextColor(HEALTH_BAR_ALIVE_COLOR));
    }

    private TextComponent buildDeadComponent(int barLength) {
        return Component.text(cfm.getStringValue(HEALTH_BAR_DEAD_SYMBOL).repeat(barLength), cfm.getTextColor(HEALTH_BAR_DEAD_COLOR));
    }

    private Component applyConfigStyles(TextComponent healthBar) {
        if (cfm.getBooleanValue(DefaultConfigValue.HEALTH_BAR_BOLD)) {
            healthBar = healthBar.decorate(TextDecoration.BOLD);
        }

        if (cfm.getBooleanValue(HEALTH_BAR_STRIKETHROUGH)) {
            healthBar = healthBar.decorate(TextDecoration.STRIKETHROUGH);
        }

        if (cfm.getBooleanValue(HEALTH_BAR_UNDERLINED)) {
            healthBar = healthBar.decorate(TextDecoration.UNDERLINED);
        }

        return healthBar;
    }
}
