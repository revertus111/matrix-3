# Mario 64 in Matrix3 / Revision 830

## Goal

Run an authentic Mario experience inside Matrix3/revision-830: Matrix3 owns the RuneScape world, input, rendering and eventual server authority, while an SM64-derived native core owns Mario's movement/action/animation state through a narrow passthrough bridge.

Detailed architecture: `docs/mario/SM64_PASSTHROUGH_ARCHITECTURE.md`.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Alternate movement/controller foundation | 🔵 In Progress |
| SM64 passthrough core bridge | 🔵 In Progress |
| Matrix world/collision adapter | ❌ Not started |
| Mario visual/animation presentation | 🔵 In Progress |
| Multiplayer/server authority integration | ❌ Not started |

## Scope

### In scope

- Matrix3-native alternate controller activation and lifecycle.
- Native SM64-derived Mario simulation through `libsm64` rather than reimplementing the full action state machine in Java.
- Binary sidecar transport for native Mario input/state, ROM-derived texture atlas data and `SM64MarioGeometryBuffers` animation geometry.
- Matrix-native Mario presentation through `Class159 -> Model` and the established Matrix renderer/scene seam.
- Temporary local-only native XYZ presentation for development/runtime proof while Matrix/RuneScape remains gameplay/server authority.
- Matrix terrain/object/water conversion into collision surfaces the SM64 core can consume.
- Eventual multiplayer/server validation after local behavior is stable.

### Current priority

Bundle 2.4 is runtime accepted. The user confirmed Mario owns WASD without moving the Construction/RTS camera, native X/Z visibly translates Mario with sensible direction/scale, moving actions preserve horizontal displacement, Ctrl+M restore/re-entry is clean, and normal camera WASD returns afterward.

The user also confirmed Mario already follows different RuneScape terrain elevations correctly in live play. That is accepted as **Matrix presentation/rebase behavior**, not proof that libsm64 is consuming RuneScape collision: the native core still simulates against its temporary flat floor.

Bundle 4.2B shared-topology smoothing is also runtime accepted at the default `70` degree threshold. The next architectural target is Phase 3: make libsm64 itself understand RuneScape terrain/object collision so the authentic SM64 action machine reacts to walls, slopes, ledges, platforms and later water rather than only receiving visually-correct Matrix presentation.

### Out of scope for the accepted local XYZ proof / next Phase 3 boundary

- Sending Mario movement packets or making the sidecar authoritative for persistent player position.
- Replacing RuneScape clipping, pathfinding, plane ownership or server correction behavior.
- Treating visually-correct terrain elevation as proof that the native flat-floor collision has been replaced.
- Server-authoritative Mario movement or remote-player replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.

## Architecture / ownership

