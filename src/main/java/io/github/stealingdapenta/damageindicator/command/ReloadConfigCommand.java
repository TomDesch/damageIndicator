package io.github.stealingdapenta.damageindicator.command;

import io.github.stealingdapenta.damageindicator.config.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.config.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadConfigCommand implements CommandExecutor {

    private static final String NO_PERMISSION = "You don't have the required %s to execute this command.";
    private static final String RELOADED = "Successfully reloaded the DamageIndicator configuration file.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {

        if (!sender.hasPermission(Permission.RELOAD.getNode())) {
            sender.sendMessage(NO_PERMISSION.formatted(Permission.RELOAD.getNode()));
            return true;
        }

        ConfigurationFileManager.getInstance()
                                .reloadConfig();
        sender.sendMessage(RELOADED);
        return true;
    }
}
