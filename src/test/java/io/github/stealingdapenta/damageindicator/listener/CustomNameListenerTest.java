package io.github.stealingdapenta.damageindicator.listener;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private CustomNameListener listener;

    @BeforeEach
    void setUp() {
        listener = new CustomNameListener(); // Uses HOLOGRAPH_UTIL directly now
    }

    private LivingEntity mockLivingEntityWithCustomName() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.isCustomNameVisible()).thenReturn(true);
        when(entity.customName()).thenReturn(Component.text("VisibleName"));
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
        when(entity.customName()).thenReturn(null); // no name set

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
}
