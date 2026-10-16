package io.github.stealingdapenta.damageindicator.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.common.base.Function;
import io.github.stealingdapenta.damageindicator.support.BukkitRegistries;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real event values exercise the intentionally isolated deprecated compatibility boundary.
 */
@SuppressWarnings("deprecation")
class NativeDamageAccountingTest {

    private static final double INITIAL_HEALTH = 20;
    private static final int IMMUNITY_DURATION_TICKS = 20;

    private final LivingEntity victim = mock(LivingEntity.class);
    private final DamageSource source = mock(DamageSource.class);

    @BeforeAll
    static void initializeRegistries() {
        BukkitRegistries.initialize();
    }

    @BeforeEach
    void setUp() {
        when(victim.getHealth()).thenReturn(INITIAL_HEALTH);
        when(victim.getMaximumNoDamageTicks()).thenReturn(IMMUNITY_DURATION_TICKS);
        when(source.getDamageType()).thenReturn(DamageType.GENERIC);
    }

    @Test
    void ordinaryDamageUsesFinalDamageAfterArmourAndAbsorption() {
        EntityDamageEvent event = damage(10, Map.of(DamageModifier.ARMOR, -5.0, DamageModifier.ABSORPTION, -2.5));
        assertEquals(2.5, NativeDamageAccounting.appliedDamage(event, victim));
    }

    @Test
    void spigotEqualOrSmallerHitsDuringImmunityDoNotDisplayDamage() {
        duringImmunity(4);
        try (var ignored = mockStatic(Bukkit.class)) {
            for (double incoming : new double[]{2, 4}) {
                assertEquals(0, NativeDamageAccounting.appliedDamage(damage(incoming), victim, false));
            }
        }
    }

    @Test
    void spigotStrongerHitOnlyCountsTheAdditionalDamage() {
        duringImmunity(4);
        try (var ignored = mockStatic(Bukkit.class)) {
            assertEquals(2, NativeDamageAccounting.appliedDamage(damage(6), victim, false));
        }
    }

    @Test
    void spigotImmunityComparisonIncludesBlockingFreezingAndHelmetModifiers() {
        duringImmunity(4);
        EntityDamageEvent event = damage(10, Map.of(DamageModifier.FREEZING, 2.0, DamageModifier.HARD_HAT, -3.0, DamageModifier.BLOCKING, -5.0, DamageModifier.ARMOR, -1.0));
        try (var ignored = mockStatic(Bukkit.class)) {
            assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim, false));
        }
    }

    @Test
    void paperFinalDamageIsNotReducedTwice() {
        duringImmunity(4);
        EntityDamageEvent reduced = damage(2);
        assertEquals(2, NativeDamageAccounting.appliedDamage(reduced, victim, true));
    }

    @Test
    void immunityEndsAtHalfTheMaximumWindow() {
        when(victim.getNoDamageTicks()).thenReturn(IMMUNITY_DURATION_TICKS / 2);
        when(victim.getLastDamage()).thenReturn(4.0);
        assertEquals(4, NativeDamageAccounting.appliedDamage(damage(4), victim, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void datapackDamageTypesCanBypassCooldownAndTagReloadsAreObserved() {
        duringImmunity(4);
        EntityDamageEvent event = damage(4);
        try (var bukkit = mockStatic(Bukkit.class)) {
            Tag<DamageType> tag = mock(Tag.class);
            when(tag.isTagged(DamageType.GENERIC)).thenReturn(true, false);
            bukkit.when(() -> Bukkit.getTag(eq("damage_types"), any(NamespacedKey.class), eq(DamageType.class)))
                  .thenReturn(tag);
            assertEquals(4, NativeDamageAccounting.appliedDamage(event, victim, false));
            assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim, false));
        }
    }

    @Test
    void cancelledBlockedAndInvalidDamageDoNotDisplay() {
        EntityDamageEvent cancelled = damage(4);
        cancelled.setCancelled(true);
        assertEquals(0, NativeDamageAccounting.appliedDamage(cancelled, victim));
        for (double amount : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0, NativeDamageAccounting.appliedDamage(damage(amount), victim));
        }
    }

    @Test
    void lethalDamageIsCappedToRemainingHealth() {
        when(victim.getHealth()).thenReturn(3.0);
        assertEquals(3, NativeDamageAccounting.appliedDamage(damage(100), victim));
    }

    private void duringImmunity(double previousDamage) {
        when(victim.getNoDamageTicks()).thenReturn(IMMUNITY_DURATION_TICKS);
        when(victim.getLastDamage()).thenReturn(previousDamage);
        when(victim.getHealth()).thenReturn(INITIAL_HEALTH - previousDamage);
    }

    private EntityDamageEvent damage(double amount) {
        return new EntityDamageEvent(victim, DamageCause.CUSTOM, source, amount);
    }

    private EntityDamageEvent damage(double baseDamage, Map<DamageModifier, Double> reductions) {
        Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
        modifiers.put(DamageModifier.BASE, baseDamage);
        modifiers.putAll(reductions);
        Map<DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(DamageModifier.class);
        modifiers.forEach((modifier, value) -> functions.put(modifier, ignored -> value));
        return new EntityDamageEvent(victim, DamageCause.CUSTOM, source, modifiers, functions);
    }
}
