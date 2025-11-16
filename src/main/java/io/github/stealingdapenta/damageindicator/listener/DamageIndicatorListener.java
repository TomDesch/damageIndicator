package io.github.stealingdapenta.damageindicator.listener;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import io.github.stealingdapenta.damageindicator.utils.AttackerVictimPair;
import io.github.stealingdapenta.damageindicator.utils.MergedDamageInfo;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Listener responsible for displaying floating damage indicators above damaged entities, and cleaning up any "orphaned" ArmorStands on a chunk load.
 */
public class DamageIndicatorListener implements Listener {

    private static final String CUSTOM_NSK_TAG = "customnsktag";

    // Map to track merged damage for attacker-victim pairs
    private final Map<AttackerVictimPair, MergedDamageInfo> mergedDamageMap = new HashMap<>();

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
     * If damage merge is enabled and this is from an attacker, merges the damage.
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

        // Check if damage merge is enabled and this is an entity attack
        if (ConfigKeys.ENABLE_DAMAGE_MERGE.asBoolean() && event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            Entity damager = damageByEntityEvent.getDamager();
            handleMergedDamage(damager, livingEntity, location, damageDealt, textColor);
        } else {
            // Normal damage indicator without merging
            animateArmorStand(location, damageDealt, textColor);
        }
    }

    /**
     * Handles merged damage display - either creates a new merged damage or updates existing one.
     */
    private void handleMergedDamage(Entity attacker, LivingEntity victim, Location location, double damage, TextColor color) {
        // Resolve the actual attacker (handle projectiles)
        Entity actualAttacker = resolveActualAttacker(attacker);

        AttackerVictimPair pair = new AttackerVictimPair(actualAttacker, victim);
        MergedDamageInfo mergedInfo = mergedDamageMap.get(pair);

        if (mergedInfo != null) {
            // Update existing merged damage
            mergedInfo.addDamage(damage);
            mergedInfo.setLatestColor(color);
            updateMergedDamageDisplay(mergedInfo);

            // Cancel old timeout task and create new one
            scheduleJumpOut(pair, mergedInfo);
        } else {
            // Create new merged damage
            ArmorStand armorStand = createArmorStand(location, damage, color);
            armorStand.setGravity(false); // Merged damage should float in place

            mergedInfo = new MergedDamageInfo(damage, color, armorStand);
            mergedDamageMap.put(pair, mergedInfo);

            // Start following the victim
            startFollowingVictim(victim, mergedInfo);

            // Schedule the jump out animation
            scheduleJumpOut(pair, mergedInfo);
        }
    }

    /**
     * Resolves the actual attacker from the damager entity. If the damager is a projectile, return the shooter instead.
     */
    private Entity resolveActualAttacker(Entity damager) {
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Entity shooter) {
                return shooter;
            }
        }
        return damager;
    }

    /**
     * Updates the display of merged damage armor stand.
     */
    private void updateMergedDamageDisplay(MergedDamageInfo info) {
        ArmorStand armorStand = info.getArmorStand();
        if (armorStand != null && armorStand.isValid()) {
            double roundedDamage = Math.round(info.getTotalDamage() * 100.0) / 100.0;
            armorStand.customName(Component.text(roundedDamage, info.getLatestColor()));
        }
    }

    /**
     * Starts a repeating task to make the armor stand follow the victim entity.
     */
    private void startFollowingVictim(LivingEntity victim, MergedDamageInfo info) {
        ArmorStand armorStand = info.getArmorStand();
        if (armorStand == null) {
            return;
        }

        BukkitTask followTask = Bukkit.getScheduler()
                                      .runTaskTimer(DamageIndicator.getInstance(), () -> {
                                                        // Stop following if victim or armor stand is no longer valid
                                                        if (!victim.isValid() || !armorStand.isValid()) {
                                                            info.getFollowTask()
                                                                .cancel();
                                                            return;
                                                        }

                                                        // Update armor stand position to follow victim
                                                        Location targetLoc = getHitLocation(victim);
                                                        armorStand.teleport(targetLoc);
                                                    }, 0L, // Start immediately
                                                    1L  // Run every tick for smooth following
                                                   );

        info.setFollowTask(followTask);
    }

    /**
     * Schedules the jump out animation after the timeout period.
     */
    private void scheduleJumpOut(AttackerVictimPair pair, MergedDamageInfo info) {
        double timeoutSeconds = ConfigKeys.DAMAGE_MERGE_TIMEOUT.asDouble();
        long timeoutTicks = (long) (timeoutSeconds * 20); // Convert seconds to ticks

        BukkitTask task = Bukkit.getScheduler()
                                .runTaskLater(DamageIndicator.getInstance(), () -> executeJumpOut(pair, info), timeoutTicks);

        info.setTimeoutTask(task);
    }

    /**
     * Executes the jump out animation for merged damage.
     */
    private void executeJumpOut(AttackerVictimPair pair, MergedDamageInfo info) {
        ArmorStand armorStand = info.getArmorStand();
        if (armorStand == null || !armorStand.isValid()) {
            mergedDamageMap.remove(pair);
            return;
        }

        // Remove from map first
        mergedDamageMap.remove(pair);

        // Cancel the follow task so the armor stand stops following
        if (info.getFollowTask() != null && !info.getFollowTask()
                                                 .isCancelled()) {
            info.getFollowTask()
                .cancel();
        }

        // Enable gravity and give upward velocity for jump effect (same as regular damage)
        armorStand.setGravity(true);
        double initialVelocityY = ConfigKeys.HOLOGRAM_VELOCITY_Y.asDouble();
        int duration = ConfigKeys.DAMAGE_MERGE_JUMP_DURATION.asInt();
        double gravity = ConfigKeys.HOLOGRAM_GRAVITY.asDouble();

        Vector velocity = new Vector((Math.random() * 0.1 - 0.05), initialVelocityY, (Math.random() * 0.1 - 0.05));

        AtomicInteger steps = new AtomicInteger(duration);

        Bukkit.getScheduler()
              .runTaskTimer(DamageIndicator.getInstance(), task -> {
                  if (!armorStand.isValid()) {
                      task.cancel();
                      return;
                  }

                  armorStand.teleport(armorStand.getLocation()
                                                .add(velocity));
                  velocity.subtract(new Vector(0, gravity, 0)); // apply gravity

                  if (steps.decrementAndGet() <= 0) {
                      armorStand.remove();
                      task.cancel();
                  }
              }, 0, 1);
    }

    /**
     * Cleans up merged damage when the entity dies.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Clean up any merged damage involving this entity
        mergedDamageMap.entrySet()
                       .removeIf(entry -> {
                           MergedDamageInfo info = entry.getValue();
                           boolean shouldRemove = false;

                           // Check if this entity is involved (we need to compare by checking the armor stand location)
                           ArmorStand armorStand = info.getArmorStand();
                           if (armorStand != null && armorStand.isValid()) {
                               Location standLoc = armorStand.getLocation();
                               Location entityLoc = entity.getLocation();

                               // If armor stand is near the dead entity, clean it up
                               if (standLoc.getWorld()
                                           .equals(entityLoc.getWorld()) && standLoc.distance(entityLoc) < 5.0) {
                                   shouldRemove = true;
                               }
                           }

                           if (shouldRemove) {
                               info.cleanup();
                           }

                           return shouldRemove;
                       });
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

        double initialVelocityY = ConfigKeys.HOLOGRAM_VELOCITY_Y.asDouble();
        double gravity = ConfigKeys.HOLOGRAM_GRAVITY.asDouble();

        Vector velocity = new Vector((Math.random() * 0.1 - 0.05), initialVelocityY, (Math.random() * 0.1 - 0.05));

        AtomicInteger steps = new AtomicInteger(30); // 30 ticks total
        int intervalTicks = 0; // run every tick (change if performance needed)

        Bukkit.getScheduler()
              .runTaskTimer(DamageIndicator.getInstance(), task -> {
                  armorStand.teleport(armorStand.getLocation()
                                                .add(velocity));
                  velocity.subtract(new Vector(0, gravity, 0)); // apply gravity

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
