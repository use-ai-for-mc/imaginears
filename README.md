# Imaginears Helper

A small client-side Fabric helper for Imaginears Club on Minecraft 26.1.

## Features

- Auto-requests and opens the Imaginears audio client through a hidden native WebView helper.
- Detects OpenAudioMC session links from `session.openaudiomc.net` and `audio.imaginears.club`.
- Adds `/oa connect`, `/oa disconnect`, `/oa reconnect`, and `/oa volume` client commands.
- Records raw ride profiles to help learn Imaginears ride markers and timings.
- Releases the mouse cursor while riding vehicles, then restores normal mouse capture afterward.
- Suppresses most positional Minecraft sounds while riding, while allowing the ride-complete chime.
- Gates behavior to `iears.us` and `*.iears.us` servers.

## Requirements

- Minecraft `26.1`
- Fabric Loader `0.19.2` or newer
- Fabric API for Minecraft 26.1
- Cloth Config for Minecraft 26.1
- Java 25

## Build

```bash
./gradlew build
```

The runtime mod jar is written to:

```text
build/libs/imears-0.1.0.jar
```

## Install Locally

Copy the runtime jar into the target Fabric instance's `mods/` directory along with Fabric API:

```bash
cp build/libs/imears-0.1.0.jar "/path/to/PrismLauncher/instances/26.1/minecraft/mods/imears-0.1.0-26.1.jar"
```

Restart Minecraft after replacing the jar. Fabric only loads mods during client startup.

## Audio Commands

```text
/oa connect
/oa disconnect
/oa reconnect
/oa volume
```

The mod also schedules an automatic `/audio` request shortly after joining an Imaginears server.

## Ride Profiling

Ride profiling starts automatically when you mount a vehicle on Imaginears and writes a JSON file
when you dismount. Profiles include duration, vehicle position samples, nearby armor-stand model
items, scoreboard snapshots, and system chat seen during the ride.

The profiler currently recognizes TRON Lightcycle vehicles when the mounted player is close to an
invisible armor stand wearing a `minecraft:diamond_pickaxe` helmet with damage `122` or `377`. Both
variants are stored under the same `tron-lightcycle` ride key, while the raw marker damage and
distances are preserved in the profile JSON.

```text
/rideprofile status
/rideprofile flush
```

Profile files are written under the Minecraft instance:

```text
logs/imears-ride-sessions/
```

## TRON Progress

TRON Lightcycle progress tracking uses the collected `tron-lightcycle` ride profiles as reference
paths. While you are sitting on a recognized TRON vehicle, the tracker matches the current vehicle
position against the nearest reference path for that vehicle marker variant and estimates progress
and remaining time.

```text
/tron status
/tron reset
```

The bundled reference data is stored at:

```text
assets/imears/rides/tron-lightcycle-reference.json
```

To regenerate it after collecting more profiles:

```bash
python3 tools/build-tron-reference.py "/path/to/minecraft/logs/imears-ride-sessions"
```

## HUD Visibility

The helper can hide the same core HUD elements that IMF exposes for cleaner ride capture. Open the
config UI through ModMenu or directly in-game:

```text
/imears config
```

The defaults mirror IMF's conservative profile: health is hidden, while scoreboard, chat, name tags,
hotbar, XP level, and crosshair stay visible until changed. The command surface remains available
for quick changes:

```text
/imears hud status
/imears hud hide scoreboard
/imears hud show scoreboard
/imears hud toggle chat
/imears hud crosshair none
/imears hud crosshair riding
/imears hud crosshair always
/imears hud reload
/imears hud reset
```

Supported HUD elements are:

```text
scoreboard chat health nametags hotbar xp
```

Settings are stored at:

```text
config/imears/config.json
```

## Releases

Releases are cut from Git tags.

1. Update `mod_version` in `gradle.properties`.
2. Commit the version change.
3. Create and push a tag:

```bash
git tag v0.1.0
git push origin main --tags
```

The `Release` GitHub Action builds the mod and attaches the runtime and sources jars to the GitHub release.
