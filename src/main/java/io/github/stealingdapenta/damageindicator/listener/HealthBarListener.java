package io.github.stealingdapenta.damageindicator.listener;

import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_HOLOGRAM_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.ENABLE_HEALTH_BAR;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_ALIVE_SYMBOL;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_ALWAYS_VISIBLE;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_DEAD_SYMBOL;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_DISPLAY_DURATION;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_LENGTH;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_PREFIX;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HEALTH_BAR_SUFFIX;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_FOLLOW_SPEED;
import static io.github.stealingdapenta.damageindicator.config.ConfigKeys.HOLOGRAM_POSITION;
import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Listens to damage and death events and displays dynamic health bars (as floating holograms or custom names) above entities based on plugin config.
 */
public class HealthBarListener implements Listener {

    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_SECONDS = 1;
    private static final int MAX_SECONDS = 10;
    private static final int MAX_HEALTH_BAR_LENGTH = 100;
    private static final NamespacedKey ORIGINAL_NAME_MARKER_KEY = new NamespacedKey("damageindicator", "health-name-overridden");
    private static final NamespacedKey ORIGINAL_NAME_KEY = new NamespacedKey("damageindicator", "health-original-name");
    private static final NamespacedKey ORIGINAL_NAME_VISIBLE_KEY = new NamespacedKey("damageindicator", "health-original-visible");

    private final Map<LivingEntity, BukkitTask> entitiesWithActiveHealthBars = new HashMap<>();
    private final Map<LivingEntity, LivingEntityTaskInfo> entitiesWithActiveHologramBars = new HashMap<>();
    private final Map<LivingEntity, OriginalNameState> originalEntityNames = new HashMap<>();
    private final Set<BukkitTask> pendingHealTasks = new HashSet<>();

