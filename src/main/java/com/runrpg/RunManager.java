package com.runrpg;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class RunManager {
    private final RunRPG pl;
    private World runWorld;
    private BossBar bar;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> ready = new HashSet<>();
    private final Map<UUID, BukkitTask> charging = new HashMap<>();

    public RunManager(RunRPG pl) {
        this.pl = pl;
    }

    public boolean isActive() {
        return runWorld != null;
    }

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    // ---------- Inicio ----------
    public void start(Collection<? extends Player> list) {
        if (list.isEmpty() || runWorld != null) return;
        WorldCreator wc = new WorldCreator("run_" + System.currentTimeMillis());
        wc.environment(World.Environment.NORMAL);
        runWorld = wc.createWorld();
        if (runWorld == null) return;
        runWorld.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
        runWorld.setDifficulty(Difficulty.NORMAL);

        Location spawn = findSpawn(runWorld);
        bar = BossBar.bossBar(Component.text("Listos para salir: 0/" + list.size()), 0f,
                BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

        for (Player p : list) {
            PlayerData d = pl.data(p);
            // Guardamos el inventario del lobby para devolverlo al volver
            d.stash = p.getInventory().getContents().clone();
            d.save();
            participants.add(p.getUniqueId());
            p.setGameMode(GameMode.SURVIVAL);
            pl.prepareInventory(p);
            p.getInventory().setItem(0, pl.shard());
            giveStartFood(p, d.foodLevel);
            pl.applyHealth(p);
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.teleport(spawn);
            p.showBossBar(bar);
            p.sendMessage(RunRPG.text("Run iniciada. Carga el Eco Shard para volver con tu botin.", NamedTextColor.AQUA));
        }
        updateBar();
    }

    /** Busca suelo firme (no agua, no arboles) cerca del centro del mundo. */
    private Location findSpawn(World w) {
        for (int r = 0; r <= 160; r += 16) {
            int points = r == 0 ? 1 : 8;
            for (int i = 0; i < points; i++) {
                double a = Math.PI / 4 * i;
                int x = (int) Math.round(r * Math.cos(a));
                int z = (int) Math.round(r * Math.sin(a));
                Block b = w.getHighestBlockAt(x, z);
                Material t = b.getType();
                if (!b.isLiquid() && t.isSolid() && !Tag.LEAVES.isTagged(t)) {
                    return new Location(w, x + 0.5, b.getY() + 1, z + 0.5);
                }
            }
        }
        return w.getSpawnLocation();
    }

    private void giveStartFood(Player p, int lvl) {
        PlayerInventory inv = p.getInventory();
        switch (lvl) {
            case 1 -> inv.addItem(new ItemStack(Material.BREAD, 6));
            case 2 -> inv.addItem(new ItemStack(Material.COOKED_BEEF, 8));
            case 3 -> {
                inv.addItem(new ItemStack(Material.COOKED_BEEF, 12));
                inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 3));
            }
            default -> { }
        }
    }

    // ---------- Eco Shard ----------
    public void tryCharge(Player p) {
        UUID id = p.getUniqueId();
        if (!participants.contains(id) || charging.containsKey(id)) return;
        if (ready.contains(id)) {
            p.sendActionBar(RunRPG.text("Ya estas listo. Esperando al resto.", NamedTextColor.GREEN));
            return;
        }
        int total = pl.getConfig().getInt("shard.charge-seconds", 5);
        BukkitTask task = new BukkitRunnable() {
            int s = 0;

            @Override
            public void run() {
                if (!p.isOnline() || !participants.contains(id) || !pl.isShard(p.getInventory().getItemInMainHand())) {
                    cancel();
                    charging.remove(id);
                    if (p.isOnline()) p.sendActionBar(RunRPG.text("Carga interrumpida", NamedTextColor.RED));
                    return;
                }
                s++;
                p.sendActionBar(RunRPG.text("Cargando Eco Shard... " + s + "/" + total, NamedTextColor.AQUA));
                if (s >= total) {
                    cancel();
                    charging.remove(id);
                    ready.add(id);
                    p.sendMessage(RunRPG.text("Eco Shard cargado.", NamedTextColor.GREEN));
                    updateBar();
                    checkReady();
                }
            }
        }.runTaskTimer(pl, 20L, 20L);
        charging.put(id, task);
    }

    private void updateBar() {
        if (bar == null) return;
        bar.name(Component.text("Listos para salir: " + ready.size() + "/" + participants.size()));
        bar.progress(participants.isEmpty() ? 0f : (float) ready.size() / participants.size());
    }

    private void checkReady() {
        if (!participants.isEmpty() && ready.containsAll(participants)) finish();
    }

    // ---------- Fin de run ----------
    private void finish() {
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) cashOut(p);
        }
        participants.clear();
        ready.clear();
        cleanup();
    }

    private void cashOut(Player p) {
        PlayerData d = pl.data(p);
        long gained = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null || pl.isLock(it) || pl.isShard(it)) continue;
            gained += (long) pl.getConfig().getInt("loot-values." + it.getType().name(), 0) * it.getAmount();
        }
        d.xp += gained;
        d.runsWon++;
        d.save();
        if (bar != null) p.hideBossBar(bar);
        pl.returnFromRun(p);
        p.sendMessage(RunRPG.text("Volviste con vida. XP ganada: " + gained + " | Total: " + d.xp, NamedTextColor.GOLD));
    }

    /** El jugador "muere": pierde la run y vuelve al lobby sin pasar por la pantalla de muerte. */
    public void fail(Player p) {
        if (!participants.contains(p.getUniqueId())) return;
        leave(p, true);
        pl.returnFromRun(p);
        p.showTitle(Title.title(Component.text("RUN PERDIDA", NamedTextColor.RED),
                Component.text("Vuelves sin el botin", NamedTextColor.GRAY)));
    }

    /** Muerte o desconexion. */
    public void leave(Player p, boolean lost) {
        UUID id = p.getUniqueId();
        if (!participants.remove(id)) return;
        ready.remove(id);
        BukkitTask t = charging.remove(id);
        if (t != null) t.cancel();
        if (bar != null) p.hideBossBar(bar);
        if (lost) {
            PlayerData d = pl.data(p);
            d.runsLost++;
            d.save();
        }
        if (participants.isEmpty()) {
            cleanup();
        } else {
            updateBar();
            checkReady();
        }
    }

    public void abort() {
        for (UUID id : new ArrayList<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                if (bar != null) p.hideBossBar(bar);
                pl.returnFromRun(p);
            }
        }
        charging.values().forEach(BukkitTask::cancel);
        charging.clear();
        participants.clear();
        ready.clear();
        cleanup();
    }

    /** Saca a todos del mundo de la run, lo descarga y borra su carpeta. */
    private void cleanup() {
        World w = runWorld;
        runWorld = null;
        bar = null;
        if (w == null) return;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            for (Player p : new ArrayList<>(w.getPlayers())) p.teleport(pl.lobby());
            File folder = w.getWorldFolder();
            if (Bukkit.unloadWorld(w, false)) {
                Bukkit.getScheduler().runTaskAsynchronously(pl, () -> deleteFolder(folder));
            }
        }, 40L);
    }

    public static void deleteFolder(File dir) {
        try (var s = Files.walk(dir.toPath())) {
            s.sorted(Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
        }
    }
}
