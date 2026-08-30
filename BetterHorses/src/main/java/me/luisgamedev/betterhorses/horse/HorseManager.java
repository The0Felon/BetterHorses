package me.luisgamedev.betterhorses.horse;

import me.luisgamedev.betterhorses.api.BetterHorse;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.UUID;

public class HorseManager implements Listener {
    private static final HorseManager instance = new HorseManager();

    private HashMap<UUID, BetterHorse> spawnedHorses = new HashMap<>();

    public static HorseManager getInstance() {
        return instance;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        removeHorse(player.getUniqueId());
    }

    public void removeAll() {
        spawnedHorses.forEach((uuid, horse) -> horse.getHandle().remove());
        spawnedHorses.clear();
    }

    public void setHorse(UUID playerUuid, AbstractHorse horse) {
        var betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        if (betterHorse == null) return;

        removeHorse(playerUuid);
        spawnedHorses.put(playerUuid, betterHorse);
    }

    private void removeHorse(UUID playerUuid) {
        var horse = spawnedHorses.get(playerUuid);
        if (horse == null) return;

        horse.getHandle().remove();
    }

    public void removeAllFromEntity() {
        Bukkit.getWorlds().forEach(world -> world.getEntities().forEach(e -> {
            var betterHorse = BetterHorsesAPI.isBetterHorse(e);
            if (betterHorse) e.remove();
        }));
    }
}