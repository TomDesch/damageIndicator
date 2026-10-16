package io.github.stealingdapenta.damageindicator.utils;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

/**
 * Spigot 26.2 applies hurt immunity after the event; Paper includes it in final damage. The required intermediate modifiers have no stable common replacement, so deprecated reads stay here.
 */
@SuppressWarnings("deprecation")
public final class NativeDamageAccounting {

    private static final String DAMAGE_TYPE_REGISTRY = "damage_types";
    private static final String IMMUNITY_REDUCTION_MODIFIER = "INVULNERABILITY_REDUCTION";
    private static final double IMMUNITY_WINDOW_DIVISOR = 2.0;
    private static final NamespacedKey BYPASSES_COOLDOWN = NamespacedKey.minecraft("bypasses_cooldown");
    private static final DamageModifier IMMUNITY_REDUCTION = Arrays.stream(DamageModifier.values())
                                                                   .filter(modifier -> modifier.name()
                                                                                               .equals(IMMUNITY_REDUCTION_MODIFIER))
                                                                   .findFirst()
                                                                   .orElse(null);

    private NativeDamageAccounting() {
    }

    /**
     * Returns HP lost by this hit, capped to the victim's pre-hit health.
     */
    public static double appliedDamage(EntityDamageEvent event, LivingEntity victim) {
        boolean immunityIncluded = IMMUNITY_REDUCTION != null && event.isApplicable(IMMUNITY_REDUCTION);
        return appliedDamage(event, victim, immunityIncluded);
    }

    static double appliedDamage(EntityDamageEvent event, LivingEntity victim, boolean immunityIncluded) {
        if (event.isCancelled()) {
            return 0;
        }
        double damage = event.getFinalDamage();
        if (!Double.isFinite(damage) || damage <= 0) {
            return 0;
        }

        if (requiresImmunityAdjustment(event, victim, immunityIncluded)) {
            damage = adjustForSpigotImmunity(event, victim, damage);
        }
        return Math.clamp(damage, 0, victim.getHealth());
    }

    private static boolean requiresImmunityAdjustment(EntityDamageEvent event, LivingEntity victim, boolean immunityIncluded) {
        return !immunityIncluded && isWithinImmunityWindow(victim) && !bypassesCooldown(event);
    }

    private static boolean isWithinImmunityWindow(LivingEntity victim) {
        return victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / IMMUNITY_WINDOW_DIVISOR;
    }

    private static double adjustForSpigotImmunity(EntityDamageEvent event, LivingEntity victim, double finalDamage) {
        float previousDamage = (float) victim.getLastDamage();
        if (damageBeforeDefences(event) <= previousDamage) {
            return 0;
        }
        return (float) finalDamage - previousDamage;
    }

    /**
     * Match native float arithmetic and the comparison made before armour/absorption.
     */
    private static float damageBeforeDefences(EntityDamageEvent event) {
        float damage = (float) event.getDamage();
        damage += (float) event.getDamage(DamageModifier.FREEZING);
        damage += (float) event.getDamage(DamageModifier.HARD_HAT);
        damage += (float) event.getDamage(DamageModifier.BLOCKING);
        return damage;
    }

    private static boolean bypassesCooldown(EntityDamageEvent event) {
        Tag<DamageType> tag = Bukkit.getTag(DAMAGE_TYPE_REGISTRY, BYPASSES_COOLDOWN, DamageType.class);
        return tag != null && tag.isTagged(event.getDamageSource()
                                                .getDamageType());
    }
}
