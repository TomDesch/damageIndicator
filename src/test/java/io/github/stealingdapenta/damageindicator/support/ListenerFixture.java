package io.github.stealingdapenta.damageindicator.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.mockito.MockedStatic;

/**
 * Only server-owned interfaces are mocked; names, tags, locations and time have shared mutable state.
 */
public final class ListenerFixture implements AutoCloseable {

    public static final double GROUND_Y = 64;
    public static final double MAX_HEALTH = 20;

    static {
        BukkitRegistries.initialize();
    }

    public final DamageIndicator plugin = mock(DamageIndicator.class);
    public final World world = mock(World.class);
    public final Logger logger = mock(Logger.class);
    public final YamlConfiguration config = new YamlConfiguration();
    public final DamageSource damageSource = mock(DamageSource.class);
    public final TickScheduler clock = new TickScheduler();
    public final Map<UUID, Entity> loaded = new HashMap<>();
    public final List<Model> stands = new ArrayList<>();
    public boolean cancelSpawns;
    public Consumer<Entity> onRemove = entity -> {
    };
    public Consumer<Entity> onTeleport = entity -> {
    };
    public Consumer<ArmorStand> onSpawn = entity -> {
    };
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<DamageIndicator> singleton;

    @SuppressWarnings("unchecked")
    public void open() {
        when(damageSource.getDamageType()).thenReturn(DamageType.GENERIC);
        when(plugin.getName()).thenReturn("DamageIndicator");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
        singleton = mockStatic(DamageIndicator.class);
        singleton.when(DamageIndicator::getInstance)
                 .thenReturn(plugin);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler)
              .thenReturn(clock.scheduler);
        bukkit.when(() -> Bukkit.getEntity(any(UUID.class)))
              .thenAnswer(call -> loaded.get(call.getArgument(0, UUID.class)));
        bukkit.when(Bukkit::getWorlds)
              .thenReturn(List.of(world));
        when(world.getEntities()).thenAnswer(call -> new ArrayList<>(loaded.values()));
        when(world.getLivingEntities()).thenAnswer(call -> loaded.values()
                                                                 .stream()
                                                                 .filter(LivingEntity.class::isInstance)
                                                                 .map(LivingEntity.class::cast)
                                                                 .toList());
        when(world.spawn(any(Location.class), eq(ArmorStand.class), any(Consumer.class))).thenAnswer(call -> {
            Model stand = entity(ArmorStand.class, null);
            stand.location = call.getArgument(0, Location.class)
                                 .clone();
            stands.add(stand);
            Consumer<ArmorStand> configure = call.getArgument(2);
            configure.accept((ArmorStand) stand.entity);
            onSpawn.accept((ArmorStand) stand.entity);
            if (cancelSpawns) {
                stand.valid = false;
                loaded.remove(stand.id);
            }
            return stand.entity;
        });
    }

    public Model mob(String name) {
        return entity(LivingEntity.class, name);
    }

    public Player attacker() {
        return (Player) entity(Player.class, null).entity;
    }

    public Model entity(Class<? extends LivingEntity> type, String name) {
        return entity(UUID.randomUUID(), type, name, new MemoryPersistentData());
    }

    public Model entity(UUID id, Class<? extends LivingEntity> type, String name, MemoryPersistentData data) {
        return new Model(id, type, name, data);
    }

    public static NamespacedKey key(String name) {
        return NamespacedKey.fromString("damageindicator:" + name);
    }

    @Override
    public void close() {
        bukkit.close();
        singleton.close();
    }

    public final class Model {

        public final UUID id;
        public final LivingEntity entity;
        public final AttributeInstance maximum = mock(AttributeInstance.class);
        public final MemoryPersistentData data;
        public String name;
        public boolean visible;
        public boolean valid = true;
        public boolean dead;
        public double health = MAX_HEALTH;
        public double maximumHealth = MAX_HEALTH;
        public boolean hasMaximum = true;
        public boolean teleportSucceeds = true;
        public Location location = new Location(world, 0, GROUND_Y, 0);
        public UnaryOperator<String> nameCanonicalizer = UnaryOperator.identity();

        private Model(UUID id, Class<? extends LivingEntity> type, String name, MemoryPersistentData data) {
            this.id = id;
            this.name = name;
            this.data = data;
            entity = mock(type);
            when(entity.getUniqueId()).thenReturn(id);
            when(entity.getPersistentDataContainer()).thenReturn(data);
            when(entity.getHealth()).thenAnswer(call -> health);
            when(entity.isValid()).thenAnswer(call -> valid);
            when(entity.isDead()).thenAnswer(call -> dead);
            when(entity.getHeight()).thenReturn(2.0);
            when(entity.getCustomName()).thenAnswer(call -> this.name);
            when(entity.isCustomNameVisible()).thenAnswer(call -> visible);
            when(entity.getAttribute(any())).thenAnswer(call -> hasMaximum ? maximum : null);
            when(maximum.getValue()).thenAnswer(call -> maximumHealth);
            when(entity.getLocation()).thenAnswer(call -> location.clone());
            when(entity.teleport(any(Location.class))).thenAnswer(call -> {
                if (teleportSucceeds) {
                    location = call.getArgument(0, Location.class)
                                   .clone();
                    onTeleport.accept(entity);
                }
                return teleportSucceeds;
            });
            doAnswer(call -> {
                this.name = nameCanonicalizer.apply(call.getArgument(0));
                return null;
            }).when(entity)
              .setCustomName(any());
            doAnswer(call -> {
                visible = call.getArgument(0);
                return null;
            }).when(entity)
              .setCustomNameVisible(anyBoolean());
            doAnswer(call -> {
                onRemove.accept(entity);
                valid = false;
                loaded.remove(id);
                return null;
            }).when(entity)
              .remove();
            loaded.put(id, entity);
        }

        public String number() {
            return ChatColor.stripColor(name);
        }
    }
}
