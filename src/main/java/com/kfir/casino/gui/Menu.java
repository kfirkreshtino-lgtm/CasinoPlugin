package com.kfir.casino.gui;

import com.kfir.casino.CasinoPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal menu framework over Bukkit inventories.
 *
 * <p>Design note: no third-party GUI library is shaded in. A menu is an InventoryHolder,
 * which is how clicks are routed back to the right object, and each slot maps to a click
 * handler. Everything else is cancelled, so items can never be pulled out of a menu.
 */
public abstract class Menu implements InventoryHolder {

    protected final CasinoPlugin plugin;
    protected final Player player;

    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();
    private Inventory inventory;

    protected Menu(CasinoPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Inventory title. */
    protected abstract Component title();

    /** Inventory size, a multiple of nine up to 54. */
    protected abstract int size();

    /** Fills the inventory. Called on open and on every refresh. */
    protected abstract void build();

    public void open() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size(), title());
        }
        refresh();
        player.openInventory(inventory);
    }

    /** Rebuilds the contents in place, so anyone already viewing sees the update. */
    public void refresh() {
        if (inventory == null) {
            return;
        }
        handlers.clear();
        inventory.clear();
        build();
    }

    /** True when this menu is the inventory the player currently has open. */
    public boolean isOpen() {
        return inventory != null && player.getOpenInventory().getTopInventory().equals(inventory);
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        }
    }

    /** Fills every empty slot with the neutral background pane. */
    protected void fillEmpty() {
        ItemStack filler = Items.filler();
        for (int slot = 0; slot < size(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    /** Overridden by games that need to react to the player walking away. */
    public void onClose(InventoryCloseEvent event) {
    }

    public Player player() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size(), title());
        }
        return inventory;
    }
}
