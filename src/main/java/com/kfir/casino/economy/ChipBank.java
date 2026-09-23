package com.kfir.casino.economy;

import com.kfir.casino.CasinoConfig;
import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.storage.ChipStore;
import com.kfir.casino.util.Text;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;

/**
 * Chips are a balance of their own, bought and cashed out with diamonds.
 *
 * <p>Design note: games never touch diamonds directly. Buying chips takes diamonds from the
 * inventory once, cashing out hands them back once, and every bet moves chips only. That
 * keeps a full inventory from ever landing in the middle of a dealt hand.
 *
 * <p>Only plain diamonds count. A renamed diamond is left alone, so a player cannot lose a
 * named keepsake to the cashier by accident.
 */
public final class ChipBank {

    private final CasinoPlugin plugin;
    private final ChipStore store;

    public ChipBank(CasinoPlugin plugin, ChipStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public long balance(UUID playerId) {
        return store.get(playerId);
    }

    public long balance(OfflinePlayer player) {
        return store.get(player.getUniqueId());
    }

    /** Removes chips if the player has them. Returns false and changes nothing otherwise. */
    public boolean take(UUID playerId, long chips) {
        if (chips <= 0) {
            return true;
        }
        long current = store.get(playerId);
        if (current < chips) {
            return false;
        }
        store.set(playerId, current - chips);
        return true;
    }

    public void give(UUID playerId, long chips) {
        if (chips <= 0) {
            return;
        }
        store.set(playerId, store.get(playerId) + chips);
    }

    /** Plain diamonds in the player's inventory. */
    public int diamonds(Player player) {
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(new ItemStack(Material.DIAMOND))) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** Most diamonds the player's chips could be cashed out for. */
    public int diamondsForChips(Player player) {
        long whole = balance(player) / plugin.config().chipsPerDiamond();
        return (int) Math.min(whole, plugin.config().maxExchangeDiamonds());
    }

    /** Trades diamonds from the inventory for chips. */
    public ExchangeResult buy(Player player, int diamonds) {
        CasinoConfig config = plugin.config();
        if (diamonds <= 0) {
            return ExchangeResult.fail("<red>You need at least one diamond to buy chips.</red>");
        }
        if (diamonds > config.maxExchangeDiamonds()) {
            return ExchangeResult.fail("<red>You cannot trade more than " + config.maxExchangeDiamonds()
                    + " diamonds at once.</red>");
        }
        int held = diamonds(player);
        if (held < diamonds) {
            return ExchangeResult.fail("<red>You need " + diamonds + " diamonds but only have " + held + ".</red>");
        }
        Map<Integer, ItemStack> missing = player.getInventory().removeItem(new ItemStack(Material.DIAMOND, diamonds));
        if (!missing.isEmpty()) {
            // Counted a moment ago, so this should not happen. Put back what was taken.
            int shortBy = missing.values().stream().mapToInt(ItemStack::getAmount).sum();
            player.getInventory().addItem(new ItemStack(Material.DIAMOND, diamonds - shortBy));
            return ExchangeResult.fail("<red>Could not take the diamonds. Nothing was changed.</red>");
        }
        long chips = (long) diamonds * config.chipsPerDiamond();
        give(player.getUniqueId(), chips);
        return ExchangeResult.ok("<green>Bought <white>" + Text.chips(chips) + "</white> chips for <aqua>"
                + diamonds + " " + plural(diamonds) + "</aqua>.</green>", chips, diamonds);
    }

    /**
     * Cashes chips back into diamonds at the buying rate. Diamonds that do not fit in the
     * inventory are dropped at the player's feet rather than lost.
     */
    public ExchangeResult sell(Player player, int diamonds) {
        CasinoConfig config = plugin.config();
        if (diamonds <= 0) {
            return ExchangeResult.fail("<red>You need chips worth at least one diamond. One diamond is "
                    + Text.chips(config.chipsPerDiamond()) + " chips.</red>");
        }
        if (diamonds > config.maxExchangeDiamonds()) {
            return ExchangeResult.fail("<red>You cannot trade more than " + config.maxExchangeDiamonds()
                    + " diamonds at once.</red>");
        }
        long chips = (long) diamonds * config.chipsPerDiamond();
        if (!take(player.getUniqueId(), chips)) {
            return ExchangeResult.fail("<red>" + diamonds + " " + plural(diamonds) + " cost "
                    + Text.chips(chips) + " chips, you have " + Text.chips(balance(player)) + ".</red>");
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.DIAMOND, diamonds));
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        String dropped = leftover.isEmpty() ? "" : " <yellow>Your inventory is full, the rest are at your feet.</yellow>";
        return ExchangeResult.ok("<green>Cashed out <white>" + Text.chips(chips) + "</white> chips for <aqua>"
                + diamonds + " " + plural(diamonds) + "</aqua>.</green>" + dropped, chips, diamonds);
    }

    private static String plural(int diamonds) {
        return diamonds == 1 ? "diamond" : "diamonds";
    }
}
