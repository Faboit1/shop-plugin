package com.donutshop.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Inventories {

    private Inventories() {}

    /** Gives plain items split into real max-stack-size stacks. Returns how many did not fit. */
    public static int give(Player player, Material material, int amount) {
        int maxStack = Math.max(1, material.getMaxStackSize());
        int notAdded = 0;
        for (int left = amount; left > 0; left -= maxStack) {
            ItemStack stack = new ItemStack(material, Math.min(maxStack, left));
            for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                notAdded += rest.getAmount();
            }
        }
        return notAdded;
    }

    /**
     * Counts only plain stacks of the material: no name, lore, enchants, damage, contents or plugin data.
     * Custom items that reuse a vanilla material are never sold at the vanilla price.
     */
    public static int countPlain(Player player, Material material) {
        ItemStack template = new ItemStack(material);
        int count = 0;
        for (ItemStack is : player.getInventory().getStorageContents()) {
            if (is != null && template.isSimilar(is)) {
                count += is.getAmount();
            }
        }
        return count;
    }

    public static void removePlain(Player player, Material material, int amount) {
        ItemStack template = new ItemStack(material);
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || !template.isSimilar(is)) continue;
            int stackAmount = is.getAmount();
            if (stackAmount <= remaining) {
                remaining -= stackAmount;
                contents[i] = null;
            } else {
                is.setAmount(stackAmount - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setStorageContents(contents);
    }
}
