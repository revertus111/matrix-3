# Steam Deck Matrix3 Runtime

## Goal

Make the protected Matrix3 baseline playable/testable from a Steam Deck with the Deck acting as the local server and client machine, while keeping setup self-contained in the Deck user's home directory and keeping the Matrix3 runtime source untouched unless evidence proves a Linux-specific runtime change is required.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Private Java 8 + SteamOS bootstrap | 🟡 Needs runtime test |
| One-launch local server + client session | 🟡 Needs runtime test |
| Steam Gaming Mode launch | ❌ Not tested |
| Phone remote play against the Deck | ❌ Not tested |

## Scope

### In scope

- Linux x86_64 / Steam Deck launch support.
- Private Eclipse Temurin Java 8 install in the user's home directory.
- Local server/client process orchestration.
- Server readiness detection.
- Logs and stale-process cleanup.
- Steam desktop/non-Steam-game entry path.
- Runtime test checklist for Deck, Gaming Mode, and phone streaming.

### Out of scope

- Changing SteamOS read-only system files.
- Replacing Matrix3 networking or login architecture.
- Changing Java level.
- Porting Matrix3 to Android.
- Reworking client rendering before SteamOS runtime evidence requires it.
- Reintroducing post-reset features.

## Architecture / ownership

- Matrix3 server authority remains the baseline `Server` project and `com.rs.GameLauncher`.
- Matrix3 client authority remains the baseline `Client` project and `game.RS3Applet`.
- Steam Deck support owns only launch/bootstrap/process-management behavior under `steamdeck/`.
- The private JDK lives outside the repository at `~/.local/share/matrix3/java8`.
- Runtime logs/PIDs live outside the repository at `~/.local/state/matrix3`.
- No Client/Server source file is changed by this workstream's first bundle.

## Verified foundation

### VERIFIED

- None yet on physical Steam Deck after the 2026-09-12 runtime reset.

### verified-static

- `Server/build.gradle` targets Java 8 and exposes `runGame`.
- `Client/build.gradle` targets Java 8 and launches `game.RS3Applet`.
- Both projects include POSIX Gradle wrappers.
- `GameLauncher` defaults to world 1, debug true, hosted false when no five-argument launch tuple is supplied.
- Non-hosted `GameLauncher` starts the embedded local login core.
- `GameChannelsManager` binds game traffic at `43593 + WORLD_ID`; default world 1 therefore binds `43594`.
- `RS3Applet` is non-hosted by default and points lobby/web/world addresses at `127.0.0.1`.
- Adoptium provides a stable API pattern for current Java 8 GA Linux x64 JDK downloads.

## Unknown / research needed

### HYPOTHESIS

- Baseline AWT/Swing client should at least open under SteamOS Desktop Mode using the private Java 8 runtime.

### UNKNOWN

- Whether the client's full graphics/native path works correctly under SteamOS.
- Whether Gaming Mode needs additional launch environment flags.
- Whether Steam Link input mapping needs a custom controller template.
- Whether an old native library dependency appears only at runtime.

## Dependencies

- Required systems/features: current protected Matrix3 baseline, SteamOS x86_64, bash, curl, tar, setsid.
- Optional supporting tools: `systemd-inhibit` for keeping the Deck awake during a Matrix3 session.
- Runtime/data dependencies: existing Matrix3 cache/data in the repository, network access for the first Java/Gradle dependency download.

## Development plan

### Phase 1 - Deck-local bootstrap

**Purpose:** Establish a self-contained local Matrix3 runtime on the Steam Deck without changing Client/Server source.

**Status:** NEEDS TEST

**Entry conditions:**

- Protected Matrix3 baseline restored.
- AAA approved for Steam Deck workstream.

**Exit conditions:**

- Java 8 setup succeeds on the Deck.
- Server reaches the local game port.
- Client opens and can reach the local server.
- Login and basic movement succeed.

#### Bundle 1.1 - Portable runtime + unified launcher

**Purpose:** Make first-run setup and normal launch one-command operations.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add shared Steam Deck path/process helpers.
- [x] Add private Java 8 installer using the Adoptium API.
- [x] Add unified server/client launcher.
- [x] Add local world readiness check on port 43594.
- [x] Add sleep inhibition while the launcher owns the session.
- [x] Add timestamped logs outside the repository.
- [x] Add stop script for stale sessions.
- [x] Add Desktop Mode application entry.
- [x] Preserve baseline Client/Server source unchanged.
- [ ] Run setup on physical Steam Deck.
- [ ] Confirm server launch.
- [ ] Confirm client window/renderer launch.
- [ ] Confirm account login and basic in-game movement.

**Runtime tests:**

- Use `docs/steam-deck/testlist.txt`.

### Phase 2 - Steam Gaming Mode

**Purpose:** Make Matrix3 launch cleanly from Steam's normal handheld UI.

**Status:** PLANNED

**Entry conditions:**

- Phase 1 runtime checks pass in Desktop Mode.

**Exit conditions:**

- Matrix3 launches from Gaming Mode.
- Steam can stop the session without leaving stale server/client processes.
- Basic Deck controls are usable.

#### Bundle 2.1 - Non-Steam Game acceptance

**Status:** PLANNED

