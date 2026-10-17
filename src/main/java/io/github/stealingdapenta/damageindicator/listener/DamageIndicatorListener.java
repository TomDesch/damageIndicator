package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.ConfigKeys;
import io.github.stealingdapenta.damageindicator.utils.AttackerVictimPair;
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
import java.util.function.BiConsumer;
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
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Owns transient damage displays; a single ticker advances all merge and animation state.
 * Native hits observe committed HP at the next hit, normal Bukkit heal/resurrection boundary, or heartbeat.
 * Silent setHealth interleavings between those boundaries cannot be attributed reliably.
 */
public class DamageIndicatorListener implements Listener {

    private static final NamespacedKey HOLOGRAM_KEY = Objects.requireNonNull(NamespacedKey.fromString("damageindicator:customnsktag"));
    private static final int TICKS_PER_SECOND = 20;
    private static final long HEARTBEAT_PERIOD_TICKS = 1;
    private static final int ANIMATION_DURATION_TICKS = 30;
    private static final int DAMAGE_DECIMAL_PLACES = 2;
    private static final double MAX_HORIZONTAL_SPEED = 0.05;
    private static final double HIT_HEIGHT_OFFSET = 1;
    private final BiConsumer<LivingEntity, Double> appliedDamageConsumer;
    private final Map<UUID, PendingHit> pendingHits = new HashMap<>();
    private final Map<AttackerVictimPair, MergedDisplay> mergedDamage = new HashMap<>();
    private final Map<UUID, Animation> animations = new HashMap<>();
    private BukkitTask heartbeat;
    private long currentTick;
    private long shutdownGeneration;
    private boolean shuttingDown;

    public DamageIndicatorListener() {
        this((victim, damage) -> {
        });
    }

    /**
     * Delivers confirmed native HP loss synchronously, before optional numeric rendering.
     */
    public DamageIndicatorListener(BiConsumer<LivingEntity, Double> appliedDamageConsumer) {
        this.appliedDamageConsumer = Objects.requireNonNull(appliedDamageConsumer);
    }

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
        if (shuttingDown || event.isCancelled() || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        double finalDamage = event.getFinalDamage();
        if (!Double.isFinite(finalDamage)) {
            warnInvalidObservation("non-finite final damage", victim.getUniqueId());
            return;
        }
        if (finalDamage <= 0) {
            return;
        }
        long generation = shutdownGeneration;
        flushPending(victim, false);
        if (generation != shutdownGeneration || !victim.isValid() || !isDamageObservationEnabled()) {
            return;
        }
        double health = victim.getHealth();
        if (!isValidHealth(health, victim.getUniqueId()) || health == 0 || generation != shutdownGeneration) {
            return;
        }
        Location location = getHitLocation(victim);
        if (!hasWorld(location)) {
            return;
        }
        Entity attacker = event instanceof EntityDamageByEntityEvent attack ? resolveAttacker(attack.getDamager()) : null;
        if (generation != shutdownGeneration) {
            return;
        }
        pendingHits.put(victim.getUniqueId(), new PendingHit(health, finalDamage, attacker == null ? null : attacker.getUniqueId(),
                                                           event.getCause(), location, currentTick));
        startHeartbeat();
    }

    private boolean isDamageObservationEnabled() {
        return ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean() || ConfigKeys.ENABLE_HEALTH_BAR.asBoolean();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof LivingEntity victim) {
            // Healing is not committed yet, so it must not erase the preceding hit.
            flushPending(victim, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!event.isCancelled()) {
            flushPending(event.getEntity(), true);
        }
    }

    private void flushPending(UUID victimId) {
        Entity entity = Bukkit.getEntity(victimId);
        if (entity instanceof LivingEntity victim) {
            flushPending(victim, false);
        } else {
            pendingHits.remove(victimId);
        }
    }

    private void flushPending(LivingEntity victim, boolean terminal) {
        PendingHit hit = pendingHits.remove(victim.getUniqueId());
        if (hit == null || !terminal && !victim.isValid()) {
            return;
        }
        long generation = shutdownGeneration;
        double health = victim.getHealth();
        if (!isValidHealth(health, victim.getUniqueId()) || generation != shutdownGeneration) {
            return;
        }
        double damage = Math.clamp(hit.health() - health, 0, hit.finalDamage());
        if (shuttingDown || !Double.isFinite(damage) || damage <= 0) {
            return;
        }
        appliedDamageConsumer.accept(victim, damage);
        if (generation != shutdownGeneration || !canDisplayDamage(victim, damage)) {
            return;
        }
        TextColor color = resolveColorForCause(hit.cause());
        if (hit.attackerId() != null && ConfigKeys.ENABLE_DAMAGE_MERGE.asBoolean()) {
            displayMergedDamage(victim.getUniqueId(), hit.attackerId(), hit.location(), damage, color, hit.tick());
        } else {
            animateArmorStand(hit.location(), damage, color);
        }
    }

    private boolean isValidHealth(double health, UUID victimId) {
        if (!Double.isFinite(health) || health < 0) {
            warnInvalidObservation("invalid health", victimId);
            return false;
        }
        return true;
    }

    private void warnInvalidObservation(String reason, UUID victimId) {
        DamageIndicator.getInstance()
                       .getLogger()
                       .warning("Cannot observe native damage for " + victimId + ": " + reason + ".");
    }

