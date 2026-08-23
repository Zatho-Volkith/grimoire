# Grimoire

Local-first worldbuilding and writing for fanfic and original fiction.

Grimoire is a desktop app that keeps your worlds and your writing in one place — and keeps
both on your machine. Everything lives in a single SQLite file. No account, no cloud, no
telemetry; the app only touches the network when you explicitly ask it to (wiki imports,
checking for updates).

## What it does

- **Worlds** hold everything: characters, locations, concepts, and anything else, shaped by
  editable templates.
- **Relationships** link entities to each other; **timelines** order events (two-sided, with
  sub-events) and have a dedicated viewer.
- **Works** are your actual writing: chaptered, Markdown, with live preview, word counts, and
  one-click AO3-ready HTML export.
- **Wiki import** pulls canon articles straight from a MediaWiki/Fandom wiki, keeps a canon
  snapshot, and shows you what changed upstream since you imported.
- **Variants** let a fanfic AU diverge from canon without losing track of what you changed.
- **Full-text search** across everything; soft-delete with a trash, never silent data loss.
- An optional **tutorial world** that teaches the app by being used.

## Install

Grab the installer for your OS from the [latest release](https://github.com/Zatho-Volkith/grimoire/releases/latest):
MSI (Windows), DMG (macOS), deb/rpm/AppImage (Linux).

The installers are currently unsigned, so Windows SmartScreen and macOS Gatekeeper will warn
on first install. Updates installed from inside the app (world picker → *Check for updates*)
skip that warning.

Your data lives outside the install directory and survives uninstall/reinstall:
`%APPDATA%\grimoire` (Windows), `~/Library/Application Support/grimoire` (macOS),
`~/.local/share/grimoire` (Linux).

## Build from source

Kotlin Multiplatform + Compose Multiplatform + SQLDelight. Needs JDK 21.

```
./gradlew run                                # run from source
./gradlew desktopTest                        # tests
./gradlew packageDistributionForCurrentOS    # native installer for this OS
```

## License

[GPLv3](LICENSE). Grimoire is free software and will stay that way: you can use, modify, and
share it, and anything built from it must remain free under the same terms.
