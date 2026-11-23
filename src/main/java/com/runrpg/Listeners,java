package com.runrpg;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
        pl.getServer().getScheduler().runTaskLater(pl, () -> pl.setupLobbyPlayer(p), 1L);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (pl.isCrystal(e.getItem())) {
            e.setCancelled(true);
            pl.runs.tryCharge(e.getPlayer());
        }
    }

    // ----- Lobby protegido -----
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && !pl.runs.isParticipant(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && !pl.runs.isParticipant(p.getUniqueId())) e.setCancelled(true);
    }

    // ----- Slots bloqueados y tienda -----
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof ShopMenu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getInventory()) menu.click(e.getSlot());
            return;
        }
        if (e.getWhoClicked().getGameMode() == GameMode.CREATIVE) return;
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
        if (pl.isLock(it) || pl.isCrystal(it)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (pl.isLock(e.getMainHandItem()) || pl.isLock(e.getOffHandItem())) e.setCancelled(true);
    }
}
