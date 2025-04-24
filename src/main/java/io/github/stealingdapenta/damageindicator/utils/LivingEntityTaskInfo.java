package io.github.stealingdapenta.damageindicator.utils;

import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

/**
 * Holds a Bukkit task and its associated ArmorStand used for holographic displays. Used to manage and cancel holograms per entity.
 */
public record LivingEntityTaskInfo(BukkitTask task, ArmorStand armorStand) {

}
