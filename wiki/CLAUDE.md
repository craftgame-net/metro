# CLAUDE.md

Rules for editing the player wiki. This file is not copied to the GitHub wiki.

## Readers and style

- Readers are players, builders and server owners. Write about what they see and do, never about code or classes.
- Use plain English: short sentences, common words, no em dashes, no filler or promotional words. Use one term for
  one thing.
- In tables, write single spaces in cells without column padding, and use `| --- |` as the separator row.
- Link pages as `[[Page]]` or `[[Page#anchor]]`. Link to a heading with a text label, as in
  `[text](Maintenance#no-out-anchor)`.

## Keeping the wiki true

- When behaviour that players see changes (commands, permissions, config keys, texts), update the matching page in
  the same change.
- Take defaults, units and ranges from `paper/src/main/resources/config.yml` and `MetroConfig`, and texts from
  `lang.yml`. Do not guess.
- Each key in `RebuildWarnings.ANCHORS` needs a heading under "Rebuild warnings" in `Maintenance.md` with the
  matching anchor.
- Screenshots go in `wiki/images/`. Every image must be used by a page, and every image a page uses must exist.
- Add each new page to `_Sidebar.md`.

## Sync

`.github/workflows/wiki-sync.yml` copies `wiki/` as is to the GitHub wiki. It skips this `CLAUDE.md`.
