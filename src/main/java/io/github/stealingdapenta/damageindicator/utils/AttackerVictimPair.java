package io.github.stealingdapenta.damageindicator.utils;

import java.util.Objects;
import java.util.UUID;
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