**Checklist / patches:**

- [ ] Add Matrix3 launcher to Steam.
- [ ] Verify Gaming Mode display/renderer behavior.
- [ ] Verify stop/relaunch behavior.
- [ ] Record any required launch flags only if runtime evidence requires them.

### Phase 3 - Phone remote play

**Purpose:** Let the Deck remain physically safe/put away while Matrix3 is played/tested from the phone.

**Status:** PLANNED

**Entry conditions:**

- Phase 2 passes.

**Exit conditions:**

- Phone can stream Matrix3 from the Deck.
- Input is usable enough for testing.
- Deck remains awake for the active session and cleans up correctly when the game is stopped.

#### Bundle 3.1 - Remote Play acceptance

**Status:** PLANNED

**Checklist / patches:**

- [ ] Pair phone with Deck through Steam Link / Remote Play.
- [ ] Verify Matrix3 video/audio stream.
- [ ] Verify touch/controller input.
- [ ] Verify disconnect/reconnect behavior.
- [ ] Record preferred controller template if needed.

## Current execution state

- Phase: Phase 1 - Deck-local bootstrap
- Phase status: NEEDS TEST
- Bundle: 1.1 - Portable runtime + unified launcher
- Bundle status: NEEDS TEST
- Approval state: AAA approved 2026-09-12
- Current checklist item: Physical Steam Deck runtime verification
- Current objective: Pull once on the Deck, run setup, then launch Matrix3 and capture the first real Linux result.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Private Java 8 bootstrap | 1 | 1.1 | NEEDS TEST | Static script validation complete. |
| Unified server/client launcher | 1 | 1.1 | NEEDS TEST | Waits on real world-1 port 43594. |
| Logs + process cleanup | 1 | 1.1 | NEEDS TEST | Runtime state kept outside Git tree. |
| Desktop Mode acceptance | 1 | 1.1 | READY | First physical Deck gate. |
| Gaming Mode acceptance | 2 | 2.1 | READY | Do only after Desktop Mode passes. |
| Phone remote-play acceptance | 3 | 3.1 | READY | Do only after Gaming Mode passes. |

## Decisions / new ideas

### Decision log

- 2026-09-12: Keep Steam Deck support outside Matrix3 engine/client source until runtime evidence requires a source change.
- 2026-09-12: Use a private home-directory Java 8 instead of altering SteamOS system Java/read-only root.
- 2026-09-12: Use the actual local world port as the readiness gate instead of a fixed startup sleep.
- 2026-09-12: Preserve all runtime logs outside the Git tree.
- 2026-09-12: Treat Steam Deck access as the current enabling workstream before rebuilding post-reset features.

## Testing

### Quick/high-value checks

1. `bash ./steamdeck/setup.sh`
2. `./steamdeck/matrix3.sh`
3. Confirm server reaches ready state instead of exiting.
4. Confirm Matrix3 client window opens.
5. Log in and move around for a minute.
6. Close the client and confirm no Matrix3 process remains.

### Deeper checks

1. Relaunch after one successful close.
2. Check timestamped logs under `~/.local/state/matrix3/logs`.
3. Add launcher to Steam and repeat from Gaming Mode.
4. Pair phone and test Remote Play.

### Smoke/regression checks

- Runtime source remains the protected baseline; no Client/Server source modifications are introduced in Bundle 1.1.
- Once physical runtime is available, apply the relevant baseline checks from `docs/rs3/SMOKE_TEST.md`.

## Carryover / blockers

### CARRYOVER

- None.

### BLOCKED

- Runtime acceptance cannot be completed until the physical Steam Deck runs the bundle.

## Resume Here

**Last completed:**

- Steam Deck Bundle 1.1 implementation and static shell syntax validation.

**Current phase:**

- Phase 1 - Deck-local bootstrap.

**Active bundle:**

- Bundle 1.1 - Portable runtime + unified launcher.

**Next checklist item:**

- Run `bash ./steamdeck/setup.sh` on the physical Steam Deck.

**Current state / next action:**

- Pull current `main` onto the Deck, run setup once, then run `./steamdeck/matrix3.sh`. If it fails, preserve the terminal message and newest server/client logs before changing code.

**Files/systems already inspected:**

- `Server/build.gradle`
- `Client/build.gradle`
- `Server/gradlew`
- `Client/gradlew`
- `Server/src/main/java/com/rs/GameLauncher.java`
- `Server/src/main/java/com/rs/Settings.java`
- `Server/src/main/java/com/rs/net/GameChannelsManager.java`
- `Client/src/main/java/game/RS3Applet.java`

**Do not re-scan without new evidence:**

- Broader Client/Server source.
- Old post-reset Steam/launcher experiments.
- Renderer/native code until physical Deck logs identify it.

**Pending runtime verification:**

- Java bootstrap.
- Server boot.
- Client renderer/window.
- Login/movement.
- Gaming Mode.
- Phone Remote Play.

**Blockers:**

- Physical Deck runtime result.

**Important remaining uncertainty:**

- SteamOS compatibility of the old client graphics/native path.

## Next recommended work

Run Bundle 1.1 on the physical Steam Deck. Do not patch renderer/native code until that test produces evidence.
