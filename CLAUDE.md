# CLAUDE.md

## What this is

Metro is a Paper plugin (Kotlin, Paper API 26.1, Java 25) for a minecart metro on ordinary vanilla rails. A rider
boards at a platform, picks a destination in a menu, and the plugin drives the cart there by setting its velocity
every tick.
Player docs live in `wiki/` and are synced to the GitHub wiki when a tag is pushed (`wiki-sync.yml`). Update the
matching page when behaviour that players see changes. The rules for editing it are in `wiki/CLAUDE.md`.

## Commands

```shell
./gradlew build # compile + ktlint + detekt + tests, both modules; jar -> build/libs/Metro-Paper-<version>.jar
./gradlew test # tests only
./gradlew :core:test --tests 'net.craftgame.metro.route.RouterTest' # one class (name its module)
./gradlew :core:test --tests 'net.craftgame.metro.route.RouterTest.someName' # one method
./gradlew ktlintFormat # auto-fix style
./gradlew ktlintCheck detekt detektMain detektTest # lint only; build runs `detekt`, the type-resolving
                                                  # detektMain/detektTest are extra and also pass
```

## Build traps

- Two modules: `:core` (`core/`) and `:paper` (`paper/`). Shared build settings (Kotlin, toolchain 25,
  `allWarningsAsErrors`, ktlint, detekt, JUnit) are in the root `build.gradle.kts`.
- `allWarningsAsErrors` is on: any compiler warning fails the build.
- ktlint (`ktlint_official`) allows 140 characters per line (`.editorconfig`), but detekt's default `MaxLineLength` is
  120. Keep lines within 120. Write multi-parameter functions with one parameter per line.
- detekt limits (`config/detekt/detekt.yml`): at most 7 function parameters, 8 constructor parameters and 20
  non-private functions per class or object.
- `:paper` sets `config/detekt/baseline.xml` for detekt; `baseline-main.xml` sits next to it. Both hold only
  `EditManager`'s `TooManyFunctions`, and `:core` has none. Add no entries. Renaming a baselined function changes its
  key, so update the entry.
- `:paper` writes the plugin jar to the root `build/libs/Metro-Paper-<version>.jar` with `:core`'s classes unpacked
  into it. The version comes from the latest `v*` git tag (`gitVersion()` in the root `build.gradle.kts`), so a build
  needs the git history.
- The Kotlin stdlib comes from the server through the `libraries` entry of `paper/src/main/resources/plugin.yml`.
- CI: `build.yml` runs `./gradlew build` on `master` and tags (tags: draft release). `quality.yml` runs
  `.github/scripts/check-rules.sh` (greps forbidden patterns) and `./gradlew build detektMain
  detektTest` on pull requests.

## Code and comment rules

- Follow the lint rules instead of working around them: no `@Suppress`, no raised limits, no new baseline entries.
  Fix a new detekt finding in the code (name a constant, split the function, use a guard clause).
- Names say what the code does (`openMenu` opens the menu, `firstVisitSpot` returns the first platform's spot). Do not
  duplicate a helper that already exists (for example `Nodes.stop` builds `station:platform`).
- Comments (KDoc and inline) are in plain English: short sentences, common words, no em dashes, no filler or
  promotional words. They say why and what, never how, and the best comment is the one you did not need.
  - Class KDoc: one sentence of purpose. Add responsibility and limits only if they are not obvious. For an interface or
    base class, say what implementers must do. Do not describe other classes, files or visible behaviour.
  - Method KDoc: only when it adds something the name and signature do not: verb + result, parameter meaning and
    units, special return values (null, -1, empty), errors, side effects (state, I/O, threads, tickets), preconditions.
    None for self-explanatory methods or overrides that keep the contract.
  - Say on which thread a callback runs and who releases what the call keeps held. Give units (blocks per tick, ticks)
    and what `null` means where it is special.
  - Inline comments only for a non-obvious reason, a trap, a workaround, a magic number's source or an optimisation.
    Say what goes wrong otherwise. Never retell the code.
  - No comments about requests, requirements, users, agents or the history of a decision. No commented-out code,
    authors or dates (git has them). Use one term for one thing within a file.

## Modules

- `:core` has the platform-free logic: geometry, data model, routing, rebuild, ride logic, stored files.
- `:paper` has everything that touches the server: commands, listeners, menus, the cart adapter, chunk loading.
- Both use the same package names. A class belongs in `:core` as soon as it needs no server type, and its tests go
  with it. `core/CLAUDE.md` and `paper/CLAUDE.md` load when you work in those directories.
- `MetroPlugin` (`:paper`) is the composition root. It builds every service in `onEnable` and exposes them as
  `lateinit` properties, so most classes take the plugin and reach siblings through it.
- `:core` reaches the server only through interfaces in `rail/`, `platform/` and `ride/`. `:paper` implements them in
  its `platform/`, `rail/` and `ride/` packages.

## Invariants and traps

- Show a platform id as `station:platform` through `Nodes.stop` in chat, menus, labels and warnings. Never show its
  position in the list: ids are strings that start at 0 and may have gaps.
- Editors change model objects in place and must save afterwards. The save bumps `Network.version`, and the routing
  and menu caches key on it, so an unsaved edit leaves stale routes.
- Only `/metro rebuild` rebuilds the graph. Nothing is rebuilt on enable: the saved graph in `data.yml` is used as it
  is. Editor actions save and prompt for a rebuild instead.
- Never call `Bukkit.getScheduler()` or read rail blocks outside the platform adapters (`Scheduler`, `RailWorlds`).
  `:core` code runs on the server thread that owns the blocks.
- Chunk tickets go only through `ChunkTickets`. Bukkit plugin tickets are on/off per plugin, so it ref-counts them
  between rides and the chunk preloader.
- All player text lives in `paper/src/main/resources/lang.yml`. Do not hard-code text.
- `:core` never depends on Bukkit, Paper, Adventure or Brigadier, not even in tests.
- `Speeds.MIN` is the floor for every speed (config, commands, `data.yml`, routing), so no stretch gets a limit of 0.

## Further documentation

Read these before changing the named code. Only the nested `CLAUDE.md` files load by themselves.

- `docs/architecture/data-model.md`: when changing `Network`, `DataStore`, the schema version, `Edge`, `Nodes`,
  `Station`, `Platform` or anchor roles.
- `docs/architecture/graph-rebuild.md`: when changing `EdgeBuilder`, `TrackChain`, `RailPathfinder`,
  `JunctionGeometry`, `LinkSpeed`, `RebuildReport`, `/metro rebuild` or when adding a rebuild warning.
- `docs/architecture/routing-and-riding.md`: when changing `Router`, `RoutePlanner`, `BoardingService`, `Rides`,
  `Ride`, `SpeedProfile`, `Steering`, `StallTracker`, chunk holds, `players.yml` or rider rescue.
- `docs/architecture/editing-and-debug.md`: when changing `EditManager`, editor menus, `PointEditor`, `HoverLabel`,
  command permissions, `DebugLog`, `DebugHub` or `/metro debug`.
- `wiki/CLAUDE.md`: when editing `wiki/**`; it loads automatically in that directory.
- `core/CLAUDE.md`: `:core` rules, YAML format, test helpers. `paper/CLAUDE.md`: `:paper` rules, commands, `lang.yml`,
  `config.yml`.
