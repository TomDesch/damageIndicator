package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class HealthBarListenerTest {

    @Test
    void resolveDisplayHealth_SubtractsFinalDamageFromPreHitHealth() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getHealth()).thenReturn(80.0);

        Method method = HealthBarListener.class.getDeclaredMethod("resolveDisplayHealth", LivingEntity.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(listener, entity, 15.0, 100.0);

        assertEquals(65.0, result, 0.0001);
    }

    @Test
    void resolveDisplayHealth_ClampsLethalDamageToZero() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getHealth()).thenReturn(10.0);

        Method method = HealthBarListener.class.getDeclaredMethod("resolveDisplayHealth", LivingEntity.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(listener, entity, 15.0, 20.0);

        assertEquals(0.0, result, 0.0001);
    }

    @Test
    void rememberOriginalName_DoesNotOverwriteNameOnLaterUpdates() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getCustomName()).thenReturn("Original name", "Current health bar");
        when(entity.isCustomNameVisible()).thenReturn(false);
        when(entity.getPersistentDataContainer()).thenReturn(data);

        Method remember = HealthBarListener.class.getDeclaredMethod("rememberOriginalName", LivingEntity.class);
        remember.setAccessible(true);
        remember.invoke(listener, entity);
        remember.invoke(listener, entity);

        Method reset = HealthBarListener.class.getDeclaredMethod("resetEntityName", LivingEntity.class);
        reset.setAccessible(true);
        reset.invoke(listener, entity);

        verify(entity).setCustomName("Original name");
        verify(entity).setCustomNameVisible(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_CancelsTasksAndRestoresOriginalNames() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        UUID entityId = UUID.randomUUID();
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        BukkitTask task = mock(BukkitTask.class);
        BukkitTask pendingTask = mock(BukkitTask.class);
        when(task.isCancelled()).thenReturn(false);
        when(pendingTask.isCancelled()).thenReturn(false);
        when(entity.getPersistentDataContainer()).thenReturn(data);
        when(entity.getUniqueId()).thenReturn(entityId);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN))).thenReturn(true);
        when(world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[]{entity});

        Field tasksField = HealthBarListener.class.getDeclaredField("entitiesWithActiveHealthBars");
        tasksField.setAccessible(true);
        ((Map<LivingEntity, BukkitTask>) tasksField.get(listener)).put(entity, task);
        Field pendingField = HealthBarListener.class.getDeclaredField("pendingHealTasks");
        pendingField.setAccessible(true);
        ((Set<BukkitTask>) pendingField.get(listener)).add(pendingTask);
        Field namesField = HealthBarListener.class.getDeclaredField("originalEntityNames");
        namesField.setAccessible(true);
        ((Map<UUID, HealthBarListener.OriginalNameState>) namesField.get(listener))
            .put(entityId, new HealthBarListener.OriginalNameState(net.kyori.adventure.text.Component.text("Original"), false));

        try (MockedStatic<Bukkit> bukkitStatic = mockStatic(Bukkit.class)) {
            bukkitStatic.when(Bukkit::getWorlds).thenReturn(List.of(world));
            listener.shutdown();
        }

        verify(task).cancel();
        verify(pendingTask).cancel();
        verify(entity).setCustomName("Original");
        verify(entity).setCustomNameVisible(false);
    }

    @Test
    void attackerNamesAreNotResetFromPreDeathDamageEvents() {
        boolean hasDamageByEntityHandler = Arrays.stream(HealthBarListener.class.getDeclaredMethods())
                                                 .filter(method -> method.isAnnotationPresent(EventHandler.class))
                                                 .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                                                 .anyMatch(EntityDamageByEntityEvent.class::equals);

        assertFalse(hasDamageByEntityHandler);
    }

    @Test
    void shouldRemoveHologramBar_DoesNotExpireAlwaysVisibleBars() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        Method method = HealthBarListener.class.getDeclaredMethod("shouldRemoveHologramBar", int.class, boolean.class, boolean.class, boolean.class);
        method.setAccessible(true);

        assertEquals(false, method.invoke(listener, Integer.MAX_VALUE, true, true, true));
        assertEquals(true, method.invoke(listener, 0, false, true, true));
        assertEquals(true, method.invoke(listener, 0, true, false, true));
    }

    @Test
    void rememberOriginalName_TracksOriginallyUnnamedEntities() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getCustomName()).thenReturn(null);
        when(entity.isCustomNameVisible()).thenReturn(false);
        when(entity.getPersistentDataContainer()).thenReturn(data);

        Method remember = HealthBarListener.class.getDeclaredMethod("rememberOriginalName", LivingEntity.class);
        remember.setAccessible(true);
        remember.invoke(listener, entity);
        Method reset = HealthBarListener.class.getDeclaredMethod("resetEntityName", LivingEntity.class);
        reset.setAccessible(true);
        reset.invoke(listener, entity);

        verify(entity).setCustomName(null);
        verify(entity).setCustomNameVisible(false);
    }

    @Test
    void restoreNamesOnChunkLoad_UsesPersistedOriginalName() {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        Chunk chunk = mock(Chunk.class);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);
        when(chunk.getEntities()).thenReturn(new org.bukkit.entity.Entity[]{entity});
        when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN)))
            .thenAnswer(invocation -> ((NamespacedKey) invocation.getArgument(0)).getKey().equals("health-name-overridden"));
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn("Original");

        listener.restoreNamesOnChunkLoad(event);

        verify(entity).setCustomName("Original");
        verify(entity).setCustomNameVisible(false);
    }
}
