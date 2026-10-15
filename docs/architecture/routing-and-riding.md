# Routing and riding

Traps in route search, boarding and the per-tick ride.

## Routing

- `EdgeCost` has no acceleration and no lead or trail window: the full ride model is too costly per Dijkstra step.
  `RoutePlanner.plan` is the one entry for boarding and the menu estimate, so they agree.
- `allowed` has two rules. A ride may start or end at a terminal station but not pass through it and circle back on
  its own line. A ride may not pass through another platform of its start station (a U-turn on a junction), but may
  end there. Its inputs (terminal flag, line) are worked out when the graph is built, so a change of terminal or line
  must save, like any edit.
- The graph is reused until `Network.version`, the speeds, the slope speed or the virtual-edge reporting state
  changes. Reporting is `debugLog.enabled(DECISION, null)`, because virtual edges are only reported while the graph is
  built.
- `BoardingService` option and route caches drop when the data version or `MetroConfig.generation` changes.

## Boarding

- The boarding under way lives only in `BoardingMenuHolder`. Closing the menu, quitting or dying drops it with
  nothing to clean up.
- On a pick, `BoardingService` checks the player is still near the trigger before it spawns the cart.

## Rides

- Distance comes from the cart's real position, so a blocked cart does not run ahead on paper.
  `RouteTrack.projectedDistance` looks only a few ticks ahead and prefers the smallest jump among equally near
  candidates, so hairpins do not snap. `RouteTrack.segmentAt` is the one speed-limit index for rides and `RideTime`.
- A slope is a dense step of one block along an axis and one block up or down (`route/Slopes`). Path points are rail
  centres, so it lies half a block after the real slope. A slope speed equal to `speed.max` turns it off.
- A `Ride` keeps the `MetroConfig.physics` it was planned with, so `/metro reload` mid-ride does not mix settings.
- Vanilla moves an occupied cart only 0.75 of its horizontal velocity per tick. `Steering` divides by
  `PASSENGER_SCALE`, so configured speeds are real blocks per tick.
- `RideSwitches` plans shapes at departure from geometry alone, with no block reads. It reads each rail when the cart
  comes within reach and restores it after the cart passes, only if the rail still has the ride's shape.
- Every end without arrival goes through `Rides.abort(ride, RideAbort, rollback)`. It restores the rails, removes the
  cart and releases the chunks. A rolled back online rider goes to the platform they boarded at.
- Getting out, quitting or dying while the ride waits at `eject`, and `stopAll()` on disable, count as a normal
  arrival (`Rides.finish`).
- A ride holds the chunk of the cart and the chunk 16 blocks ahead (`Rides.CHUNK_LOOKAHEAD`), once each, until it
  ends.

## Riders who drop out

- `RideGuardListener` blocks dismount and sneak until the ride is ARRIVED. `RideCleanupListener` removes tagged carts
  that no active ride owns.
- `PlayerStateStore` writes to `players.yml` only riders who dropped out mid-ride. A record without a platform is
  ignored. The rider is put back on respawn or join; `rescueSpot` falls back to the world spawn when the station is
  gone.
