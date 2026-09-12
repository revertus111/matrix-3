# Matrix3 on Steam Deck

This folder contains the SteamOS/Linux launcher support for the protected Matrix3 baseline.

## Goal

Run the local Matrix3 server and client together on a Steam Deck with one launcher, using a private Java 8 runtime under the Deck user's home directory. The Matrix3 Client and Server source remain unchanged.

## First setup

From Desktop Mode, open Konsole in the repository root and run:

```bash
bash ./steamdeck/setup.sh
```

The setup script:

- downloads Eclipse Temurin Java 8 for Linux x64 into `~/.local/share/matrix3/java8`;
- does not modify SteamOS system Java or disable the read-only OS;
- ensures the Matrix3 Gradle wrappers and Deck scripts are executable;
- creates a Matrix3 desktop entry.

The first Gradle launch may still need network access so the existing project wrapper can obtain its configured Gradle distribution/dependencies.

## Launch

Run:

```bash
./steamdeck/matrix3.sh
```

The launcher:

1. blocks idle/sleep while the Matrix3 session is active when `systemd-inhibit` is available;
2. starts the local server with the baseline `runGame` task;
3. waits for world 1 to bind on `127.0.0.1:43594`;
4. starts the local client;
5. writes separate timestamped server/client logs under `~/.local/state/matrix3/logs`;
6. shuts the server down after the client exits.

To force-stop a stale session:

```bash
./steamdeck/stop.sh
```

## Steam / phone use

After the Desktop Mode launch is proven:

1. Add `steamdeck/matrix3.sh` (or the installed Matrix3 desktop entry) to Steam as a Non-Steam Game.
2. Return to Gaming Mode and launch Matrix3 there.
3. Pair the phone with the Steam Deck through Steam Link / Remote Play and stream the Matrix3 window from the Deck.

The Deck must remain powered and awake while hosting Matrix3. Keep its vents unobstructed even if it is physically put away.

## Logs

Runtime logs live outside the repository:

```text
~/.local/state/matrix3/logs/
```

This avoids dirtying the Git working tree during testing.

## Current verification state

- `verified-static`: baseline server defaults to local non-hosted mode when `runGame` receives no arguments and starts the embedded login core.
- `verified-static`: world 1 listens on base port `43593 + 1 = 43594`.
- `verified-static`: baseline client points its lobby/world addresses at `127.0.0.1`.
- `verified-static`: Client and Server both target Java 8 and include POSIX Gradle wrappers.
- `UNKNOWN`: client graphics/native behavior under SteamOS/Game Mode until tested on the physical Deck.
