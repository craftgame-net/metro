`:paper` holds everything that touches the server; keep logic that needs no server type in `:core`.

## Server boundary

- Convert `Location`, `Vector`, `World`, `Block` and `Rail.Shape` to and from `:core` types only in
  `util/BukkitGeo.kt`. Convert at the boundary there and add no conversions elsewhere.
- Schedule only through `PaperScheduler`.
- Chunk tickets go only through `route/ChunkTickets`, which ref-counts them between rides and the chunk preloader.
  Never call `addPluginChunkTicket` or `removePluginChunkTicket` directly.

## Commands and permissions

- Brigadier commands are registered through Paper's lifecycle `COMMANDS` event.
- Every node that needs a permission uses `.requires(permission(...))`, so a sender without it does not see the
  subcommand. `/metro help` lists only what the sender may use.
- Permissions are declared in `plugin.yml` and `config/Permissions.kt` (`:core`). Keep both in step:
  - `metro.ride`: board (default for everyone).
  - `metro.build`: editor, crowbar, every editing command, `/metro rebuild`, `/metro issues` and its `[tp]` links.
  - `metro.reload`: `/metro reload`.
  - `metro.debug`: `/metro debug`.
  - `metro.admin`: parent of the others. `/metro debug` needs it for `all` or another player.

## Editor menus

Editor menus place their slots through `edit/MenuLayout` (`:core`) and the shared `EditMenu` helpers. Labels stay one
short line.

## Text and config

- All player text is in `src/main/resources/lang.yml`, with legacy `&` colour codes and `{name}` placeholders. Read it
  with `metroConfig.message(key, "name" to value)`.
- `util/Text.fill` fills placeholders in one pass, so a value that contains `{other}` is not substituted again.
- The bundled `lang.yml` is flattened first and the server copy overlays it, so new keys work without users updating
  their file. Changing what an existing key means needs a new key (old server copies keep the old text). Drop the old
  key.
- `config.yml` is read into the typed `MetroConfig`. Missing keys fall back to named defaults.
- Speeds are validated and clamped with a logged warning. `Speeds.MIN` (0.05) is the floor and `speed.max` is at most
  1.5, the vanilla limit.
- `plugin.yml` has `${version}` expanded by Gradle.
- Resources are in `paper/src/main/resources/` (`:core` has none).

## Tests

Bukkit-bound classes (controller, listeners, menus) have no tests. `:paper` tests cover what needs server types but no
running server (`Text`).
