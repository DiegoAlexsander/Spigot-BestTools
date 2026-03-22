package de.jeff_media.BestTools;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ConfigUpdater {

    final Main main;

    // Root-level ConfigurationSection keys whose sub-keys must be preserved
    private static final String[] SECTION_ROOTS = {"mysql", "redis"};

    public ConfigUpdater(Main main) {
        this.main = main;
    }

    // Admins hate config updates. Just relax and let BestTools update to the newest
    // config version. Don't worry! Your changes will be kept

    void updateConfig() {

        try {
            Files.deleteIfExists(new File(main.getDataFolder().getAbsolutePath() + File.separator + "config.old.yml").toPath());
        } catch (IOException ignored) {

        }

        if (main.debug)
            main.getLogger().info("rename config.yml -> config.old.yml");
        FileUtils.renameFileInPluginDir(main,"config.yml", "config.old.yml");
        if (main.debug)
            main.getLogger().info("saving new config.yml");
        main.saveDefaultConfig();

        File oldConfigFile = new File(main.getDataFolder().getAbsolutePath() + File.separator + "config.old.yml");
        FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile);

        try {
            oldConfig.load(oldConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        Map<String, Object> oldValues = oldConfig.getValues(false);

        // Read default config to keep comments
        ArrayList<String> linesInDefaultConfig = new ArrayList<>();
        try {

            Scanner scanner = new Scanner(
                    new File(main.getDataFolder().getAbsolutePath() + File.separator + "config.yml"), "UTF-8");
            while (scanner.hasNextLine()) {
                linesInDefaultConfig.add(scanner.nextLine() + "");
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        ArrayList<String> newLines = new ArrayList<>();
        List<String> sectionStack = new ArrayList<>();

        for (String line : linesInDefaultConfig) {
            // --- Track section nesting for mysql/redis sub-keys ---
            String sectionPath = resolveSectionPath(line, sectionStack);
            if (sectionPath != null && oldConfig.isSet(sectionPath)) {
                Object val = oldConfig.get(sectionPath);
                if (val != null && !(val instanceof ConfigurationSection)) {
                    int indent = 0;
                    for (int i = 0; i < line.length(); i++) {
                        if (line.charAt(i) == ' ') indent++;
                        else break;
                    }
                    String prefix = line.substring(0, indent);
                    String key = sectionPath.contains(".")
                            ? sectionPath.substring(sectionPath.lastIndexOf('.') + 1)
                            : sectionPath;

                    // Handle list values (e.g. sentinel.nodes)
                    if (val instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) val;
                        if (list.isEmpty()) {
                            newLines.add(prefix + key + ": []");
                        } else {
                            newLines.add(prefix + key + ":");
                            for (Object item : list) {
                                newLines.add(prefix + "- " + item);
                            }
                        }
                        continue;
                    }

                    String strVal = val.toString();
                    if (val instanceof String) {
                        String s = (String) val;
                        if (s.isEmpty() || s.contains(":") || s.contains("#")
                                || s.contains("{") || s.contains("}")
                                || s.contains("[") || s.contains("]")
                                || s.contains(",") || s.contains("'")
                                || s.contains("\"") || s.contains("&")
                                || s.contains("*") || s.contains("!")
                                || s.contains("|") || s.contains(">")
                                || s.contains("%") || s.contains("@")
                                || s.startsWith(" ") || s.endsWith(" ")) {
                            strVal = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                        }
                    }
                    newLines.add(prefix + key + ": " + strVal);
                    continue;
                }
            }

            String newline = line;
            if (line.startsWith("config-version:")) {

            }

            else if (line.startsWith("global-block-blacklist:")) {
                newline = null;
                newLines.add("global-block-blacklist:");
                if (main.toolHandler != null && main.toolHandler.globalBlacklist != null) {
                    for (Material disabledBlock : main.toolHandler.globalBlacklist) {
                        newLines.add("- " + disabledBlock.name());
                    }
                }
            }

            else {
                for (String node : oldValues.keySet()) {
                    if (line.startsWith(node + ":")) {
                        // Skip ConfigurationSection values
                        Object rawValue = oldValues.get(node);
                        if (rawValue instanceof ConfigurationSection) {
                            break;
                        }

                        String quotes = "";

                        if (node.startsWith("message-")) // needs double quotes
                            quotes = "\"";

                        if (node.startsWith("gui-"))
                            quotes = "\"";

                        newline = node + ": " + quotes + rawValue.toString() + quotes;
                        if (main.debug)
                            main.getLogger().info("Updating config node " + newline);
                        break;
                    }
                }
            }
            if (newline != null) {
                newLines.add(newline);
            } else {
                main.getLogger().warning("newline == null");
            }
        }

        BufferedWriter fw;
        String[] linesArray = newLines.toArray(new String[linesInDefaultConfig.size()]);
        try {
            fw = Files.newBufferedWriter(new File(main.getDataFolder().getAbsolutePath(), "config.yml").toPath(), StandardCharsets.UTF_8);
            for (String s : linesArray) {
                fw.write(s + "\n");
            }
            fw.close();
        } catch (IOException e) {
            main.getLogger().warning("Error while updating config file!");
            e.printStackTrace();
        }

    }

    /**
     * Tracks section nesting for mysql/redis sub-keys and returns the full
     * dotted config path if the line is a sub-key inside a tracked section.
     */
    private static String resolveSectionPath(String rawLine, List<String> sectionStack) {
        String trimmed = rawLine.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        int spaces = 0;
        for (int i = 0; i < rawLine.length(); i++) {
            if (rawLine.charAt(i) == ' ') spaces++;
            else break;
        }
        int depth = spaces / 2;

        while (sectionStack.size() > depth) {
            sectionStack.remove(sectionStack.size() - 1);
        }

        if (sectionStack.isEmpty() && depth > 0) return null;

        int colon = trimmed.indexOf(':');
        if (colon <= 0) return null;
        String key = trimmed.substring(0, colon).trim();

        if (depth == 0) {
            for (String root : SECTION_ROOTS) {
                if (key.equals(root)) {
                    sectionStack.add(key);
                    return null;
                }
            }
            sectionStack.clear();
            return null;
        }

        if (sectionStack.isEmpty()) return null;

        String afterColon = trimmed.substring(colon + 1).trim();
        boolean isSubSection = afterColon.isEmpty();

        StringBuilder path = new StringBuilder();
        for (String s : sectionStack) {
            path.append(s).append('.');
        }
        path.append(key);

        if (isSubSection) {
            sectionStack.add(key);
            return null;
        }

        return path.toString();
    }

}
