package io.github.stealingdapenta.damageindicator.command;

import io.github.stealingdapenta.damageindicator.ConfigurationFileManager;
import io.github.stealingdapenta.damageindicator.DefaultConfigValue;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ReloadConfigCommand implements CommandExecutor {

    private static final String NO_PERMISSION = "You don't have the required %s to execute this command.";
    private static final String RELOADED = "Successfully reloaded the DamageIndicator configuration file.";
    private final ConfigurationFileManager cfm = ConfigurationFileManager.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {


        String permissionNode = cfm.getStringValue(DefaultConfigValue.RELOAD_PERMISSION);
        if (Objects.nonNull(permissionNode) && (!sender.hasPermission(permissionNode))) {
            sender.sendMessage(NO_PERMISSION.formatted(permissionNode));
            return true;
        }

        ConfigurationFileManager.getInstance().reloadConfig();
        sender.sendMessage(RELOADED);
        return true;
    }

}
