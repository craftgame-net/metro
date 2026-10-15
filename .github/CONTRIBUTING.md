# Contributing

This page says how to build Metro and what a pull request has to pass.

## Getting started
Requires JDK 25.

```shell
./gradlew build
./gradlew :core:test --tests 'net.craftgame.metro.route.RouterTest'
```

`build` compiles, runs ktlint, detekt and all tests. The second command runs one test class;
name its module.

## Code
- `:core` has the logic that needs no server (routing, rides, data). It must not use Bukkit or Paper.
- `:paper` has everything that touches the server: listeners, menus, commands.
- Follow ktlint and detekt. Do not add `@Suppress` or new baseline entries. Keep lines within 120 characters.
- `.github/scripts/check-rules.sh` checks the project rules. It runs locally and in CI.
- Put new player-visible text in `lang.yml`.

## Documentation
The `CLAUDE.md` files and `docs/architecture/` describe the code. They are written for AI agents,
but you may read them too. The player wiki is in `wiki/`; update it when player-visible
behaviour changes.

## Pull requests
- Open pull requests against `master` and keep changes focused. A release is a `v*` tag on `master`.
- Write each commit message as one imperative line, with no body.
- The `Quality` workflow checks every pull request. Fill in the pull request template.

## Reporting bugs and requesting features
Open an issue using one of the issue templates. For security issues, see
[SECURITY.md](SECURITY.md) instead of opening a public issue.
