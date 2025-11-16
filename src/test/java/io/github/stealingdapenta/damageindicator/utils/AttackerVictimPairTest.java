package io.github.stealingdapenta.damageindicator.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

class AttackerVictimPairTest {

    @Test
    void testEquals_SameAttackerAndVictim_ReturnsTrue() {
        UUID attackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        Entity attacker = mock(Player.class);
        Entity victim = mock(Zombie.class);
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(victim.getUniqueId()).thenReturn(victimId);

        AttackerVictimPair pair1 = new AttackerVictimPair(attacker, victim);
        AttackerVictimPair pair2 = new AttackerVictimPair(attacker, victim);

        assertEquals(pair1, pair2);
    }

    @Test
    void testEquals_DifferentAttacker_ReturnsFalse() {
        UUID attacker1Id = UUID.randomUUID();
        UUID attacker2Id = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        Entity attacker1 = mock(Player.class);
        Entity attacker2 = mock(Player.class);
        Entity victim = mock(Zombie.class);
        when(attacker1.getUniqueId()).thenReturn(attacker1Id);
        when(attacker2.getUniqueId()).thenReturn(attacker2Id);
        when(victim.getUniqueId()).thenReturn(victimId);

        AttackerVictimPair pair1 = new AttackerVictimPair(attacker1, victim);
        AttackerVictimPair pair2 = new AttackerVictimPair(attacker2, victim);

        assertNotEquals(pair1, pair2);
    }

    @Test
    void testEquals_DifferentVictim_ReturnsFalse() {
        UUID attackerId = UUID.randomUUID();
        UUID victim1Id = UUID.randomUUID();
        UUID victim2Id = UUID.randomUUID();

        Entity attacker = mock(Player.class);
        Entity victim1 = mock(Zombie.class);
        Entity victim2 = mock(Zombie.class);
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(victim1.getUniqueId()).thenReturn(victim1Id);
        when(victim2.getUniqueId()).thenReturn(victim2Id);

        AttackerVictimPair pair1 = new AttackerVictimPair(attacker, victim1);
        AttackerVictimPair pair2 = new AttackerVictimPair(attacker, victim2);

        assertNotEquals(pair1, pair2);
    }

    @Test
    void testEquals_NullAttacker_HandlesCorrectly() {
        UUID victimId = UUID.randomUUID();

        Entity victim = mock(Zombie.class);
        when(victim.getUniqueId()).thenReturn(victimId);

        AttackerVictimPair pair1 = new AttackerVictimPair(null, victim);
        AttackerVictimPair pair2 = new AttackerVictimPair(null, victim);

        assertEquals(pair1, pair2);
    }

    @Test
    void testEquals_OneNullAttacker_ReturnsFalse() {
        UUID attackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        Entity attacker = mock(Player.class);
        Entity victim = mock(Zombie.class);
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(victim.getUniqueId()).thenReturn(victimId);

        AttackerVictimPair pair1 = new AttackerVictimPair(attacker, victim);
        AttackerVictimPair pair2 = new AttackerVictimPair(null, victim);

        assertNotEquals(pair1, pair2);
    }

    @Test
    void testHashCode_SamePairs_ReturnsSameHash() {
        UUID attackerId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        Entity attacker = mock(Player.class);
        Entity victim = mock(Zombie.class);
        when(attacker.getUniqueId()).thenReturn(attackerId);
        when(victim.getUniqueId()).thenReturn(victimId);

        AttackerVictimPair pair1 = new AttackerVictimPair(attacker, victim);
        AttackerVictimPair pair2 = new AttackerVictimPair(attacker, victim);

        assertEquals(pair1.hashCode(), pair2.hashCode());
    }

    @Test
    void testHashCode_DifferentPairs_ReturnsDifferentHash() {
        UUID attacker1Id = UUID.randomUUID();
        UUID attacker2Id = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();

        Entity attacker1 = mock(Player.class);
        Entity attacker2 = mock(Player.class);
        Entity victim = mock(Zombie.class);
        when(attacker1.getUniqueId()).thenReturn(attacker1Id);
        when(attacker2.getUniqueId()).thenReturn(attacker2Id);
        when(victim.getUniqueId()).thenReturn(victimId);

        AttackerVictimPair pair1 = new AttackerVictimPair(attacker1, victim);
        AttackerVictimPair pair2 = new AttackerVictimPair(attacker2, victim);

        // Hash codes should be different (not guaranteed by contract, but highly likely)
        assertNotEquals(pair1.hashCode(), pair2.hashCode());
    }
}

