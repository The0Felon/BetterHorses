package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HorseCreateTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("betterhorses.create")) {
            return Collections.emptyList();
        }

        int logicalArgIndex = 1;
        boolean inQuotes = false;

        // Iterate over all arguments EXCEPT the one currently being typed
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];

            if (!inQuotes) {
                // Argument 4 is Name. Check if it starts a multi-word quoted string.
                if (logicalArgIndex == 4 && (arg.startsWith("\"") && (arg.length() == 1 || !arg.endsWith("\"")))) {
                    inQuotes = true;
                }
                logicalArgIndex++;
            } else {
                if (arg.endsWith("\"")) {
                    inQuotes = false;
                }
                // logicalArgIndex does NOT increase because these words are part of the Name argument
            }
        }

        // If we are currently typing inside a quoted multi-word string, provide no suggestions
        if (inQuotes) {
            return Collections.emptyList();
        }

        List<String> suggestions = new ArrayList<>();
        FileConfiguration config = BetterHorses.getInstance().getConfig();

        switch (logicalArgIndex) {
            case 1 -> suggestions.addAll(List.of("20", "50", "100")); // Health
            case 2 -> suggestions.addAll(List.of("0.4", "0.5", "0.6", "0.7")); // Speed
            case 3 -> suggestions.addAll(List.of("0.6", "0.8", "1.0")); // Jump
            case 4 -> suggestions.add("\"Name\""); // Name
            case 5 -> { // Trait
                ConfigurationSection traits = config.getConfigurationSection("traits");
                if (traits != null) {
                    Set<String> keys = traits.getKeys(false);
                    for (String key : keys) {
                        if (!key.equalsIgnoreCase("enabled") && traits.getBoolean(key + ".enabled", false)) {
                            suggestions.add(key.toLowerCase());
                        }
                    }
                }
                suggestions.add("none");
            }
            case 6 -> suggestions.addAll(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")); // Growth Stage
            case 7 -> { // Mount Type
                for (SupportedMountType type : SupportedMountType.values()) {
                    if (type.isEnabled(config)) {
                        suggestions.add(type.getEntityType().name().toLowerCase());
                    }
                }
            }
            case 8 -> { // Target Player Name
                for (Player p : Bukkit.getOnlinePlayers()) {
                    suggestions.add(p.getName());
                }
            }
            case 9 -> { // Horse Color
                for (Horse.Color color : Horse.Color.values()) {
                    suggestions.add(color.name().toLowerCase());
                }
            }
            case 10 -> { // Horse Style
                for (Horse.Style style : Horse.Style.values()) {
                    suggestions.add(style.name().toLowerCase());
                }
            }
            default -> {
                return Collections.emptyList();
            }
        }

        return StringUtil.copyPartialMatches(args[args.length - 1], suggestions, new ArrayList<>());
    }
}