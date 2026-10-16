package io.github.stealingdapenta.damageindicator.command;

import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;

import io.github.stealingdapenta.damageindicator.DamageIndicator;
import io.github.stealingdapenta.damageindicator.config.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.util.function.Consumer;
import io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener;
import org.jetbrains.annotations.NotNull;

public class AreaRenameCommand implements CommandExecutor {

    private static final int RADIUS = 50;
    private static final String NO_PERMISSION_MSG = "You don't have the required %s to execute this command.";
    private static final String REMOVED_MSG = "Successfully renamed %s nearby entities.";
    private final Consumer<LivingEntity> clearDisplays;

    public AreaRenameCommand() {
        this(entity -> DamageIndicator.getInstance().clearNameDisplays(entity));
    }

    AreaRenameCommand(Consumer<LivingEntity> clearDisplays) {
        this.clearDisplays = clearDisplays;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission(Permission.AREA_RENAME.getNode())) {
            player.sendMessage(TEXT_UTIL.serializeComponent(Component.text(NO_PERMISSION_MSG.formatted(Permission.AREA_RENAME.getNode()))));
            return true;
        }

        int removedCount = 0;
        for (Entity entity : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (!entity.isValid()) {
                continue;
            }
            boolean hadName = entity.getCustomName() != null;
            if (entity instanceof ArmorStand stand && Boolean.TRUE.equals(stand.getPersistentDataContainer()
                    .get(DamageIndicatorListener.getCustomNamespacedKey(), PersistentDataType.BOOLEAN))) {
                HOLOGRAPH_UTIL.cleanup(stand);
                if (hadName) {
                    removedCount++;
                }
                continue;
            }
            if (entity instanceof LivingEntity living) {
                clearDisplays.accept(living);
            }
            if (hadName || entity.getCustomName() != null) {
                entity.setCustomName(null);
                entity.setCustomNameVisible(false);
                removedCount++;
            }
        }

        player.sendMessage(TEXT_UTIL.serializeComponent(Component.text(REMOVED_MSG.formatted(removedCount))));
        return true;
    }
}
