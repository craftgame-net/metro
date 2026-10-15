# Data model

How `Network` and `DataStore` hold the graph and what the ids mean. YAML format rules are in `core/CLAUDE.md`.

## Network and DataStore

- `DataStore` overrides `addStation`, `deleteLine` and `putJunction` so its raw entries for unloaded worlds stay in
  step. Replacing an object drops its raw entry.
- Deleting a station, platform or junction also prunes the edges that touch it, so the saved graph stays consistent
  until the next rebuild.
- `load()` first drains pending background writes (`SnapshotWriter.flush`), so it never reads a stale file.
- `save()` snapshots on the calling thread. `SnapshotWriter` writes atomically on one background thread and keeps only
  the latest pending snapshot. After `close`, writes run on the calling thread. `PlayerStateStore` uses it too.
- After a parse failure `save()` writes nothing until a later `load()` succeeds. Editors save through
  `EditManager.save(player)` for that reason.
- `SCHEMA_VERSION` is written as `version` but `load()` does not read it yet. When the format changes, bump it and
  handle old data in `load()`.

## Ids and nodes

- Platform ids are strings, numeric from 0, possibly with gaps. The only `+ 1` allowed is the fallback name of an
  unnamed platform (`Platform.fallbackNumber()`).
- Routing nodes are built and parsed only by `Nodes`. Junction ids hold no colon, so `Nodes.parse` returns null for a
  junction. Code that handles station nodes relies on that to skip junctions.

## Platforms and edges

- `Platform.boards` is the one test for "riders can board here": no route leaves `begin` without `out`.
  `Platform.alights` is its counterpart.
- Edge `points` are stored compressed (`route/Polyline`) and densified to one point per block for rides.
  `EdgeCost` and `ChunkSet` step the stored segments arithmetically instead.
- Edge `switches` null means unknown, so the ride checks every point.
- `SwitchZone` slows the first or last `SwitchZone.SEGMENTS` dense segments. `RoutePlanner` and `EdgeCost` both apply
  it, so they must stay in step.
- `auto = true` edges are regenerated on every rebuild. `auto = false` edges are drawn by hand and survive it.
