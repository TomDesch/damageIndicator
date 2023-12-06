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
import java.util.Objects;

import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.ENABLE_HOLOGRAM_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_ALIVE_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_ALIVE_SYMBOL;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_DEAD_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_DEAD_SYMBOL;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_STRIKETHROUGH;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_UNDERLINED;

public class HealthBarListener implements Listener {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;
    private final HashMap<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final HashMap<LivingEntity, Component> originalEntityNames = new HashMap<>();
    private final ConfigurationFileManager cfm;

    public HealthBarListener() {
        cfm = ConfigurationFileManager.getInstance();
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;

        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
        if (Objects.nonNull(existingHealthBarTask)) {
            existingHealthBarTask.cancel();
        }

        double currentHealth = Math.max(0, ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage());
        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
        Component name = createHealthBar(currentHealth, maxHealth);

        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) {
            displayHologramBar(livingEntity, name);
        } else {
            BukkitTask displayBarTask = getDisplayBarTask(livingEntity, getTicksDuration());
            entitiesWithActiveHealthBars.put(livingEntity, displayBarTask);

            Component originalName = Objects.nonNull(livingEntity.customName()) ? livingEntity.customName() : livingEntity.name();
            originalEntityNames.put(livingEntity, originalName);

            displayBar(livingEntity, name);
        }
    }

    private void displayHologramBar(LivingEntity livingEntity, Component name) {
        BukkitTask followTask = new BukkitRunnable() {
            int ticks = 0;
            final ArmorStand armorStand = createArmorStandHologram(locationAboveEntity(livingEntity), name);

            @Override
            public synchronized void cancel() throws IllegalStateException {
                armorStand.remove();
                super.cancel();
            }

            @Override
            public void run() {
                if (ticks >= getTicksDuration()) {
                    this.cancel();
                    return;
                }

                armorStand.teleport(locationAboveEntity(livingEntity));
                ticks++;
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0, 1);

        entitiesWithActiveHealthBars.put(livingEntity, followTask);
    }

    private Location locationAboveEntity(LivingEntity livingEntity) {
        return livingEntity.getLocation().add(0, livingEntity.getHeight(), 0);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void restoreNameUponDeath(EntityDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
        if (Objects.nonNull(existingHealthBarTask)) {
            existingHealthBarTask.cancel();
        }
    }

    private void resetEntityName(LivingEntity entity) {
        entity.customName(originalEntityNames.get(entity));
        originalEntityNames.remove(entity);
    }

    private int getTicksDuration() {
        int displayDuration = cfm.getValue(DefaultConfigValue.HEALTH_BAR_DISPLAY_DURATION);
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(displayDuration, MAX_SECONDS));
    }

    private void displayBar(LivingEntity livingEntity, Component name) {
        livingEntity.customName(name);
        livingEntity.setCustomNameVisible(true);
    }

    private BukkitTask getDisplayBarTask(LivingEntity entity, int displayDuration) {
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
        }.runTaskLater(DamageIndicator.getInstance(), displayDuration);
    }
}
