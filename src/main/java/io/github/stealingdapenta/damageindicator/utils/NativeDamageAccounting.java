package io.github.stealingdapenta.damageindicator.utils;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.tag.DamageTypeTags;

/** Accounts for the different points at which Spigot and Paper apply hurt immunity. */
public final class NativeDamageAccounting {

    private static final NamespacedKey BYPASSES_COOLDOWN = NamespacedKey.minecraft("bypasses_cooldown");
    private static final DamageModifier IMMUNITY_REDUCTION = Arrays.stream(DamageModifier.values())
            .filter(modifier -> modifier.name().equals("INVULNERABILITY_REDUCTION"))
            .findFirst()
            .orElse(null);

    private NativeDamageAccounting() {
    }

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

        if (!immunityIncluded && victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2.0
                && !bypassesCooldown(event)) {
            // Spigot 26.2 compares these pre-armour modifiers before subtracting lastHurt.
            float comparedDamage = (float) event.getDamage(DamageModifier.BASE);
            comparedDamage += (float) event.getDamage(DamageModifier.FREEZING);
            comparedDamage += (float) event.getDamage(DamageModifier.HARD_HAT);
            comparedDamage += (float) event.getDamage(DamageModifier.BLOCKING);
            float previousDamage = (float) victim.getLastDamage();
            if (comparedDamage <= previousDamage) {
                return 0;
            }
            damage = (float) damage - previousDamage;
        }
        return Math.max(0, Math.min(damage, victim.getHealth()));
    }

    private static boolean bypassesCooldown(EntityDamageEvent event) {
        Tag<DamageType> tag = Bukkit.getTag(DamageTypeTags.REGISTRY_DAMAGE_TYPES, BYPASSES_COOLDOWN, DamageType.class);
        return tag != null && tag.isTagged(event.getDamageSource().getDamageType());
    }
}
