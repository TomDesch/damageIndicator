package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import io.github.stealingdapenta.damageindicator.utils.AttackerVictimPair;
import io.github.stealingdapenta.damageindicator.utils.NativeDamageAccounting;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Owns transient damage displays; a single ticker advances all merge and animation state.
 */
public class DamageIndicatorListener implements Listener {

    private static final NamespacedKey HOLOGRAM_KEY = Objects.requireNonNull(NamespacedKey.fromString("damageindicator:customnsktag"));
    private static final int TICKS_PER_SECOND = 20;
    private static final long HEARTBEAT_PERIOD_TICKS = 1;
    private static final int ANIMATION_DURATION_TICKS = 30;
    private static final int DAMAGE_DECIMAL_PLACES = 2;
    private static final double MAX_HORIZONTAL_SPEED = 0.05;
    private static final double HIT_HEIGHT_OFFSET = 1;
    private final Map<AttackerVictimPair, MergedDisplay> mergedDamage = new HashMap<>();
    private final Map<UUID, Animation> animations = new HashMap<>();
    private BukkitTask heartbeat;
    private long currentTick;

    public static NamespacedKey getCustomNamespacedKey() {
        return HOLOGRAM_KEY;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void removeStuckArmorStands(EntitiesLoadEvent event) {
        event.getEntities()
             .forEach(this::maybeRemoveArmorStandIfTagged);
    }

    void maybeRemoveArmorStandIfTagged(Entity entity) {
        if (entity instanceof ArmorStand stand && HOLOGRAPH_UTIL.isTaggedHologram(stand) && !HOLOGRAPH_UTIL.isActive(stand)) {
            HOLOGRAPH_UTIL.remove(stand);
        }
    }

    public void cleanupLoadedChunks() {
        Bukkit.getWorlds()
              .forEach(world -> world.getEntities()
                                     .forEach(this::maybeRemoveArmorStandIfTagged));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayDamageIndicator(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity victim) {
            Entity attacker = event instanceof EntityDamageByEntityEvent attack ? attack.getDamager() : null;
            displayDamage(victim, attacker, NativeDamageAccounting.appliedDamage(event, victim), event.getCause());
        }
    }

    /**
     * Displays already-applied HP damage; never modifies health or manufactures a damage event.
     */
    public void displayDamage(LivingEntity victim, Entity attacker, double damage, EntityDamageEvent.DamageCause cause) {
        if (!canDisplayDamage(victim, damage)) {
            return;
        }
        Location location = getHitLocation(victim);
        if (location.getWorld() == null) {
            DamageIndicator.getInstance()
                           .getLogger()
                           .warning("Cannot display damage without a victim world.");
            return;
        }
        TextColor color = resolveColorForCause(cause == null ? EntityDamageEvent.DamageCause.CUSTOM : cause);
        if (!canMergeDamage(victim, attacker)) {
            animateArmorStand(location, damage, color);
            return;
        }

        displayMergedDamage(victim, resolveAttacker(attacker), location, damage, color);
    }

    private boolean canDisplayDamage(LivingEntity victim, double damage) {
        return victim != null && Double.isFinite(damage) && damage > 0 && ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean();
    }

    private boolean canMergeDamage(LivingEntity victim, Entity attacker) {
        return attacker != null && victim.isValid() && ConfigKeys.ENABLE_DAMAGE_MERGE.asBoolean();
    }

    private Entity resolveAttacker(Entity attacker) {
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return attacker;
    }

    private void displayMergedDamage(LivingEntity victim, Entity attacker, Location location, double damage, TextColor color) {
        AttackerVictimPair pair = new AttackerVictimPair(attacker, victim);
        MergedDisplay display = mergedDamage.get(pair);
        if (display != null && !display.stand.isValid()) {
            removeMerged(pair);
            display = null;
        }
        if (display == null) {
            ArmorStand stand = createArmorStand(location, damage, color);
            if (!stand.isValid()) {
                return;
            }
            display = new MergedDisplay(victim.getUniqueId(), stand, damage);
            mergedDamage.put(pair, display);
            HOLOGRAPH_UTIL.onCleanup(stand, () -> removeMerged(pair));
        } else {
            double total = display.damage + damage;
            if (!Double.isFinite(total)) {
                DamageIndicator.getInstance()
                               .getLogger()
                               .warning("Merged damage overflow; starting a new damage display.");
                release(pair);
                displayMergedDamage(victim, attacker, location, damage, color);
                return;
            }
            display.damage = total;
            display.stand.setCustomName(formatDamage(total, color));
        }
        display.deadline = currentTick + (long) Math.ceil(ConfigKeys.DAMAGE_MERGE_TIMEOUT.asDouble() * TICKS_PER_SECOND);
        startHeartbeat();
    }

    private ArmorStand createArmorStand(Location location, double damage, TextColor color) {
        return HOLOGRAPH_UTIL.createArmorStandHologram(location, Component.text(formatNumber(damage), color));
    }

    private String formatDamage(double damage, TextColor color) {
        return TEXT_UTIL.serializeComponent(Component.text(formatNumber(damage), color));
    }

    private String formatNumber(double damage) {
        return Double.toString(BigDecimal.valueOf(damage)
                                         .setScale(DAMAGE_DECIMAL_PLACES, RoundingMode.HALF_UP)
                                         .doubleValue());
    }

    private TextColor resolveColorForCause(EntityDamageEvent.DamageCause cause) {
        ConfigKeys key;
        try {
            key = ConfigKeys.valueOf(cause.name());
        } catch (IllegalArgumentException unknownCause) {
            key = ConfigKeys.OTHER;
        }
        return key.getTextColor();
    }

    public void animateArmorStand(Location location, double damage, TextColor color) {
        ArmorStand stand = createArmorStand(location, damage, color);
        if (stand.isValid()) {
            startAnimation(stand, ANIMATION_DURATION_TICKS);
        }
    }

    private void startAnimation(ArmorStand stand, int duration) {
        UUID id = stand.getUniqueId();
        animations.put(id, new Animation(stand, duration, new Vector(randomHorizontalSpeed(), ConfigKeys.HOLOGRAM_VELOCITY_Y.asDouble(), randomHorizontalSpeed()), ConfigKeys.HOLOGRAM_GRAVITY.asDouble()));
        HOLOGRAPH_UTIL.onCleanup(stand, () -> removeAnimation(id));
        startHeartbeat();
    }

    private double randomHorizontalSpeed() {
        return ThreadLocalRandom.current()
                                .nextDouble(-MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED);
    }

    private void startHeartbeat() {
        if (heartbeat == null) {
            heartbeat = Bukkit.getScheduler()
                              .runTaskTimer(DamageIndicator.getInstance(), this::advance, HEARTBEAT_PERIOD_TICKS, HEARTBEAT_PERIOD_TICKS);
        }
    }

    private void advance() {
        currentTick++;
        // Entity callbacks can detach other displays while this snapshot is processed.
        new ArrayList<>(mergedDamage.keySet()).forEach(this::advanceMergedDisplay);
        new ArrayList<>(animations.keySet()).forEach(this::advanceAnimation);
        if (heartbeat != null && mergedDamage.isEmpty() && animations.isEmpty()) {
            heartbeat.cancel();
            heartbeat = null;
        }
    }

    private void advanceMergedDisplay(AttackerVictimPair pair) {
        MergedDisplay display = mergedDamage.get(pair);
        if (display == null) {
            return;
        }
        Entity victim = Bukkit.getEntity(display.victimId);
        if (!display.stand.isValid() || victim == null || !victim.isValid()) {
            removeMerged(pair);
        } else if (currentTick >= display.deadline) {
            release(pair);
        } else if (!HOLOGRAPH_UTIL.teleportIfMoved(display.stand, getHitLocation(victim))) {
            removeMerged(pair);
        }
    }

    private void advanceAnimation(UUID id) {
        Animation animation = animations.get(id);
        if (animation == null) {
            return;
        }
        if (!animation.stand.isValid()) {
            removeAnimation(id);
            return;
        }
        Location nextLocation = animation.stand.getLocation()
                                               .add(animation.velocity);
        if (!HOLOGRAPH_UTIL.teleportIfMoved(animation.stand, nextLocation)) {
            removeAnimation(id);
            return;
        }
        animation.velocity.setY(animation.velocity.getY() - animation.gravity);
        if (--animation.remaining == 0) {
            removeAnimation(id);
        }
    }

    private void release(AttackerVictimPair pair) {
        MergedDisplay display = mergedDamage.remove(pair);
        if (display == null) {
            return;
        }
        if (display.stand.isValid()) {
            startAnimation(display.stand, ConfigKeys.DAMAGE_MERGE_JUMP_DURATION.asInt());
        } else {
            HOLOGRAPH_UTIL.remove(display.stand);
        }
    }

    private void removeMerged(AttackerVictimPair pair) {
        MergedDisplay display = mergedDamage.remove(pair);
        if (display != null) {
            HOLOGRAPH_UTIL.remove(display.stand);
        }
    }

    private void removeAnimation(UUID id) {
        Animation animation = animations.remove(id);
        if (animation != null) {
            HOLOGRAPH_UTIL.remove(animation.stand);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        UUID id = event.getEntity()
                       .getUniqueId();
        new ArrayList<>(mergedDamage.keySet()).stream()
                                              .filter(pair -> pair.involves(id))
                                              .forEach(this::release);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Set<UUID> ids = new HashSet<>();
        event.getEntities()
             .forEach(entity -> ids.add(entity.getUniqueId()));
        removeForEntities(ids);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        Set<UUID> ids = new HashSet<>();
        event.getWorld()
             .getEntities()
             .forEach(entity -> ids.add(entity.getUniqueId()));
        removeForEntities(ids);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ArmorStand stand) {
            HOLOGRAPH_UTIL.removed(stand);
        } else if (event.getCause() != EntityRemoveEvent.Cause.DEATH) {
            removeForEntities(Set.of(entity.getUniqueId()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeForEntities(Set.of(event.getPlayer()
                                      .getUniqueId()));
    }

    /**
     * Victim/display removal destroys a number; attacker removal only releases its completed hit.
     */
    private void removeForEntities(Set<UUID> ids) {
        for (AttackerVictimPair pair : new ArrayList<>(mergedDamage.keySet())) {
            MergedDisplay display = mergedDamage.get(pair);
            if (display == null) {
                continue;
            }
            if (ids.contains(display.victimId) || ids.contains(display.stand.getUniqueId())) {
                removeMerged(pair);
            } else if (pair.involvesAny(ids)) {
                release(pair);
            }
        }
        for (UUID id : new ArrayList<>(animations.keySet())) {
            if (ids.contains(id)) {
                removeAnimation(id);
            }
        }
    }

    public void shutdown() {
        if (heartbeat != null) {
            heartbeat.cancel();
            heartbeat = null;
        }
        new ArrayList<>(mergedDamage.keySet()).forEach(this::removeMerged);
        new ArrayList<>(animations.keySet()).forEach(this::removeAnimation);
    }

    public Location getHitLocation(Entity entity) {
        return entity.getLocation()
                     .add(0, HIT_HEIGHT_OFFSET, 0);
    }

    private static final class MergedDisplay {

        private final UUID victimId;
        private final ArmorStand stand;
        private double damage;
        private long deadline;

        private MergedDisplay(UUID victimId, ArmorStand stand, double damage) {
            this.victimId = victimId;
            this.stand = stand;
            this.damage = damage;
        }
    }

    private static final class Animation {

        private final ArmorStand stand;
        private final Vector velocity;
        private final double gravity;
        private int remaining;

        private Animation(ArmorStand stand, int remaining, Vector velocity, double gravity) {
            this.stand = stand;
            this.remaining = remaining;
            this.velocity = velocity;
            this.gravity = gravity;
        }
    }
}