- Matrix3 remains host architecture and world/server authority.
- `PlayerControllerMode` owns deliberate local RuneScape/Mario activation; RuneScape remains default.
- `Sm64BridgeSession` owns the persistent sidecar process, fixed 30 Hz SM64 tick, immutable control snapshots, and immutable native state/geometry publication.
- `Sm64BridgeSession` publishes one-native-tick-delayed interpolated native X/Y/Z from the same previous/latest frame pair and shared interpolation alpha.
- `MarioInputKeyboard` is a reversible view over Matrix3's existing `Class549` keyboard owner. The original AWT listener remains installed and tracks physical keys; while Mario mode is active normal `method6514(...)` consumers see W/A/S/D released, while Mario reads the original raw held state through `method6518(...)`. No second keyboard listener is installed.
- `MarioJumpController` retains its legacy hook-facing name but is the Matrix client-thread Mario input/presentation adapter. It forwards held Matrix keys to libsm64 and presents native XYZ through the verified Matrix player transform; it does not recreate Mario movement physics in Java.
- Current keyboard mapping is WASD -> native analog stick, Space -> A, F -> B, Shift -> Z. Diagonal WASD is normalized to unit magnitude.
- A/B/Z held while Mario mode is entered are suppressed until released so mode activation cannot manufacture a jump/attack/crouch action.
- Bundle 2.4 captures Matrix/native XYZ baselines when Mario mode starts. Native X/Z deltas are applied locally at the horizontal presentation scale while native Y retains the established positive-up -> Matrix negative-Y conversion.
- Default horizontal presentation scale is `3.0`; `-Dmatrix3.sm64.horizontalScale=<positive-float>` provides runtime calibration. The default `3.0` and direct X/Z signs are runtime accepted for the current local presentation path.
- Matrix/server corrections remain authoritative beneath the temporary presentation offset. If Matrix changes an axis externally, the presentation baseline rebases to that correction instead of fighting it. This runtime behavior is sufficient for Mario to follow differing visible RuneScape terrain elevations cleanly.
- Ctrl+M exit, native failure and local-player lifecycle replacement restore the tracked RuneScape XYZ baseline and restore the original Matrix keyboard owner.
- No Bundle 2.4 code sends Mario movement packets, replaces RuneScape clipping/pathfinding/plane authority, or streams RuneScape collision into libsm64.
- `MarioVisualRenderer` owns local Mario visual presentation from native geometry. It consumes immutable libsm64 frames on Matrix's render thread, converts them to `Class159`, builds a normal Matrix `Model`, and renders through the established direct scene-preview seam.
- Textured Mario faces keep the accepted micro-face atlas approximation. Bundle 4.2B shares compatible coincident source-triangle boundary vertices through an angle-gated topology path so Matrix normal generation shades compatible surfaces more smoothly without indiscriminately welding hard edges.
- Default smoothing threshold is `70` degrees; `-Dmatrix3.sm64.smoothAngleDegrees=0..180` provides runtime calibration. The default `70` degree path is runtime accepted.
- `Class578.method6834(...)` remains the established Matrix direct-preview render seam; Mario is another consumer rather than a second renderer.
- `Player.method10696(...)` remains the normal player model-build owner. A narrow fail-open gate suppresses only the local RuneScape appearance after `MarioVisualRenderer` has a fresh successful replacement frame. Remote players remain unchanged; stale/native-failure frames fall back to the RuneScape player.
- Native-state vertical presentation uses initial `3.0` SM64-to-Matrix Y scale; `-Dmatrix3.sm64.verticalScale=<value>` can override it.
- Mario mesh scale uses initial `2.0`; `-Dmatrix3.sm64.modelScale=<value>` can override it for visual calibration.
- Matrix collision is converted into SM64 surfaces in Phase 3; until then the sidecar uses the temporary flat native floor even though Matrix presentation already follows visible terrain elevations correctly.

## Verified foundation

### VERIFIED

