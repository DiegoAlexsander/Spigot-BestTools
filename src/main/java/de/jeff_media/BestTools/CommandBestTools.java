package de.jeff_media.BestTools;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandBestTools implements CommandExecutor, TabCompleter {

    final Main main;

    CommandBestTools(Main main) {
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        // ── Admin commands (no allow-commands restriction) ──────────
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            CommandReload.reload(sender, command, main);
            return true;
        }

        if (args.length > 0 && (args[0].equalsIgnoreCase("debug")
                || args[0].equalsIgnoreCase("performance"))) {
            CommandDebug.debug(sender, command, main, args[0]);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("resetplayersettings")) {
            if (!sender.hasPermission("besttools.admin")) {
                Messages.sendMessage(sender, main.messages.MSG_NO_PERMISSION);
                return true;
            }
            main.playerSettings.clear();
            main.incrementFingerprint();
            Messages.sendMessage(sender, main.messages.MSG_RESET_ALL_DONE);
            return true;
        }

        // ── allow-commands check (OPs bypass) ───────────────────────
        if (!main.getConfig().getBoolean("allow-commands", true)) {
            if (sender instanceof Player && !((Player) sender).isOp()) {
                Messages.sendMessage(sender, main.messages.MSG_NO_PERMISSION);
                return true;
            }
        }

        // ── Player-only from here ────────────────────────────────────
        if (!(sender instanceof Player)) {
            Messages.sendMessage(sender, main.messages.MSG_PLAYER_ONLY);
            return true;
        }

        Player p = (Player) sender;
        PlayerSetting setting = main.getPlayerSetting(p);
        setting.getBtcache().invalidated();

        if (args.length > 0 && (args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("bl"))) {
            String[] newArgs = Arrays.copyOfRange(args, 1, args.length);
            main.commandBlacklist.onCommand(sender, command, alias, newArgs);
            return true;
        }

        setting.setHasSeenBestToolsMessage(true);

        if (args.length > 0) {

            if (args[0].equalsIgnoreCase("hotbaronly") || args[0].equalsIgnoreCase("hotbar")) {
                if (setting.toggleHotbarOnly()) {
                    Messages.sendMessage(p, main.messages.MSG_HOTBAR_ONLY_ENABLED);
                } else {
                    Messages.sendMessage(p, main.messages.MSG_HOTBAR_ONLY_DISABLED);
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("settings") || args[0].equalsIgnoreCase("gui")) {
                if (main.getConfig().getBoolean("allow-gui", true)) {
                    main.guiHandler.open(p);
                } else {
                    // When GUI is disabled, fall back to toggle
                    if (main.getPlayerSetting(p).toggleBestToolsEnabled()) {
                        Messages.sendMessage(p, main.messages.MSG_BESTTOOL_ENABLED);
                    } else {
                        Messages.sendMessage(p, main.messages.MSG_BESTTOOL_DISABLED);
                    }
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("favoriteslot") || args[0].equalsIgnoreCase("fs")) {
                if (args.length < 2) {
                    // Toggle moveToFavorite
                    if (setting.toggleMoveToFavorite()) {
                        Messages.sendMessage(p, main.messages.MSG_MOVE_TO_FAVORITE_ENABLED);
                    } else {
                        Messages.sendMessage(p, main.messages.MSG_MOVE_TO_FAVORITE_DISABLED);
                    }
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[1]);
                    if (slot < 1 || slot > 9) {
                        Messages.sendMessage(p, main.messages.MSG_FAVORITESLOT_INVALID);
                        return true;
                    }
                    setting.setFavoriteSlot(slot - 1); // Store as 0-8 internally
                    Messages.sendMessage(p, String.format(main.messages.MSG_FAVORITESLOT_SET, slot));
                } catch (NumberFormatException e) {
                    Messages.sendMessage(p, main.messages.MSG_FAVORITESLOT_INVALID);
                }
                return true;
            }
        }

        // ── Default: toggle besttools ────────────────────────────────
        if (main.getPlayerSetting(p).toggleBestToolsEnabled()) {
            Messages.sendMessage(p, main.messages.MSG_BESTTOOL_ENABLED);
        } else {
            Messages.sendMessage(p, main.messages.MSG_BESTTOOL_DISABLED);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();

            // Admin subcommands
            if (sender.hasPermission("besttools.reload")) addIfStartsWith(suggestions, partial, "reload");
            if (sender.hasPermission("besttools.debug")) {
                addIfStartsWith(suggestions, partial, "debug");
                addIfStartsWith(suggestions, partial, "performance");
            }
            if (sender.hasPermission("besttools.admin")) addIfStartsWith(suggestions, partial, "resetplayersettings");

            // Regular subcommands (check allow-commands for non-OPs)
            boolean commandsAllowed = main.getConfig().getBoolean("allow-commands", true)
                    || !(sender instanceof Player) || ((Player) sender).isOp();
            if (commandsAllowed && sender.hasPermission("besttools.use")) {
                addIfStartsWith(suggestions, partial, "hotbar");
                addIfStartsWith(suggestions, partial, "bl");
                addIfStartsWith(suggestions, partial, "favoriteslot");
                addIfStartsWith(suggestions, partial, "fs");
                if (main.getConfig().getBoolean("allow-gui", true)) {
                    addIfStartsWith(suggestions, partial, "gui");
                    addIfStartsWith(suggestions, partial, "settings");
                }
            }

            return suggestions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (sub.equals("bl") || sub.equals("blacklist")) {
                for (String s : new String[]{"add", "remove", "reset", "show"}) {
                    addIfStartsWith(suggestions, partial, s);
                }
                return suggestions;
            }

            if (sub.equals("favoriteslot") || sub.equals("fs")) {
                for (int i = 1; i <= 9; i++) {
                    addIfStartsWith(suggestions, partial, String.valueOf(i));
                }
                return suggestions;
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String blSub = args[1].toLowerCase();
            String partial = args[2].toLowerCase();

            if ((sub.equals("bl") || sub.equals("blacklist"))
                    && (blSub.equals("remove") || blSub.equals("add"))) {
                // Safe check: only show blacklist materials if already cached
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    if (main.playerSettings.containsKey(p.getUniqueId())) {
                        Blacklist bl = main.getPlayerSetting(p).getBlacklist();
                        for (String mat : bl.toStringList()) {
                            addIfStartsWith(suggestions, partial, mat);
                        }
                    }
                }
                return suggestions;
            }
        }

        return Collections.emptyList();
    }

    private static void addIfStartsWith(List<String> list, String partial, String value) {
        if (value.startsWith(partial)) list.add(value);
    }
}
