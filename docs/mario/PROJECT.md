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
- `libsm64` as the first native-core candidate because it exposes SM64 decomp movement/rendering to external engines.
- Matrix terrain/object/water conversion into collision surfaces the SM64 core can consume.
- Mario visual presentation through either revision-830 assets or a later direct native-geometry adapter.
- Eventual multiplayer/server validation after local behavior is stable.

### Out of scope for the current bridge slice

- Full RuneScape collision conversion.
- Mario model replacement in the 830 scene.
- Server-authoritative Mario movement.
- Remote-player Mario replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.
- Rewriting long jump/triple jump/wall kick/etc. in Java unless a specific Matrix-only behavior later requires an adapter.

## Architecture / ownership

- Matrix3 remains host architecture and world authority.
- `PlayerControllerMode` owns deliberate local RuneScape/Mario activation; RuneScape remains default.
- The existing Java vertical jump proof establishes that the 830 local player transform can leave terrain; it is not the final Mario physics implementation.
- `libsm64` is the first native-core candidate. It is derived from the SM64 decomp and is designed to expose Mario movement/rendering to external engines while loading the user's own US ROM at runtime.
- V1 transport is an isolated native sidecar process driven from Java 8 through `ProcessBuilder` + stdin/stdout.
- Matrix input is normalized and sent to the bridge. Native `SM64MarioState` returns to Matrix for presentation.
- Matrix collision is converted into SM64 surfaces by a future adapter; Bridge Spike A uses a temporary flat native floor only.
- Sidecar/native failures must never replace normal RuneScape control, server authority, plane, persistence or clipping.

## Verified foundation

### VERIFIED

- User runtime testing confirmed the actual revision-830 local player visibly leaves RuneScape terrain with the Matrix3-native vertical proof.
- User runtime acceptance confirmed the explicit RuneScape/Mario controller boundary works.
- Bridge Spike A is runtime-confirmed against real `libsm64` + the user's local US ROM on Windows/MSYS2 MinGW64.
- Manual native protocol startup returned `READY 1` and `PONG 1`.
- The Eclipse/Java bridge probe produced `[SM64 Bridge] PASS native SM64 state: y -0.00 -> 96.50 (rise 96.50), action 205521409 -> 205521409` after entering Mario mode.
- The PASS gate proves Java observed both real native Mario Y movement and at least one intermediate native action-state change during the deterministic sequence. The printed final action had returned to the baseline action by the end of the probe.
- Matrix3 can therefore launch the native SM64 sidecar, send input, execute SM64-derived movement/action code, and receive native Mario state back into Java.

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam already driving Mario controller presentation.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`; Space/Ctrl/M mappings are established.
- `libsm64` exposes `SM64MarioInputs`, `SM64MarioState`, `SM64Surface`, native Mario create/tick/delete calls, static surfaces, dynamic surface objects and geometry buffers.
- The `libsm64` example advances Mario in fixed 30 Hz steps and uses caller-provided collision surfaces.
- `libsm64` loads Mario texture/animation data from a user-supplied SM64 US ROM at runtime.
- Bridge Spike A C source passes a local C11 syntax check.
- `Sm64BridgeProbe` passes a local Java 8 source check.

## Unknown / research needed

### HYPOTHESIS

- The sidecar transport remains suitable for persistent fixed-30-Hz gameplay ticks without requiring JNI; measure before changing transport.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Final Matrix<->SM64 coordinate scale/sign calibration beyond the proven native vertical-state round trip and the established Matrix vertical transform direction.
- Final collision-bubble radius/rebuild threshold.
- Best final Mario visual path: revision-830 imported asset/animation mapping vs direct `SM64MarioGeometryBuffers` rendering.
- Runtime cost of object collision proxy extraction.
- Final client/server reconciliation model.

## Dependencies

- Matrix3 client input/player-transform ownership.
- `libsm64` built externally from `https://github.com/libsm64/libsm64`.
- User-owned SM64 US ROM as a local runtime dependency only; never committed.
- User runtime testing for native bridge and game-feel acceptance.

## Development plan

### Phase 1 - Alternate Controller Foundation

