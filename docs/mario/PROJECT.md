# Mario 64 in Matrix3 / Revision 830

## Goal

Run an authentic Mario experience inside Matrix3/revision-830: Matrix3 owns the RuneScape world, input, rendering and eventual server authority, while an SM64-derived native core owns Mario's movement/action state machine through a narrow passthrough bridge.

Detailed architecture: `docs/mario/SM64_PASSTHROUGH_ARCHITECTURE.md`.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Alternate movement/controller foundation | 🟡 Foundation |
| SM64 passthrough core bridge | 🔵 In Progress |
| Matrix world/collision adapter | ❌ Not started |
| Mario visual/animation presentation | ❌ Not started |
| Multiplayer/server authority integration | ❌ Not started |

## Scope

### In scope

- Matrix3-native alternate controller activation and lifecycle.
- Native SM64-derived Mario simulation through a bridge rather than reimplementing the full action state machine in Java.
- `libsm64` as the current native-core implementation.
- Matrix terrain/object/water conversion into collision surfaces the SM64 core can consume.
- Mario visual presentation through either revision-830 assets or a later direct native-geometry adapter.
- Eventual multiplayer/server validation after local behavior is stable.

### Out of scope for the current bridge slice

- Full RuneScape collision conversion.
- Native SM64 X/Z replacing RuneScape movement before the collision adapter exists.
- Mario model replacement in the 830 scene.
- Server-authoritative Mario movement or remote-player replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.

## Architecture / ownership

- Matrix3 remains host architecture and world authority.
- `PlayerControllerMode` owns deliberate local RuneScape/Mario activation; RuneScape remains default.
- `Sm64BridgeSession` owns the persistent sidecar process, fixed 30 Hz SM64 tick and immutable latest/previous native state publication.
- `MarioJumpController` retains its legacy hook-facing name but is now a Matrix client-thread presentation adapter. It no longer computes Mario gravity in Java.
- Native SM64 Y is applied through Matrix3's already-VERIFIED local-player transform only from the established client/viewport thread.
- The current bridge slice maps vertical state only. RuneScape X/Z movement, plane, clipping, pathfinding and server position authority remain Matrix-owned.
- Native-state presentation uses an initial `3.0` SM64-to-Matrix Y scale; `-Dmatrix3.sm64.verticalScale=<value>` can override it for runtime calibration.
- Matrix collision is converted into SM64 surfaces in Phase 3; until then the sidecar still uses the temporary flat native floor.
- Leaving Mario mode, player lifecycle changes, missing native dependencies or sidecar failure stop the session and restore/fall back to RuneScape control.

## Verified foundation

### VERIFIED

