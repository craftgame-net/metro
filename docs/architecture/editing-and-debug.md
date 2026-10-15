# Editing and debug log

Editor traps and the debug log rules. Permissions are in `paper/CLAUDE.md`.

## Editor mode

- `EditManager` saves and offers a rebuild but never rebuilds.
- `PointEditor` finds the marker under the crosshair with `PointLookup`. The ray ignores blocks in the way.
  `EditListener` denies the vanilla item use, so a trident crowbar never charges a throw.
- On quit, `EditListener` clears the editor mode, selections, `PointEditor` and `HoverLabel` state and the debug watch.
- The editor refuses a rail anchor (`Platform.RAIL_ROLES`) on a junction's rail block, and a junction on the block of
  a rail anchor. Searches stop at a junction, so such an anchor would never link.
- `HoverLabel` rebuilds only when the block, the data version or the config generation changes.

## Debug log

- `log(level, subject) { text }` runs the lambda only when a viewer wants it. Build the text inside it, never outside.
- Levels (`DebugLevel`): `EVENT` (1) is safe in production (ride start and end, rebuild done, data load). `DECISION`
  (2) says why something happened (route, abort causes, boarding refusals). `DETAIL` (3) is per tick or per block.
- The subject is the UUID of the player a line is about. Null is a global line. Pass the player who started an action.
  A viewer with scope `Own` or `Player(id)` never sees global lines.
- `DebugHub` holds the viewers: the console or one player, each with a level, a `DebugScope` (`All`, `Own`,
  `Player(id)`) and a sink. Nothing is saved.
- A player can watch levels 1 and 2 only and gets at most 5 lines a second (`RateLimiter`), then a `suppressed`
  notice. The console can watch level 3, with no limit and no `Own` scope.
- `ConsoleDebugSink` logs at INFO, because Paper hides the finer levels.
- Log only on the server thread. The hub has no locks, and `SnapshotWriter` runs on its own thread, so log a save
  before the hand-off.
