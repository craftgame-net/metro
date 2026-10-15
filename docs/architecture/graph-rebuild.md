# Graph rebuild

How `/metro rebuild` turns anchors and junctions into auto edges, and how its findings reach `/metro issues`.

## Order and traps

1. `ChunkPreloader` loads the chunks of `ChunkSet.forRebuild(network)` one preload at a time. They stay held until the
   rebuild callback returns, because the path search reads blocks.
2. `EdgeBuilder.rebuild(input)` runs on the server thread. Switch marks of hand-authored edges are recomputed first
   (`SwitchMarks.remark`), because their points may have changed since the marks were saved.
3. Pocket edges come before `TrackChain`, which needs a seed with a known direction: a platform `heading` or one read
   from the rails (`PocketHeadings`).
4. A junction is a transit point on its straight path. Branch exits and crossing turns are added only after both
   tracks through it have a direction. A branch result marked `settled` is not asked again.
5. `Network.replaceAutoEdges` swaps edges in only when all are built. A failed rebuild is logged and not saved.

## Rail search

- `RailPathfinder` ignores the shape of level rails. A shape holds only two directions, so switches would break the
  search. A step up or down needs a slope pointing along it (`rail/RailSlopes`).
- Dropped or merged links are reported: `junction-side-split` (`JunctionGeometry.turnsBack`),
  `junction-join-backward` (`GraphChecks.dropBackwardRamps`), `point-shared-block`, `point-on-junction`.

## Speeds

- A branch or ramp has a junction end, so it runs at `speed.cruise`. The junction's speed (else `speed.branch`) is
  only its switch zone: the start of a branch leaving a `split`, the end of a ramp entering a `join` sideways.
- Changing a line's speed or moving a station to another line needs a rebuild, because `LinkSpeed` bakes speeds.

## Report

- `RebuildReport` is frozen at rebuild time. It also snapshots the terminal stations.
- `/metro reload` is refused while `chunkPreloader.running`, so a rebuild never saves a graph built from data that
  reload replaces.
- `Issues` drops findings whose station, platform or junction was deleted since.
- One-way detour hints come from the saved graph, not from the report.

## Checklist: adding a rebuild warning

1. Create it with `WarningText.warning(key, target, ...)`, with the right `IssueTarget`.
2. Add the text `command.<key>` to `lang.yml`.
3. Add the key to `RebuildWarnings.ANCHORS`, mapped to the wiki heading anchor.
4. Add a matching heading under "Rebuild warnings" in `wiki/Maintenance.md`. This is the one wiki edit that goes with
   code work.
5. If the finding keeps something out of the graph, add the key to `RebuildWarning.ERROR_KEYS`.
