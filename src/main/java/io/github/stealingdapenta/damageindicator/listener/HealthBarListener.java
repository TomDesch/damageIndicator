package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.DefaultConfigValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Objects;

public class HealthBarListener implements Listener {
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;
    private static final String SPACE = " ";
    private final HashMap<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final HashMap<LivingEntity, Component> originalEntityNames = new HashMap<>();
    private final ConfigurationFileManager configurationFileManager;

    public HealthBarListener() {
        configurationFileManager = ConfigurationFileManager.getInstance();
    }

    private Component createHealthBar(double currentHealth, double maxHealth) {
        double percentAlive = currentHealth / maxHealth;
        int aliveBarLength = Math.max(0, (int) (percentAlive * 16));
        int deadBarLength = Math.max(0, 16 - aliveBarLength);

        TextComponent aliveComponent = getBoldAndStrikeThroughSpaceLine(aliveBarLength, configurationFileManager.getTextColor(DefaultConfigValue.HEALTH_BAR_ALIVE_COLOR));
        TextComponent deadComponent = getBoldAndStrikeThroughSpaceLine(deadBarLength, configurationFileManager.getTextColor(DefaultConfigValue.HEALTH_BAR_DEAD_COLOR));

        return aliveComponent.append(deadComponent);
    }

    private TextComponent getBoldAndStrikeThroughSpaceLine(int barLength, TextColor color) {
        return Component.text(SPACE.repeat(barLength), color, TextDecoration.STRIKETHROUGH, TextDecoration.BOLD);
    }

    @EventHandler
    public void displayHealthBar(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;

        BukkitTask existingHealthBarTask = entitiesWithActiveHealthBars.get(livingEntity);
        if (Objects.nonNull(existingHealthBarTask)) {
            cancelTask(existingHealthBarTask, livingEntity);
            resetEntityName(livingEntity);
        }

        BukkitTask displayBarTask = getDisplayBarTask(livingEntity, getTicksDuration());
        entitiesWithActiveHealthBars.put(livingEntity, displayBarTask);
        Component originalName = Objects.nonNull(livingEntity.customName()) ? livingEntity.customName() : livingEntity.name();
        originalEntityNames.put(livingEntity, originalName);

        displayHealthBar(livingEntity);
    }

    private void cancelTask(BukkitTask taskToCancel, LivingEntity entityToRename) {
        taskToCancel.cancel();
        entitiesWithActiveHealthBars.remove(entityToRename);
    }

    private void resetEntityName(LivingEntity entity) {
        entity.customName(originalEntityNames.get(entity));
        originalEntityNames.remove(entity);
    }

    private int getTicksDuration() {
        int displayDuration = configurationFileManager.getValue(DefaultConfigValue.HEALTH_BAR_DISPLAY_DURATION);
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(displayDuration, MAX_SECONDS));
    }

    private void displayHealthBar(LivingEntity livingEntity) {
        double health = livingEntity.getHealth();
        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();

        livingEntity.customName(createHealthBar(health, maxHealth));
        livingEntity.setCustomNameVisible(true);
    }

    private BukkitTask getDisplayBarTask(LivingEntity entity, int displayDuration) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                entitiesWithActiveHealthBars.remove(entity);
                resetEntityName(entity);
            }
        }.runTaskLater(DamageIndicator.getInstance(), displayDuration);
    }
}
