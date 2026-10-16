package io.github.stealingdapenta.damageindicator.utils;

import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import org.bukkit.entity.Entity;

/**
 * Represents a unique attacker-victim pair for tracking merged damage.
 */
public class AttackerVictimPair {

    private final UUID attackerId;
    private final UUID victimId;

    public AttackerVictimPair(Entity attacker, Entity victim) {
        this.attackerId = attacker != null ? attacker.getUniqueId() : null;
        this.victimId = victim.getUniqueId();
    }

    public boolean involves(UUID entityId) {
        return Objects.equals(attackerId, entityId) || Objects.equals(victimId, entityId);
    }

    public boolean involvesAny(Set<UUID> entityIds) {
        return entityIds.contains(victimId) || attackerId != null && entityIds.contains(attackerId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AttackerVictimPair that = (AttackerVictimPair) o;
        return Objects.equals(attackerId, that.attackerId) && Objects.equals(victimId, that.victimId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attackerId, victimId);
    }
}
