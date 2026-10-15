# Metro

[![Build](https://github.com/craftgame-net/metro/actions/workflows/build.yml/badge.svg)](https://github.com/craftgame-net/metro/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/craftgame-net/metro)](LICENSE)

A Paper plugin for a small minecart metro on ordinary rails. Riders pick a
station, the cart drives them there, and they get off.

Metro is an early version: the data format and commands may change between releases.

## Requirements

- Paper 26.1 or newer
- Java 25

## Installation

Put `Metro-Paper-<version>.jar` in the server's `plugins/` folder and restart. You can build it
yourself (see below) or drop in a release jar.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/metro editor` | `metro.build` | toggle edit mode |
| `/metro crowbar` | `metro.build` | get the editor tool |
| `/metro line create\|name\|icon\|speed\|delete ...` | `metro.build` | edit lines |
| `/metro station create\|name\|icon\|terminal\|line\|list\|teleport\|delete ...` | `metro.build` | edit and find stations |
| `/metro platform add\|name\|icon\|heading\|delete ...` | `metro.build` | edit platforms |
| `/metro anchor set\|clear ...` | `metro.build` | set or remove an anchor |
| `/metro junction create\|kind\|turns\|speed\|delete\|list\|teleport ...` | `metro.build` | edit and find junctions |
| `/metro rebuild` | `metro.build` | rebuild segments from the rails |
| `/metro issues [page]` | `metro.build` | problems found by the last rebuild |
| `/metro teleport <world> <x> <y> <z>` | `metro.build` | go to a block |
| `/metro reload` | `metro.reload` | reload config and data |
| `/metro debug [off\|1\|2\|3] [own\|all\|<player>]` | `metro.debug` | watch debug logging |
| `/metro version` | | show the plugin version |
| `/metro help` | | list the commands you may use |

See the wiki Commands page for details.

Names and icons are edited by command (item ids tab-complete); the in-world menu
sends a clickable command for those points, while anchors are set with the
crowbar.

## Permissions

- `metro.ride` ride the metro (default: everyone)
- `metro.build` edit, rebuild and inspect the network (default: operators)
- `metro.reload` reload config and data (default: operators)
- `metro.debug` watch debug logging (default: operators)
- `metro.admin` all of the above (default: operators)

## Building

Needs JDK 25.

```shell
./gradlew build
```

The jar is written to `build/libs/Metro-Paper-<version>.jar`. The version comes from the
latest `v*` git tag. Builds after a tag are named `<tag>-dev.<commits>-<hash>`. The build also runs
ktlint, detekt and the tests.

## License

MIT
