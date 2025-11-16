package io.github.stealingdapenta.damageindicator.utils;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tracks information about merged damage for a specific attacker-victim pair.
 */
public class MergedDamageInfo {

    private final ArmorStand armorStand;
    private double totalDamage;
    private TextColor latestColor;
    private BukkitTask timeoutTask;
    private BukkitTask followTask;
    private long lastDamageTime;

    public MergedDamageInfo(double initialDamage, TextColor color, ArmorStand armorStand) {
        this.totalDamage = initialDamage;
        this.latestColor = color;
        this.armorStand = armorStand;
        this.lastDamageTime = System.currentTimeMillis();
    }

    public void addDamage(double damage) {
        this.totalDamage += damage;
        this.lastDamageTime = System.currentTimeMillis();
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public TextColor getLatestColor() {
        return latestColor;
    }

    public void setLatestColor(TextColor color) {
        this.latestColor = color;
    }

    public ArmorStand getArmorStand() {
        return armorStand;
    }

    public BukkitTask getTimeoutTask() {
        return timeoutTask;
    }

    public void setTimeoutTask(BukkitTask task) {
        if (this.timeoutTask != null && !this.timeoutTask.isCancelled()) {
            this.timeoutTask.cancel();
        }
        this.timeoutTask = task;
    }

    public BukkitTask getFollowTask() {
        return followTask;
    }

    public void setFollowTask(BukkitTask task) {
        if (this.followTask != null && !this.followTask.isCancelled()) {
            this.followTask.cancel();
        }
        this.followTask = task;
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public void cleanup() {
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        if (followTask != null && !followTask.isCancelled()) {
            followTask.cancel();
        }
        if (armorStand != null && armorStand.isValid()) {
            armorStand.remove();
        }
    }
}

