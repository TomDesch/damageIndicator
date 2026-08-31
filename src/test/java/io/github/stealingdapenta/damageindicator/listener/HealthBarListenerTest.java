package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class HealthBarListenerTest {

    @Test
    void resolveDisplayHealth_UsesCurrentEntityHealthWithoutDoubleSubtracting() throws Exception {
        HealthBarListener listener = new HealthBarListener();
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getHealth()).thenReturn(80.0);

        Method method = HealthBarListener.class.getDeclaredMethod("resolveDisplayHealth", LivingEntity.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(listener, entity, 100.0);

        assertEquals(80.0, result, 0.0001);
    }
}
