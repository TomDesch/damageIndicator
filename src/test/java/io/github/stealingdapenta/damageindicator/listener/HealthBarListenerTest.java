package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.LivingEntity;
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

        Method remember = HealthBarListener.class.getDeclaredMethod("rememberOriginalName", LivingEntity.class);
        remember.setAccessible(true);
        remember.invoke(listener, entity);
        remember.invoke(listener, entity);

        Method reset = HealthBarListener.class.getDeclaredMethod("resetEntityName", LivingEntity.class);
        reset.setAccessible(true);
        reset.invoke(listener, entity);

        verify(entity).setCustomName("Original name");
    }

    @Test
    void isLethalHit_RequiresDamageToReachCurrentHealth() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity victim = mock(LivingEntity.class);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(victim.getHealth()).thenReturn(10.0);
        when(event.getFinalDamage()).thenReturn(9.5, 10.0);

        Method method = HealthBarListener.class.getDeclaredMethod("isLethalHit", EntityDamageByEntityEvent.class, LivingEntity.class);
        method.setAccessible(true);

        assertEquals(false, method.invoke(listener, event, victim));
        assertEquals(true, method.invoke(listener, event, victim));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_CancelsTasksAndRestoresOriginalNames() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        BukkitTask task = mock(BukkitTask.class);
        when(task.isCancelled()).thenReturn(false);

        Field tasksField = HealthBarListener.class.getDeclaredField("entitiesWithActiveHealthBars");
        tasksField.setAccessible(true);
        ((Map<LivingEntity, BukkitTask>) tasksField.get(listener)).put(entity, task);
        Field namesField = HealthBarListener.class.getDeclaredField("originalEntityNames");
        namesField.setAccessible(true);
        ((Map<LivingEntity, Component>) namesField.get(listener)).put(entity, Component.text("Original"));

        listener.shutdown();

        verify(task).cancel();
        verify(entity).setCustomName("Original");
    }
}
