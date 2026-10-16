package io.github.stealingdapenta.damageindicator.command;

import static io.github.stealingdapenta.damageindicator.utils.HolographUtil.HOLOGRAPH_UTIL;
import static io.github.stealingdapenta.damageindicator.utils.TextUtil.TEXT_UTIL;

import io.github.stealingdapenta.damageindicator.config.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AreaRemoveCommand implements CommandExecutor {

    private static final int RADIUS = 50;
    private static final String NO_PERMISSION_MSG = "You don't have the required %s to execute this command.";
    private static final String REMOVED_MSG = "Successfully removed %s nearby entities.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission(Permission.AREA_REMOVE.getNode())) {
            player.sendMessage(TEXT_UTIL.serializeComponent(Component.text(NO_PERMISSION_MSG.formatted(Permission.AREA_REMOVE.getNode()))));
            return true;
        }

        int removedCount = 0;
        for (Entity entity : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (entity instanceof ArmorStand stand && stand.isValid() && HOLOGRAPH_UTIL.isTaggedHologram(stand)) {
                HOLOGRAPH_UTIL.cleanup(stand);
                removedCount++;
            }
        }

        player.sendMessage(TEXT_UTIL.serializeComponent(Component.text(REMOVED_MSG.formatted(removedCount))));
        return true;
    }
}
