`:core` is platform-free: no Bukkit, Paper, Adventure or Brigadier here, not even in tests; server access goes
through the interfaces in `rail/`, `platform/` and `ride/`.

## Server access

- The interfaces are `RailWorlds` (with `RailWorld`), `Scheduler`, `RideCart` and `RideHost`. `:paper` implements them.
  Read and set rails only through `RailWorlds`, and schedule only through `Scheduler`.
- All of them are called on the server thread that owns the blocks.
- A class that needs a server type belongs in `:paper`. Pass what `:core` needs as a lambda or an interface
  (for example the loaded-world check and the icon check of `DataStore`, the default line name).

## Geometry

- `Vec3` and `Point` are data classes, so `==` compares doubles exactly (`0.0 != -0.0`). Compare blocks (`block()`,
  `sameBlock`) or use a tolerance.
- A `Point`'s world is the world name, as `data.yml` stores it. `IssueTarget.Block` uses the world key instead, because
  a command's world argument reads the key.
- Item icons in the model are item names (`COMPASS`), not materials.

## Stored files

- `DataStore` and `PlayerStateStore` read and write YAML only through `data/YamlSection` (SnakeYAML). It behaves like
  Bukkit's `YamlConfiguration`, so older plugin versions read the files and the reverse. Keep it that way:
  - On read, keys become text (an unquoted `yes` key is `"true"`, `0` is `"0"`), a dot in a key nests the value and
    null values are dropped.
  - `string` turns any value into text. `int`, `double` and `boolean` take only a value of that type and otherwise
    return the default (`'0.5'` reads as 0).
  - `write` gives block style with a 2-space indent and 80-column lines, writes maps under maps as sections and never
    uses anchors.
  - Store each id as one key. Ids hold no dots.
- `DataFileFormatTest` compares against files that Bukkit read and wrote (`core/src/test/resources/data/`). A change
  there is a format change.
- SnakeYAML is `compileOnly` (and `testImplementation`) at the version paper-api brings, see `core/build.gradle.kts`.
  The server provides it, so the jar must not bundle it. Move the version when paper-api changes its own.
- `Network.version` grows on every mutator, `load()` and `save()`. Mutators do not save, so callers do. Anything cached
  per data version (routing graph, menu options, detour hints) depends on that.

## Tests

- JUnit 5 and `kotlin.test`, no server and no server API on the classpath.
- Tests for pure logic live in `:core` next to the logic.
- Build setups from the shared helpers instead of copying them: `Fixtures.kt` (networks, points, edges, `testStore`),
  `rail/FakeRails.kt` (in-memory rails) and `ride/FakeCart.kt` (fake cart and recording host). Add a missing helper
  there.
- Stores (`DataStore`, `PlayerStateStore`) are tested with `@TempDir` round trips.
- Debug output is checked with `RecordingDebugLog`.