**Purpose:** Prove Matrix3 can safely host alternate local-player behavior without replacing vanilla RuneScape control.

**Status:** NEEDS TEST

#### Bundle 1.1 - Vertical Jump POC

**Status:** NEEDS TEST

- [x] Trace local-player transform/input/tick seams. `verified-static`
- [x] Add isolated Java vertical jump proof.
- [x] Runtime-confirm visible vertical displacement. `VERIFIED`
- [ ] Carryover regression: airborne X/Z movement, slopes, hold/repress, post-landing behavior, relog.

#### Bundle 1.2 - Controller Mode Boundary

**Status:** DONE

- [x] Add `PlayerControllerMode`.
- [x] RuneScape default / Ctrl+M Mario activation.
- [x] Gate Mario proof behind mode.
- [x] Clean mode/lifecycle reset behavior.
- [x] User runtime acceptance that the mode boundary works. `VERIFIED`

Phase 1 carryover regression does not block the independent native-bridge work; preserve it for a consolidated runtime session.

### Phase 2 - SM64 Passthrough Core Bridge

**Purpose:** Make actual SM64-derived native code advance Mario state and return it to Matrix3.

**Status:** ACTIVE

#### Bundle 2.1 - Sidecar transport/native-core spike

**Status:** DONE

- [x] Record passthrough architecture and responsibility split.
- [x] Select sidecar-first transport; JNI remains a later optimization only if measured need exists.
- [x] Select `libsm64` as the first headless SM64 core candidate.
- [x] Add `sm64_bridge` native sidecar source and deterministic flat-floor protocol.
- [x] Add Java 8 `Sm64BridgeProbe` wrapper and one-shot Mario-mode probe trigger.
- [x] Add ignored local dependency/ROM layout and reproducible bridge build instructions.
- [x] Runtime prove Java can send A-button input and receive real native Mario Y/action changes from actual `libsm64`. `VERIFIED` 2026-10-04.

**Acceptance:** PASSED. Real `libsm64` initialized from the user's ROM and the Java probe reported a 96.50-unit native Y rise while its action-change gate also succeeded.

#### Bundle 2.2 - Native state -> Matrix transform

**Status:** ACTIVE

- [ ] Replace one-shot probe ownership with a persistent bridge session while Mario mode is active.
- [ ] Run native simulation at fixed 30 Hz.
- [ ] Map native vertical state onto the already-VERIFIED Matrix transform baseline.
- [ ] Preserve normal RuneScape mode/failure fallback and clean native reset/lifecycle behavior.
- [ ] Runtime prove an SM64-derived tick causes the visible 830 player/Mario presentation to jump.

### Phase 3 - Matrix World / Collision Adapter

**Purpose:** Let the SM64 core physically reason about RuneScape terrain and nearby collision instead of a temporary test floor.

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

**Purpose:** Replace the temporary RuneScape player presentation with Mario.

**Status:** PLANNED

- [ ] Evaluate revision-830 imported Mario model/animation path.
- [ ] Evaluate direct `SM64MarioGeometryBuffers` dynamic render path.
- [ ] Choose one based on real renderer/asset evidence, not preference alone.
- [ ] Prove idle/run/jump visual state driven from native Mario state.

### Phase 5 - Multiplayer / Server Authority

**Purpose:** Keep responsive native Mario behavior while restoring authoritative multiplayer/world validation.

**Status:** PLANNED

- [ ] Define legal Mario movement/state sent to server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Combat/world interaction authority boundaries.

## Current execution state

- Phase: 2 - SM64 Passthrough Core Bridge
- Phase status: ACTIVE
- Bundle: 2.2 - Native state -> Matrix transform
- Bundle status: ACTIVE
- Approval state: Bridge Spike A runtime verification recorded under the existing approved bundle; new Bundle 2.2 behavior requires fresh AAA before patching.
- Current checklist item: replace the one-shot probe with a persistent Mario-mode bridge session and fixed-step native simulation.
- Current objective: make returned native SM64 state, rather than Java gravity, drive the visible 830 local-player vertical transform while preserving RuneScape fallback.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Core lift and controller mode are runtime-proven; deeper movement/relog regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2 | 2.1 | DONE | Real libsm64 + user-ROM runtime verified. |
| Java bridge probe | 2 | 2.1 | DONE | Real native PASS verified with 96.50-unit Y rise. |
| Native -> visible Matrix transform | 2 | 2.2 | ACTIVE | Next patch target; requires fresh AAA. |
| Matrix terrain adapter | 3 | 3.1 | READY | Starts after native transform proof. |
| Mario visual presentation | 4 | 4.x | READY | Choose asset-vs-direct-geometry path after bridge evidence. |

