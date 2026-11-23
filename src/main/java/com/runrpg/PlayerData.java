package com.runrpg;

import org.bukkit.configuration.file.YamlConfiguration;

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
    }

    public void save() {
        YamlConfiguration c = new YamlConfiguration();
        c.set("xp", xp);
        c.set("health-level", healthLevel);
        c.set("slots-unlocked", slotsUnlocked);
        c.set("food-level", foodLevel);
        c.set("runs-lost", runsLost);
        c.set("runs-won", runsWon);
        try {
            c.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
