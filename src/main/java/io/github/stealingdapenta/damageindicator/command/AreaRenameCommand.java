package io.github.stealingdapenta.damageindicator.command;

import io.github.stealingdapenta.damageindicator.config.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AreaRenameCommand implements CommandExecutor {

    private static final int RADIUS = 50;
    private static final String NO_PERMISSION_MSG = "You don't have the required %s to execute this command.";
    private static final String REMOVED_MSG = "Successfully renamed %s nearby entities.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission(Permission.AREA_RENAME.getNode())) {
            player.sendMessage(Component.text(NO_PERMISSION_MSG.formatted(Permission.AREA_RENAME.getNode())));
            return true;
        }

        int removedCount = 0;
        for (Entity entity : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (entity.customName() != null) {
                entity.customName(null); // Renaming to null effectively removes the name
                entity.setCustomNameVisible(false);
                removedCount++;
            }
        }

        player.sendMessage(Component.text(REMOVED_MSG.formatted(removedCount)));
        return true;
    }
}
