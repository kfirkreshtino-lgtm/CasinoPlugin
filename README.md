# CasinoPlugin

A casino for Paper servers with Blackjack, Roulette and Texas Hold'em poker. Poker is
built in but still sits behind a published interface, so another plugin can replace it.

## Requirements

- Paper `26.1.2`. The Maven artifact is `io.papermc.paper:paper-api:26.1.2.build.74-stable`,
  the newest stable build of that release. PaperMC publishes per-build coordinates rather
  than a `-R0.1-SNAPSHOT` version.
- Java 25 (Paper 26.1+ requires it)

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
| `/casino chips buy <diamonds\|all>` | `casino.use` | Trades diamonds from your inventory for chips |
| `/casino chips sell <diamonds\|all>` | `casino.use` | Cashes chips back into diamonds |
| `/casino balance` | `casino.use` | Shows chips and diamonds |
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

**Chips are bought with diamonds only.** The cashier takes plain diamonds from the
inventory once, cashing out hands them back once at the same rate, and every bet moves
chips only. No economy plugin is needed. The rate is `economy.chips-per-diamond`.

**The game is played on a table, not in a chest.** Cards are display entities that slide
out of the dealer shoe and turn face up where they land. Hand totals, the current bet and
the result float over the felt. The player sits in a chair and watches the table.

An inventory appears in exactly two places: choosing a stake before the hand, and a single
row of hit, stand and double that opens by itself when the table stops moving and it is the
player's turn. It closes the instant they choose, so an inventory is never covering the
table while something is happening.

**Cards are display entities rather than maps in item frames.** A filled map only renders
its picture inside an item frame, which pins the card to a whole block face. A text display
can be scaled to the proportions of a real card and stands on the felt turned towards
whoever looks at it, so it always reads the right way up. Cards appear in place with no
animation. Suit symbols come from the default font, so there is no resource pack to accept.

**No shaded GUI library.** `Menu` is an `InventoryHolder` that maps slots to click
handlers. One listener routes clicks and cancels everything else, so items cannot be pulled
out of a menu.

**Nothing in progress is persisted.** A hand lasts seconds. On shutdown, disconnect or
reload, open stakes are refunded rather than resumed. Restoring a half-played hand across a
restart is far more likely to lose chips than to save a game.

**Everything runs on the main thread.** Only the chip file write is asynchronous, and it
writes a snapshot copied on the main thread.

## Games

**Blackjack** is played at a physical table, one player per table against an automated
dealer, with four tables in the building. Right-clicking the seat marker sits you down and
opens the stake menu. Cards are dealt one at a time onto the felt, the dealer hole card
stays face down until they play, and the action menu appears only when it is your turn.

It implements hit, stand, double down on the first two cards, natural blackjack bonus, push,
bust detection, and a dealer who draws to seventeen with configurable soft-seventeen
behaviour. Splitting and insurance are not implemented.

Presentation is separated from the rules by the `BlackjackView` interface, so the table can
be restyled, or a second style added, without touching a single rule.

**Roulette** is one shared round per table, which is how a real spin works and is what lets
several players bet into the same result. The betting window opens on the first bet and
closes after a configurable number of seconds. Supported bets: straight up, red, black, odd,
even, low, high, three dozens and three columns. A player can place as many bets as they can
afford in one round. True adjacency bets (split, street, corner, line) are not implemented,
because selecting them needs a physical layout grid. European single zero is the default;
American is a config change.

**Texas Hold'em** is played at oval six-seat tables with a felt top, a wooden rail and
chairs. Right-click any part of a table to choose a buy-in and sit down. Chips you bring
leave your balance and stay on the table until you stand up, walk away or disconnect.

A hand needs at least two players (`poker.min-players`). Once enough are seated a countdown
starts, then the button moves, the blinds are posted and everyone is dealt two cards. When
it is your turn a menu opens with your cards, the board and fold, check or call, three raise
sizes and all in. Running out of time checks if you can and folds otherwise.

Rules: no-limit Texas Hold'em with small and big blinds, heads-up blind order, four betting
rounds, minimum raise of the last full raise, all in for less, a short all in does not
reopen raising, side pots, split pots with the odd chip left of the button, and showdown
with the best five of seven cards. The rules are in `HoldemHand` with no Minecraft code in
it, which is what the unit tests exercise.

**Nobody can see another player's hole cards, from any angle.** Each hole card is two
entities at the same spot: a face-down card that everyone except the owner is sent, and a
face-up card that only the owner is sent. The server never sends the face to anyone else,
so no position, camera trick or client mod can reveal it. Cards are only shown to the table
at showdown, and folded cards are taken off the table unseen.

## Replacing poker

The built-in poker is `HoldemPokerHook`. Another plugin can replace it by implementing
`com.kfir.casino.game.poker.PokerHook` and registering it:

```java
CasinoAPI casino = Bukkit.getServicesManager().load(CasinoAPI.class);
casino.registerPokerHook(new MyPokerHook());
```

Registering `null` goes back to the built-in game. Move chips with `CasinoAPI.takeChips`
and `CasinoAPI.giveChips`. Never take or hand out diamonds directly, or buy-ins and payouts
will drift out of step with the cashier.

`PokerHook` requires `openTable`, and should override `leave`, `isBusy`, `shutdown` and
`moduleName`. `leave` and `shutdown` must refund every open pot.

`Card`, `Rank`, `Suit` and `Deck` in `com.kfir.casino.game.card` are ready to reuse, and so
is `HandEvaluator`. The table pieces in `com.kfir.casino.table` are game agnostic:
`CardVisual` is a card, including private cards only one player can see, `Hologram` is a
floating line of text and `Seat` sits a player down.

## Files written to the data folder

- `config.yml` settings
- `chips.yml` chip balances by player id
- `stations.yml` registered interaction points
- `structure.yml` build footprint, terrain snapshot, station ids and label entity ids
