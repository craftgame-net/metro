# Installation

## Requirements
- Paper 26.1 or newer
- Java 25

## Install
1. Put `Metro-Paper-<version>.jar` in `plugins/`.
2. Restart the server.
3. Give `metro.build` to the players who will build the network.

Metro loads after Multiverse-Core, so stations in worlds it loads work from
the first start.

## Build from source
You need JDK 25.

```shell
./gradlew build
```

The jar is `build/libs/Metro-Paper-<version>.jar`.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `metro.ride` | everyone | boarding the metro |
| `metro.build` | operators | editor mode, the crowbar, every editing command, `/metro rebuild`, `/metro issues` and its `[tp]` links |
| `metro.reload` | operators | `/metro reload` |
| `metro.debug` | operators | `/metro debug` |
| `metro.admin` | operators | all of the above |

Give `metro.build` only to trusted builders who know what they are doing. Every builder edits the same
network, and every edit changes it for all riders. [[Commands]] lists which
command needs which permission.

## Files
All files are in `plugins/Metro/`:

- `config.yml`: speeds, timings and editor settings (see below).
- `lang.yml`: every text players see. New keys from an update work without
  changes to your copy.
- `data.yml`: the network. Edit it by hand only while the server is stopped.
  If you edit it while the server runs, run `/metro reload` at once, or the
  next edit in game writes over your changes. See [[Maintenance]].
- `players.yml`: riders who left during a ride, so they can be put back on
  their platform when they return.

Run `/metro reload` after you change `config.yml` or `lang.yml`.

## config.yml
A missing key uses its default. A value out of range is changed to the nearest
allowed value, and the console shows a warning.

### speed
All speeds are in blocks per tick (20 ticks = 1 second), and the cart really
moves this fast with a rider in it. 0.6 is 12 blocks per second. Each speed
must be at least 0.05.

Speeds are saved into the routes when they are built. After you change any of
them except `turn`, run `/metro rebuild`. Rides take the fastest route by
travel time, so speeds can also change which way a ride goes.

| Key | Default | Meaning |
| --- | --- | --- |
| `max` | 1.2 | Top speed for everything. At most 1.5, the vanilla limit for a cart with a rider. |
| `max-cruise` | 1.0 | Top speed for a line's own speed (`/metro line speed`). At most `max`. |
| `cruise` | 0.6 | Main track. Between two stations of one line, the line's own speed is used instead, if it has one (see [[Lines]]). At most `max-cruise`. |
| `station` | 0.3 | Track at a platform where the cart stops: the platform and its pocket. |
| `through` | 0.55 | Track through a station where the cart does not stop. |
| `branch` | 0.3 | Switch of a junction that has no speed of its own (see [[Junctions]]). |
| `turn` | 0.35 | Sharp turns of 90 degrees or more. Used from the next ride, no rebuild needed. |

`station`, `through`, `branch` and `turn` are at most `max`.

### motion

| Key | Default | Meaning |
| --- | --- | --- |
| `acceleration` | 0.02 | Speed the cart gains each tick, in blocks per tick. Above 0, at most `speed.max`. |
| `deceleration` | 0.03 | Speed the cart loses each tick when it brakes. Same range. |
| `slow-lead` | 6 | Blocks before a slower part (a station, a sharp turn, a switch) where the cart is already at the lower speed. 0 to 16. |
| `slow-trail` | 2 | Blocks after a slower part before the cart speeds up again. 0 to 16. |
| `slope-speed` | 0.6 | Speed limit on a slope, up or down, in blocks per tick. A track that is slower keeps its own speed. At least 0.05, at most `speed.max`; `speed.max` turns it off. |
| `slope-lead` | 2 | Blocks before a slope where the cart already runs at the slope speed. 0 to 16. It is counted from the middle of the first slope rail, so use 1 or more. |
| `slope-trail` | 2 | Blocks after a slope before the cart speeds up again. 0 to 16. It is counted from the middle of the first rail after the slope. |

### timing

| Key | Default | Meaning |
| --- | --- | --- |
| `departure` | 0 | Ticks the cart waits at `begin` before it leaves. |
| `eject-delay` | 20 | Ticks the cart waits at `eject` before the rider is set down. |

A negative value is used as 0.

### Other keys

| Key | Default | Meaning |
| --- | --- | --- |
| `docs-url` | this wiki | Where the `[?]` links of `/metro issues` point. Set it if you host your own copy of the wiki. |
| `gui.destination-material` | `COMPASS` | Station icon in the boarding menu when a station has none. |
| `authoring.crowbar-material` | `TRIDENT` | Item used as the crowbar. |
| `rebuild.max-search` | 5000 | Rail blocks one search may visit during a rebuild (at least 16). See [Search gave up](Maintenance#search-gave-up). |
| `preview.enabled` | `true` | Show the route particles in editor mode. |
| `preview.route-color` | `#FFFFFF` | Colour of the route particles. |
| `preview.interval` | 10 | Ticks between two particle frames (at least 2). |
| `preview.radius` | 48 | Particles are shown only within this many blocks of you. |
