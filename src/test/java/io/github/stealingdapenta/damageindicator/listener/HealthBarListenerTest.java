package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

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
        when(entity.getCustomName()).thenReturn("Original name", "Current health bar");
        when(entity.isCustomNameVisible()).thenReturn(false);

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
        BukkitTask task = mock(BukkitTask.class);
        BukkitTask pendingTask = mock(BukkitTask.class);
        when(task.isCancelled()).thenReturn(false);
        when(pendingTask.isCancelled()).thenReturn(false);

        Field tasksField = HealthBarListener.class.getDeclaredField("entitiesWithActiveHealthBars");
        tasksField.setAccessible(true);
        ((Map<LivingEntity, BukkitTask>) tasksField.get(listener)).put(entity, task);
        Field pendingField = HealthBarListener.class.getDeclaredField("pendingHealTasks");
        pendingField.setAccessible(true);
        ((Set<BukkitTask>) pendingField.get(listener)).add(pendingTask);
        Field namesField = HealthBarListener.class.getDeclaredField("originalEntityNames");
        namesField.setAccessible(true);
        ((Map<LivingEntity, HealthBarListener.OriginalNameState>) namesField.get(listener))
            .put(entity, new HealthBarListener.OriginalNameState(net.kyori.adventure.text.Component.text("Original"), false));

        listener.shutdown();

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
        when(entity.getCustomName()).thenReturn(null);
        when(entity.isCustomNameVisible()).thenReturn(false);

        Method remember = HealthBarListener.class.getDeclaredMethod("rememberOriginalName", LivingEntity.class);
        remember.setAccessible(true);
        remember.invoke(listener, entity);
        Method reset = HealthBarListener.class.getDeclaredMethod("resetEntityName", LivingEntity.class);
        reset.setAccessible(true);
        reset.invoke(listener, entity);

        verify(entity).setCustomName(null);
        verify(entity).setCustomNameVisible(false);
    }
}