    /**
     * Displays already-applied HP damage; never modifies health or manufactures a damage event.
     */
    public void displayDamage(LivingEntity victim, Entity attacker, double damage, EntityDamageEvent.DamageCause cause) {
        if (!canDisplayDamage(victim, damage)) {
            return;
        }
        Location location = getHitLocation(victim);
        if (!hasWorld(location)) {
            return;
        }
        TextColor color = resolveColorForCause(cause == null ? EntityDamageEvent.DamageCause.CUSTOM : cause);
        if (!canMergeDamage(victim, attacker)) {
            animateArmorStand(location, damage, color);
            return;
        }

        displayMergedDamage(victim.getUniqueId(), resolveAttacker(attacker).getUniqueId(), location, damage, color, currentTick);
    }

    private boolean hasWorld(Location location) {
        if (location.getWorld() != null) {
            return true;
        }
        DamageIndicator.getInstance()
                       .getLogger()
                       .warning("Cannot display damage without a victim world.");
        return false;
    }

    private boolean canDisplayDamage(LivingEntity victim, double damage) {
        return !shuttingDown && victim != null && Double.isFinite(damage) && damage > 0 && ConfigKeys.ENABLE_DAMAGE_INDICATOR.asBoolean();
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

    private void displayMergedDamage(UUID victimId, UUID attackerId, Location location, double damage, TextColor color, long hitTick) {
        long generation = shutdownGeneration;
        AttackerVictimPair pair = new AttackerVictimPair(attackerId, victimId);
        MergedDisplay display = mergedDamage.get(pair);
        if (display != null && !display.stand.isValid()) {
            removeMerged(pair);
            display = null;
        }
        if (generation != shutdownGeneration) {
            return;
        }
        if (display == null) {
            ArmorStand stand = createArmorStand(location, damage, color);
            if (generation != shutdownGeneration) {
                HOLOGRAPH_UTIL.remove(stand);
                return;
            }
            if (!stand.isValid()) {
                return;
            }
            display = new MergedDisplay(victimId, stand, damage);
            mergedDamage.put(pair, display);
            HOLOGRAPH_UTIL.onCleanup(stand, () -> removeMerged(pair));
        } else {
            double total = display.damage + damage;
            if (!Double.isFinite(total)) {
                DamageIndicator.getInstance()
                               .getLogger()
                               .warning("Merged damage overflow; starting a new damage display.");
                release(pair);
                if (generation == shutdownGeneration) {
                    displayMergedDamage(victimId, attackerId, location, damage, color, hitTick);
                }
                return;
            }
            display.damage = total;
            display.stand.setCustomName(formatDamage(total, color));
        }
        if (generation != shutdownGeneration || mergedDamage.get(pair) != display) {
            return;
        }
        display.deadline = hitTick + (long) Math.ceil(ConfigKeys.DAMAGE_MERGE_TIMEOUT.asDouble() * TICKS_PER_SECOND);
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
        if (shuttingDown) {
            return;
        }
        long generation = shutdownGeneration;
        ArmorStand stand = createArmorStand(location, damage, color);
        if (generation != shutdownGeneration) {
            HOLOGRAPH_UTIL.remove(stand);
        } else if (stand.isValid()) {
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
        if (!shuttingDown && heartbeat == null) {
            heartbeat = Bukkit.getScheduler()
                              .runTaskTimer(DamageIndicator.getInstance(), this::advance, HEARTBEAT_PERIOD_TICKS, HEARTBEAT_PERIOD_TICKS);
        }
    }

    private void advance() {
        currentTick++;
        // Entity callbacks can detach other displays while this snapshot is processed.
        new ArrayList<>(pendingHits.keySet()).forEach(this::flushPending);
        new ArrayList<>(mergedDamage.keySet()).forEach(this::advanceMergedDisplay);
        new ArrayList<>(animations.keySet()).forEach(this::advanceAnimation);
        if (heartbeat != null && pendingHits.isEmpty() && mergedDamage.isEmpty() && animations.isEmpty()) {
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
        // Death can invalidate the victim before this callback, but its final merged number must survive.
        flushPending(event.getEntity(), true);
        flushPendingForAttackers(Set.of(id));
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
        pendingHits.keySet().removeAll(ids);
        flushPendingForAttackers(ids);
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

    private void flushPendingForAttackers(Set<UUID> ids) {
        for (UUID victimId : new ArrayList<>(pendingHits.keySet())) {
            PendingHit hit = pendingHits.get(victimId);
            if (hit != null && hit.attackerId() != null && ids.contains(hit.attackerId())) {
                flushPending(victimId);
            }
        }
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        try {
            shutdownGeneration++;
            pendingHits.clear();
            if (heartbeat != null) {
                heartbeat.cancel();
                heartbeat = null;
            }
            new ArrayList<>(mergedDamage.keySet()).forEach(this::removeMerged);
            new ArrayList<>(animations.keySet()).forEach(this::removeAnimation);
        } finally {
            shuttingDown = false;
        }
    }

    public Location getHitLocation(Entity entity) {
        return entity.getLocation()
                     .add(0, HIT_HEIGHT_OFFSET, 0);
    }

    private record PendingHit(double health, double finalDamage, UUID attackerId, EntityDamageEvent.DamageCause cause,
                              Location location, long tick) {

        private PendingHit {
            location = location.clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
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
