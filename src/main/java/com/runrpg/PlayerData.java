package com.runrpg;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerData {
    public long xp;
    public int healthLevel;
    public int slotsUnlocked = 3;
    public int foodLevel;
    public int runsLost;
    public int runsWon;
    /** Inventario del lobby guardado mientras dura la run (null = no hay). */
    public ItemStack[] stash;
    private final File file;

    public PlayerData(File dir, UUID id) {
        file = new File(dir, id + ".yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        xp = c.getLong("xp", 0);
        healthLevel = c.getInt("health-level", 0);
        slotsUnlocked = c.getInt("slots-unlocked", 3);
        foodLevel = c.getInt("food-level", 0);
        runsLost = c.getInt("runs-lost", 0);
        runsWon = c.getInt("runs-won", 0);
        if (c.getBoolean("has-stash", false)) {
            stash = new ItemStack[41];
            ConfigurationSection s = c.getConfigurationSection("stash");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    try {
                        int i = Integer.parseInt(k);
                        if (i >= 0 && i < stash.length) stash[i] = s.getItemStack(k);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("xp", xp);
        c.set("health-level", healthLevel);
        c.set("slots-unlocked", slotsUnlocked);
        c.set("food-level", foodLevel);
        c.set("runs-lost", runsLost);
        c.set("runs-won", runsWon);
        if (stash != null) {
            c.set("has-stash", true);
            for (int i = 0; i < stash.length; i++) {
                if (stash[i] != null) c.set("stash." + i, stash[i]);
            }
        }
        try {
            c.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
