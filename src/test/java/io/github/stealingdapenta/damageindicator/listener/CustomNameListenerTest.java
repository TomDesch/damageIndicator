package io.github.stealingdapenta.damageindicator.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.utils.HolographUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomNameListenerTest {

    private CustomNameListener customNameListener;
    private HolographUtil mockHolographUtil;

    private static void setPrivateFieldUsingReflection(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass()
                                                  .getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.out.printf("Error setting private field %s for target %s and value %s.%n", fieldName, target, value.toString());
            e.printStackTrace();
        }
    }

    private static LivingEntity mockLivingEntity() {
        LivingEntity livingEntity = mock(LivingEntity.class);
        when(livingEntity.isCustomNameVisible()).thenReturn(true);
        when(livingEntity.customName()).thenReturn(Component.text("TestName"));
        return livingEntity;
    }

    @BeforeEach
    void setUp() {
        customNameListener = new CustomNameListener();
        mockHolographUtil = mock(HolographUtil.class);

        setPrivateFieldUsingReflection(customNameListener, "holographUtil", mockHolographUtil);
    }

    @Test
    void replaceCustomName_EntitySpawnEvent_LivingEntityWithoutCustomName() {
        EntitySpawnEvent spawnEvent = mock(EntitySpawnEvent.class);
        LivingEntity livingEntity = mock(LivingEntity.class);
        when(spawnEvent.getEntity()).thenReturn(livingEntity);
        when(livingEntity.hasMetadata(anyString())).thenReturn(false);
        when(livingEntity.isCustomNameVisible()).thenReturn(true);
        when(livingEntity.customName()).thenReturn(null);

        customNameListener.replaceCustomName(spawnEvent);

        verify(mockHolographUtil, never()).createArmorStandHologram(any(), any());
        verify(livingEntity, never()).setCustomNameVisible(false);
    }

    @Test
    void replaceCustomName_EntitySpawnEvent_NonLivingEntity() {
        EntitySpawnEvent spawnEvent = mock(EntitySpawnEvent.class);
        Entity entity = mock(Entity.class);
        when(spawnEvent.getEntity()).thenReturn(entity);

        customNameListener.replaceCustomName(spawnEvent);

        verify(mockHolographUtil, never()).createArmorStandHologram(any(), any());
    }

    @Test
    void replaceCustomName_ChunkLoadEvent_NonLivingEntity() {
        ChunkLoadEvent chunkLoadEvent = mock(ChunkLoadEvent.class);
        Chunk chunk = mock(Chunk.class);
        when(chunkLoadEvent.getChunk()).thenReturn(chunk);

        Entity entity = mock(Entity.class);
        when(chunk.getEntities()).thenReturn(new Entity[]{entity});

        customNameListener.replaceCustomName(chunkLoadEvent);

        verify(mockHolographUtil, never()).createArmorStandHologram(any(), any());
    }

    @Test
    void removeHolographicCustomNames_EntityDeathEvent_success() {
        EntityDeathEvent entityDeathEvent = mock(EntityDeathEvent.class);
        LivingEntity livingEntity = mockLivingEntity();

        when(entityDeathEvent.getEntity()).thenReturn(livingEntity);

        customNameListener.removeHolographicCustomNames(entityDeathEvent);

        verify(mockHolographUtil).cancelHologramFor(eq(livingEntity), any());
    }
}
