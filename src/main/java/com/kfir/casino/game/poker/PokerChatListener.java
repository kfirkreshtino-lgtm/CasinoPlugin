package com.kfir.casino.game.poker;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.util.Tasks;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Reads a custom bet typed in chat.
 *
 * <p>After a player picks "Custom amount" in the poker menu, their next chat message is
 * the amount. It is taken out of chat so nobody else sees it, then handed to the table on
 * the main thread, because chat arrives on its own thread and the game must not be touched
 * from there.
 */
public final class PokerChatListener implements Listener {

    private final CasinoPlugin plugin;

    public PokerChatListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!(plugin.pokerHook() instanceof HoldemPokerHook poker)) {
            return;
        }
        Player player = event.getPlayer();
        if (!poker.isAwaitingAmount(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        Tasks.later(plugin, 0L, () -> poker.typedAmount(player, text));
    }
}
