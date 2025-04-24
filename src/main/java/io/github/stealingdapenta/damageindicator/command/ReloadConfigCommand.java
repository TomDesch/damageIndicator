package io.github.stealingdapenta.damageindicator.command;

import io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.config.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadConfigCommand implements CommandExecutor {

    private static final String NO_PERMISSION_MSG = "You don't have the required %s to execute this command.";
    private static final String RELOADED_MSG = "Successfully reloaded the DamageIndicator configuration file.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {

        if (!hasPermissionOrWarn(sender, Permission.RELOAD.getNode())) {
            return true;
        }

        ConfigurationFileManager.getInstance()
                                .reloadConfig();
        sender.sendMessage(Component.text(RELOADED_MSG));
        return true;
    }

    private boolean hasPermissionOrWarn(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }

        sender.sendMessage(Component.text(NO_PERMISSION_MSG.formatted(permission)));
        return false;
    }
}
