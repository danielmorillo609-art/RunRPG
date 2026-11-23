package com.runrpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class RunRPG extends JavaPlugin {
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private File dataDir;
    public NamespacedKey lockKey;
    public NamespacedKey crystalKey;
    public RunManager runs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataDir = new File(getDataFolder(), "data");
        dataDir.mkdirs();
        lockKey = new NamespacedKey(this, "lock");
        crystalKey = new NamespacedKey(this, "crystal");

        // Borra mundos de runs que quedaron de un apagado anterior
        File[] folders = Bukkit.getWorldContainer().listFiles();
        if (folders != null) {
            for (File f : folders) {
                if (f.isDirectory() && f.getName().startsWith("run_")) RunManager.deleteFolder(f);
            }
        }

        runs = new RunManager(this);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        configureLobby(lobby().getWorld());
        for (Player p : Bukkit.getOnlinePlayers()) setupLobbyPlayer(p);
    }

    @Override
    public void onDisable() {
        players.values().forEach(PlayerData::save);
    }

    // ---------- Datos ----------
    public PlayerData data(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), id -> new PlayerData(dataDir, id));
    }

    public void unload(Player p) {
        PlayerData d = players.remove(p.getUniqueId());
        if (d != null) d.save();
    }

    // ---------- Lobby ----------
    public Location lobby() {
        World w = Bukkit.getWorld(getConfig().getString("lobby.world", ""));
        if (w == null) return Bukkit.getWorlds().get(0).getSpawnLocation();
        return new Location(w,
                getConfig().getDouble("lobby.x"), getConfig().getDouble("lobby.y"), getConfig().getDouble("lobby.z"),
                (float) getConfig().getDouble("lobby.yaw"), 0f);
    }

    public void configureLobby(World w) {
        w.setGameRule(GameRule.REDUCED_DEBUG_INFO, true); // oculta coordenadas en F3
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setTime(6000);
        w.setStorm(false);
    }

    public void setupLobbyPlayer(Player p) {
        p.teleport(lobby());
        p.setGameMode(GameMode.ADVENTURE);
        prepareInventory(p);
        applyHealth(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.clearActivePotionEffects();
    }

    // ---------- Mejoras ----------
    public void applyHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) a.setBaseValue(20 + 2 * data(p).healthLevel);
    }

    /** Vacia el inventario y rellena con "Bloqueado" los slots aun no comprados. */
    public void prepareInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        int unlocked = data(p).slotsUnlocked;
        for (int i = unlocked; i < 36; i++) inv.setItem(i, lockItem());
    }

    // ---------- Items ----------
    public static Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack lockItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(text("Slot bloqueado", NamedTextColor.RED));
        m.lore(List.of(text("Se desbloquea en la tienda", NamedTextColor.GRAY)));
        m.getPersistentDataContainer().set(lockKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isLock(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(lockKey, PersistentDataType.BYTE);
    }

    public ItemStack crystal() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(text("Cristal de salida", NamedTextColor.LIGHT_PURPLE));
        m.lore(List.of(text("Sostenlo en la mano para cargarlo", NamedTextColor.GRAY),
                text("Todos deben cargarlo para volver", NamedTextColor.GRAY)));
        m.getPersistentDataContainer().set(crystalKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isCrystal(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(crystalKey, PersistentDataType.BYTE);
    }

    // ---------- Comandos ----------
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "setlobby" -> {
                if (!(s instanceof Player p)) return true;
                Location l = p.getLocation();
                getConfig().set("lobby.world", l.getWorld().getName());
                getConfig().set("lobby.x", l.getX());
                getConfig().set("lobby.y", l.getY());
                getConfig().set("lobby.z", l.getZ());
                getConfig().set("lobby.yaw", l.getYaw());
                saveConfig();
                configureLobby(l.getWorld());
                p.sendMessage(text("Sala principal definida.", NamedTextColor.GREEN));
            }
            case "tienda" -> {
                if (!(s instanceof Player p)) return true;
                if (runs.isParticipant(p.getUniqueId())) {
                    p.sendMessage(text("La tienda solo se abre en la sala principal.", NamedTextColor.RED));
                } else {
                    new ShopMenu(this, p).open();
                }
            }
            case "run" -> {
                String sub = args.length > 0 ? args[0].toLowerCase() : "";
                if (sub.equals("start")) {
                    if (runs.isActive()) {
                        s.sendMessage(text("Ya hay una run en curso.", NamedTextColor.RED));
                    } else {
                        runs.start(new ArrayList<>(Bukkit.getOnlinePlayers()));
                    }
                } else if (sub.equals("abort")) {
                    runs.abort();
                    s.sendMessage(text("Run cancelada.", NamedTextColor.YELLOW));
                } else {
                    s.sendMessage(text("Uso: /run start | abort", NamedTextColor.GRAY));
                }
            }
        }
        return true;
    }
}
