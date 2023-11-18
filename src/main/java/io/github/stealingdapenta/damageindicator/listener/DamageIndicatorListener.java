package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.concurrent.atomic.AtomicInteger;


public class DamageIndicatorListener implements Listener {
    private static final TextColor MAGIC = TextColor.color(95, 10, 95);
    private static final TextColor MELEE = TextColor.color(100, 100, 100);
    private static final TextColor POISON = TextColor.color(0, 100, 20);
    private static final TextColor FIRE = TextColor.color(200, 90, 25);
    private static final TextColor RANGED = TextColor.color(130, 70, 0);
    private static final TextColor OTHER = TextColor.color(130, 130, 30);

    @EventHandler
    public void displayDamageIndicator(EntityDamageEvent event) {
        Entity damagedEntity = event.getEntity();

        if (!(damagedEntity instanceof LivingEntity livingDamagedEntity)) {
            return;
        }

        Location initialLocation = this.getHitLocation(livingDamagedEntity);
        double damageDealt = event.getDamage();
        TextColor textColor = this.calculateColor(event.getCause());
        this.animateArmorStand(initialLocation, damageDealt, textColor);
    }

    private ArmorStand createArmorStand(Location initialLocation, double damageDealt, TextColor textColor) {
        ArmorStand armorStand = initialLocation.getWorld().spawn(initialLocation, ArmorStand.class);

        double hit = Math.round(damageDealt * 100.0) / 100.0;

        armorStand.customName(Component.text(hit, textColor));
        armorStand.setCustomNameVisible(true);
        armorStand.setVisible(false);
        armorStand.setCollidable(false);
        armorStand.setInvulnerable(true);
        armorStand.setMarker(true);
        armorStand.setGravity(true);
        armorStand.setSmall(true);

        return armorStand;
    }

    private TextColor calculateColor(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case MAGIC -> MAGIC;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, THORNS -> MELEE;
            case POISON -> POISON;
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> FIRE;
            case PROJECTILE -> RANGED;
            default -> OTHER;
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

