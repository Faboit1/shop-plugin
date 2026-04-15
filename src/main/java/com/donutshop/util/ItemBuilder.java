package com.donutshop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        ItemMeta m = item.getItemMeta();
        if (m == null) {
            throw new IllegalArgumentException("Material " + material + " does not support item meta");
        }
        this.meta = m;
    }

    /** Set display name — text is automatically converted to small caps. */
    public ItemBuilder name(String name) {
        meta.displayName(MINI.deserialize(SmallCaps.convert(name)));
        return this;
    }

    /** Set display name without small caps conversion. */
    public ItemBuilder rawName(String name) {
        meta.displayName(MINI.deserialize(name));
        return this;
    }

    /** Set lore lines — each line is converted to small caps. */
    public ItemBuilder lore(List<String> lines) {
        List<Component> lore = lines.stream()
                .map(line -> MINI.deserialize(SmallCaps.convert(line)))
                .collect(Collectors.toList());
        meta.lore(lore);
        return this;
    }

    /** Set lore lines without small caps conversion. */
    public ItemBuilder rawLore(List<String> lines) {
        List<Component> lore = lines.stream()
                .map(MINI::deserialize)
                .collect(Collectors.toList());
        meta.lore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /** Add an enchantment glow effect (hidden enchant). */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        meta.setCustomModelData(data);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
