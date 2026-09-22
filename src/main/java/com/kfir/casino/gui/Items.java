package com.kfir.casino.gui;

import com.kfir.casino.game.card.Card;
import com.kfir.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Item builders shared by every menu. */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, String name, String... loreLines) {
        return of(material, 1, name, loreLines);
    }

    public static ItemStack of(Material material, int amount, String name, String... loreLines) {
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name));
            if (loreLines.length > 0) {
                List<Component> lore = new ArrayList<>(loreLines.length);
                for (String line : loreLines) {
                    lore.add(Text.mm(line));
                }
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Neutral background pane with a blank name. */
    public static ItemStack filler() {
        return of(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    /** A face-up card. Red suits render red, black suits render dark grey. */
    public static ItemStack card(Card card) {
        return of(Material.PAPER, "<bold>" + card.colored() + "</bold>",
                "<gray>" + card.rank().name().charAt(0) + card.rank().name().substring(1).toLowerCase()
                        + " of " + card.suit().name().charAt(0)
                        + card.suit().name().substring(1).toLowerCase() + "</gray>");
    }

    /** The dealer hole card. */
    public static ItemStack faceDownCard() {
        return of(Material.MAP, "<dark_purple><bold>Face down</bold></dark_purple>",
                "<gray>Revealed when the dealer plays.</gray>");
    }

    /** A chip stack label. */
    public static ItemStack chips(long amount, String name, String... loreLines) {
        return of(Material.GOLD_NUGGET, (int) Math.max(1, Math.min(64, amount)), name, loreLines);
    }

    public static ItemStack button(Material material, String name, String... loreLines) {
        return of(material, name, loreLines);
    }
}
