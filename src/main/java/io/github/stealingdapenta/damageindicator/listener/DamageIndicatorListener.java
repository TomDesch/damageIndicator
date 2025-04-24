package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import java.util.Arrays;
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

/**
 * Listener responsible for displaying floating damage indicators above damaged entities, and cleaning up any "orphaned" ArmorStands on a chunk load.
 */
public class DamageIndicatorListener implements Listener {

    private static final String CUSTOM_NSK_TAG = "customnsktag";

    /**
     * Returns the unique tag key used to identify plugin-controlled armor stands.
     */
    public static NamespacedKey getCustomNamespacedKey() {
        return new NamespacedKey(DamageIndicator.getInstance(), CUSTOM_NSK_TAG);
    }

    /**
     * On chunk load, removes any armor stands left over from animation tasks (e.g. after crashes) by checking for a plugin-specific tag.
     */
    @EventHandler
    public void removeStuckArmorStands(ChunkLoadEvent event) {
        Arrays.stream(event.getChunk()
                           .getEntities())
              .forEach(this::maybeRemoveArmorStandIfTagged);
    }

    private void maybeRemoveArmorStandIfTagged(Entity entity) {
        if (hasCustomTag(entity)) {
            entity.remove();
        }
    }

    private boolean hasCustomTag(Entity entity) {
        return Boolean.TRUE.equals(entity.getPersistentDataContainer()
                                         .get(getCustomNamespacedKey(), PersistentDataType.BOOLEAN));
    }

    /**
     * Displays a floating number above the damaged entity using an invisible ArmorStand.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayDamageIndicator(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        double damageDealt = event.getFinalDamage();
        TextColor textColor = resolveColorForCause(event.getCause());
        Location location = getHitLocation(livingEntity);

        animateArmorStand(location, damageDealt, textColor);
    }

    /**
     * Spawns a small invisible armor stand at the given location and tags it.
     */
    private ArmorStand createArmorStand(Location location, double damage, TextColor color) {
        double roundedDamage = Math.round(damage * 100.0) / 100.0;

        return location.getWorld()
                       .spawn(location, ArmorStand.class, armorStand -> {
                           armorStand.customName(Component.text(roundedDamage, color));
                           armorStand.setCustomNameVisible(true);
                           armorStand.setVisible(false);
                           armorStand.setMarker(true);
                           armorStand.setGravity(true);
                           armorStand.setCollidable(false);
                           armorStand.setSmall(true);
                           armorStand.setInvulnerable(true);
                           armorStand.getPersistentDataContainer()
                                     .set(getCustomNamespacedKey(), PersistentDataType.BOOLEAN, true);
                       });
    }

    /**
     * Resolves a text color based on the damage cause. Falls back to ConfigKeys.OTHER if unknown.
     */
    private TextColor resolveColorForCause(EntityDamageEvent.DamageCause cause) {
        try {
            return ConfigKeys.valueOf(cause.name())
                             .getTextColor();
        } catch (IllegalArgumentException e) {
            return ConfigKeys.OTHER.getTextColor();
        }
    }

    /**
     * Animates a floating armor stand damage indicator that rises then disappears.
     *
     * @param location starting location
     * @param damage   the amount of damage dealt
     * @param color    the color of the displayed text
     */
    public void animateArmorStand(Location location, double damage, TextColor color) {
        ArmorStand armorStand = createArmorStand(location, damage, color);

        Vector velocity = new Vector((Math.random() * 0.1 - 0.05), 0.15, (Math.random() * 0.1 - 0.05));

        AtomicInteger steps = new AtomicInteger(30); // 30 ticks total
        int intervalTicks = 0; // run every tick (change if performance needed)

        Bukkit.getScheduler()
              .runTaskTimer(DamageIndicator.getInstance(), task -> {
                  armorStand.teleport(armorStand.getLocation()
                                                .add(velocity));
                  velocity.subtract(new Vector(0, 0.01, 0)); // simulate gravity

                  if (steps.decrementAndGet() <= 0) {
                      armorStand.remove();
                      task.cancel();
                  }
              }, 0, intervalTicks);
    }

    /**
     * Gets the location slightly above the entity's head where the indicator should start.
     */
    public Location getHitLocation(Entity entity) {
        return entity.getLocation()
                     .add(0d, 1d, 0d);
    }
}
