# CasinoPlugin

A casino for Paper servers. Blackjack and Roulette are implemented end to end.
Texas Holdem is deliberately not implemented: it is a separate module that plugs in
through a published interface.

## Requirements

- Paper `26.1.2`. The Maven artifact is `io.papermc.paper:paper-api:26.1.2.build.74-stable`,
  the newest stable build of that release. PaperMC publishes per-build coordinates rather
  than a `-R0.1-SNAPSHOT` version.
- Java 25 (Paper 26.1+ requires it)
- Vault, plus an economy provider such as EssentialsX

## Building

```
mvn clean package
```

The jar lands in `target/CasinoPlugin-1.0.0.jar`.

If your server reports a different API generation, two lines control it and they must
agree with each other:

- `pom.xml`, the `paper.version` property
- `src/main/resources/plugin.yml`, the `api-version` field

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/casino spawn` | `casino.admin` | Builds the casino at world spawn plus the configured offset |
| `/casino remove` | `casino.admin` | Removes the casino and restores the original terrain |
| `/casino station add <type>` | `casino.admin` | Registers the block you are looking at as cashier, blackjack, roulette or poker |
| `/casino station remove` | `casino.admin` | Unregisters the block you are looking at |
| `/casino station list` | `casino.admin` | Lists every registered station |
| `/casino reload` | `casino.admin` | Reloads config.yml and stations.yml |
| `/casino chips buy <amount>` | `casino.use` | Buys chips with currency |
| `/casino chips sell <amount>` | `casino.use` | Cashes chips back into currency |
| `/casino balance` | `casino.use` | Shows chips and money |
| `/casino leave` | `casino.use` | Leaves your table and refunds open bets |

`casino.admin` includes `casino.use`.

## Design decisions

**Procedural build, not a schematic.** A schematic paste would add WorldEdit as a runtime
dependency, ship a binary file inside the jar, and still need a hand-maintained table of
where each game table sits inside the paste. The procedural builder knows exactly where it
put every table, so it registers the stations itself. `StructureBuilder` is an interface and
`SchematicBuilder` is a documented stub, so swapping approaches later touches one class.

**Remove is a real undo.** Before the first block is placed, every non-air block in the
footprint is recorded as block data in `structure.yml`. Removing clears the footprint and
replays that snapshot, so building on a hillside is not destructive.

**Right-click on a table block, not pressure plates.** Plates fire when players walk past.
A right-click on a registered block is unambiguous and needs no extra entity. A floating
text display above each table names it.

**Chips are separate from Vault money.** Buying withdraws currency once, cashing out
deposits once, and every bet moves chips only. A failed economy transaction can therefore
never land in the middle of a dealt hand.

**No shaded GUI library.** `Menu` is an `InventoryHolder` that maps slots to click
handlers. One listener routes clicks and cancels everything else, so items cannot be pulled
out of a menu.

**Nothing in progress is persisted.** A hand lasts seconds. On shutdown, disconnect or
reload, open stakes are refunded rather than resumed. Restoring a half-played hand across a
restart is far more likely to lose chips than to save a game.

**Everything runs on the main thread.** Only the chip file write is asynchronous, and it
writes a snapshot copied on the main thread.

## Games

**Blackjack** is one instance per player, so any number of people can use the same table
block at once. It implements hit, stand, double down on the first two cards, natural
blackjack bonus, push, bust detection, and a dealer who draws to seventeen with configurable
soft-seventeen behaviour. Splitting and insurance are not implemented.

**Roulette** is one shared round per table, which is how a real spin works and is what lets
several players bet into the same result. The betting window opens on the first bet and
closes after a configurable number of seconds. Supported bets: straight up, red, black, odd,
even, low, high, three dozens and three columns. A player can place as many bets as they can
afford in one round. True adjacency bets (split, street, corner, line) are not implemented,
because selecting them needs a physical layout grid. European single zero is the default;
American is a config change.

## Poker integration

This plugin owns the building, the stations, the chip economy and the menu framework. The
poker module implements `com.kfir.casino.game.poker.PokerHook` and registers it:

```java
CasinoAPI casino = Bukkit.getServicesManager().load(CasinoAPI.class);
casino.registerPokerHook(new MyPokerHook());
```

Move chips with `CasinoAPI.takeChips` and `CasinoAPI.giveChips`. Never call Vault directly
from the poker module, or buy-ins and payouts will drift out of step with the cashier.

`PokerHook` requires `openTable`, and should override `leave`, `isBusy`, `shutdown` and
`moduleName`. `leave` and `shutdown` must refund every open pot.

Set `poker.enabled: true` in config.yml once the module is installed. Until then poker
stations exist in the building and tell players the room is closed, so the layout never has
to change.

`Card`, `Rank`, `Suit` and `Deck` in `com.kfir.casino.game.card` are ready to reuse.
`Rank.pokerValue()` returns two through fourteen for hand ranking.

## Files written to the data folder

- `config.yml` settings
- `chips.yml` chip balances by player id
- `stations.yml` registered interaction points
- `structure.yml` build footprint, terrain snapshot, station ids and label entity ids
