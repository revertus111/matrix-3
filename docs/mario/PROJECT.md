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
- Matrix terrain/object/water conversion into collision surfaces the SM64 core can consume.
- Eventual multiplayer/server validation after local behavior is stable.

### Current priority

The user explicitly reprioritized the active slice to Mario visual smoothing on 2026-10-04 before running the pending Bundle 2.3 keyboard-control acceptance. Bundle 4.2B now owns the immediate gate: make the already-working textured Mario shade visibly rounder/less faceted without disturbing atlas detail, animation, movement/controller behavior, or hard-edge presentation.

Bundle 2.3 remains implemented and waiting for runtime acceptance after the smoothing check. Phase 3 remains the required next architectural step before native SM64 horizontal position can replace Matrix/RuneScape X/Z safely.

### Out of scope for the current smoothing slice

- Changing Mario movement/action input behavior or fixing the separate idle->Space fallback issue.
- Writing native SM64 X/Z directly into Matrix player position before the collision/authority adapter exists.
- Full RuneScape terrain/object collision conversion.
- Server-authoritative Mario movement or remote-player replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.
- Replacing Matrix renderer ownership or adding a second OpenGL renderer.

## Architecture / ownership

- Matrix3 remains host architecture and world authority.
- `PlayerControllerMode` owns deliberate local RuneScape/Mario activation; RuneScape remains default.
- `Sm64BridgeSession` owns the persistent sidecar process, fixed 30 Hz SM64 tick, immutable control snapshots, and immutable native state/geometry publication.
- `MarioJumpController` retains its legacy hook-facing name but is now the Matrix client-thread Mario input/presentation adapter. It forwards held Matrix keys to libsm64 and maps native vertical state onto the verified Matrix player transform; it does not recreate Mario movement physics in Java.
- Current keyboard mapping is WASD -> native analog stick, Space -> A, F -> B, Shift -> Z. Diagonal WASD is normalized to unit magnitude.
- A/B/Z held while Mario mode is entered are suppressed until released so mode activation cannot manufacture a jump/attack/crouch action.
- `MarioVisualRenderer` owns local Mario visual presentation from native geometry. It consumes immutable libsm64 frames on Matrix's render thread, converts them to `Class159`, builds a normal Matrix `Model`, and renders through the established direct scene-preview seam.
- Textured Mario faces keep the accepted micro-face atlas approximation. Bundle 4.2B changes generated topology only: coincident source-triangle boundary vertices may be shared when source-face normals fall within the smoothing threshold, allowing Matrix's normal generation to shade compatible surfaces more smoothly without indiscriminately welding hard edges.
- Default smoothing threshold is `70` degrees; `-Dmatrix3.sm64.smoothAngleDegrees=0..180` provides runtime calibration without another patch.
- `Class578.method6834(...)` remains the established Matrix direct-preview render seam; Mario is another consumer rather than a second renderer.
- `Player.method10696(...)` remains the normal player model-build owner. A narrow fail-open gate suppresses only the local RuneScape appearance after `MarioVisualRenderer` has a fresh successful replacement frame. Remote players remain unchanged; stale/native-failure frames fall back to the RuneScape player.
- Native SM64 Y is applied through Matrix3's VERIFIED local-player transform from the established client/viewport thread.
- Native SM64 X/Z currently advances inside libsm64 for authentic movement/action/animation decisions, but `MarioVisualRenderer` subtracts native frame position from generated geometry and anchors Mario to the Matrix local-player transform. Matrix X/Z, plane, clipping, pathfinding and server position authority therefore remain unchanged in Bundle 2.3.
- Native-state presentation uses initial `3.0` SM64-to-Matrix Y scale; `-Dmatrix3.sm64.verticalScale=<value>` can override it.
- Mario mesh scale uses initial `2.0`; `-Dmatrix3.sm64.modelScale=<value>` can override it for visual calibration.
- Matrix collision is converted into SM64 surfaces in Phase 3; until then the sidecar uses the temporary flat native floor.
- Leaving Mario mode, player lifecycle changes, missing native dependencies or sidecar failure stop the session and restore/fall back to RuneScape control/presentation.

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
- Bundle 4.1 binary geometry-capable `sm64_bridge.exe` was rebuilt locally under MSYS2 MinGW64 and reports the expected `[--binary]` usage.
- Actual libsm64 Mario geometry is visibly rendered inside the revision-830 world at the local player position and replaces the RuneScape body.
- Native Mario idle animation visibly updates in-world across successive geometry frames.
- Native Mario jump animation visibly plays while native SM64 Y drives the visible jump.
- Ctrl+M restores the normal RuneScape local-player presentation, and re-entering Mario mode recreates the animated Mario replacement cleanly.
- The atlas micro-face v3 path restores Mario's texture details while keeping the former giant black whole-source-triangle artifact fixed. Bundle 4.2A visual fidelity is runtime accepted 2026-10-04.
- Runtime screenshots show the accepted textured Mario is still visibly more faceted/triangular than the SM64 gameplay reference, motivating Bundle 4.2B smoothing.

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam driving `MarioJumpController.tick()`.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`.
- `Class549_Sub1.anIntArray8901` maps the Bundle 2.3 controls to Matrix internal keys: W=33, A=48, S=49, D=50, F=51, Shift=81, Space=83.
- `libsm64` exposes Mario input/state, collision surfaces, dynamic surface objects and already-animated `SM64MarioGeometryBuffers`.
- The existing binary bridge command already carries `camLookX`, `camLookZ`, `stickX`, `stickY`, A, B and Z, so Bundle 2.3 requires no native protocol version change or sidecar rebuild.
- `sm64_global_init(...)` supplies the ROM-derived Mario RGBA atlas used by libsm64's renderer path.
- `Class159(int vertexCapacity, int faceCapacity, int textureCapacity)` plus its vertex/triangle arrays can represent generated raw geometry for `Class106.method1755(...)`.
- `Class159.method2560(x,y,z)` establishes that coincident vertex reuse is a supported raw-model topology operation; Bundle 4.2B applies the same shared-topology principle with an angle guard rather than welding every same-position vertex.
- Matrix's Live Model Editor already proves the renderer-native `Class159 -> Model -> Model.method1375(...)` path.
- `Class578.method6834(...)` is an established direct scene-preview seam used by Matrix developer/runtime previews.
- `Player.method10696(...)` is the shared player model-build path consumed by the player's render/picking variants, so the local Mario replacement gate does not require duplicating suppression across renderer variants.
- The binary bridge sends the ROM atlas once at handshake, then each fixed 30 Hz step sends native state plus positions/colors/UVs for the triangles used by the current SM64 animation frame.
- Native simulation/geometry publication happens on the worker; Matrix model construction/rendering remains on Matrix's render/client ownership path.
- `MarioVisualRenderer` generates model-local vertices from `(geometry position - native state position)` before anchoring the Model to Matrix player position; this is why Bundle 2.3 can exercise native horizontal movement/actions without moving the Matrix player X/Z yet.
- libsm64's reference GL renderer draws Mario base colour/lighting first, then overlays the ROM texture as a separate UV-mapped pass; the accepted micro-face presentation approximates those texture details inside Matrix without creating a second renderer.
- Bundle 4.2B shares only generated boundary vertices whose transformed geometric face normals are within the configured smooth-angle threshold; atlas face colours remain per-face and are not merged.

## Unknown / research needed

### HYPOTHESIS

- Sidecar binary transport is fast enough for sustained 30 Hz state + geometry + full control input; runtime measurement is required before considering JNI.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Whether the default `70` degree smoothing threshold best matches the original SM64 gameplay look in Matrix; runtime comparison may prefer roughly `45-90` degrees.
- Final Matrix<->SM64 coordinate scale/sign calibration for full XYZ movement.
- Whether vertical scale `3.0` and model scale `2.0` are the best game-feel/visual calibration.
- Whether fixed native camera-look `(0,-1)` feels acceptable for keyboard steering or should become Matrix-camera-relative before the full collision handoff.
- Final collision-bubble radius/rebuild threshold.
- Runtime cost of rebuilding one Matrix `Model` per native 30 Hz animated geometry frame under sustained use.
- Runtime cost of object collision proxy extraction.
- Final client/server reconciliation model.

## Dependencies

- Matrix3 client input/player-transform/renderer ownership.
- Local `libsm64`/`sm64_bridge` build.
- User-owned SM64 US ROM as a local runtime dependency only; never committed.
- User runtime testing for visual smoothing, native controls, collision/movement feel and regression acceptance.

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

**Status:** ACTIVE / NEEDS TEST

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

**Status:** NEEDS TEST / PRIORITY-PAUSED FOR 4.2B

- [x] Reuse Matrix held-key owner; no second keyboard listener.
- [x] Map WASD to normalized libsm64 analog stick input.
- [x] Map Space -> A, F -> B, Shift -> Z.
- [x] Publish stick/A/B/Z as one immutable input snapshot to the fixed 30 Hz worker.
- [x] Preserve held-action entry guards for A/B/Z.
- [x] Preserve binary protocol/native executable compatibility; no sidecar rebuild required.
- [x] Keep Matrix X/Z/clipping/pathfinding/server authority unchanged during this input-only slice.
- [ ] Runtime verify W/A/S/D movement/turning action states and diagonal stability.
- [ ] Runtime verify B-button grounded attack behavior.
- [ ] Runtime verify Z crouch and airborne ground-pound behavior.
- [ ] Runtime verify native long-jump transition using movement + Z/A timing.
- [ ] Runtime verify Ctrl+M exit and held-key entry guards remain clean.

### Phase 3 - Matrix World / Collision Adapter

**Status:** READY AFTER CONTROL GATE

#### Bundle 3.1 - Terrain heightfield -> SM64 surfaces

- [ ] Establish Matrix terrain-corner height sampler.
- [ ] Convert nearby RuneScape tile quads into correctly wound SM64 triangles.
- [ ] Add local origin/scale conversion and bounded collision bubble.
- [ ] Hand native SM64 horizontal position into Matrix presentation only after collision/coordinate conversion is proven safe.
- [ ] Runtime verify native SM64 stand/run/jump across RuneScape hills.

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

**Status:** 4.2A RUNTIME ACCEPTED / 4.2B NEEDS TEST

- [x] Runtime-identify V1 giant dark/black whole-triangle colour artifacts. `VERIFIED` 2026-10-04.
- [x] Trace the artifact to the lossy atlas-UV-to-single-face-colour approximation. `verified-static`.
- [x] Compare libsm64 reference rendering semantics. `verified-static`.
- [x] Implement native-base-colour fallback and prove the giant source-triangle artifact disappears.
- [x] Implement atlas micro-face v3 with clamp-to-edge + bilinear RGBA sampling and bounded tessellation.
- [x] Runtime-accept atlas micro-face v3: facial/eye/clothing texture details restored and giant black source-triangle blocks remain gone. `VERIFIED` 2026-10-04.
- [x] Implement 4.2B angle-gated shared boundary topology for smoother Matrix normal generation. `verified-static`.
- [x] Add `-Dmatrix3.sm64.smoothAngleDegrees=0..180`; default `70`.
- [ ] Runtime-accept 4.2B: Mario looks visibly rounder/less faceted without melted hard edges, texture regression, render errors or obvious FPS loss.
- [ ] Calibrate model scale/orientation/ground anchor after smoothing acceptance.
- [ ] Add geometry interpolation only if 30 Hz pose stepping is visibly objectionable.
- [ ] Map/forward native visual events such as sounds/particles after movement/collision ownership is stable.

### Phase 5 - Multiplayer / Server Authority

**Status:** PLANNED

- [ ] Define legal Mario movement/state sent to server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Combat/world interaction authority boundaries.

## Current execution state

- Phase: 4 - Mario Visual / Animation Presentation
- Phase status: ACTIVE / NEEDS TEST
- Bundle: 4.2B - Shared-topology smoothing
- Bundle status: NEEDS TEST
- Approval state: user explicitly said to do smoothing first and supplied `SAP AAA` on 2026-10-04.
- Current checklist item: runtime-test the new angle-gated shared-topology build once at the default `70` degree smoothing threshold.
- Current objective: reduce the visibly faceted/triangular Matrix Mario toward the smoother SM64 gameplay reference without disturbing the already-accepted atlas detail or animation path.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Controller boundary and native jump are runtime-proven; deeper lifecycle regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2/4 | 2.1/4.1 | VERIFIED | State + binary geometry executable runtime-proven locally. |
| Native -> visible Matrix transform | 2 | 2.2 | VERIFIED | Native SM64 Y visibly drives 830 vertical presentation. |
| Mario keyboard/action controls | 2 | 2.3 | NEEDS TEST / PRIORITY-PAUSED | WASD/Space/F/Shift feed native stick/A/B/Z; runtime gate resumes after smoothing. |
| Mario native geometry transport | 4 | 4.1 | VERIFIED | Actual Mario geometry and successive native animation frames reach Matrix at runtime. |
| Matrix Mario visual renderer | 4 | 4.1 | VERIFIED / CARRYOVER REGRESSION | Body replacement, idle, jump, restore and re-entry work; remote-player/stability checks remain. |
| Mario visual fidelity | 4 | 4.2 | NEEDS TEST | Atlas micro-face v3 is runtime accepted; 4.2B shared-topology smoothing is implemented and awaiting runtime comparison. |
| Matrix terrain adapter | 3 | 3.1 | READY AFTER CONTROL GATE | Required before native horizontal position becomes Matrix world movement. |

## Decisions / new ideas

- Matrix3 is the host world/renderer/input/server architecture.
- Authentic SM64-derived logic owns Mario-mode movement/action/animation state; do not rebuild the full action state machine in Java.
- Sidecar process remains transport until measurement gives a reason for JNI.
- Matrix scene/model mutation stays on Matrix's client/render ownership path even though native simulation runs on a worker.
- Direct libsm64 animated geometry is the selected Mario visual path.
- The accepted micro-face atlas path remains the texture-detail solution for now; smoothing is a topology/normal-generation concern, not another texture rewrite.
- Shared smoothing is angle-gated rather than global so low-poly hard edges remain available where source face normals diverge.
- WASD/Space/F/Shift are the first developer keyboard mapping: analog stick/A/B/Z respectively.
- Native horizontal input is allowed before horizontal Matrix authority because Mario geometry is model-localized and anchored to the Matrix player. Native X/Z must not be copied to Matrix until Phase 3 provides collision and coordinate conversion.
- RuneScape control/presentation remains the safe default and fail-open fallback.
- Matrix terrain will be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.

## Testing

See `docs/mario/TESTLIST.md` for the consolidated runtime gate.

## Carryover / blockers

### CARRYOVER

- Phase 1 deeper lifecycle regression where still useful.
- Bundle 2.2 airborne exit/relog/stale-transform regression.
- Bundle 2.3 keyboard/action runtime acceptance, explicitly paused while smoothing is tested first.
- Bundle 4.1 remote-player isolation and sustained-runtime/performance regression.
- Bundle 4.2 scale/orientation/ground-anchor polish after smoothing acceptance.
- Separate reported idle->Space fallback-to-RuneScape-player bug; intentionally not changed by the smoothing slice.

### BLOCKED

- Native SM64 horizontal position -> Matrix world movement is intentionally gated on Phase 3 collision/coordinate ownership, not blocked by an unknown bug.

## Resume Here

**Last completed:**

- Bridge Spike A runtime-VERIFIED against real `libsm64` + user ROM.
- Native Y -> Matrix transform runtime-VERIFIED.
- Actual libsm64 Mario geometry, idle and jump animations, RuneScape restore and Mario re-entry are runtime-VERIFIED.
- Atlas micro-face v3 visual fidelity is runtime-VERIFIED: texture details restored without the giant black source-triangle artifact.
- Bundle 2.3 implementation is complete statically: WASD -> native analog stick, Space -> A, F -> B, Shift -> Z, normalized diagonals and held-action entry guards. Runtime acceptance is paused.
- Bundle 4.2B shared-topology smoothing is implemented statically: compatible coincident boundary vertices are angle-gated and reused; runtime visual acceptance is pending.

**Current phase:**

- Phase 4 - Mario Visual / Animation Presentation (`ACTIVE / NEEDS TEST`).

**Active bundle:**

- Bundle 4.2B - Shared-topology smoothing (`NEEDS TEST`).

**Next checklist item:**

1. `git pull origin main`, Eclipse Java 8 clean/build, launch once. No native sidecar rebuild is required.
2. Ctrl+M and confirm Mario still has the accepted eyes/facial/hat/clothing details with no giant black-triangle regression.
3. Compare Mario's face, nose, cap, gloves, arms and overalls against the prior build; compatible surfaces should look visibly rounder/less faceted.
4. Confirm hard edges do not look melted or incorrectly blended.
5. Confirm idle and jump animation still render normally.
6. Confirm the ACTIVE log reports `matrixVertices=` and `smoothAngle=70.0` with no model-build/render errors.
7. Watch briefly for an obvious FPS regression.
8. If still too faceted, test `-Dmatrix3.sm64.smoothAngleDegrees=90`; if over-smoothed, test `45` or `60` before changing code again.
9. After smoothing acceptance, return to the paused Bundle 2.3 WASD/F/Shift control gate unless reprioritized.

**Files/systems already inspected:**

- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `Client/src/main/java/game/MarioVisualRenderer.java`
- `Client/src/main/java/game/Class549_Sub1.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/Class159.java`
- `Client/src/main/java/game/Class106.java`
- `Client/src/main/java/game/Player.java`
- `native/sm64-bridge/sm64_bridge.c`
- libsm64 input/state/geometry contract already documented in `SM64_PASSTHROUGH_ARCHITECTURE.md`.

**Do not re-scan without new evidence:**

- Local-player transform/input/viewport ownership.
- Matrix held-key owner and Bundle 2.3 W/A/S/D/F/Shift/Space internal mappings.
- Binary input packet structure; it already carries stick X/Y + A/B/Z.
- `Class159 -> Model` generated geometry ownership.
- `Class159.method2560(...)` coincident-vertex reuse precedent.
- `Class578.method6834(...)` direct scene-preview seam.
- `Player.method10696(...)` shared player model-build suppression seam.
- libsm64 geometry buffer/ROM atlas availability.
- Atlas micro-face v3 artifact cause/fix unless runtime regresses.
- Bundle 4.2B shared-boundary topology path unless runtime shows a new defect.

**Pending runtime verification:**

- Bundle 4.2B visual smoothing quality at default 70 degrees.
- Bundle 4.2B atlas/animation/render-performance regression check.
- Bundle 2.3 WASD/native movement-state response after smoothing gate.
- F/B attack behavior.
- Shift/Z crouch + airborne ground pound and long-jump timing.
- Held-action mode-entry guards.
- Fixed-camera-look steering feel.
- Remote-player isolation.
- Sustained stability / render-model error and performance behavior.
- Airborne exit/relog lifecycle carryover.
- Separate idle->Space fallback bug remains pending after smoothing.

**Important remaining uncertainty:**

- Whether `70` degrees best approximates SM64's original vertex-normal look through Matrix's generated-model normal builder; runtime comparison will decide before any deeper renderer work.
- Whether the existing fixed native camera-look vector feels natural enough for WASD or needs a Matrix-camera-relative adapter.
- Final Matrix<->SM64 horizontal scale/sign and collision conversion remain intentionally unresolved until Phase 3.

## Next recommended work

Runtime-test Bundle 4.2B smoothing once. If Mario is visibly rounder without texture/hard-edge regressions, accept that visual slice and return to the paused Bundle 2.3 keyboard-control gate. The separate idle->Space fallback bug remains a later presentation-stability fix and was intentionally not mixed into this patch.
