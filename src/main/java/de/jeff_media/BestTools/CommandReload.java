package de.jeff_media.BestTools;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class CommandReload {

    static void reload(CommandSender sender, Command command, Main main) {


            if (!sender.hasPermission("besttools.reload")) {
                Messages.sendMessage(sender, main.messages.MSG_NO_PERMISSION);
                return;
            }
            main.load(true);
            Messages.sendMessage(sender, main.messages.MSG_CONFIG_RELOADED);
    }

}