- The actual revision-830 local player can visibly leave RuneScape terrain through the established local-player transform.
- The RuneScape/Mario controller boundary works at runtime.
- Bridge Spike A is runtime-confirmed against real `libsm64` + the user's local US ROM on Windows/MSYS2 MinGW64.
- Manual native protocol returned `READY 1` / `PONG 1`.
- Eclipse/Java produced `[SM64 Bridge] PASS native SM64 state: y -0.00 -> 96.50 (rise 96.50), action 205521409 -> 205521409`.
- That PASS proves Matrix Java can launch the native core, send input, execute SM64-derived movement/action code and receive real native Mario state back.
- Bundle 2.2 runtime confirmed the persistent sidecar reaches `READY (30 Hz)`, native-state presentation reaches `ACTIVE`, and the native-driven Matrix transform path works in the live 830 client.
- The visible Mario-mode vertical presentation path is now driven by real native SM64 state rather than the removed Java-gravity implementation.

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam already driving `MarioJumpController.tick()`.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`; Space/Ctrl/M mappings are established.
- `libsm64` exposes Mario input/state, collision surfaces, dynamic surface objects and generated geometry.
- The native example/bridge advances one SM64 tick per `STEP`; `Sm64BridgeSession` schedules those calls at fixed 30 Hz.
- Native simulation state is published from the worker and Matrix transform mutation remains on the client thread.

## Unknown / research needed

### HYPOTHESIS

- Sidecar transport is fast enough for sustained 30 Hz Mario simulation; measure before considering JNI.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Final Matrix<->SM64 coordinate scale/sign calibration for full XYZ movement.
- Whether the initial vertical scale `3.0` is the best game-feel calibration.
- Final collision-bubble radius/rebuild threshold.
- Best final Mario visual path: revision-830 imported asset/animation mapping vs direct `SM64MarioGeometryBuffers` rendering.
- Runtime cost of object collision proxy extraction.
- Final client/server reconciliation model.

## Dependencies

- Matrix3 client input/player-transform ownership.
- Local `libsm64`/`sm64_bridge` build.
- User-owned SM64 US ROM as a local runtime dependency only; never committed.
- User runtime testing for native bridge and game-feel acceptance.

## Development plan

### Phase 1 - Alternate Controller Foundation

**Purpose:** Prove Matrix3 can safely host alternate local-player behavior without replacing vanilla RuneScape control.

**Status:** NEEDS TEST

#### Bundle 1.1 - Vertical Jump POC

**Status:** CARRYOVER

- [x] Trace local-player transform/input/tick seams. `verified-static`
- [x] Add isolated Java vertical jump proof.
- [x] Runtime-confirm visible vertical displacement. `VERIFIED`
- [ ] Legacy proof regression: airborne X/Z movement, slopes, hold/repress, post-landing behavior, relog.

The Java-gravity proof is no longer Mario-mode physics authority after Bundle 2.2 implementation; keep only useful RuneScape lifecycle regression coverage.

#### Bundle 1.2 - Controller Mode Boundary

**Status:** DONE

- [x] Add `PlayerControllerMode`.
- [x] RuneScape default / Ctrl+M Mario activation.
- [x] Gate Mario behavior behind mode.
- [x] Clean mode/lifecycle reset behavior.
- [x] User runtime acceptance. `VERIFIED`

### Phase 2 - SM64 Passthrough Core Bridge

**Purpose:** Make actual SM64-derived native code advance Mario state and drive Matrix presentation.

**Status:** ACTIVE

#### Bundle 2.1 - Sidecar transport/native-core spike

**Status:** DONE

- [x] Record passthrough architecture and responsibility split.
- [x] Select sidecar-first transport.
- [x] Select/build `libsm64` as the native core.
- [x] Add `sm64_bridge` protocol and deterministic flat floor.
- [x] Add Java bridge proof.
- [x] Runtime prove real native Y/action state returns to Matrix Java. `VERIFIED` 2026-10-04.

#### Bundle 2.2 - Native state -> Matrix transform

**Status:** NEEDS TEST

- [x] Replace one-shot `Sm64BridgeProbe` ownership with persistent `Sm64BridgeSession` while Mario mode is active.
- [x] Run native simulation at fixed 30 Hz on a daemon worker.
- [x] Publish native state across the worker/client-thread boundary without mutating Matrix scene state from the worker.
- [x] Replace `MarioJumpController` Java gravity with native-Y presentation from the established viewport tick.
- [x] Add one-native-tick delayed interpolation for smoother presentation above 30 Hz.
- [x] Preserve Matrix terrain baseline updates until real RuneScape collision is supplied to SM64.
- [x] Stop/reset on Mario-mode exit and local-player lifecycle change.
- [x] Auto-fallback to RuneScape mode if the persistent native session fails or dependencies are unavailable.
- [x] Prevent entering Mario mode while Space is already held from manufacturing a native jump.
- [x] Runtime prove native SM64-derived state visibly drives the 830 player. `VERIFIED` 2026-10-04.
- [ ] Runtime verify Ctrl+M exit restores the ground baseline and stops native ownership cleanly.
- [ ] Runtime verify native failure returns to RuneScape mode without corrupting the player transform.

### Phase 3 - Matrix World / Collision Adapter

**Purpose:** Let the SM64 core physically reason about RuneScape terrain and nearby collision instead of a temporary flat native floor.

**Status:** PLANNED

#### Bundle 3.1 - Terrain heightfield -> SM64 surfaces

- [ ] Establish the Matrix terrain-corner height sampler.
- [ ] Convert nearby 512-unit RuneScape tile quads into two correctly wound SM64 triangles each.
- [ ] Add local origin/scale conversion.
- [ ] Keep a bounded collision bubble around Mario.
- [ ] Runtime verify standing/running/jumping across real RuneScape hills using native SM64 collision.

#### Bundle 3.2 - Objects / walls / platforms

- [ ] Add low-cost collision proxies for nearby solid objects.
- [ ] Add walls/ceilings/bridges/platform floors as required by test content.
- [ ] Use `SM64SurfaceObject` for moving platforms when needed.
- [ ] Add water-level bridging when content requires it.

### Phase 4 - Mario Visual / Animation Presentation

**Status:** PLANNED

- [ ] Evaluate revision-830 imported Mario model/animation path.
- [ ] Evaluate direct `SM64MarioGeometryBuffers` render path.
- [ ] Choose from real renderer/asset evidence.
- [ ] Prove idle/run/jump presentation driven from native Mario state.

### Phase 5 - Multiplayer / Server Authority

**Status:** PLANNED

- [ ] Define legal Mario movement/state sent to server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Combat/world interaction authority boundaries.

## Current execution state

- Phase: 2 - SM64 Passthrough Core Bridge
- Phase status: ACTIVE
- Bundle: 2.2 - Native state -> Matrix transform
- Bundle status: NEEDS TEST
- Approval state: `SAP AAA` approved for Bundle 2.2 on 2026-10-04.
- Current checklist item: regression-accept Ctrl+M exit and native-failure fallback after the core visible transform path passed.
- Current objective: preserve the now-VERIFIED native SM64 -> visible Matrix path while finishing only the remaining safety regression checks.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Core lift and controller mode are runtime-proven; legacy deeper regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2 | 2.1 | DONE | Real libsm64 + user-ROM runtime verified. |
| Java bridge proof | 2 | 2.1 | DONE | Real native PASS verified with 96.50-unit Y rise. |
| Persistent native session | 2 | 2.2 | VERIFIED | 30 Hz session and published-state path accepted at runtime. |
| Native -> visible Matrix transform | 2 | 2.2 | VERIFIED | Real native SM64 state now visibly drives the 830 vertical presentation path. |
| Matrix terrain adapter | 3 | 3.1 | READY | Starts after native transform safety regression is closed or explicitly deferred. |
| Mario visual presentation | 4 | 4.x | READY | Direct native geometry vs imported 830 asset path remains to be chosen. |

## Decisions / new ideas

- Matrix3 is the host world/renderer/input/server architecture.
- Authentic SM64-derived logic owns Mario-mode simulation; do not rebuild the full action state machine in Java.
- Sidecar process remains the transport until runtime measurement gives a reason for JNI.
- `MarioJumpController` keeps the established viewport hook but is now presentation-only; its old Java gravity implementation is removed.
- Matrix scene mutation stays on the client thread even though native simulation runs on a worker.
- RuneScape control remains the safe default and fallback.
- Matrix terrain will be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.
- Construction Revamp remains the repository's separate main workstream unless priority is explicitly changed.

## Testing

### Bundle 2.2 quick acceptance

Verified in the current runtime session:

1. Eclipse Java 8 build/launch path works.
2. Ctrl+M enters Mario mode.
3. Persistent native session reaches `READY (30 Hz)`.
4. Native-state -> Matrix transform reaches `ACTIVE` and the native-driven presentation path works.

Still carryover:

5. Ctrl+M exit while grounded and airborne restores RuneScape cleanly.
6. Deliberate missing/failed sidecar falls back to RuneScape without stale height.
7. Logout/relog and normal RuneScape Space/chat behavior remain clean.

### Carryover

- Normal movement after relog remains unchanged.
- Space still behaves normally in RuneScape mode/chat contexts.

## Carryover / blockers

### CARRYOVER

- Phase 1 legacy Java-proof deeper regression where still useful for Matrix lifecycle coverage.
- Bundle 2.2 Ctrl+M exit/relog/failure-fallback regression.
- Mario model/visual selection remains independent work after the native movement path is proven.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Bridge Spike A runtime-VERIFIED against real `libsm64` + user ROM.
- Bundle 2.2 implementation added `Sm64BridgeSession`, fixed 30 Hz native stepping, state interpolation/publication, native-Y Matrix presentation and failure/exit fallback.
- Runtime-VERIFIED persistent `READY (30 Hz)` + `Native state -> Matrix transform ACTIVE` in the live 830 client; user accepted the native-driven path as working.

**Current phase:**

- Phase 2 - SM64 Passthrough Core Bridge (`ACTIVE`).

**Active bundle:**

- Bundle 2.2 - Native state -> Matrix transform (`NEEDS TEST`).

**Next checklist item:**

- Finish only the remaining Ctrl+M exit/relog/failure-fallback regression unless the user explicitly changes priority to another Mario workstream area.

**Files/systems already inspected:**

- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `native/sm64-bridge/sm64_bridge.c`
- Established Matrix player transform/input/viewport mappings from prior verified work.

**Do not re-scan without new evidence:**

- Local-player transform/input/viewport ownership.
- Basic vertical-displacement feasibility.
- Controller-mode activation boundary.
- libsm64 public input/state API and Windows sidecar viability.
- Protocol v1 for the current vertical bridge slice.

**Pending runtime verification:**

- Mario-mode exit restores baseline and stops native ownership cleanly.
- Native session failure falls back to RuneScape mode.
- Initial `3.0` vertical scale feels reasonable or needs calibration.

**Important remaining uncertainty:**

- Final XYZ scale/collision calibration belongs to the collision-adapter phase after this vertical ownership proof is accepted.

## Next recommended work

Finish Bundle 2.2 safety regression, then continue with the next explicitly prioritized Mario milestone.
