package io.github.stealingdapenta.damageindicator.listener;

import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

public class LivingEntityTaskInfo {
    private final BukkitTask task;
    private final ArmorStand armorStand;

    public LivingEntityTaskInfo(BukkitTask task, ArmorStand armorStand) {
        this.task = task;
        this.armorStand = armorStand;
    }

    public BukkitTask getTask() {
        return task;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

}
