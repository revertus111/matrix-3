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

### Out of scope for the current bridge spike

- Full RuneScape collision conversion.
- Mario model replacement in the 830 scene.
- Server-authoritative Mario movement.
- Remote-player Mario replication.
- JNI/in-process native loading before sidecar transport is measured.
- Rewriting long jump/triple jump/wall kick/etc. in Java unless a specific Matrix-only behavior later requires an adapter.

## Architecture / ownership

- Matrix3 remains host architecture and world authority.
- `PlayerControllerMode` owns deliberate local RuneScape/Mario activation; RuneScape remains default.
- The existing Java vertical jump proof establishes that the 830 local player transform can leave terrain; it is not the final Mario physics implementation.
- `libsm64` is the first native-core candidate. It is derived from the SM64 decomp and is designed to expose Mario movement/rendering to external engines while loading the user's own US ROM at runtime.
- V1 transport is an isolated native sidecar process driven from Java 8 through `ProcessBuilder` + stdin/stdout.
- Matrix input is normalized and sent to the bridge. Native `SM64MarioState` returns to Matrix for presentation.
- Matrix collision is converted into SM64 surfaces by a future adapter; the first bridge spike uses a temporary flat native floor only.
- Sidecar/native failures must never replace normal RuneScape control, server authority, plane, persistence or clipping.

## Verified foundation

### VERIFIED

- User runtime testing confirmed the actual revision-830 local player visibly leaves RuneScape terrain with the Matrix3-native vertical proof.
- User runtime acceptance confirmed the explicit RuneScape/Mario controller boundary works.
- This proves Matrix3 can host foreign local movement/presentation behind an opt-in controller mode.

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam already driving Mario controller presentation.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`; Space/Ctrl/M mappings are established.
- `libsm64` exposes `SM64MarioInputs`, `SM64MarioState`, `SM64Surface`, native Mario create/tick/delete calls, static surfaces, dynamic surface objects and geometry buffers.
- The `libsm64` example advances Mario in fixed 30 Hz steps and uses caller-provided collision surfaces.
- `libsm64` loads Mario texture/animation data from a user-supplied SM64 US ROM at runtime.

## Unknown / research needed

### HYPOTHESIS

- A sidecar process with a tiny line protocol is sufficient for the first 30 Hz native-state proof and keeps native failure isolated during development.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Final Matrix<->SM64 coordinate scale/sign calibration beyond the already-proven Matrix vertical direction.
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

Phase 1 carryover regression does not block the independent native-bridge transport spike; preserve it for the next consolidated runtime session.

### Phase 2 - SM64 Passthrough Core Bridge

**Purpose:** Make actual SM64-derived native code advance Mario state and return it to Matrix3.

**Status:** ACTIVE

#### Bundle 2.1 - Sidecar transport/native-core spike

**Status:** ACTIVE

- [x] Record passthrough architecture and responsibility split.
- [x] Select sidecar-first transport; JNI remains a later optimization only if measured need exists.
- [x] Select `libsm64` as the first headless SM64 core candidate.
- [ ] Add `sm64_bridge` native sidecar source and deterministic flat-floor protocol.
- [ ] Add Java 8 bridge probe/client wrapper.
- [ ] Runtime prove Java can send A-button input and receive a real native Mario Y/action change.

**Acceptance:** Java reports PASS only after native SM64-derived state changes from deterministic input. No Matrix player movement is required yet.

#### Bundle 2.2 - Native state -> Matrix transform

**Status:** PLANNED

- [ ] Run native simulation at fixed 30 Hz.
- [ ] Map native vertical state onto the already-VERIFIED Matrix transform baseline.
- [ ] Preserve normal RuneScape mode/failure fallback.
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
- Bundle: 2.1 - Sidecar transport/native-core spike
- Bundle status: ACTIVE
- Approval state: `SAP AAA` approved for architecture documentation + bridge spike on 2026-10-03.
- Current checklist item: add native sidecar + Java probe.
- Current objective: prove actual SM64-derived native code can receive Matrix-side input and return Mario state before touching RuneScape collision.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Core lift and controller mode are runtime-proven; deeper movement/relog regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2 | 2.1 | ACTIVE | Flat-floor protocol spike. |
| Java bridge probe | 2 | 2.1 | READY | Launch sidecar, send deterministic A input, verify native state. |
| Native -> visible Matrix transform | 2 | 2.2 | READY | Starts after transport proof. |
| Matrix terrain adapter | 3 | 3.1 | READY | Starts after native transform proof. |
| Mario visual presentation | 4 | 4.x | READY | Choose asset-vs-direct-geometry path after bridge evidence. |

## Decisions / new ideas

### Decision log

- Matrix3 is the host world/renderer/input/server architecture.
- The target architecture now runs authentic SM64-derived Mario logic alongside Matrix rather than recreating the full action state machine in Java.
- `libsm64` is the first native-core candidate because its API already matches the required external-engine contract.
- Sidecar process first; JNI only after a measured reason.
- The Java jump proof remains useful as host-transform evidence/fallback but is not the target Mario mechanics engine.
- Matrix terrain will be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.
- RuneScape control remains the safe default.
- Construction Revamp remains the repository's separate main workstream unless priority is explicitly changed.

## Testing

### Current quick checks

1. Existing: normal RuneScape mode remains safe and Mario mode can still be toggled.
2. Bridge Spike A: sidecar starts with user ROM and prints protocol `READY`.
3. Java probe gets `PONG`.
4. Java sends deterministic 30 Hz A-button sequence.
5. PASS only if returned native Mario Y/action changes from the baseline.

### Carryover checks

- Java jump proof: airborne X/Z movement, slopes, hold/repress, post-landing movement, relog/reset.

### Smoke/regression checks

- Relevant `docs/rs3/SMOKE_TEST.md`: Eclipse Java 8 clean/build, client startup/login, normal movement, logout/relog after runtime-affecting bridge integration.

## Carryover / blockers

### CARRYOVER

- Phase 1 jump deeper regression: does not block isolated sidecar/protocol development; finish during the next consolidated client runtime test.
- Mario model/visual selection: intentionally deferred until native bridge state is real.

### BLOCKED

- Runtime native bridge verification requires a locally built `libsm64`/sidecar and the user's US ROM. The ROM remains outside Git.

## Resume Here

**Last completed:**

- Runtime-accepted RuneScape/Mario mode boundary.
- Reframed the target architecture around an authentic native SM64 passthrough core.
- Added `docs/mario/SM64_PASSTHROUGH_ARCHITECTURE.md` and selected `libsm64` + sidecar-first transport.

**Current phase:**

- Phase 2 - SM64 Passthrough Core Bridge (`ACTIVE`).

**Active bundle:**

- Bundle 2.1 - Sidecar transport/native-core spike (`ACTIVE`).

**Next checklist item:**

- Add native `sm64_bridge` flat-floor protocol and Java bridge probe.

**Do not re-scan without new evidence:**

- Matrix local-player transform/input/viewport ownership.
- Basic vertical-displacement feasibility.
- Controller-mode activation boundary.
- `libsm64` public input/state/surface API and 30 Hz example behavior.

**Pending runtime verification:**

- Native sidecar can initialize from the user ROM.
- Java <-> sidecar protocol works.
- Deterministic A input changes actual native Mario state.
- Phase 1 deeper movement/relog carryover.

**Important remaining uncertainty:**

- Final coordinate scale and collision conversion are intentionally deferred until the native transport proof succeeds.

## Next recommended work

Finish Bridge Spike A: native sidecar + Java probe, then runtime-test one deterministic native Mario jump before applying native state to the visible Matrix player.
