package io.github.stealingdapenta.damageindicator.command;

import static io.github.stealingdapenta.damageindicator.listener.DamageIndicatorListener.getCustomNamespacedKey;

import io.github.stealingdapenta.damageindicator.config.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class AreaRemoveCommand implements CommandExecutor {

    private static final String NO_PERMISSION = "You don't have the required %s to execute this command.";
    private static final String RELOADED = "Successfully removed %s nearby entities.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!sender.hasPermission(Permission.RELOAD.getNode())) {
            sender.sendMessage(NO_PERMISSION.formatted(Permission.RELOAD.getNode()));
            return true;
        }

        int counter = player.getNearbyEntities(50, 50, 50)
                            .stream()
                            .filter(entity -> Boolean.TRUE.equals(entity.getPersistentDataContainer()
                                                                        .getOrDefault(getCustomNamespacedKey(), PersistentDataType.BOOLEAN, false)))
                            .mapToInt(entity -> {
                                entity.remove();
                                return 1;
                            })
                            .sum();

        sender.sendMessage(RELOADED.formatted(counter));

        return true;
    }
}
