package com.kfir.casino.command;

import com.kfir.casino.CasinoPlugin;
import com.kfir.casino.economy.ExchangeResult;
import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationType;
import com.kfir.casino.util.Text;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Every casino command lives behind one root command with subcommands. */
public final class CasinoCommand implements CommandExecutor {

    private final CasinoPlugin plugin;

    public CasinoCommand(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> spawn(sender);
            case "remove" -> remove(sender);
            case "station" -> station(sender, args);
            case "reload" -> reload(sender);
            case "chips" -> chips(sender, args);
            case "balance", "bal" -> balance(sender);
            case "leave" -> leave(sender);
            default -> help(sender);
        }
        return true;
    }

    // ----------------------------------------------------------------- admin

    private void spawn(CommandSender sender) {
        if (!require(sender, "casino.admin") || !(sender instanceof Player player)) {
            if (sender.hasPermission("casino.admin")) {
                plugin.message(sender, "<red>Run this in game so the world is unambiguous.</red>");
            }
            return;
        }
        String error = plugin.structures().build(player.getWorld());
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        plugin.message(player, "<green>Casino built at the spawn point of <white>"
                + player.getWorld().getName() + "</white>.</green>");
        plugin.message(player, "<gray>Stations registered: <white>"
                + plugin.stations().size() + "</white>. Run <white>/casino remove</white> to undo.</gray>");
    }

    private void remove(CommandSender sender) {
        if (!require(sender, "casino.admin")) {
            return;
        }
        plugin.games().shutdown();
        String error = plugin.structures().remove();
        if (error != null) {
            plugin.message(sender, error);
            return;
        }
        plugin.message(sender, "<green>Casino removed and the terrain restored.</green> "
                + "<gray>Open bets were refunded.</gray>");
    }

    private void reload(CommandSender sender) {
        if (!require(sender, "casino.admin")) {
            return;
        }
        plugin.games().shutdown();
        plugin.reloadEverything();
        plugin.message(sender, "<green>Configuration reloaded. Games in progress were refunded.</green>");
    }

    // --------------------------------------------------------------- stations

    private void station(CommandSender sender, String[] args) {
        if (!require(sender, "casino.admin")) {
            return;
        }
        if (args.length < 2) {
            plugin.message(sender, "<gray>Usage: <white>/casino station add|remove|list</white></gray>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> stationAdd(sender, args);
            case "remove", "delete" -> stationRemove(sender);
            case "list" -> stationList(sender);
            default -> plugin.message(sender, "<gray>Usage: <white>/casino station add|remove|list</white></gray>");
        }
    }

    private void stationAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.message(sender, "<red>Only a player can point at a block.</red>");
            return;
        }
        if (args.length < 3) {
            plugin.message(player, "<gray>Usage: <white>/casino station add "
                    + "cashier|blackjack|roulette|poker</white></gray>");
            return;
        }
        StationType type = StationType.fromString(args[2]);
        if (type == null) {
            plugin.message(player, "<red>Unknown station type <white>" + args[2] + "</white>.</red>");
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            plugin.message(player, "<red>Look at a block within six blocks of you.</red>");
            return;
        }
        Station existing = plugin.stations().at(target.getLocation());
        if (existing != null) {
            plugin.message(player, "<red>That block is already the <white>" + existing.type().displayName()
                    + "</white> station <white>" + existing.id() + "</white>.</red>");
            return;
        }
        Station station = new Station(plugin.stations().nextId(type), type, target.getLocation());
        plugin.stations().add(station);
        plugin.stationStore().save(plugin.stations());
        plugin.message(player, "<green>Registered <white>" + station.id() + "</white> as a <white>"
                + type.displayName() + "</white> station.</green>");
    }

    private void stationRemove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.message(sender, "<red>Only a player can point at a block.</red>");
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            plugin.message(player, "<red>Look at a block within six blocks of you.</red>");
            return;
        }
        Station removed = plugin.stations().removeAt(target.getLocation());
        if (removed == null) {
            plugin.message(player, "<red>That block is not a registered station.</red>");
            return;
        }
        plugin.stationStore().save(plugin.stations());
        plugin.message(player, "<green>Unregistered <white>" + removed.id() + "</white>.</green>");
    }

    private void stationList(CommandSender sender) {
        if (plugin.stations().size() == 0) {
            plugin.message(sender, "<gray>No stations are registered.</gray>");
            return;
        }
        plugin.message(sender, "<gold>Registered stations (" + plugin.stations().size() + "):</gold>");
        for (Station station : plugin.stations().all()) {
            plugin.message(sender, "<gray>- <white>" + station.id() + "</white> "
                    + station.type().color() + station.type().displayName() + "</gray> <dark_gray>at "
                    + station.world().getName() + " " + station.location().getBlockX() + ", "
                    + station.location().getBlockY() + ", " + station.location().getBlockZ() + "</dark_gray>");
        }
        plugin.message(sender, "<gray>Poker module: <white>"
                + plugin.pokerHook().moduleName() + "</white></gray>");
    }

    // ---------------------------------------------------------------- players

    private void chips(CommandSender sender, String[] args) {
        if (!require(sender, "casino.use") || !(sender instanceof Player player)) {
            if (sender.hasPermission("casino.use")) {
                plugin.message(sender, "<red>Only a player has a chip balance.</red>");
            }
            return;
        }
        if (args.length < 3) {
            plugin.message(player, "<gray>Usage: <white>/casino chips buy 100</white> "
                    + "or <white>/casino chips sell 100</white></gray>");
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            plugin.message(player, "<red><white>" + args[2] + "</white> is not a whole number.</red>");
            return;
        }

        ExchangeResult result = switch (args[1].toLowerCase()) {
            case "buy" -> plugin.chipBank().buy(player, amount);
            case "sell", "cash", "cashout" -> plugin.chipBank().sell(player, amount);
            default -> ExchangeResult.fail("<gray>Usage: <white>/casino chips buy|sell 100</white></gray>");
        };
        plugin.message(player, result.message());
        if (result.success()) {
            plugin.message(player, "<gray>Chip balance: <white>"
                    + Text.chips(plugin.chipBank().balance(player)) + "</white></gray>");
        }
    }

    private void balance(CommandSender sender) {
        if (!require(sender, "casino.use") || !(sender instanceof Player player)) {
            if (sender.hasPermission("casino.use")) {
                plugin.message(sender, "<red>Only a player has a chip balance.</red>");
            }
            return;
        }
        plugin.message(player, "<gold>Chips: <white>"
                + Text.chips(plugin.chipBank().balance(player)) + "</white></gold>");
        plugin.message(player, "<gray>Money: <white>"
                + plugin.chipBank().vault().format(plugin.chipBank().vault().balance(player))
                + "</white>, one chip costs <white>" + Text.money(plugin.config().chipPrice())
                + "</white></gray>");
    }

    private void leave(CommandSender sender) {
        if (!require(sender, "casino.use") || !(sender instanceof Player player)) {
            return;
        }
        long refunded = plugin.games().leave(player.getUniqueId());
        player.closeInventory();
        if (refunded > 0) {
            plugin.message(player, "<yellow>You left the table. <white>" + Text.chips(refunded)
                    + "</white> chips were refunded.</yellow>");
        } else {
            plugin.message(player, "<gray>You are not in a game.</gray>");
        }
    }

    // ---------------------------------------------------------------- helpers

    private void help(CommandSender sender) {
        plugin.message(sender, "<gold><bold>Casino</bold></gold>");
        if (sender.hasPermission("casino.use")) {
            plugin.message(sender, "<gray>/casino chips buy 100 <dark_gray>- buy chips</dark_gray></gray>");
            plugin.message(sender, "<gray>/casino chips sell 100 <dark_gray>- cash out</dark_gray></gray>");
            plugin.message(sender, "<gray>/casino balance <dark_gray>- your chips and money</dark_gray></gray>");
            plugin.message(sender, "<gray>/casino leave <dark_gray>- leave your table</dark_gray></gray>");
        }
        if (sender.hasPermission("casino.admin")) {
            plugin.message(sender, "<gray>/casino spawn <dark_gray>- build at world spawn</dark_gray></gray>");
            plugin.message(sender, "<gray>/casino remove <dark_gray>- undo the build</dark_gray></gray>");
            plugin.message(sender, "<gray>/casino station add|remove|list</gray>");
            plugin.message(sender, "<gray>/casino reload</gray>");
        }
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.message(sender, "<red>You do not have permission to do that.</red>");
        return false;
    }
}
