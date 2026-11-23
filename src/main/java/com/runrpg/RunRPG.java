package com.runrpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
    public NamespacedKey shardKey;
    public RunManager runs;
    private World lobbyWorld;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataDir = new File(getDataFolder(), "data");
        dataDir.mkdirs();
        lockKey = new NamespacedKey(this, "lock");
        shardKey = new NamespacedKey(this, "eco_shard");

        // Borra mundos de runs que quedaron de un apagado anterior
        File[] folders = Bukkit.getWorldContainer().listFiles();
        if (folders != null) {
            for (File f : folders) {
                if (f.isDirectory() && f.getName().startsWith("run_")) RunManager.deleteFolder(f);
            }
        }

        // Mundo del lobby (vacio, con una plataforma construida por el plugin)
        lobbyWorld = Bukkit.getWorld("runrpg_lobby");
        if (lobbyWorld == null) {
            WorldCreator wc = new WorldCreator("runrpg_lobby");
            wc.generator(new VoidGenerator());
            wc.generateStructures(false);
            lobbyWorld = wc.createWorld();
        }
        boolean builtHere = lobbyWorld != null;
        if (lobbyWorld == null) lobbyWorld = Bukkit.getWorlds().get(0);
        if (builtHere && !getConfig().getBoolean("lobby.built", false)) {
            buildLobby(lobbyWorld);
            getConfig().set("lobby.built", true);
            saveConfig();
        }
        configureLobby(lobbyWorld);

        runs = new RunManager(this);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);

        // Si alguien cae al vacio del lobby, lo devolvemos
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : lobby().getWorld().getPlayers()) {
                if (p.getLocation().getY() < 40) p.teleport(lobby());
            }
        }, 20L, 20L);

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
        FileConfiguration c = getConfig();
        if (c.getBoolean("lobby.custom", false)) {
            World w = Bukkit.getWorld(c.getString("lobby.world", ""));
            if (w != null) {
                return new Location(w, c.getDouble("lobby.x"), c.getDouble("lobby.y"), c.getDouble("lobby.z"),
                        (float) c.getDouble("lobby.yaw"), 0f);
            }
        }
        return new Location(lobbyWorld, 0.5, 65, 5.5, 180f, 0f);
    }

    public void configureLobby(World w) {
        w.setGameRule(GameRule.REDUCED_DEBUG_INFO, true); // oculta coordenadas en F3
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setTime(18000);
        w.setStorm(false);
    }

    /** Plataforma pequena: tienda (mesa de encantamientos) y portal de inicio (piedra iman). */
    public void buildLobby(World w) {
        int y = 64;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 10.5) continue;
                Material m = d >= 9 ? Material.POLISHED_BLACKSTONE
                        : (((x + z) & 1) == 0 ? Material.DEEPSLATE_BRICKS : Material.POLISHED_DEEPSLATE);
                w.getBlockAt(x, y, z).setType(m);
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                w.getBlockAt(x, y, z).setType(Material.AMETHYST_BLOCK);
                w.getBlockAt(x, y, z - 7).setType(Material.CRYING_OBSIDIAN);
            }
        }
        w.getBlockAt(0, 65, 0).setType(Material.ENCHANTING_TABLE);
        w.getBlockAt(0, 65, -7).setType(Material.LODESTONE);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            int px = (int) Math.round(9 * Math.cos(a));
            int pz = (int) Math.round(9 * Math.sin(a));
            for (int h = 65; h <= 67; h++) w.getBlockAt(px, h, pz).setType(Material.POLISHED_BLACKSTONE);
            w.getBlockAt(px, 68, pz).setType(Material.SEA_LANTERN);
        }
        for (TextDisplay t : w.getEntitiesByClass(TextDisplay.class)) t.remove();
        label(w, 0.5, 66.6, 0.5, "TIENDA DE MEJORAS", "Clic derecho en la mesa", NamedTextColor.GOLD);
        label(w, 0.5, 66.6, -6.5, "INICIAR RUN", "Clic derecho en la piedra iman", NamedTextColor.RED);
    }

    private void label(World w, double x, double y, double z, String title, String sub, NamedTextColor color) {
        Component text = Component.text(title, color, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text(sub, NamedTextColor.GRAY));
        w.spawn(new Location(w, x, y, z), TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(true);
        });
    }

    /** Al entrar al servidor: va al lobby y, si quedo una run a medias, recupera su inventario. */
    public void setupLobbyPlayer(Player p) {
        toLobby(p, false);
    }

    /** Al terminar o perder una run: limpia lo de la run y devuelve el inventario del lobby. */
    public void returnFromRun(Player p) {
        toLobby(p, true);
    }

    private void toLobby(Player p, boolean fromRun) {
        PlayerData d = data(p);
        p.teleport(lobby());
        p.setGameMode(GameMode.ADVENTURE);
        if (fromRun || d.stash != null) {
            PlayerInventory inv = p.getInventory();
            inv.clear();
            if (d.stash != null) {
                inv.setContents(d.stash);
                d.stash = null;
                d.save();
            }
        }
        applyHealth(p);
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) p.setHealth(max.getValue());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.clearActivePotionEffects();
    }

    // ---------- Mejoras ----------
    public void applyHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) a.setBaseValue(20 + 2 * data(p).healthLevel);
    }

    /** Solo para la run: vacia el inventario y bloquea los slots no comprados. */
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

    public ItemStack shard() {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.displayName(text("Eco Shard", NamedTextColor.AQUA));
        m.lore(List.of(text("Sostenlo en la mano para cargarlo", NamedTextColor.GRAY),
                text("Todos deben cargarlo para volver", NamedTextColor.GRAY)));
        m.getPersistentDataContainer().set(shardKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isShard(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(shardKey, PersistentDataType.BYTE);
    }

    // ---------- Comandos ----------
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "setlobby" -> {
                if (!(s instanceof Player p)) return true;
                Location l = p.getLocation();
                getConfig().set("lobby.custom", true);
                getConfig().set("lobby.world", l.getWorld().getName());
                getConfig().set("lobby.x", l.getX());
                getConfig().set("lobby.y", l.getY());
                getConfig().set("lobby.z", l.getZ());
                getConfig().set("lobby.yaw", l.getYaw());
                saveConfig();
                configureLobby(l.getWorld());
                p.sendMessage(text("Sala principal personalizada definida.", NamedTextColor.GREEN));
            }
            case "buildlobby" -> {
                buildLobby(lobbyWorld);
                s.sendMessage(text("Lobby reconstruido.", NamedTextColor.GREEN));
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
                        runs.start(new ArrayList<>(lobby().getWorld().getPlayers()));
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
