package de.jeff_media.BestTools;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;


public class CommandDebug {

    static void debug(CommandSender sender, Command command, Main main, String arg) {


        if (!sender.hasPermission("besttools.debug")) {
            Messages.sendMessage(sender, main.messages.MSG_NO_PERMISSION);
            return;
        }
        if(arg.equalsIgnoreCase("debug")) {
            main.debug=!main.debug;
            if(main.debug) {
                Messages.sendMessage(sender, main.messages.MSG_DEBUG_ENABLED);
            } else {
                Messages.sendMessage(sender, main.messages.MSG_DEBUG_DISABLED);
            }
        }
        else if(arg.equals("performance")) {
            main.measurePerformance=!main.measurePerformance;
            if(main.measurePerformance) {
                Messages.sendMessage(sender, main.messages.MSG_PERFORMANCE_ENABLED);
            } else {
                Messages.sendMessage(sender, main.messages.MSG_PERFORMANCE_DISABLED);
            }
        }
    }
}
