package me.luisgamedev.betterhorses.listeners;

import me.chancesd.pvpmanager.PvPManager;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.horse.HorseManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class RightClickListener implements Listener {
    private HashSet<UUID> clickedThisTick = new HashSet<>();
    private HashMap<UUID, Integer> cooldowns = new HashMap<>();

    public RightClickListener() {
        Bukkit.getScheduler().runTaskTimer(BetterHorses.getInstance(), () -> {
            clickedThisTick.clear();
            cooldowns.entrySet().removeIf(entry -> Bukkit.getCurrentTick() > entry.getValue());
        }, 20, 1);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        LanguageManager lang = BetterHorses.getInstance().getLang();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.allow-rightclick-spawn")) return;

        String configuredItem = config.getString("settings.horse-item", "SADDLE");
        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (!item.hasItemMeta() || item.getType() != expectedMaterial) return;

        if (!player.hasPermission(PermissionUtils.SPAWN_RIGHT_CLICK)) {
            event.setCancelled(true);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        TrainingManager.ensureTrainingData(meta.getPersistentDataContainer());
        item.setItemMeta(meta);

        boolean hasStoredChest = meta.getPersistentDataContainer().has(BetterHorseKeys.CHEST_CONTENTS, PersistentDataType.STRING);
        Double health = meta.getPersistentDataContainer().get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double speed = meta.getPersistentDataContainer().get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = meta.getPersistentDataContainer().get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String mountTypeName = meta.getPersistentDataContainer().get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);
        String mountName = mountType.getDisplayName(lang, player);

        if (clickedThisTick.contains(player.getUniqueId())) return;
        clickedThisTick.add(player.getUniqueId());

        if (PvPManager.getInstance().getPlayerManager().get(player).getEnemies().size() > 0) {
            lang.send(player, "messages.in-pvp");
            return;
        }

        if (player.isInsideVehicle()) {
            lang.send(player, "messages.already-mounted");
            return;
        }

        Long cooldownMs = HorseManager.getInstance().getRemainingMsToSummon(item);
        if (cooldownMs > 0) {
            lang.sendFormatted(player, "messages.horse-in-cooldown", "%time%", formatTime(cooldownMs));
            return;
        }

        if (health == null || speed == null || jump == null || !mountType.isEnabled(config)) {
            lang.sendFormatted(player, "messages.invalid-horse-data", "%mount%", mountName);
            return;
        }

        if (cooldowns.containsKey(player.getUniqueId())) {
            lang.send(player, "messages.cooldown");
            return;
        }

        cooldowns.put(player.getUniqueId(), Bukkit.getCurrentTick() + 3 * 20);

        AbstractHorse horse = BetterHorsesAPI.toHorse(item, player);
        if (horse == null) {
            if (hasStoredChest) {
                lang.sendFormatted(player, "messages.cant-spawn-chested", "%mount%", mountName);
            } else {
                lang.send(player, "messages.cant-spawn");
            }
            return;
        }

        BetterHorsesAPI.callSpawnEvent(horse, item.clone(), BetterHorseSpawnEvent.SpawnCause.ITEM);
        if (!horse.getPersistentDataContainer().has(BetterHorseKeys.UNDEAD_SKELETON, PersistentDataType.BYTE)) {
            TrainingManager.recalculateAndApplyBonuses(horse);
        }

        //lang.sendFormatted(player, "messages.horse-respawned", "%mount%", mountName);
    }

    private Object formatTime(Long cooldownMs) {
        long totalSeconds = cooldownMs / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours).append(" saat");
        }

        if (minutes > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(minutes).append(" dakika");
        }

        if (seconds > 0) {
            if (result.length() > 0) result.append(" ");
            result.append(seconds).append(" saniye");
        }

        return result.length() > 0 ? result.toString() : "0 saniye";
    }
}
