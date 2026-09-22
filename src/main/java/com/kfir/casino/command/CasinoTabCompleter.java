package com.kfir.casino.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tab completion for the casino command, filtered by permission. */
public final class CasinoTabCompleter implements TabCompleter {

    private static final List<String> PLAYER_ROOTS = List.of("chips", "balance", "leave");
    private static final List<String> ADMIN_ROOTS = List.of("spawn", "remove", "station", "reload");
    private static final List<String> STATION_ACTIONS = List.of("add", "remove", "list");
    private static final List<String> STATION_TYPES = List.of("cashier", "blackjack", "roulette", "poker");
    private static final List<String> CHIP_ACTIONS = List.of("buy", "sell");
    private static final List<String> CHIP_AMOUNTS = List.of("10", "50", "100", "500", "1000");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("casino.use")) {
                options.addAll(PLAYER_ROOTS);
            }
            if (sender.hasPermission("casino.admin")) {
                options.addAll(ADMIN_ROOTS);
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("station") && sender.hasPermission("casino.admin")) {
                options.addAll(STATION_ACTIONS);
            } else if (args[0].equalsIgnoreCase("chips") && sender.hasPermission("casino.use")) {
                options.addAll(CHIP_ACTIONS);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("station") && args[1].equalsIgnoreCase("add")
                    && sender.hasPermission("casino.admin")) {
                options.addAll(STATION_TYPES);
            } else if (args[0].equalsIgnoreCase("chips") && sender.hasPermission("casino.use")) {
                options.addAll(CHIP_AMOUNTS);
            }
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
