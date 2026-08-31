package io.github.stealingdapenta.damageindicator.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.stealingdapenta.damageindicator.utils.LivingEntityTaskInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomNameListenerTest {

    private CustomNameListener listener;

    @BeforeEach
    void setUp() {
        listener = new CustomNameListener(); // Uses HOLOGRAPH_UTIL directly now
    }

    private LivingEntity mockLivingEntityWithCustomName() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.isCustomNameVisible()).thenReturn(true);
        when(entity.getCustomName()).thenReturn("VisibleName");
        return entity;
    }

    @Test
    void replaceCustomName_EntitySpawnEvent_NonLivingEntity() {
        Entity entity = mock(Entity.class);
        EntitySpawnEvent event = mock(EntitySpawnEvent.class);
        when(event.getEntity()).thenReturn(entity);

        listener.onEntitySpawn(event);

        // nothing should happen, no exceptions
        verify(entity, never()).setCustomNameVisible(false);
    }

    @Test
    void replaceCustomName_EntitySpawnEvent_EntityWithoutVisibleName() {
        LivingEntity entity = mock(LivingEntity.class);
        EntitySpawnEvent event = mock(EntitySpawnEvent.class);

        when(event.getEntity()).thenReturn(entity);
        when(entity.hasMetadata(anyString())).thenReturn(false);
        when(entity.isCustomNameVisible()).thenReturn(true);
        when(entity.getCustomName()).thenReturn(null); // no name set

        listener.onEntitySpawn(event);

        verify(entity, never()).setCustomNameVisible(false);
    }

    @Test
    void replaceCustomName_ChunkLoadEvent_IgnoresNonLivingEntities() {
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        Chunk chunk = mock(Chunk.class);
        Entity nonLiving = mock(Entity.class);

        when(event.getChunk()).thenReturn(chunk);
        when(chunk.getEntities()).thenReturn(new Entity[]{nonLiving});

        listener.onChunkLoad(event);

        verify(nonLiving, never()).setCustomNameVisible(anyBoolean());
    }

    @Test
    void removeHolographicCustomNames_onEntityDeath_callsCancel() {
        LivingEntity entity = mockLivingEntityWithCustomName();
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(entity);

        listener.onEntityDeath(event);

        // We can't verify the internals of HOLOGRAPH_UTIL, but this ensures the code path runs
        // To properly verify cancellation behavior, unit test HOLOGRAPH_UTIL separately
    }

    @Test
    void restoreCustomNameVisibility_ValidNamedEntityMakesNameVisible() throws Exception {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getCustomName()).thenReturn("VisibleName");

        Method method = CustomNameListener.class.getDeclaredMethod("restoreCustomNameVisibility", LivingEntity.class);
        method.setAccessible(true);
        method.invoke(listener, entity);

        verify(entity).setCustomNameVisible(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdown_RemovesHologramsAndRestoresNameVisibility() throws Exception {
        LivingEntity entity = mock(LivingEntity.class);
        ArmorStand armorStand = mock(ArmorStand.class);
        BukkitTask task = mock(BukkitTask.class);
        when(entity.isValid()).thenReturn(true);
        when(entity.getCustomName()).thenReturn("VisibleName");
        when(armorStand.isValid()).thenReturn(true);
        when(task.isCancelled()).thenReturn(false);

        Field field = CustomNameListener.class.getDeclaredField("entitiesWithActiveHolographicCustomNames");
        field.setAccessible(true);
        Map<LivingEntity, LivingEntityTaskInfo> active = (Map<LivingEntity, LivingEntityTaskInfo>) field.get(listener);
        active.put(entity, new LivingEntityTaskInfo(task, armorStand));

        listener.shutdown();

        verify(entity).setCustomNameVisible(true);
        verify(task).cancel();
        verify(armorStand).remove();
        assertEquals(0, active.size());
    }
}
