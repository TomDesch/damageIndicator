package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.DefaultConfigValue;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;


public class DamageIndicatorListener implements Listener {
    private static final String CUSTOM_NSK_TAG = "customnsktag";

    /**
     * In case any armor stands get 'stuck'
     * For example after a server crash, if any still alive
     * we'll check all entities for our custom tag and remove if any match
     *
     * @param event whenever a chunk loads
     */
    @EventHandler
    public void removeStuckArmorStands(ChunkLoadEvent event) {
        for (Entity possibleArmorStand : event.getChunk().getEntities()) {
            if (Boolean.TRUE.equals(possibleArmorStand.getPersistentDataContainer().get(getCustomNamespacedKey(), PersistentDataType.BOOLEAN))) {
                possibleArmorStand.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayDamageIndicator(EntityDamageEvent event) {
        Entity damagedEntity = event.getEntity();

        if (!(damagedEntity instanceof LivingEntity livingDamagedEntity)) return;

        Location initialLocation = this.getHitLocation(livingDamagedEntity);
        double damageDealt = event.getFinalDamage();
        TextColor textColor = this.calculateColor(event.getCause());
        this.animateArmorStand(initialLocation, damageDealt, textColor);
    }

    public static NamespacedKey getCustomNamespacedKey() {
        return new NamespacedKey(DamageIndicator.getInstance(), CUSTOM_NSK_TAG);
    }

    private ArmorStand createArmorStand(Location initialLocation, double damageDealt, TextColor textColor) {
        double hit = Math.round(damageDealt * 100.0) / 100.0;

        return initialLocation.getWorld().spawn(initialLocation, ArmorStand.class, armorStand -> {
            armorStand.customName(Component.text(hit, textColor));
            armorStand.setCustomNameVisible(true);
            armorStand.setVisible(false);
            armorStand.setCollidable(false);
            armorStand.setInvulnerable(true);
            armorStand.setMarker(true);
            armorStand.setGravity(true);
            armorStand.setSmall(true);
            armorStand.getPersistentDataContainer().set(getCustomNamespacedKey(), PersistentDataType.BOOLEAN, true);
        });
    }

    private TextColor calculateColor(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case MAGIC -> DefaultConfigValue.MAGIC.getTextColor();
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, THORNS -> DefaultConfigValue.MELEE.getTextColor();
            case POISON -> DefaultConfigValue.POISON.getTextColor();
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> DefaultConfigValue.FIRE.getTextColor();
            case PROJECTILE -> DefaultConfigValue.RANGED.getTextColor();
            default -> DefaultConfigValue.OTHER.getTextColor();
        };
    }

    public void animateArmorStand(Location initialLocation, double damageDealt, TextColor textColor) {
        ArmorStand armorStand = this.createArmorStand(initialLocation, damageDealt, textColor);

        // Define initial velocity (upward motion)
        double upwardSpeed = 0.15;
        Vector velocity = new Vector(0, upwardSpeed, 0);

        // Add randomness to velocity
        double randomX = Math.random() * 0.1 - 0.05; // Random value between -0.05 and 0.05
        double randomZ = Math.random() * 0.1 - 0.05; // Random value between -0.05 and 0.05
        velocity.add(new Vector(randomX, 0, randomZ));

        // Simulate animation using Bukkit's scheduler
        final AtomicInteger steps = new AtomicInteger(30); // Number of animation steps
        int period = 0; // Delay between animation steps in server ticks (adjust as needed)

        Bukkit.getScheduler().runTaskTimer(DamageIndicator.getInstance(), task -> {
            // Update ArmorStand position
            armorStand.teleport(armorStand.getLocation().add(velocity));

            // Apply gravity (decrease y-component)
            velocity.subtract(new Vector(0, 0.01, 0));

            int remainingSteps = steps.decrementAndGet();
            if (remainingSteps <= 0) {
                armorStand.remove();
                task.cancel();
            }
        }, 0, period);
    }

    public Location getHitLocation(Entity targetEntity) {
        Location targetLocation = targetEntity.getLocation();
        double height = 1;
        return targetLocation.add(0d, height, 0d);
    }
}