- The actual revision-830 local player can visibly leave RuneScape terrain through the established local-player transform.
- The RuneScape/Mario controller boundary works at runtime.
- Bridge Spike A is runtime-confirmed against real `libsm64` + the user's local US ROM on Windows/MSYS2 MinGW64.
- Manual native protocol returned `READY 1` / `PONG 1` for the original text bridge.
- Eclipse/Java produced `[SM64 Bridge] PASS native SM64 state: y -0.00 -> 96.50 (rise 96.50), action 205521409 -> 205521409`.
- That PASS proves Matrix Java can launch the native core, send input, execute SM64-derived movement/action code and receive real native Mario state back.
- Bundle 2.2 runtime confirmed the persistent sidecar reaches `READY (30 Hz)`, native-state presentation reaches `ACTIVE`, and the native-driven Matrix transform path works in the live 830 client.
- The visible Mario-mode vertical presentation path is driven by real native SM64 state rather than Java gravity.
- Bundle 2.3 runtime confirms WASD reaches native movement/action state: Mario visibly enters movement/turning animations.
- Bundle 2.3 runtime confirms native Z/crouch behavior, native backflip, and airborne ground-pound work through the real libsm64 action machine.
- Bundle 2.4 runtime confirms Mario-mode WASD no longer drives the Construction/RTS camera while raw WASD still reaches Mario.
- Bundle 2.4 runtime confirms native X/Z visibly translates Mario with sensible direction/scale, including during moving actions.
- Bundle 2.4 runtime confirms Ctrl+M restore/re-entry and normal camera-WASD restoration work without stale XYZ displacement.
- Bundle 2.4 runtime confirms arrow-key camera pan and Q/E rotation remain available while Mario owns WASD.
- Bundle 2.4 runtime confirms the current Matrix presentation/rebase path follows different RuneScape terrain elevations correctly in live play.
- Bundle 4.1 binary geometry-capable `sm64_bridge.exe` was rebuilt locally under MSYS2 MinGW64 and reports the expected `[--binary]` usage.
- Actual libsm64 Mario geometry is visibly rendered inside the revision-830 world at the local player position and replaces the RuneScape body.
- Native Mario idle animation visibly updates in-world across successive geometry frames.
- Native Mario jump animation visibly plays while native SM64 Y drives the visible jump.
- Ctrl+M restores the normal RuneScape local-player presentation, and re-entering Mario mode recreates the animated Mario replacement cleanly.
- The atlas micro-face v3 path restores Mario's texture details while keeping the former giant black whole-source-triangle artifact fixed. Bundle 4.2A visual fidelity is runtime accepted 2026-10-04.
- Bundle 4.2B shared-topology smoothing is runtime accepted at the default `70` degree threshold: Mario reads visibly rounder/less faceted while accepted atlas detail and hard-edge presentation remain intact.

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam driving `ConstructionBuildCamera.tick()` and `MarioJumpController.tick()`.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`.
- `Class549_Sub1.anIntArray8901` maps the controls to Matrix internal keys: W=33, A=48, S=49, D=50, F=51, Shift=81, Space=83.
- Construction Free Build and RTS camera paths both poll W/A/S/D through the shared `method6514(...)` held-key seam; arrows are separate camera movement keys and Q/E remain separate camera controls.
- `MarioInputKeyboard` delegates the full `Class549` contract to the original owner and filters only W/A/S/D from `method6514(...)`; Mario reads the original raw held state rather than installing another listener.
- `libsm64` exposes Mario input/state, collision surfaces, dynamic surface objects and already-animated `SM64MarioGeometryBuffers`.
- The existing binary bridge command already carries `camLookX`, `camLookZ`, `stickX`, `stickY`, A, B and Z, and every binary frame already returns native X/Y/Z, so Bundle 2.4 requires no native protocol version change or sidecar rebuild.
- `Sm64BridgeSession.NativePosition` uses one shared interpolation alpha for X/Y/Z, preventing presentation axes from sampling different native phases.
- Bundle 2.4 applies native X/Z only through the local player transform. No networking/clipping/pathfinding owner is changed.
- `sm64_global_init(...)` supplies the ROM-derived Mario RGBA atlas used by libsm64's renderer path.
- `Class159(int vertexCapacity, int faceCapacity, int textureCapacity)` plus its vertex/triangle arrays can represent generated raw geometry for `Class106.method1755(...)`.
- `Class159.method2560(x,y,z)` establishes that coincident vertex reuse is a supported raw-model topology operation; Bundle 4.2B applies the same shared-topology principle with an angle guard rather than welding every same-position vertex.
- Matrix's Live Model Editor already proves the renderer-native `Class159 -> Model -> Model.method1375(...)` path.
- `Class578.method6834(...)` is an established direct scene-preview seam used by Matrix developer/runtime previews.
- `Player.method10696(...)` is the shared player model-build path consumed by the player's render/picking variants, so the local Mario replacement gate does not require duplicating suppression across renderer variants.
- The binary bridge sends the ROM atlas once at handshake, then each fixed 30 Hz step sends native state plus positions/colors/UVs for the triangles used by the current SM64 animation frame.
- Native simulation/geometry publication happens on the worker; Matrix model construction/rendering remains on Matrix's render/client ownership path.
- `MarioVisualRenderer` generates model-local vertices from `(geometry position - native state position)` before anchoring the Model to Matrix player position; with Bundle 2.4 the anchor itself follows the accepted temporary local native XYZ presentation.
- libsm64's reference GL renderer draws Mario base colour/lighting first, then overlays the ROM texture as a separate UV-mapped pass; the accepted micro-face presentation approximates those texture details inside Matrix without creating a second renderer.
- Bundle 4.2B shares only generated boundary vertices whose transformed geometric face normals are within the configured smooth-angle threshold; atlas face colours remain per-face and are not merged.

## Unknown / research needed

### HYPOTHESIS

- Sidecar binary transport is fast enough for sustained 30 Hz state + geometry + full control input; runtime measurement is required before considering JNI.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Final Matrix<->SM64 coordinate conversion for **collision-backed** XYZ movement. The current local presentation scale/sign is runtime accepted but does not by itself prove the native collision-space conversion.
- Whether fixed native camera-look `(0,-1)` feels acceptable for keyboard steering or should become Matrix-camera-relative.
- Final collision-bubble radius/rebuild threshold.
- Correct terrain triangle winding/material selection for RuneScape slopes when translated into SM64 surfaces.
- Runtime cost of rebuilding one Matrix `Model` per native 30 Hz animated geometry frame under sustained use.
- Runtime cost of object collision proxy extraction.
- Final client/server reconciliation model.

## Dependencies

- Matrix3 client input/player-transform/renderer ownership.
- Local `libsm64`/`sm64_bridge` build.
- User-owned SM64 US ROM as a local runtime dependency only; never committed.
- User runtime testing for collision-backed terrain/object behavior and regression acceptance.

## Development plan

### Phase 1 - Alternate Controller Foundation

**Status:** NEEDS TEST / FOUNDATION PROVEN

#### Bundle 1.1 - Vertical Jump POC

**Status:** CARRYOVER

- [x] Trace local-player transform/input/tick seams. `verified-static`
- [x] Add isolated Java vertical jump proof.
- [x] Runtime-confirm visible vertical displacement. `VERIFIED`
- [ ] Legacy lifecycle/movement regression where still useful.

#### Bundle 1.2 - Controller Mode Boundary

**Status:** DONE

- [x] Add `PlayerControllerMode`.
- [x] RuneScape default / Ctrl+M Mario activation.
- [x] Gate Mario behavior behind mode.
- [x] Clean mode/lifecycle reset behavior.
- [x] User runtime acceptance. `VERIFIED`

### Phase 2 - SM64 Passthrough Core Bridge

**Status:** ACTIVE / CARRYOVER CHECKS

#### Bundle 2.1 - Sidecar transport/native-core spike

**Status:** DONE

- [x] Record passthrough architecture and responsibility split.
- [x] Select sidecar-first transport and `libsm64` native core.
- [x] Add `sm64_bridge` and deterministic flat floor.
- [x] Runtime prove real native Y/action state returns to Matrix Java. `VERIFIED` 2026-10-04.

#### Bundle 2.2 - Native state -> Matrix transform

**Status:** RUNTIME ACCEPTED / CARRYOVER REGRESSION

- [x] Persistent `Sm64BridgeSession` while Mario mode is active.
- [x] Fixed 30 Hz native simulation on daemon worker.
- [x] Worker publishes state without mutating Matrix scene state.
- [x] Native-Y presentation replaces Java gravity.
- [x] One-native-tick delayed Y interpolation.
- [x] Preserve Matrix terrain baseline until real collision adapter.
- [x] Stop/reset on mode exit/player lifecycle.
- [x] Auto-fallback on missing/failed native session.
- [x] Prevent held-Space mode entry from manufacturing a jump.
- [x] Runtime prove native state visibly drives the 830 player. `VERIFIED` 2026-10-04.
- [x] Regression: grounded Ctrl+M exit restores normal RuneScape presentation/stops Mario ownership. `VERIFIED` 2026-10-04.
- [ ] Regression: airborne exit/relog lifecycle and stale-transform behavior.

#### Bundle 2.3 - Native keyboard movement/action controls

**Status:** RUNTIME PARTIAL / CARRYOVER CHECKS

- [x] Reuse Matrix held-key owner; no second keyboard listener.
- [x] Map WASD to normalized libsm64 analog stick input.
- [x] Map Space -> A, F -> B, Shift -> Z.
- [x] Publish stick/A/B/Z as one immutable input snapshot to the fixed 30 Hz worker.
- [x] Preserve held-action entry guards for A/B/Z.
- [x] Preserve binary protocol/native executable compatibility; no sidecar rebuild required.
- [x] Runtime verify WASD reaches native movement/turning animation states. `VERIFIED` 2026-10-04.
- [x] Runtime verify Z crouch. `VERIFIED` 2026-10-04.
- [x] Runtime verify native backflip. `VERIFIED` 2026-10-04.
- [x] Runtime verify airborne ground-pound. `VERIFIED` 2026-10-04.
- [ ] Runtime verify B-button grounded attack behavior.
- [ ] Runtime verify native long-jump transition using movement + Z/A timing.
- [ ] Runtime verify held-key entry guards remain clean.

#### Bundle 2.4 - Local XYZ presentation + WASD ownership

**Status:** RUNTIME ACCEPTED

- [x] Add reversible Mario keyboard view that reserves W/A/S/D from normal `method6514(...)` consumers without adding another AWT listener.
- [x] Preserve raw W/A/S/D state for Mario input through the original held-key owner.
- [x] Restore original keyboard owner on Ctrl+M exit, native failure and local-player lifecycle reset.
- [x] Extend published presentation state to interpolated native X/Y/Z with one shared interpolation alpha.
- [x] Capture Matrix/native XYZ baselines on Mario-mode activation.
- [x] Apply native X/Z deltas plus native Y height through the local Matrix player transform only.
- [x] Add `-Dmatrix3.sm64.horizontalScale=<positive-float>`; default `3.0`.
- [x] Rebase external Matrix/server corrections per axis and restore tracked RuneScape XYZ on exit/fallback.
- [x] Preserve networking, clipping, pathfinding, plane and persistent server authority.
- [x] Runtime verify Mario-mode WASD no longer pans the Construction Free/RTS camera. `VERIFIED` 2026-10-04.
- [x] Runtime verify arrow-key pan and Q/E camera rotation remain usable while Mario owns WASD. `VERIFIED` 2026-10-04.
- [x] Runtime verify Mario visibly translates from native X/Z and direction/scale are sensible. `VERIFIED` 2026-10-04.
- [x] Runtime verify moving jump/backflip/ground-pound retain horizontal displacement. `VERIFIED` 2026-10-04.
- [x] Runtime verify Ctrl+M restore/re-entry has no stale XYZ offset and normal camera WASD returns afterward. `VERIFIED` 2026-10-04.
- [x] Runtime verify visible Mario presentation follows different RuneScape terrain elevations correctly. `VERIFIED` 2026-10-04; this is presentation evidence, not native collision ingestion.

### Phase 3 - Matrix World / Collision Adapter

**Status:** READY / NEXT

#### Bundle 3.1 - Terrain heightfield -> SM64 surfaces

**Status:** READY

- [ ] Establish Matrix terrain-corner height sampler.
- [ ] Convert nearby RuneScape tile quads into correctly wound SM64 triangles.
- [ ] Add local origin/scale conversion and bounded collision bubble.
- [ ] Replace temporary flat-floor assumptions with RuneScape terrain-backed native collision.
- [ ] Runtime verify the **native SM64 action/collision machine**, not only Matrix presentation, reacts correctly to RuneScape slopes/terrain.

Runtime note: visible Mario already traverses different RuneScape terrain elevations correctly through Matrix presentation/rebasing. Bundle 3.1 therefore focuses on collision authenticity rather than fixing visible terrain height following.

#### Bundle 3.2 - Objects / walls / platforms

- [ ] Add nearby solid-object collision proxies.
- [ ] Add walls/ceilings/bridges/platform floors as required.
- [ ] Use `SM64SurfaceObject` for moving platforms when needed.
- [ ] Add water-level bridging when content requires it.

### Phase 4 - Mario Visual / Animation Presentation

**Status:** ACTIVE / RUNTIME ACCEPTED CORE / POLISH REMAINS

#### Bundle 4.1 - Direct native Mario geometry -> Matrix Model

**Status:** RUNTIME ACCEPTED / CARRYOVER REGRESSION

- [x] Select direct `SM64MarioGeometryBuffers` presentation for the first authentic Mario visual path.
- [x] Extend `sm64_bridge` with binary mode so animated geometry can cross the sidecar without text-float serialization.
- [x] Send the ROM-derived Mario RGBA atlas once during binary handshake.
- [x] Send native Mario state + used animated geometry positions/colors/UVs each fixed 30 Hz step.
- [x] Extend `Sm64BridgeSession` with immutable `GeometryFrame` and `TextureAtlas` publication.
- [x] Add `MarioVisualRenderer` and consume geometry only on Matrix's renderer/client path.
- [x] Convert each native animation frame into generated `Class159` raw triangles and build a normal Matrix `Model` through `Class106.method1755(...)`.
- [x] Render through the established `Class578.method6834(...)` scene-preview seam; no second renderer/OpenGL path.
- [x] Add local-player fail-open replacement gate in `Player.method10696(...)`.
- [x] Add `-Dmatrix3.sm64.modelScale=<positive-float>` calibration override.
- [x] Rebuild local `sm64_bridge.exe` for binary protocol. `VERIFIED` 2026-10-04.
- [x] Runtime: actual Mario replaces the local RuneScape player at the same world transform. `VERIFIED` 2026-10-04.
- [x] Runtime: idle and jump native animations visibly work. `VERIFIED` 2026-10-04.
- [x] Runtime: Ctrl+M restore/re-entry works. `VERIFIED` 2026-10-04.
- [ ] Runtime regression carryover: other players remain normal RuneScape players.
- [ ] Runtime regression carryover: sustained use has no render/model-build error spam or unacceptable performance cost.

#### Bundle 4.2 - Visual fidelity / animation polish

**Status:** 4.2A RUNTIME ACCEPTED / 4.2B RUNTIME ACCEPTED

- [x] Runtime-identify V1 giant dark/black whole-triangle colour artifacts. `VERIFIED` 2026-10-04.
- [x] Trace the artifact to the lossy atlas-UV-to-single-face-colour approximation. `verified-static`.
- [x] Compare libsm64 reference rendering semantics. `verified-static`.
- [x] Implement native-base-colour fallback and prove the giant source-triangle artifact disappears.
- [x] Implement atlas micro-face v3 with clamp-to-edge + bilinear RGBA sampling and bounded tessellation.
- [x] Runtime-accept atlas micro-face v3: facial/eye/clothing texture details restored and giant black source-triangle blocks remain gone. `VERIFIED` 2026-10-04.
- [x] Implement 4.2B angle-gated shared boundary topology for smoother Matrix normal generation. `verified-static`.
- [x] Add `-Dmatrix3.sm64.smoothAngleDegrees=0..180`; default `70`.
- [x] Runtime-accept 4.2B: Mario looks visibly rounder/less faceted without melted hard edges or texture regression. `VERIFIED` 2026-10-04.
- [ ] Calibrate model scale/orientation/ground anchor if later runtime evidence shows it is needed.
- [ ] Add geometry interpolation only if 30 Hz pose stepping is visibly objectionable.
- [ ] Map/forward native visual events such as sounds/particles after movement/collision ownership is stable.

### Phase 5 - Multiplayer / Server Authority

**Status:** PLANNED

- [ ] Define legal Mario movement/state sent to server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Combat/world interaction authority boundaries.

## Current execution state

- Phase: 3 - Matrix World / Collision Adapter
- Phase status: READY / NEXT
- Bundle: 3.1 - Terrain heightfield -> SM64 surfaces
- Bundle status: READY
- Approval state: Bundle 2.4 was approved with `SAP AAA` and is runtime accepted. Phase 3 implementation has **not** received a new AAA yet.
- Current checklist item: start Bundle 3.1 only after approval; first establish the Matrix terrain-corner height sampler and the Matrix<->SM64 local collision-space conversion.
- Current objective: replace libsm64's temporary flat native floor with bounded RuneScape terrain collision while preserving the already-accepted visible XYZ/terrain-elevation presentation path.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Controller boundary and native jump are runtime-proven; deeper lifecycle regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2/4 | 2.1/4.1 | VERIFIED | State + binary geometry executable runtime-proven locally. |
| Native -> visible Matrix transform | 2 | 2.2/2.4 | VERIFIED | Native XYZ local presentation is runtime accepted; visible terrain elevation following also works. |
| Mario keyboard/action controls | 2 | 2.3 | RUNTIME PARTIAL | Movement states, crouch, backflip and ground-pound verified; B attack/long-jump/entry guards remain. |
| Mario WASD ownership | 2 | 2.4 | VERIFIED | Mario owns WASD without moving the Construction/RTS camera; camera controls restore cleanly. |
| Mario native geometry transport | 4 | 4.1 | VERIFIED | Actual Mario geometry and successive native animation frames reach Matrix at runtime. |
| Matrix Mario visual renderer | 4 | 4.1 | VERIFIED / CARRYOVER REGRESSION | Body replacement, idle, jump, restore and re-entry work; remote-player/stability checks remain. |
| Mario visual fidelity | 4 | 4.2 | VERIFIED / POLISH CARRYOVER | Atlas micro-face v3 and shared-topology smoothing are runtime accepted. |
| Matrix terrain adapter | 3 | 3.1 | READY | Next architectural step: native SM64 collision surfaces from RuneScape terrain. |

## Decisions / new ideas

- Matrix3 is the host world/renderer/input/server architecture.
- Authentic SM64-derived logic owns Mario-mode movement/action/animation state; do not rebuild the full action state machine in Java.
- Sidecar process remains transport until measurement gives a reason for JNI.
- Matrix scene/model mutation stays on Matrix's client/render ownership path even though native simulation runs on a worker.
- WASD/Space/F/Shift are the first developer keyboard mapping: analog stick/A/B/Z respectively.
- The user explicitly approved and runtime-accepted a temporary **local-only** native X/Z presentation proof before Phase 3. This changes presentation, not networking/clipping/pathfinding/server authority.
- Mario mode owns WASD through a reversible view of the existing Matrix keyboard owner; do not add a second keyboard listener or fork Construction camera controls.
- Visible terrain elevation following is already accepted through Matrix presentation/rebasing. Phase 3 should not redo that visual behavior; it should make the native SM64 collision/action machine consume equivalent RuneScape surfaces.
- Phase 3 remains the required boundary for authentic RuneScape terrain/object collision and final movement/coordinate authority.
- Direct libsm64 animated geometry is the selected Mario visual path.
- The accepted micro-face atlas path and shared-topology smoothing remain the visual solution unless new runtime evidence regresses them.
- RuneScape control/presentation remains the safe default and fail-open fallback.
- Matrix terrain will be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.

## Testing

See `docs/mario/TESTLIST.md` for the consolidated runtime gate.

## Carryover / blockers

### CARRYOVER

- Phase 1 deeper lifecycle regression where still useful.
- Bundle 2.2 airborne exit/relog/stale-transform regression.
- Bundle 2.3 F/B attack, long-jump timing and held-action entry-guard checks.
- Bundle 4.1 remote-player isolation and sustained-runtime/performance regression.
- Bundle 4.2 scale/orientation/ground-anchor polish only if later runtime evidence requires it.
- Separate reported idle->Space fallback-to-RuneScape-player bug.

### BLOCKED

- None. Phase 3 is ready but intentionally awaits a new AAA before source changes.

## Resume Here

**Last completed:**

- Bridge Spike A runtime-VERIFIED against real `libsm64` + user ROM.
- Native Y -> Matrix transform runtime-VERIFIED.
- Actual libsm64 Mario geometry, idle and jump animations, RuneScape restore and Mario re-entry are runtime-VERIFIED.
- Atlas micro-face v3 visual fidelity is runtime-VERIFIED: texture details restored without the giant black source-triangle artifact.
- Bundle 4.2B shared-topology smoothing is runtime-VERIFIED at the default `70` degree threshold.
- Bundle 2.3 native controls are runtime-partially accepted: movement animations, crouch, backflip and ground-pound work through libsm64.
- Bundle 2.4 is runtime-VERIFIED: reversible WASD ownership, shared interpolated XYZ, visible native X/Z translation, moving actions, Ctrl+M restore/re-entry and camera-control restoration all work.
- Bundle 2.4 visible Matrix presentation also follows different RuneScape terrain elevations correctly. Do not mistake that for native SM64 collision ingestion.

**Current phase:**

- Phase 3 - Matrix World / Collision Adapter (`READY / NEXT`).

**Active bundle:**

- Bundle 3.1 - Terrain heightfield -> SM64 surfaces (`READY`).

**Next checklist item:**

1. Wait for AAA for Phase 3 source changes.
2. Establish the Matrix terrain-corner height sampler at the local player/collision bubble.
3. Define the local Matrix<->SM64 coordinate conversion using the already-accepted presentation orientation as evidence, while independently validating native collision-space signs/winding.
4. Convert nearby RuneScape tile quads into two correctly wound SM64 terrain triangles each.
5. Feed only a bounded local collision bubble to libsm64; do not convert the whole map.
6. Replace the temporary flat-floor native assumption with those RuneScape terrain surfaces.
7. Runtime-test stand/run/jump on slopes and elevation transitions, specifically verifying **native collision/action behavior**, not merely visible Matrix height following.
8. After terrain surfaces are accepted, continue into objects/walls/platform collision proxies.

**Files/systems already inspected:**

- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/MarioInputKeyboard.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `Client/src/main/java/game/MarioVisualRenderer.java`
- `Client/src/main/java/game/Class549.java`
- `Client/src/main/java/game/Class549_Sub1.java`
- `Client/src/main/java/game/Class108.java`
- `Client/src/main/java/game/ConstructionBuildCamera.java`
- `Client/src/main/java/game/Class343.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/Class159.java`
- `Client/src/main/java/game/Class106.java`
- `Client/src/main/java/game/Player.java`
- `native/sm64-bridge/sm64_bridge.c`
- libsm64 input/state/geometry contract already documented in `SM64_PASSTHROUGH_ARCHITECTURE.md`.

**Do not re-scan without new evidence:**

- Local-player transform/input/viewport ownership.
- Matrix held-key owner and W/A/S/D/F/Shift/Space internal mappings.
- Construction camera's W/A/S/D held-key seam; Bundle 2.4 runtime accepted the shared-keyboard-owner solution.
- Bundle 2.4 local XYZ interpolation/presentation/restore behavior unless a new runtime regression appears.
- Binary input/state packet structure; it already carries stick X/Y + A/B/Z and returns native XYZ.
- `Class159 -> Model` generated geometry ownership.
- `Class159.method2560(...)` coincident-vertex reuse precedent.
- `Class578.method6834(...)` direct scene-preview seam.
- `Player.method10696(...)` shared player model-build suppression seam.
- libsm64 geometry buffer/ROM atlas availability.
- Atlas micro-face v3 artifact cause/fix unless runtime regresses.
- Bundle 4.2B shared-boundary topology unless runtime shows a new defect.

**Pending runtime verification:**

- Bundle 2.3 F/B attack behavior, long-jump timing and held-entry guards.
- Fixed-camera-look steering feel.
- Remote-player isolation.
- Sustained stability / render-model error and performance behavior.
- Airborne exit/relog lifecycle carryover.
- Separate idle->Space fallback bug remains pending.

**Important remaining uncertainty:**

- The current `3.0` horizontal scale/direct X/Z signs are runtime accepted for local presentation, but native collision-space conversion/winding still needs independent Phase 3 validation.
- Temporary local XYZ still runs against libsm64's flat floor. The fact that visible Mario follows RuneScape terrain elevations does not mean the native action/collision machine understands those surfaces yet.
- Whether the existing fixed native camera-look vector feels natural enough for WASD or needs a Matrix-camera-relative adapter.

## Next recommended work

Phase 3 Bundle 3.1: stream a bounded RuneScape terrain heightfield into libsm64 as native collision surfaces. Preserve the already-working Matrix terrain-elevation presentation, and use the new surfaces specifically to make authentic SM64 collision/action logic react to RuneScape slopes and terrain. After terrain is accepted, move into object/wall/platform collision.