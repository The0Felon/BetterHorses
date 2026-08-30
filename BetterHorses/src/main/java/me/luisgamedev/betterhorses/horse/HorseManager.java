package me.luisgamedev.betterhorses.horse;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorse;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class HorseManager implements Listener {
    private static final HorseManager instance = new HorseManager();

    private HashMap<UUID, BetterHorse> spawnedHorses = new HashMap<>();
    private HashMap<UUID, ItemStack> horseItems = new HashMap<>();

    public static HorseManager getInstance() {
        return instance;
    }

    private HorseManager() {
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        removeHorse(player.getUniqueId());
    }

    public void removeAll() {
        var cloneSet = new HashSet<>(spawnedHorses.keySet());
        cloneSet.forEach(this::removeHorse);
        spawnedHorses.clear();
    }

    public void setHorse(UUID playerUuid, AbstractHorse horse, ItemStack horseItem) {
        var betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        if (betterHorse == null) return;

        removeHorse(playerUuid);

        spawnedHorses.put(playerUuid, betterHorse);
        horseItems.put(horse.getUniqueId(), horseItem);
    }

    private void removeHorse(UUID playerUuid) {
        var horse = spawnedHorses.remove(playerUuid);

        if (horse == null) return;

        UUID horseUuid = horse.getHandle().getUniqueId();

        updateHorseItem(horse);

        horse.getHandle().remove();

        horseItems.remove(horseUuid);
    }

    public void removeAllFromEntity() {
        Bukkit.getWorlds().forEach(world -> world.getEntities().forEach(e -> {
            if (!(e instanceof AbstractHorse horse)) return;

            var betterHorse = BetterHorsesAPI.getBetterHorse(horse);
            if (betterHorse == null) return;

            e.remove();
            updateHorseItem(betterHorse);
        }));
    }

    private void updateHorseItem(BetterHorse horse) {
        if (horse == null) {
            BetterHorses.getInstance().getLogger().warning("BetterHorse null!");
            return;
        }

        UUID handleUuid = horse.getHandle().getUniqueId();

        ItemStack item = horseItems.get(handleUuid);

        if (item == null) {
            BetterHorses.getInstance().getLogger().warning("Horse item bulunamadı!");
            return;
        }

        item.setItemMeta(BetterHorsesAPI.toItem(horse.getHandle(), null).getItemMeta());
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        var e = event.getDismounted();
        if (!(e instanceof AbstractHorse horse)) return;

        BetterHorse betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        updateHorseItem(betterHorse);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (!BetterHorsesAPI.isBetterHorse(horse)) return;

        event.setDroppedExp(0);
        event.getDrops().clear();
    }
}