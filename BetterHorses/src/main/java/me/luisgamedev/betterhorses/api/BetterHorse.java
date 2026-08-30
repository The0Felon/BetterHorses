package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseArmorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Wrapper around an in-world BetterHorses mount to simplify reading and
 * writing its custom stats while keeping persistent data synchronized.
 */
public final class BetterHorse {

    private final AbstractHorse handle;

    public BetterHorse(AbstractHorse handle) {
        this.handle = handle;
    }

    public AbstractHorse getHandle() {
        return handle;
    }

    public double getMaxHealth() {
        AttributeInstance attr = handle.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    public void setMaxHealth(double value) {
        setAttribute(AttributeResolver.generic("MAX_HEALTH"), BetterHorseKeys.HEALTH, value);
    }

    public double getSpeed() {
        AttributeInstance attr = handle.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    public void setSpeed(double value) {
        setAttribute(AttributeResolver.generic("MOVEMENT_SPEED"), BetterHorseKeys.SPEED, value);
    }

    public double getJump() {
        AttributeInstance attr = handle.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    public void setJump(double value) {
        setAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"), BetterHorseKeys.JUMP, value);
    }

    public Optional<String> getTrait() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING));
    }

    public void setTrait(String trait) {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        if (trait == null || trait.isBlank()) {
            data.remove(BetterHorseKeys.TRAIT);
        } else {
            data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, trait.toLowerCase());
        }
    }

    public Optional<Integer> getGrowthStage() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER));
    }

    public void setGrowthStage(int stage) {
        handle.getPersistentDataContainer().set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, stage);
    }

    public HorseItemTextureData getTextureData() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return new HorseItemTextureData(
                data.get(BetterHorseKeys.TEXTURE_CUSTOM_MODEL_DATA, PersistentDataType.INTEGER),
                data.get(BetterHorseKeys.TEXTURE_ITEM_MODEL, PersistentDataType.STRING),
                data.get(BetterHorseKeys.TEXTURE_CIT_STRING, PersistentDataType.STRING),
                data.get(BetterHorseKeys.TEXTURE_MODEL_STRING, PersistentDataType.STRING)
        );
    }

    public void setTextureData(HorseItemTextureData textureData) {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        HorseItemTextureData normalized = textureData == null ? HorseItemTextureData.empty() : textureData;
        setOrRemove(data, BetterHorseKeys.TEXTURE_CUSTOM_MODEL_DATA, PersistentDataType.INTEGER, normalized.getCustomModelData());
        setOrRemove(data, BetterHorseKeys.TEXTURE_ITEM_MODEL, PersistentDataType.STRING, normalized.getItemModel());
        setOrRemove(data, BetterHorseKeys.TEXTURE_CIT_STRING, PersistentDataType.STRING, normalized.getCitString());
        setOrRemove(data, BetterHorseKeys.TEXTURE_MODEL_STRING, PersistentDataType.STRING, normalized.getModelString());
    }

    /**
     * Synchronizes the stats from this BetterHorse entity to the provided ItemStack.
     * This updates health, speed, jump, growth stage, trait, and other metadata
     * on the item to match the current state of the horse entity.
     *
     * @param itemStack the ItemStack to synchronize with this horse
     */
    public void syncToItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return;
        }

        PersistentDataContainer horseData = handle.getPersistentDataContainer();
        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer itemData = meta.getPersistentDataContainer();

        // Sync base stats
        itemData.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, getMaxHealth());
        itemData.set(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE, handle.getHealth());
        itemData.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, getSpeed());
        itemData.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, getJump());

        // Sync base values for training
        copyIfPresent(horseData, itemData, BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE);

        // Sync owner
        copyIfPresent(horseData, itemData, BetterHorseKeys.OWNER, PersistentDataType.STRING);

        // Sync growth stage
        copyIfPresent(horseData, itemData, BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);

        // Sync mount type
        copyIfPresent(horseData, itemData, BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);

        // Sync trait
        copyIfPresent(horseData, itemData, BetterHorseKeys.TRAIT, PersistentDataType.STRING);

        // Sync neutered status
        copyIfPresent(horseData, itemData, BetterHorseKeys.NEUTERED, PersistentDataType.BYTE);

        // Sync cooldown
        copyIfPresent(horseData, itemData, BetterHorseKeys.COOLDOWN, PersistentDataType.LONG);

        // Sync style and color for regular horses
        if (handle instanceof Horse horse) {
            itemData.set(BetterHorseKeys.STYLE, PersistentDataType.STRING, horse.getStyle().name());
            itemData.set(BetterHorseKeys.COLOR, PersistentDataType.STRING, horse.getColor().name());
        }

        // Sync saddle
        ItemStack saddle = handle.getInventory().getSaddle();
        if (saddle != null && saddle.getType() != Material.AIR) {
            itemData.set(BetterHorseKeys.SADDLE, PersistentDataType.STRING, saddle.getType().name());
        }

        // Sync armor
        ItemStack armor = HorseArmorUtils.getArmor(handle.getInventory());
        if (armor != null) {
            itemData.set(BetterHorseKeys.ARMOR, PersistentDataType.STRING, armor.getType().name());
            try {
                itemData.set(BetterHorseKeys.ARMOR_DATA, PersistentDataType.STRING, Base64.getEncoder().encodeToString(armor.serializeAsBytes()));
            } catch (Exception ignored) {}
        }

        // Sync chest contents
        if (handle instanceof ChestedHorse chestedHorse && chestedHorse.isCarryingChest()) {
            itemData.set(BetterHorseKeys.CHESTED, PersistentDataType.BYTE, (byte) 1);
            String serializedContents = serializeStorageContents(handle.getInventory().getStorageContents());
            if (serializedContents != null) {
                itemData.set(BetterHorseKeys.CHEST_CONTENTS, PersistentDataType.STRING, serializedContents);
            }
        }

        // Sync undead data
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_SKELETON, PersistentDataType.BYTE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, PersistentDataType.STRING);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH, PersistentDataType.DOUBLE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ORIGINAL_SPEED, PersistentDataType.DOUBLE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ORIGINAL_JUMP, PersistentDataType.DOUBLE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ORIGINAL_COLOR, PersistentDataType.STRING);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ORIGINAL_STYLE, PersistentDataType.STRING);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_ARMOR_DATA, PersistentDataType.BYTE_ARRAY);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_CHESTED, PersistentDataType.BYTE);
        copyIfPresent(horseData, itemData, BetterHorseKeys.UNDEAD_CHEST_CONTENTS, PersistentDataType.STRING);

        // Sync texture data
        HorseItemTextureData textureData = getTextureData();
        if (textureData != null) {
            setOrRemove(itemData, BetterHorseKeys.TEXTURE_CUSTOM_MODEL_DATA, PersistentDataType.INTEGER, textureData.getCustomModelData());
            setOrRemove(itemData, BetterHorseKeys.TEXTURE_ITEM_MODEL, PersistentDataType.STRING, textureData.getItemModel());
            setOrRemove(itemData, BetterHorseKeys.TEXTURE_CIT_STRING, PersistentDataType.STRING, textureData.getCitString());
            setOrRemove(itemData, BetterHorseKeys.TEXTURE_MODEL_STRING, PersistentDataType.STRING, textureData.getModelString());
        }

        item.setItemMeta(meta);
    }

    private static String serializeStorageContents(ItemStack[] contents) {
        try (ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
             BukkitObjectOutputStream output = new BukkitObjectOutputStream(byteOutput)) {
            output.writeInt(contents.length);
            for (ItemStack content : contents) {
                output.writeObject(content);
            }
            return Base64.getEncoder().encodeToString(byteOutput.toByteArray());
        } catch (IOException ignored) {
            return null;
        }
    }

    private <T> void copyIfPresent(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key, PersistentDataType<?, T> type) {
        if (from.has(key, type)) {
            to.set(key, type, from.get(key, type));
        }
    }

    private <T> void setOrRemove(PersistentDataContainer data, NamespacedKey key, PersistentDataType<?, T> type, T value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.set(key, type, value);
        }
    }

    private void setAttribute(Attribute attribute, NamespacedKey key, double value) {
        AttributeInstance attr = handle.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(value);
        }
        handle.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, value);
    }
}
