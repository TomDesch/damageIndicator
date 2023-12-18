package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.ENABLE_HOLOGRAM_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_ALIVE_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_ALIVE_SYMBOL;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_BOLD;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_DEAD_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_DEAD_SYMBOL;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_LENGTH;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_PREFIX;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_PREFIX_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_PREFIX_STRIKETHROUGH;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_PREFIX_UNDERLINED;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_STRIKETHROUGH;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_SUFFIX;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_SUFFIX_COLOR;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_SUFFIX_STRIKETHROUGH;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_SUFFIX_UNDERLINED;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HEALTH_BAR_UNDERLINED;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HOLOGRAM_FOLLOW_SPEED;
import static io.github.stealingdapenta.damageindicator.DefaultConfigValue.HOLOGRAM_POSITION;

import io.github.stealingdapenta.damageindicator.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.DefaultConfigValue;
import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final ConfigurationFileManager cfm = ConfigurationFileManager.getInstance();
    private final HolographUtil holographUtil = HolographUtil.getInstance();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;

        double currentHealth = Math.max(0, ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage());
        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
        Component name = createHealthBar(currentHealth, maxHealth);

        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) {
            holographUtil.cancelHologramFor(livingEntity, entitiesWithActiveHologramBars);
            LivingEntityTaskInfo newTaskInfo = displayHologramBar(livingEntity, name);
            entitiesWithActiveHologramBars.put(livingEntity, newTaskInfo);

        } else {
            BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
            if (Objects.nonNull(existingHealthBarTask)) {
                if (!existingHealthBarTask.isCancelled()) {
                    existingHealthBarTask.cancel();
                }
                resetEntityName(livingEntity);
                entitiesWithActiveHealthBars.remove(livingEntity);
            }

            BukkitTask displayBarTask = displayNameBar(livingEntity);
            entitiesWithActiveHealthBars.put(livingEntity, displayBarTask);

            Component originalName = Objects.nonNull(livingEntity.customName()) ? livingEntity.customName() : livingEntity.name();
            originalEntityNames.put(livingEntity, originalName);

            livingEntity.customName(name);
            livingEntity.setCustomNameVisible(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void restoreNameUponKilling(EntityDamageByEntityEvent event) {
        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) return;
        if (!(event.getEntity() instanceof LivingEntity) || !(event.getDamager() instanceof LivingEntity killer))
            return;

        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(killer);
        if (Objects.nonNull(existingHealthBarTask) && !existingHealthBarTask.isCancelled()) {
            existingHealthBarTask.cancel();
        }
        entitiesWithActiveHealthBars.remove(killer);
        resetEntityName(killer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeathEvents(EntityDeathEvent event) {
        if (cfm.getBooleanValue(ENABLE_HOLOGRAM_HEALTH_BAR)) {
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
        final ArmorStand armorStand = holographUtil.createArmorStandHologram(holographUtil.locationAboveEntity(livingEntity, cfm.getDouble(HOLOGRAM_POSITION)), name);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public synchronized void cancel() throws IllegalStateException {
                if (armorStand.isValid()) {
                    armorStand.remove();
                } else {
                    DamageIndicator.getInstance().getLogger().warning("Cancelling task, but invalid armor stand");
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
                    armorStand.teleport(holographUtil.locationAboveEntity(livingEntity, cfm.getDouble(HOLOGRAM_POSITION)));
                }

                ticks++;
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0, cfm.getInt(HOLOGRAM_FOLLOW_SPEED));

        return new LivingEntityTaskInfo(task, armorStand);
    }

    private BukkitTask displayNameBar(LivingEntity entity) {
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
        int displayDuration = cfm.getInt(DefaultConfigValue.HEALTH_BAR_DISPLAY_DURATION);
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(displayDuration, MAX_SECONDS));
    }

    private Component createHealthBar(double currentHealth, double maxHealth) {
        int healthBarLength = cfm.getInt(HEALTH_BAR_LENGTH);
        double percentDead = 1 - (currentHealth / maxHealth);
        int deadBarLength = Math.max(0, (int) Math.round(percentDead * healthBarLength));
        if (deadBarLength >= healthBarLength) {
            deadBarLength = healthBarLength - 1;
        }
        int aliveBarLength = healthBarLength - deadBarLength;

        TextComponent aliveComponent = buildAliveComponent(aliveBarLength);
        TextComponent deadComponent = buildDeadComponent(deadBarLength);

        return getPrefix().append(applyStyles(aliveComponent.append(deadComponent), HEALTH_BAR_BOLD, HEALTH_BAR_STRIKETHROUGH, HEALTH_BAR_UNDERLINED)).append(getSuffix());
    }


    private TextComponent buildAliveComponent(int barLength) {
        return Component.text(cfm.getStringValue(HEALTH_BAR_ALIVE_SYMBOL).repeat(barLength), cfm.getTextColor(HEALTH_BAR_ALIVE_COLOR));
    }

    private TextComponent buildDeadComponent(int barLength) {
        return Component.text(cfm.getStringValue(HEALTH_BAR_DEAD_SYMBOL).repeat(barLength), cfm.getTextColor(HEALTH_BAR_DEAD_COLOR));
    }


    private Component applyStyles(Component component, DefaultConfigValue boldConfig, DefaultConfigValue strikethroughConfig, DefaultConfigValue underlinedConfig) {
        if (cfm.getBooleanValue(boldConfig)) {
            component = component.decorate(TextDecoration.BOLD);
        }

        if (cfm.getBooleanValue(strikethroughConfig)) {
            component = component.decorate(TextDecoration.STRIKETHROUGH);
        }

        if (cfm.getBooleanValue(underlinedConfig)) {
            component = component.decorate(TextDecoration.UNDERLINED);
        }

        return component;
    }

    private Component getPrefix() {
        Component prefix = Component.text(cfm.getStringValue(HEALTH_BAR_PREFIX), cfm.getTextColor(HEALTH_BAR_PREFIX_COLOR));
        return applyStyles(prefix, DefaultConfigValue.HEALTH_BAR_PREFIX_BOLD, HEALTH_BAR_PREFIX_STRIKETHROUGH, HEALTH_BAR_PREFIX_UNDERLINED);
    }

    private Component getSuffix() {
        Component suffix = Component.text(cfm.getStringValue(HEALTH_BAR_SUFFIX), cfm.getTextColor(HEALTH_BAR_SUFFIX_COLOR));
        return applyStyles(suffix, DefaultConfigValue.HEALTH_BAR_SUFFIX_BOLD, HEALTH_BAR_SUFFIX_STRIKETHROUGH, HEALTH_BAR_SUFFIX_UNDERLINED);
    }
}
