package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CustomHorseCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();

        plugin.debugLog(
                "HORSE_CREATE",
                "RECEIVED",
                true,
                "Sender=" + sender.getName() + ", args=" + args.length + "."
        );

        if (!sender.hasPermission("betterhorses.create")) {
            lang.sendFormatted(
                    sender,
                    "messages.insufficient-permission",
                    "%command%",
                    "/horsecreate"
            );

            plugin.debugLog(
                    "HORSE_CREATE",
                    "PERMISSION",
                    false,
                    "Sender " + sender.getName() + " lacks betterhorses.create."
            );

            return true;
        }

        if (args.length < 3) {
            lang.send(sender, "messages.horsecreate-usage");

            plugin.debugLog(
                    "HORSE_CREATE",
                    "USAGE",
                    false,
                    "Sender " + sender.getName() + " provided too few arguments."
            );

            return true;
        }

        Player senderPlayer = sender instanceof Player ? (Player) sender : null;

        try {
            double health = Double.parseDouble(args[0]);
            double speed = Double.parseDouble(args[1]);
            double jump = Double.parseDouble(args[2]);

            String name = lang.getRaw(senderPlayer, "messages.horse");
            int nextArgument = 3;

            if (args.length > 3) {
                // Tırnak ister args[3]'ün içinde olsun ister Bukkit tarafından kaldırılmış olsun,
                // tırnakla başlayan veya tırnak içeren durumu tespit edelim.
                boolean startsWithQuote = args[3].startsWith("\"");

                if (startsWithQuote) {
                    StringBuilder nameBuilder = new StringBuilder();
                    boolean closedQuote = false;

                    for (int i = 3; i < args.length; i++) {
                        if (nameBuilder.length() > 0) {
                            nameBuilder.append(" ");
                        }

                        nameBuilder.append(args[i]);

                        if (args[i].endsWith("\"") && (i != 3 || args[i].length() > 1)) {
                            nextArgument = i + 1;
                            closedQuote = true;
                            break;
                        }

                        nextArgument = i + 1;
                    }

                    String rawName = nameBuilder.toString().trim();
                    if (rawName.startsWith("\"")) rawName = rawName.substring(1);
                    if (rawName.endsWith("\"")) rawName = rawName.substring(0, rawName.length() - 1);
                    rawName = rawName.trim();

                    if (!rawName.isEmpty()) {
                        name = rawName;
                    }

                    if (!closedQuote) {
                        nextArgument = args.length;
                    }
                } else {
                    /*
                     * Bukkit tırnakları kendisi soyduysa, tırnaksız giren boşluklu metinleri
                     * veya tek parça Hex isimlerini burada doğru tespit ediyoruz.
                     */
                    StringBuilder nameBuilder = new StringBuilder();
                    int i = 3;

                    for (; i < args.length; i++) {
                        // Eğer sonraki argüman bilinen bir trait ise veya sayısal bir stage ise ismi bitir
                        if (isTraitOrStage(args[i], plugin.getConfig())) {
                            break;
                        }

                        if (nameBuilder.length() > 0) {
                            nameBuilder.append(" ");
                        }
                        nameBuilder.append(args[i]);
                    }

                    name = nameBuilder.toString();
                    nextArgument = i;
                }
            }

            /*
             * Remaining arguments:
             * trait, growthStage, mountType, targetPlayer, color, style
             */
            String trait = args.length > nextArgument
                    ? args[nextArgument].toLowerCase()
                    : null;

            int growthStage = args.length > nextArgument + 1
                    ? Integer.parseInt(args[nextArgument + 1])
                    : 10;

            String mountTypeArg = args.length > nextArgument + 2
                    ? args[nextArgument + 2]
                    : null;

            Player targetPlayer = senderPlayer;

            if (args.length > nextArgument + 3) {
                String targetName = args[nextArgument + 3];
                Player foundPlayer = Bukkit.getPlayerExact(targetName);

                if (foundPlayer == null) {
                    foundPlayer = Bukkit.getPlayer(targetName);
                }

                if (foundPlayer == null) {
                    lang.sendFormatted(sender, "messages.player-not-found", "%player%", targetName);
                    plugin.debugLog("HORSE_CREATE", "PLAYER_NOT_FOUND", false, "Target player '" + targetName + "' was not found or is offline.");
                    return true;
                }

                targetPlayer = foundPlayer;
            }

            if (targetPlayer == null) {
                lang.send(sender, "messages.only-players");
                plugin.debugLog("HORSE_CREATE", "PLAYER_REQUIRED", false, "Console attempted /horsecreate without a target player.");
                return true;
            }

            // Color
            String color = null;
            if (args.length > nextArgument + 4) {
                String inputColor = args[nextArgument + 4].toUpperCase();
                try {
                    Horse.Color.valueOf(inputColor);
                    color = inputColor;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid color '" + inputColor + "', defaulting to CREAMY.");
                    color = "CREAMY";
                }
            }

            // Style
            String style = null;
            if (args.length > nextArgument + 5) {
                String inputStyle = args[nextArgument + 5].toUpperCase();
                try {
                    Horse.Style.valueOf(inputStyle);
                    style = inputStyle;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid style '" + inputStyle + "', defaulting to WHITE.");
                    style = "WHITE";
                }
            }

            FileConfiguration config = plugin.getConfig();

            // Mount type
            SupportedMountType mountType = mountTypeArg == null
                    ? SupportedMountType.HORSE
                    : SupportedMountType.fromUserInput(mountTypeArg).orElse(null);

            if (mountType == null) {
                lang.sendFormatted(sender, "messages.invalid-mount-type", "%types%", getEnabledMountTypes(config));
                plugin.debugLog("HORSE_CREATE", "MOUNT_TYPE", false, "Invalid mount type input: " + mountTypeArg);
                return true;
            }

            if (!mountType.isEnabled(config)) {
                lang.send(sender, "messages.mount-type-disabled");
                plugin.debugLog("HORSE_CREATE", "MOUNT_TYPE", false, "Disabled mount type requested: " + mountType.getEntityType());
                return true;
            }

            // Trait
            String validatedTrait = null;
            if (trait != null && !trait.equalsIgnoreCase("none")) {
                if (!config.getBoolean("traits.enabled", false)) {
                    lang.send(sender, "messages.traits-disabled");
                    plugin.debugLog("HORSE_CREATE", "TRAIT", false, "Trait requested while traits are disabled.");
                    return true;
                }

                ConfigurationSection traitSection = config.getConfigurationSection("traits." + trait);
                if (traitSection == null || !traitSection.getBoolean("enabled", false)) {
                    lang.send(sender, "messages.traits-error");
                    plugin.debugLog("HORSE_CREATE", "TRAIT", false, "Invalid or disabled trait requested: " + trait);
                    return true;
                }

                validatedTrait = trait;
            }

            // Create horse item
            Inventory targetInventory = targetPlayer.getInventory();

            BetterHorsesAPI.createHorseItem(
                    health,
                    speed,
                    jump,
                    "male",
                    name,
                    targetPlayer,
                    targetInventory,
                    true,
                    validatedTrait,
                    false,
                    growthStage,
                    mountType,
                    color,
                    style
            );

            plugin.debugLog(
                    "HORSE_CREATE",
                    "COMPLETE",
                    true,
                    "Created horse item for " + targetPlayer.getName() + " with mount=" + mountType.getEntityType() + ", trait=" + validatedTrait + "."
            );

            return true;

        } catch (NumberFormatException e) {
            lang.send(sender, "messages.invalid-number-format");
            plugin.debugLog("HORSE_CREATE", "PARSE", false, "Invalid numeric argument from " + sender.getName() + ": " + e.getMessage());
            return true;
        }
    }

    /**
     * Argümanın isim mi yoksa trait/growthStage aşaması mı olduğunu anlamaya yarar.
     */
    private boolean isTraitOrStage(String arg, FileConfiguration config) {
        if (arg.equalsIgnoreCase("none")) return true;

        // Eğer bir sayıysa (örneğin growthStage = 10)
        try {
            Integer.parseInt(arg);
            return true;
        } catch (NumberFormatException ignored) {}

        // Konfigürasyonda kayıtlı bir trait ise
        ConfigurationSection traits = config.getConfigurationSection("traits");
        return traits != null && traits.contains(arg.toLowerCase());
    }

    private String getEnabledMountTypes(FileConfiguration config) {
        List<String> enabled = Arrays.stream(SupportedMountType.values())
                .filter(type -> type.isEnabled(config))
                .map(type -> type.getEntityType().name().toLowerCase())
                .collect(Collectors.toList());

        return String.join(", ", enabled);
    }
}