    /**
     * Displays the health bar after an entity takes damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void displayHealthBar(EntityDamageEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.MAX_HEALTH))
                                  .getValue();
        double currentHealth = resolveDisplayHealth(livingEntity, event.getFinalDamage(), maxHealth);
        Component name = createHealthBar(currentHealth, maxHealth);

        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
            displayHolographicHealthBar(livingEntity, name);
        } else {
            displayCustomNameHealthBar(livingEntity, name);
        }
    }

    /**
     * Updates the health bar when an entity regains health.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void updateHealthBarOnHeal(EntityRegainHealthEvent event) {
        if (!ENABLE_HEALTH_BAR.asBoolean()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        boolean hasActiveBar = ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean() ? entitiesWithActiveHologramBars.containsKey(livingEntity) : entitiesWithActiveHealthBars.containsKey(livingEntity);

        if (!hasActiveBar && !HEALTH_BAR_ALWAYS_VISIBLE.asBoolean()) {
            return; // respect visibility rules
        }

        // Defer one tick so the healing is fully applied before we read health.
        BukkitTask[] taskHolder = new BukkitTask[1];
        BukkitTask pendingTask = DamageIndicator.getInstance()
                                                .getServer()
                                                .getScheduler()
                                                .runTask(DamageIndicator.getInstance(), () -> {
                                                    try {
                                                        if (!ENABLE_HEALTH_BAR.asBoolean() || !livingEntity.isValid()) {
                                                            return;
                                                        }
                                                        double maxHealth = Objects.requireNonNull(livingEntity.getAttribute(Attribute.MAX_HEALTH))
                                                                                  .getValue();
                                                        double currentHealth = Math.min(livingEntity.getHealth(), maxHealth);
                                                        Component name = createHealthBar(currentHealth, maxHealth);

                                                        if (ENABLE_HOLOGRAM_HEALTH_BAR.asBoolean()) {
                                                            displayHolographicHealthBar(livingEntity, name);
                                                        } else {
                                                            displayCustomNameHealthBar(livingEntity, name);
                                                        }
                                                    } finally {
                                                        pendingHealTasks.remove(taskHolder[0]);
                                                    }
                                                });
        taskHolder[0] = pendingTask;
        pendingHealTasks.add(pendingTask);
    }


    /**
     * Handles entity death to clear health bars or restore names.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeathEvents(EntityDeathEvent event) {
        removeHologramsUponDeath(event);
        restoreNameUponDeath(event);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void restoreNamesOnChunkLoad(ChunkLoadEvent event) {
        java.util.Arrays.stream(event.getChunk()
                                    .getEntities())
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast)
                        .filter(this::hasPersistedOriginalName)
                        .forEach(this::resetEntityName);
    }

    private void displayHolographicHealthBar(LivingEntity entity, Component name) {
        HOLOGRAPH_UTIL.cancelHologramFor(entity, entitiesWithActiveHologramBars);
        LivingEntityTaskInfo taskInfo = spawnHologramBar(entity, name);
        entitiesWithActiveHologramBars.put(entity, taskInfo);
    }

    private void displayCustomNameHealthBar(LivingEntity entity, Component name) {
        BukkitTask oldTask = entitiesWithActiveHealthBars.remove(entity);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }

        rememberOriginalName(entity);

        entity.setCustomName(TEXT_UTIL.serializeComponent(name));
        entity.setCustomNameVisible(true);

        if (!HEALTH_BAR_ALWAYS_VISIBLE.asBoolean()) {
            BukkitTask resetTask = scheduleNameReset(entity);
            entitiesWithActiveHealthBars.put(entity, resetTask);
        }
    }

    private void restoreNameUponDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        BukkitTask task = entitiesWithActiveHealthBars.remove(entity);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        resetEntityName(entity);
    }

    private void removeHologramsUponDeath(EntityDeathEvent event) {
        HOLOGRAPH_UTIL.cancelHologramFor(event.getEntity(), entitiesWithActiveHologramBars);
    }

    private BukkitTask scheduleNameReset(LivingEntity entity) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                resetEntityName(entity);
                entitiesWithActiveHealthBars.remove(entity);
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                resetEntityName(entity);
                entitiesWithActiveHealthBars.remove(entity);
                super.cancel();
            }
        }.runTaskLater(DamageIndicator.getInstance(), getDisplayDurationInTicks());
    }

    void resetEntityName(LivingEntity entity) {
        OriginalNameState originalName = originalEntityNames.remove(entity);
        if (originalName == null) {
            originalName = readPersistedOriginalName(entity);
        }
        if (originalName == null) {
            return;
        }

        entity.setCustomName(TEXT_UTIL.serializeComponent(originalName.name()));
        entity.setCustomNameVisible(originalName.visible());
        clearPersistedOriginalName(entity);
    }

    private void rememberOriginalName(LivingEntity entity) {
        OriginalNameState originalName = readPersistedOriginalName(entity);
        if (originalName == null) {
            originalName = new OriginalNameState(TEXT_UTIL.deserializeComponent(entity.getCustomName()), entity.isCustomNameVisible());
            persistOriginalName(entity, originalName);
        }
        originalEntityNames.putIfAbsent(entity, originalName);
    }

    public void shutdown() {
        new ArrayList<>(pendingHealTasks).forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        pendingHealTasks.clear();

        new ArrayList<>(entitiesWithActiveHealthBars.values()).forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        entitiesWithActiveHealthBars.clear();

        new ArrayList<>(entitiesWithActiveHologramBars.keySet()).forEach(entity -> HOLOGRAPH_UTIL.cancelHologramFor(entity, entitiesWithActiveHologramBars));
        new ArrayList<>(originalEntityNames.keySet()).forEach(this::resetEntityName);
    }

    public void restorePersistedNames() {
        Bukkit.getWorlds()
              .stream()
              .flatMap(world -> java.util.Arrays.stream(world.getLoadedChunks()))
              .flatMap(chunk -> java.util.Arrays.stream(chunk.getEntities()))
              .filter(LivingEntity.class::isInstance)
              .map(LivingEntity.class::cast)
              .filter(this::hasPersistedOriginalName)
              .forEach(this::resetEntityName);
    }

    private void persistOriginalName(LivingEntity entity, OriginalNameState originalName) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(ORIGINAL_NAME_MARKER_KEY, PersistentDataType.BOOLEAN, true);
        data.set(ORIGINAL_NAME_VISIBLE_KEY, PersistentDataType.BOOLEAN, originalName.visible());
        if (originalName.name() == null) {
            data.remove(ORIGINAL_NAME_KEY);
        } else {
            data.set(ORIGINAL_NAME_KEY, PersistentDataType.STRING, TEXT_UTIL.serializeComponent(originalName.name()));
        }
    }

    private OriginalNameState readPersistedOriginalName(LivingEntity entity) {
        if (!hasPersistedOriginalName(entity)) {
            return null;
        }

        PersistentDataContainer data = entity.getPersistentDataContainer();
        String name = data.get(ORIGINAL_NAME_KEY, PersistentDataType.STRING);
        boolean visible = Boolean.TRUE.equals(data.get(ORIGINAL_NAME_VISIBLE_KEY, PersistentDataType.BOOLEAN));
        return new OriginalNameState(TEXT_UTIL.deserializeComponent(name), visible);
    }

    private boolean hasPersistedOriginalName(LivingEntity entity) {
        return Boolean.TRUE.equals(entity.getPersistentDataContainer()
                                        .get(ORIGINAL_NAME_MARKER_KEY, PersistentDataType.BOOLEAN));
    }

    private void clearPersistedOriginalName(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.remove(ORIGINAL_NAME_MARKER_KEY);
        data.remove(ORIGINAL_NAME_KEY);
        data.remove(ORIGINAL_NAME_VISIBLE_KEY);
    }

    record OriginalNameState(Component name, boolean visible) {
    }

    private LivingEntityTaskInfo spawnHologramBar(LivingEntity entity, Component name) {
        ArmorStand armorStand = HOLOGRAPH_UTIL.createArmorStandHologram(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_POSITION.asDouble()), name);

        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (shouldRemoveHologramBar(ticks++, entity.isValid(), armorStand.isValid(), HEALTH_BAR_ALWAYS_VISIBLE.asBoolean())) {
                    cancel();
                    return;
                }
                armorStand.teleport(HOLOGRAPH_UTIL.locationAboveEntity(entity, HOLOGRAM_POSITION.asDouble()));
            }

            @Override
            public synchronized void cancel() {
                if (armorStand.isValid()) {
                    armorStand.remove();
                }
                entitiesWithActiveHologramBars.remove(entity);
                super.cancel();
            }
        }.runTaskTimer(DamageIndicator.getInstance(), 0, HOLOGRAM_FOLLOW_SPEED.asIntAtLeast(1));

        return new LivingEntityTaskInfo(task, armorStand);
    }

    private boolean shouldRemoveHologramBar(int ticks, boolean entityValid, boolean armorStandValid, boolean alwaysVisible) {
        return !entityValid || !armorStandValid || (!alwaysVisible && ticks >= getDisplayDurationInTicks());
    }

    private int getDisplayDurationInTicks() {
        int seconds = HEALTH_BAR_DISPLAY_DURATION.asInt();
        return TICKS_PER_SECOND * Math.max(MIN_SECONDS, Math.min(seconds, MAX_SECONDS));
    }

    private double resolveDisplayHealth(LivingEntity livingEntity, double finalDamage, double maxHealth) {
        return Math.min(Math.max(0.0, livingEntity.getHealth() - finalDamage), maxHealth);
    }

    private Component createHealthBar(double current, double max) {
        int total = HEALTH_BAR_LENGTH.asIntBetween(1, MAX_HEALTH_BAR_LENGTH);
        double percent = 1.0 - (current / max);
        int dead = Math.min(total - 1, Math.max(0, (int) Math.round(percent * total)));
        int alive = total - dead;

        TextComponent alivePart = TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_ALIVE_SYMBOL.asFormattedString(), alive);
        TextComponent deadPart = TEXT_UTIL.repeatTextWithStyles(HEALTH_BAR_DEAD_SYMBOL.asFormattedString(), dead);

        return HEALTH_BAR_PREFIX.asFormattedString()
                                .append(alivePart.append(deadPart))
                                .append(HEALTH_BAR_SUFFIX.asFormattedString());
    }
}
