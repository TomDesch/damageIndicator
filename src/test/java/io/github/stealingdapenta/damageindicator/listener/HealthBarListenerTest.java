package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.bukkit.entity.LivingEntity;
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
}
