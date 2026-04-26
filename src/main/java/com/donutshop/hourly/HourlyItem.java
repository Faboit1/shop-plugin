package com.donutshop.hourly;

import java.util.Collections;
import java.util.List;

/**
 * Represents a single entry in the hourly shop item pool.
 * Can be a standard Bukkit material item or a command-driven custom item.
 */
public class HourlyItem {

    private final String id;
    private final String type;       // "material" or "command"
    private final String material;   // Bukkit Material name (used for display & giving if type=material)
    private final String name;       // Display name — supports legacy &codes and MiniMessage
    private final List<String> lore;
    private final int weight;        // Higher = more common
    private final double cost;       // -1 = free
    private final List<String> commands; // Executed as console when type=command
    private final int purchaseLimit; // Max purchases per player per hour; -1 = unlimited

    public HourlyItem(String id, String type, String material, String name,
                      List<String> lore, int weight, double cost, List<String> commands,
                      int purchaseLimit) {
        this.id = id;
        this.type = (type != null) ? type : "material";
        this.material = (material != null) ? material : "STONE";
        this.name = (name != null) ? name : id;
        this.lore = (lore != null) ? Collections.unmodifiableList(lore) : Collections.emptyList();
        this.weight = Math.max(1, weight);
        this.cost = cost;
        this.commands = (commands != null) ? Collections.unmodifiableList(commands) : Collections.emptyList();
        this.purchaseLimit = purchaseLimit;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getMaterial() { return material; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public int getWeight() { return weight; }
    public double getCost() { return cost; }
    public List<String> getCommands() { return commands; }
    public int getPurchaseLimit() { return purchaseLimit; }

    public boolean isCommandItem() {
        return "command".equalsIgnoreCase(type);
    }
}
