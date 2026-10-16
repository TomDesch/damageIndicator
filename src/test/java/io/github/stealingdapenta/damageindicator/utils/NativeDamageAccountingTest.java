package io.github.stealingdapenta.damageindicator.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

class NativeDamageAccountingTest {

    @BeforeAll
    static void initializeRegistries() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getRegistry(any())).thenAnswer(invocation ->
                    new TestRegistry<>(invocation.getArgument(0)));
            Objects.requireNonNull(Registry.DAMAGE_TYPE);
        }
    }

    private final LivingEntity victim = mock(LivingEntity.class);
    private final EntityDamageEvent event = mock(EntityDamageEvent.class);

    @BeforeEach
    void setUp() {
        when(victim.getHealth()).thenReturn(20.0);
        when(victim.getMaximumNoDamageTicks()).thenReturn(20);
    }

    @Test
    void ordinaryDamageUsesFinalDamageAfterArmourAndAbsorption() {
        when(event.getDamage(DamageModifier.BASE)).thenReturn(10.0);
        when(event.getFinalDamage()).thenReturn(2.5);

        assertEquals(2.5, NativeDamageAccounting.appliedDamage(event, victim));
    }

    @Test
    void spigotEqualOrSmallerHitsDuringImmunityDoNotDisplayDamage() {
        when(victim.getNoDamageTicks()).thenReturn(20);
        when(victim.getLastDamage()).thenReturn(4.0);
        when(victim.getHealth()).thenReturn(16.0);
        try (var bukkit = mockStatic(Bukkit.class)) {
            for (double incoming : new double[] {2, 4}) {
                when(event.getDamage(DamageModifier.BASE)).thenReturn(incoming);
                when(event.getFinalDamage()).thenReturn(incoming);
                assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim, false));
            }
        }
    }

    @Test
    void spigotStrongerHitOnlyCountsTheAdditionalDamage() {
        when(victim.getNoDamageTicks()).thenReturn(20);
        when(victim.getLastDamage()).thenReturn(4.0);
        when(victim.getHealth()).thenReturn(16.0);
        when(event.getDamage(DamageModifier.BASE)).thenReturn(6.0);
        when(event.getFinalDamage()).thenReturn(6.0);

        try (var bukkit = mockStatic(Bukkit.class)) {
            assertEquals(2, NativeDamageAccounting.appliedDamage(event, victim, false));
        }
    }

    @Test
    void spigotImmunityComparisonIncludesBlockingFreezingAndHelmetModifiers() {
        when(victim.getNoDamageTicks()).thenReturn(20);
        when(victim.getLastDamage()).thenReturn(4.0);
        when(event.getDamage(DamageModifier.BASE)).thenReturn(10.0);
        when(event.getDamage(DamageModifier.FREEZING)).thenReturn(2.0);
        when(event.getDamage(DamageModifier.HARD_HAT)).thenReturn(-3.0);
        when(event.getDamage(DamageModifier.BLOCKING)).thenReturn(-5.0);
        when(event.getFinalDamage()).thenReturn(3.0);

        try (var bukkit = mockStatic(Bukkit.class)) {
            assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim, false));
        }
    }

    @Test
    void paperFinalDamageIsNotReducedTwice() {
        when(victim.getNoDamageTicks()).thenReturn(20);
        when(victim.getLastDamage()).thenReturn(4.0);
        when(event.getDamage(DamageModifier.BASE)).thenReturn(6.0);
        when(event.getFinalDamage()).thenReturn(2.0);

        assertEquals(2, NativeDamageAccounting.appliedDamage(event, victim, true));
    }

    @Test
    void immunityEndsAtHalfTheMaximumWindow() {
        when(victim.getNoDamageTicks()).thenReturn(10);
        when(victim.getLastDamage()).thenReturn(4.0);
        when(event.getFinalDamage()).thenReturn(4.0);

        assertEquals(4, NativeDamageAccounting.appliedDamage(event, victim, false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void datapackDamageTypesCanBypassCooldownAndTagReloadsAreObserved() {
        when(victim.getNoDamageTicks()).thenReturn(20);
        when(victim.getLastDamage()).thenReturn(4.0);
        when(event.getDamage(DamageModifier.BASE)).thenReturn(4.0);
        when(event.getFinalDamage()).thenReturn(4.0);
        DamageSource source = mock(DamageSource.class);
        DamageType type = mock(DamageType.class);
        when(event.getDamageSource()).thenReturn(source);
        when(source.getDamageType()).thenReturn(type);

        try (var bukkit = mockStatic(Bukkit.class)) {
            Tag<DamageType> tag = mock(Tag.class);
            when(tag.isTagged(type)).thenReturn(true, false);
            bukkit.when(() -> Bukkit.getTag(eq("damage_types"), any(NamespacedKey.class), eq(DamageType.class)))
                    .thenReturn(tag);
            assertEquals(4, NativeDamageAccounting.appliedDamage(event, victim, false));
            assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim, false));
        }
    }

    @Test
    void cancelledBlockedAndInvalidDamageDoNotDisplay() {
        when(event.isCancelled()).thenReturn(true);
        when(event.getFinalDamage()).thenReturn(4.0);
        assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim));
        when(event.isCancelled()).thenReturn(false);
        for (double damage : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            when(event.getFinalDamage()).thenReturn(damage);
            assertEquals(0, NativeDamageAccounting.appliedDamage(event, victim));
        }
    }

    @Test
    void lethalDamageIsCappedToRemainingHealth() {
        when(victim.getHealth()).thenReturn(3.0);
        when(event.getFinalDamage()).thenReturn(100.0);

        assertEquals(3, NativeDamageAccounting.appliedDamage(event, victim));
    }

    private static final class TestRegistry<T extends Keyed> implements Registry<T> {
        private final Class<T> type;
        private final Map<NamespacedKey, T> entries = new HashMap<>();

        private TestRegistry(Class<T> type) {
            this.type = type;
        }

        @Override
        public T get(NamespacedKey key) {
            return entries.computeIfAbsent(key, ignored -> mock(type));
        }

        @Override
        public T getOrThrow(NamespacedKey key) {
            return get(key);
        }

        @Override
        public Stream<T> stream() {
            return entries.values().stream();
        }

        @Override
        public Iterator<T> iterator() {
            return entries.values().iterator();
        }
    }
}