## Decisions / new ideas

### Decision log

- Matrix3 is the host world/renderer/input/server architecture.
- The target architecture runs authentic SM64-derived Mario logic alongside Matrix rather than recreating the full action state machine in Java.
- `libsm64` is the first native-core candidate because its API already matches the required external-engine contract and Bridge Spike A is now runtime-proven.
- Sidecar process first; JNI only after a measured reason.
- The Java jump proof remains useful as host-transform evidence/fallback but is not the target Mario mechanics engine.
- Matrix terrain will be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.
- RuneScape control remains the safe default.
- Construction Revamp remains the repository's separate main workstream unless priority is explicitly changed.

## Testing

### Current quick checks

Bridge Spike A is runtime-accepted. Bundle 2.2 will require:

1. Enter Mario mode and establish one persistent native session.
2. Space/A input advances real SM64 state at fixed 30 Hz.
3. Returned native Y drives the visible 830 player transform.
4. Leaving Mario mode restores normal RuneScape control and clears/stops native session state.
5. Native bridge failure falls back safely without corrupting player position or normal input.

Detailed runtime checklist: `docs/mario/TESTLIST.md`.

### Carryover checks

- Java jump proof: airborne X/Z movement, slopes, hold/repress, post-landing movement, relog/reset.

### Smoke/regression checks

- Relevant `docs/rs3/SMOKE_TEST.md`: Eclipse Java 8 clean/build, client startup/login, normal movement, logout/relog after runtime-affecting bridge integration.

## Carryover / blockers

### CARRYOVER

- Phase 1 jump deeper regression: preserve for a consolidated client runtime test; the Java proof will be superseded for Mario vertical physics once Bundle 2.2 is accepted.
- Mario model/visual selection: intentionally deferred until native bridge state visibly drives Matrix presentation.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Runtime-accepted RuneScape/Mario mode boundary.
- Reframed the target architecture around an authentic native SM64 passthrough core.
- Implemented the native `sm64_bridge` protocol/build source and Java 8 `Sm64BridgeProbe`.
- Built real `libsm64`/sidecar under Windows MSYS2 MinGW64 with the user's local US ROM.
- Runtime-VERIFIED Bridge Spike A: manual `READY`/`PONG`, then Eclipse produced native PASS with a 96.50-unit Mario Y rise and intermediate native action-state change.

**Current phase:**

- Phase 2 - SM64 Passthrough Core Bridge (`ACTIVE`).

**Active bundle:**

- Bundle 2.2 - Native state -> Matrix transform (`ACTIVE`).

**Next checklist item:**

- With fresh AAA, replace one-shot probe ownership with a persistent Mario-mode bridge session, fixed 30 Hz native ticks, and native Y -> Matrix transform presentation.

**Do not re-scan without new evidence:**

- Matrix local-player transform/input/viewport ownership.
- Basic vertical-displacement feasibility.
- Controller-mode activation boundary.
- `libsm64` public input/state/surface API and 30 Hz example behavior.
- Bridge protocol/parser design and Windows sidecar/toolchain viability; Bridge Spike A is runtime-VERIFIED.

**Pending runtime verification:**

- Bundle 2.2 native state visibly drives the 830 player.
- Mario-mode exit/failure fallback remains clean with a persistent native session.
- Phase 1 deeper movement/relog carryover.

**Important remaining uncertainty:**

- Final Matrix<->SM64 scale/sign calibration for visible movement and later collision still needs Bundle 2.2 runtime tuning.

## Next recommended work

Bundle 2.2: make the persistent native SM64 simulation drive the visible Matrix player transform, replacing `MarioJumpController` Java gravity as Mario-mode vertical-physics ownership.
