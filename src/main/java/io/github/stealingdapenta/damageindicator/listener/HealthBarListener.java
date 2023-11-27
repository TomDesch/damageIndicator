package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.DefaultConfigValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
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
    private static final String SPACE = " ";
    private final HashMap<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
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
        Entity damagedEntity = event.getEntity();
        if (!(damagedEntity instanceof LivingEntity livingDamagedEntity)) return;

        BukkitTask existingHealthBar = entitiesWithActiveHealthBars.get(livingDamagedEntity);
        if (Objects.nonNull(existingHealthBar)) {
            existingHealthBar.cancel();
            livingDamagedEntity.setCustomNameVisible(false);
        }

        int displayDuration = configurationFileManager.getValue(DefaultConfigValue.HEALTH_BAR_DISPLAY_DURATION);

        BukkitTask displayBarTask = new BukkitRunnable() {
            private int ticksLeft = TICKS_PER_SECOND * Math.max(1, Math.min(displayDuration, 10));

            @Override
            public void run() {
                if (ticksLeft <= 0) {
                    this.cancel();
                    livingDamagedEntity.setCustomNameVisible(false);
                    return;
                }

                double health = livingDamagedEntity.getHealth();
                double maxHealth = Objects.requireNonNull(livingDamagedEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();

                livingDamagedEntity.customName(createHealthBar(health, maxHealth));
                livingDamagedEntity.setCustomNameVisible(true);

                ticksLeft--;
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0L, 1L); // Run every tick (1L)

        entitiesWithActiveHealthBars.put(livingDamagedEntity, displayBarTask);
    }
}

