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

```text
/rideprofile status
/rideprofile flush
```

Profile files are written under the Minecraft instance:

```text
logs/imears-ride-sessions/
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
