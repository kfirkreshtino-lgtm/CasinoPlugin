package com.kfir.casino;

import com.kfir.casino.api.CasinoAPI;
import com.kfir.casino.command.CasinoCommand;
import com.kfir.casino.command.CasinoTabCompleter;
import com.kfir.casino.economy.ChipBank;
import com.kfir.casino.game.GameManager;
import com.kfir.casino.game.poker.PokerHook;
import com.kfir.casino.game.poker.UnavailablePokerHook;
import com.kfir.casino.gui.CashierMenu;
import com.kfir.casino.gui.MenuListener;
import com.kfir.casino.station.Station;
import com.kfir.casino.station.StationListener;
import com.kfir.casino.station.StationRegistry;
import com.kfir.casino.station.StationType;
import com.kfir.casino.storage.StationStore;
import com.kfir.casino.storage.YamlChipStore;
import com.kfir.casino.structure.ProceduralBuilder;
import com.kfir.casino.structure.StructureService;
import com.kfir.casino.util.Tasks;
import com.kfir.casino.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/**
 * Plugin entry point and the object every other class hangs off.
 *
 * <p>Wiring order matters: config, then storage, then the registries, then the games.
 */
public final class CasinoPlugin extends JavaPlugin implements CasinoAPI {

    private CasinoConfig config;
    private YamlChipStore chipStore;
    private ChipBank chipBank;
    private StationRegistry stations;
    private StationStore stationStore;
    private StructureService structures;
    private GameManager games;
    private PokerHook pokerHook;

    private BukkitTask autosaveTask;
    private BukkitTask sweepTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new CasinoConfig(getConfig());

        this.chipStore = new YamlChipStore(this);
        this.chipStore.load();
        this.chipBank = new ChipBank(this, chipStore);

        this.stations = new StationRegistry();
        this.stationStore = new StationStore(this);
        this.stationStore.load(stations);

        this.structures = new StructureService(this, new ProceduralBuilder());
        this.games = new GameManager(this);
        this.pokerHook = new UnavailablePokerHook(this);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new StationListener(this), this);
        getServer().getPluginManager().registerEvents(games, this);

        PluginCommand command = getCommand("casino");
        if (command != null) {
            command.setExecutor(new CasinoCommand(this));
            command.setTabCompleter(new CasinoTabCompleter());
        } else {
            getLogger().severe("The casino command is missing from plugin.yml.");
        }

        getServer().getServicesManager().register(CasinoAPI.class, this, this, ServicePriority.Normal);
        startAutosave();

        getLogger().info("Casino ready. Blackjack and roulette are live; poker is "
                + pokerHook.moduleName() + ".");
    }

    @Override
    public void onDisable() {
        Tasks.cancel(autosaveTask);
        Tasks.cancel(sweepTask);
        if (games != null) {
            games.shutdown();
        }
        if (chipStore != null) {
            chipStore.saveNow();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    private void startAutosave() {
        Tasks.cancel(autosaveTask);
        long period = config.autosaveSeconds() * 20L;
        autosaveTask = Tasks.timer(this, period, period, () -> chipStore.saveAsync());
        Tasks.cancel(sweepTask);
        sweepTask = Tasks.timer(this, 200L, 200L, () -> games.sweepAbandonedTables());
    }

    /** Reloads config.yml and stations.yml. Games are refunded by the caller first. */
    public void reloadEverything() {
        reloadConfig();
        this.config = new CasinoConfig(getConfig());
        this.stationStore.load(stations);
        startAutosave();
    }

    /** Opens the menu that belongs to a station. */
    public void openStation(Player player, Station station) {
        switch (station.type()) {
            case CASHIER -> new CashierMenu(this, player).open();
            case BLACKJACK -> games.openBlackjack(player, station);
            case ROULETTE -> games.openRoulette(player, station);
            case POKER -> {
                if (!config.pokerEnabled()) {
                    message(player, "<yellow>The poker room is closed.</yellow>");
                } else {
                    pokerHook.openTable(player, station);
                }
            }
        }
    }

    public void message(CommandSender sender, String miniMessage) {
        sender.sendMessage(Text.mm(config.prefix() + miniMessage));
    }

    // ------------------------------------------------------------ public API

    @Override
    public void message(Player player, String miniMessage) {
        message((CommandSender) player, miniMessage);
    }

    @Override
    public long getChips(UUID playerId) {
        return chipBank.balance(playerId);
    }

    @Override
    public boolean takeChips(UUID playerId, long chips) {
        return chipBank.take(playerId, chips);
    }

    @Override
    public void giveChips(UUID playerId, long chips) {
        chipBank.give(playerId, chips);
    }

    @Override
    public int chipsPerDiamond() {
        return config.chipsPerDiamond();
    }

    @Override
    public List<Station> stations(StationType type) {
        return stations.ofType(type);
    }

    @Override
    public void registerPokerHook(PokerHook hook) {
        this.pokerHook = hook == null ? new UnavailablePokerHook(this) : hook;
        getLogger().info("Poker module is now: " + this.pokerHook.moduleName());
    }

    @Override
    public boolean isBusy(UUID playerId) {
        return games.isBusy(playerId);
    }

    // ------------------------------------------------------------- accessors

    public CasinoConfig config() {
        return config;
    }

    public ChipBank chipBank() {
        return chipBank;
    }

    public StationRegistry stations() {
        return stations;
    }

    public StationStore stationStore() {
        return stationStore;
    }

    public StructureService structures() {
        return structures;
    }

    public GameManager games() {
        return games;
    }

    public PokerHook pokerHook() {
        return pokerHook;
    }
}
