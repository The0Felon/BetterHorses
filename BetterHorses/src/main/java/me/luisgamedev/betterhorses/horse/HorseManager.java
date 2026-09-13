package me.luisgamedev.betterhorses.horse;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorse;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class HorseManager implements Listener {
    private static final HorseManager instance = new HorseManager();

    private HashMap<UUID, BetterHorse> spawnedHorsesByPlayer = new HashMap<>();
    private HashMap<UUID, Long> horseTimeouts = new HashMap<>();
    private HashMap<UUID, ItemStack> horseItems = new HashMap<>();
    private HashMap<UUID, UUID> playersBySpawnedHorseIds = new HashMap<>();
    private HashMap<BetterHorse, Double> speedAttributeCache = new HashMap<>();

    private NamespacedKey horseItemKey = new NamespacedKey(BetterHorses.getInstance(), "horse_uuid");
    private NamespacedKey summonableAfterUnixKey = new NamespacedKey(BetterHorses.getInstance(), "summonable_after_unix");

    public static HorseManager getInstance() {
        return instance;
    }

    public void start() {
        startHorseTimeoutTask();
    }

    private void startHorseTimeoutTask() {
        Bukkit.getScheduler().runTaskTimer(BetterHorses.getInstance(), () -> {

            var cloneSet = new HashSet<>(spawnedHorsesByPlayer.keySet());

            cloneSet.forEach(uuid -> {
                boolean mounted = !spawnedHorsesByPlayer.get(uuid).getHandle().getPassengers().isEmpty();
                if (mounted) return;

                if (Bukkit.getCurrentTick() > horseTimeouts.get(uuid))
                    removeHorse(uuid);
            });

        }, 10L, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        removeHorse(player.getUniqueId());
    }

    public void removeAll() {
        var cloneSet = new HashSet<>(spawnedHorsesByPlayer.keySet());
        cloneSet.forEach(this::removeHorse);
    }

    public void setHorse(UUID playerUuid, AbstractHorse horse, ItemStack horseItem) {
        var betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        if (betterHorse == null) return;

        removeHorse(playerUuid);

        UUID horseUuid = horse.getUniqueId();

        // 1. AT DOĞDUĞU AN EŞYAYA MÜHÜRÜ (PDC) VURUYORUZ
        ItemMeta meta = horseItem.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(horseItemKey, PersistentDataType.STRING, horseUuid.toString());
            horseItem.setItemMeta(meta);
        }

        spawnedHorsesByPlayer.put(playerUuid, betterHorse);
        playersBySpawnedHorseIds.put(horseUuid, playerUuid);
        horseItems.put(horseUuid, horseItem);

        updateTimeout(playerUuid);

        // 2. OYUNCUNUN ENVANTERİNDEKİ EŞYAYI BULUP MÜHÜRLÜ HALİYLE DEĞİŞTİRİYORUZ (İlk doğma anı garantisi)
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack invItem = inventory.getItem(i);
                if (invItem != null && invItem.hasItemMeta() && meta != null) {
                    // İsim ve tip eşleşiyorsa o an elindeki at eşyasıdır
                    if (invItem.getType() == horseItem.getType() &&
                            invItem.getItemMeta().getDisplayName().equals(meta.getDisplayName())) {
                        inventory.setItem(i, horseItem);
                        break;
                    }
                }
            }
        }
    }

    public void removeHorse(UUID playerUuid) {
        var horse = spawnedHorsesByPlayer.remove(playerUuid);

        if (horse == null) return;

        UUID horseUuid = horse.getHandle().getUniqueId();

        // DİKKAT: Burası önceki kodda silinmişti, at silindiğinde eşyanın güncellenmesi için ŞARTTIR.
        updateHorseItem(horse);

        cleanupExistingHorse(horse, playerUuid);

        horseTimeouts.remove(playerUuid);
        horseItems.remove(horseUuid);
        playersBySpawnedHorseIds.remove(horseUuid);
        speedAttributeCache.remove(horse);
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

    private void updateTimeout(UUID playerUuid) {
        horseTimeouts.put(playerUuid, Bukkit.getCurrentTick() + 5L * 60L * 20L);
    }

    private void updateHorseItem(BetterHorse horse) {
        updateHorseItem(horse, null);
    }

    private void updateHorseItem(BetterHorse horse, @Nullable Long summonableAfterUnix) {
        if (horse == null) {
            BetterHorses.getInstance().getLogger().warning("BetterHorse null!");
            return;
        }

        UUID handleUuid = horse.getHandle().getUniqueId();
        ItemStack cachedItem = horseItems.get(handleUuid);

        if (cachedItem == null) {
            BetterHorses.getInstance().getLogger().warning("Horse item bulunamadı!");
            return;
        }

        var playerUuid = playersBySpawnedHorseIds.get(handleUuid);
        var player = Bukkit.getPlayer(playerUuid);

        if (player == null) return;

        ItemStack updatedItem = BetterHorsesAPI.toItem(horse.getHandle(), player, cachedItem.getItemMeta().getDisplayName());

        ItemMeta updatedMeta = updatedItem.getItemMeta();
        if (updatedMeta != null) {
            updatedMeta.getPersistentDataContainer().set(horseItemKey, PersistentDataType.STRING, handleUuid.toString());

            if (summonableAfterUnix != null)
                updatedMeta.getPersistentDataContainer().set(summonableAfterUnixKey, PersistentDataType.LONG, summonableAfterUnix);

            updatedItem.setItemMeta(updatedMeta);
        }

        horseItems.put(handleUuid, updatedItem);

        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack invItem = inventory.getItem(i);

            if (invItem == null || !invItem.hasItemMeta()) continue;

            boolean isMatch = false;
            var pdc = invItem.getItemMeta().getPersistentDataContainer();

            // A) Güvenli Yöntem: PDC (Mühür) Taraması
            if (pdc.has(horseItemKey, PersistentDataType.STRING)) {
                String uuidStr = pdc.get(horseItemKey, PersistentDataType.STRING);
                if (handleUuid.toString().equals(uuidStr)) {
                    isMatch = true;
                }
            }
            // B) isSimilar Yerine İsim/Materyal (Fallback) Taraması
            else if (invItem.getType() == cachedItem.getType() &&
                    invItem.getItemMeta().getDisplayName().equals(cachedItem.getItemMeta().getDisplayName())) {
                isMatch = true;
            }

            if (isMatch) {
                inventory.setItem(i, updatedItem);
                break;
            }
        }
    }

    /**
     * Cleans up any horse this player is already tracked as owning before
     * we spawn a fresh one. This is the actual fix for the "kicked out /
     * bugged horse" symptom: previously a second spawn would silently
     * overwrite the HorseManager mapping and leave the first horse entity
     * alive, tamed, and ownerless in the world.
     * <p>
     * Adjust the HorseManager method names here to match your real API.
     */
    private void cleanupExistingHorse(BetterHorse horse, UUID playerUuid) {
        var handle = horse.getHandle();

        BetterHorses.getInstance().debugLog("API_CREATE_ITEM", "CLEANUP",
                true, "Removing stale tracked horse for player with uuid " + playerUuid + " before respawning.");

        if (handle.isValid()) {
            handle.eject(); // make sure nobody is still sitting on it
            handle.remove();
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        var e = event.getDismounted();
        if (!(e instanceof AbstractHorse horse)) return;

        BetterHorse betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        if (betterHorse == null) return;

        updateHorseItem(betterHorse);
        updateTimeout(event.getEntity().getUniqueId());

        var cacheSpeed = betterHorse.getSpeed();
        speedAttributeCache.put(betterHorse, cacheSpeed);

        betterHorse.setSpeed(0.06);
    }

    @EventHandler
    public void onEntityMount(EntityMountEvent event) {
        var e = event.getMount();
        if (!(e instanceof AbstractHorse horse)) return;

        BetterHorse betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        if (betterHorse == null) return;

        disableSlowMode(betterHorse);
    }

    private void disableSlowMode(BetterHorse betterHorse) {
        var cacheSpeed = speedAttributeCache.remove(betterHorse);
        if (cacheSpeed == null) return;

        betterHorse.setSpeed(cacheSpeed);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        var betterHorse = BetterHorsesAPI.getBetterHorse(horse);
        if (betterHorse == null) return;

        event.setDroppedExp(0);
        event.getDrops().clear();

        // x saniye sonra summonable.
        var summonableAfterUnix = System.currentTimeMillis() + 40 * 1000;

        updateHorseItem(betterHorse, summonableAfterUnix);
    }

    // kalan ms süresini verir (summonable olabilmesi için.) 0 ise summonable.
    public Long getRemainingMsToSummon(ItemStack horseItem) {
        return Math.max(horseItem.getItemMeta().getPersistentDataContainer().getOrDefault(summonableAfterUnixKey, PersistentDataType.LONG, 0L) - System.currentTimeMillis(), 0);
    }

    //@EventHandler
    //public void onEntityDamage(EntityDamageEvent event) {
    //    if (!(event.getEntity() instanceof AbstractHorse horse)) return;
    //    if (!BetterHorsesAPI.isBetterHorse(horse)) return;
    //    event.setCancelled(true);
    //}

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (!BetterHorsesAPI.isBetterHorse(horse)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onHorseInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) return;
        if (!BetterHorsesAPI.isBetterHorse(horse)) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();

        if (item.getType().isEdible()) {
            event.setCancelled(true);
        }
    }
}