package com.runrpg;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class Listeners implements Listener {
    private final RunRPG pl;

    public Listeners(RunRPG pl) {
        this.pl = pl;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        pl.setupLobbyPlayer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.runs.leave(e.getPlayer(), true);
        pl.unload(e.getPlayer());
    }

    // ----- Muerte: interceptamos el golpe mortal, asi no hay pantalla de muerte -----
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!pl.runs.isParticipant(p.getUniqueId())) {
            e.setCancelled(true); // en el lobby nadie recibe dano
            return;
        }
        if (p.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            pl.runs.fail(p);
        }
    }

    // Respaldo por si una muerte se escapa del evento anterior
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!pl.runs.isParticipant(p.getUniqueId())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepLevel(false);
        pl.runs.leave(p, true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        e.setRespawnLocation(pl.lobby());
        pl.getServer().getScheduler().runTaskLater(pl, () -> pl.returnFromRun(p), 1L);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && !pl.runs.isParticipant(p.getUniqueId())) e.setCancelled(true);
    }

    // ----- Interacciones: tienda, inicio de run y Eco Shard -----
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();

        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && !pl.runs.isParticipant(p.getUniqueId())
                && e.getClickedBlock().getWorld().equals(pl.lobby().getWorld())) {
            Material t = e.getClickedBlock().getType();
            if (t == Material.ENCHANTING_TABLE) {
                e.setCancelled(true);
                new ShopMenu(pl, p).open();
                return;
            }
            if (t == Material.LODESTONE) {
                e.setCancelled(true);
                if (pl.runs.isActive()) {
                    p.sendMessage(RunRPG.text("Ya hay una run en curso.", net.kyori.adventure.text.format.NamedTextColor.RED));
                } else {
                    pl.runs.start(new ArrayList<>(pl.lobby().getWorld().getPlayers()));
                }
                return;
            }
        }

        if (pl.isShard(e.getItem())) {
            e.setCancelled(true);
            pl.runs.tryCharge(p);
        }
    }

    // ----- Slots bloqueados y tienda -----
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack it = e.getItemInHand();
        if (pl.isLock(it) || pl.isShard(it)) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof ShopMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getInventory()) menu.click(e.getSlot());
            return;
        }
        if (pl.isLock(e.getCurrentItem()) || pl.isLock(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hot = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());
            if (pl.isLock(hot)) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ShopMenu) {
            e.setCancelled(true);
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (pl.isLock(e.getView().getItem(raw))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (pl.isLock(it) || pl.isShard(it)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (pl.isLock(e.getMainHandItem()) || pl.isLock(e.getOffHandItem())) e.setCancelled(true);
    }
}
