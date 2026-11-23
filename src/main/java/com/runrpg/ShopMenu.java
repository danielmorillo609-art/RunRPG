package com.runrpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ShopMenu implements InventoryHolder {
    private static final String[] IDS = {"health", "slots", "food"};
    private static final int[] SLOTS = {11, 13, 15};
    private static final Material[] ICONS = {Material.GOLDEN_APPLE, Material.CHEST, Material.BREAD};
    private static final String[] NAMES = {"Vida maxima (+1 corazon)", "Slot de inventario", "Comida inicial"};

    private final RunRPG pl;
    private final Player p;
    private final Inventory inv;

    public ShopMenu(RunRPG pl, Player p) {
        this.pl = pl;
        this.p = p;
        this.inv = Bukkit.createInventory(this, 27, Component.text("Mejoras permanentes"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        p.openInventory(inv);
    }

    private int level(PlayerData d, String id) {
        return switch (id) {
            case "health" -> d.healthLevel;
            case "slots" -> d.slotsUnlocked - 3;
            default -> d.foodLevel;
        };
    }

    private void levelUp(PlayerData d, String id) {
        switch (id) {
            case "health" -> d.healthLevel++;
            case "slots" -> d.slotsUnlocked++;
            default -> d.foodLevel++;
        }
    }

    private void render() {
        inv.clear();
        PlayerData d = pl.data(p);
        FileConfiguration c = pl.getConfig();
        inv.setItem(4, icon(Material.EXPERIENCE_BOTTLE, "XP: " + d.xp, List.of()));
        for (int i = 0; i < IDS.length; i++) {
            String id = IDS[i];
            int lvl = level(d, id);
            int max = c.getInt("shop." + id + ".max-level");
            int cost = c.getInt("shop." + id + ".base-cost") + c.getInt("shop." + id + ".cost-step") * lvl;
            List<Component> lore = lvl >= max
                    ? List.of(RunRPG.text("Nivel " + lvl + "/" + max + " (MAXIMO)", NamedTextColor.GREEN))
                    : List.of(RunRPG.text("Nivel " + lvl + "/" + max, NamedTextColor.GRAY),
                    RunRPG.text("Costo: " + cost + " XP", d.xp >= cost ? NamedTextColor.GOLD : NamedTextColor.RED));
            inv.setItem(SLOTS[i], icon(ICONS[i], NAMES[i], lore));
        }
    }

    private ItemStack icon(Material m, String name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(RunRPG.text(name, NamedTextColor.YELLOW));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public void click(int slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            String id = IDS[i];
            PlayerData d = pl.data(p);
            FileConfiguration c = pl.getConfig();
            int lvl = level(d, id);
            int max = c.getInt("shop." + id + ".max-level");
            int cost = c.getInt("shop." + id + ".base-cost") + c.getInt("shop." + id + ".cost-step") * lvl;
            if (lvl >= max) {
                p.sendMessage(RunRPG.text("Ya esta al maximo.", NamedTextColor.YELLOW));
            } else if (d.xp < cost) {
                p.sendMessage(RunRPG.text("No tienes suficiente XP.", NamedTextColor.RED));
            } else {
                d.xp -= cost;
                levelUp(d, id);
                d.save();
                pl.applyHealth(p);
                if (id.equals("slots")) pl.prepareInventory(p);
                p.sendMessage(RunRPG.text("Mejora comprada: " + NAMES[i], NamedTextColor.GREEN));
            }
            render();
            return;
        }
    }
}
