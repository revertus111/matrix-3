# Mario 64 in Matrix3 / Revision 830

## Goal

Run an authentic Mario experience inside Matrix3/revision-830: Matrix3 owns the RuneScape world, input, rendering and eventual server authority, while an SM64-derived native core owns Mario's movement/action/animation state through a narrow passthrough bridge.

Detailed architecture: `docs/mario/SM64_PASSTHROUGH_ARCHITECTURE.md`.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Alternate movement/controller foundation | 🟡 Foundation |
| SM64 passthrough core bridge | 🔵 In Progress |
| Matrix world/collision adapter | ❌ Not started |
| Mario visual/animation presentation | 🔵 In Progress |
| Multiplayer/server authority integration | ❌ Not started |

## Scope

### In scope

- Matrix3-native alternate controller activation and lifecycle.
- Native SM64-derived Mario simulation through `libsm64` rather than reimplementing the full action state machine in Java.
- Binary sidecar transport for native Mario state, ROM-derived texture atlas data and `SM64MarioGeometryBuffers` animation geometry.
- Matrix-native Mario presentation through `Class159 -> Model` and the established Matrix renderer/scene seam.
- Matrix terrain/object/water conversion into collision surfaces the SM64 core can consume.
- Eventual multiplayer/server validation after local behavior is stable.

### Current priority

The user explicitly moved Mario visual/animation presentation ahead of the world/collision adapter on 2026-10-04. Phase 3 remains valid planned work but is intentionally paused while Phase 4 proves the actual Mario body/animation presentation.

### Out of scope for the current visual slice

- Full RuneScape collision conversion.
- Native SM64 X/Z replacing RuneScape movement before the collision adapter exists.
- Server-authoritative Mario movement or remote-player replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.
- Renderer-backend-specific runtime texture injection; V1 bakes ROM atlas sampling into Matrix face albedo and keeps Matrix texture ownership intact.

## Architecture / ownership

- Matrix3 remains host architecture and world authority.
- `PlayerControllerMode` owns deliberate local RuneScape/Mario activation; RuneScape remains default.
- `Sm64BridgeSession` owns the persistent sidecar process, fixed 30 Hz SM64 tick and immutable native state/geometry publication.
- `MarioJumpController` retains its legacy hook-facing name but is a Matrix client-thread movement-presentation adapter; it does not compute Mario gravity in Java.
- `MarioVisualRenderer` owns local Mario visual presentation from native geometry. It consumes immutable libsm64 frames on Matrix's render thread, converts them to `Class159`, builds a normal Matrix `Model`, and renders through the established direct scene-preview seam.
- `Class578.method6834(...)` remains the established Matrix direct-preview render seam; Mario is another consumer rather than a second renderer.
- `Player.method10696(...)` remains the normal player model-build owner. A narrow fail-open gate suppresses only the local RuneScape appearance after `MarioVisualRenderer` has a fresh successful replacement frame. Remote players remain unchanged; stale/native-failure frames fall back to the RuneScape player.
- Native SM64 Y is still applied through Matrix3's VERIFIED local-player transform from the established client/viewport thread.
- The movement bridge currently maps vertical state only. RuneScape X/Z movement, plane, clipping, pathfinding and server position authority remain Matrix-owned.
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

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam driving `MarioJumpController.tick()`.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`; Space/Ctrl/M mappings are established.
- `libsm64` exposes Mario input/state, collision surfaces, dynamic surface objects and already-animated `SM64MarioGeometryBuffers`.
- `sm64_global_init(...)` supplies the ROM-derived Mario RGBA atlas used by libsm64's renderer path.
- `Class159(int vertexCapacity, int faceCapacity, int textureCapacity)` plus its vertex/triangle arrays can represent generated raw geometry for `Class106.method1755(...)`.
- Matrix's Live Model Editor already proves the renderer-native `Class159 -> Model -> Model.method1375(...)` path.
- `Class578.method6834(...)` is an established direct scene-preview seam used by Matrix developer/runtime previews.
- `Player.method10696(...)` is the shared player model-build path consumed by the player's render/picking variants, so the local Mario replacement gate does not require duplicating suppression across renderer variants.
- The binary bridge sends the ROM atlas once at handshake, then each fixed 30 Hz step sends native state plus positions/colors/UVs for the triangles used by the current SM64 animation frame.
- Native simulation/geometry publication happens on the worker; Matrix model construction/rendering remains on Matrix's render/client ownership path.

## Unknown / research needed

### HYPOTHESIS

- Sidecar binary transport is fast enough for sustained 30 Hz state + geometry; runtime measurement is required before considering JNI.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Final Matrix<->SM64 coordinate scale/sign calibration for full XYZ movement.
- Whether vertical scale `3.0` and model scale `2.0` are the best game-feel/visual calibration.
- Final collision-bubble radius/rebuild threshold.
- Whether V1 face-albedo atlas sampling is visually sufficient or exact runtime UV texture injection is worth a renderer-backend-neutral extension.
- Runtime cost of rebuilding one Matrix `Model` per native 30 Hz animated geometry frame.
- Runtime cost of object collision proxy extraction.
- Final client/server reconciliation model.

## Dependencies

- Matrix3 client input/player-transform/renderer ownership.
- Local `libsm64`/`sm64_bridge` build.
- User-owned SM64 US ROM as a local runtime dependency only; never committed.
- User runtime testing for native bridge, Mario visual acceptance and game-feel calibration.

## Development plan

### Phase 1 - Alternate Controller Foundation

**Status:** NEEDS TEST

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

**Status:** NEEDS TEST / CARRYOVER REGRESSION

#### Bundle 2.1 - Sidecar transport/native-core spike

**Status:** DONE

- [x] Record passthrough architecture and responsibility split.
- [x] Select sidecar-first transport and `libsm64` native core.
- [x] Add `sm64_bridge` and deterministic flat floor.
- [x] Runtime prove real native Y/action state returns to Matrix Java. `VERIFIED` 2026-10-04.

#### Bundle 2.2 - Native state -> Matrix transform

**Status:** NEEDS TEST

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
- [ ] Regression: Ctrl+M exit restores baseline/stops native ownership cleanly.
- [ ] Regression: native failure returns to RuneScape without stale transform.

The remaining Bundle 2.2 safety checks are carryover and do not block the explicitly reprioritized independent visual slice.

### Phase 3 - Matrix World / Collision Adapter

**Status:** PLANNED / PRIORITY-DEFERRED

#### Bundle 3.1 - Terrain heightfield -> SM64 surfaces

- [ ] Establish Matrix terrain-corner height sampler.
- [ ] Convert nearby RuneScape tile quads into correctly wound SM64 triangles.
- [ ] Add local origin/scale conversion and bounded collision bubble.
- [ ] Runtime verify native SM64 collision across RuneScape hills.

#### Bundle 3.2 - Objects / walls / platforms

- [ ] Add nearby solid-object collision proxies.
- [ ] Add walls/ceilings/bridges/platform floors as required.
- [ ] Use `SM64SurfaceObject` for moving platforms when needed.
- [ ] Add water-level bridging when content requires it.

### Phase 4 - Mario Visual / Animation Presentation

**Status:** ACTIVE

#### Bundle 4.1 - Direct native Mario geometry -> Matrix Model

**Status:** IMPLEMENTED / NEEDS TEST

- [x] Select direct `SM64MarioGeometryBuffers` presentation for the first authentic Mario visual path.
- [x] Extend `sm64_bridge` with binary mode so animated geometry can cross the sidecar without text-float serialization.
- [x] Send the ROM-derived Mario RGBA atlas once during binary handshake.
- [x] Send native Mario state + used animated geometry positions/colors/UVs each fixed 30 Hz step.
- [x] Extend `Sm64BridgeSession` with immutable `GeometryFrame` and `TextureAtlas` publication.
- [x] Add `MarioVisualRenderer` and consume geometry only on Matrix's renderer/client path.
- [x] Convert each native animation frame into generated `Class159` raw triangles and build a normal Matrix `Model` through `Class106.method1755(...)`.
- [x] Render through the established `Class578.method6834(...)` scene-preview seam; no second renderer/OpenGL path.
- [x] V1 ROM atlas presentation: sample libsm64 UV/texture output into per-triangle Matrix face albedo without taking ownership of Matrix texture backends.
- [x] Add local-player fail-open replacement gate in `Player.method10696(...)`; remote players are untouched and RuneScape reappears if Mario has no fresh successful render frame.
- [x] Add `-Dmatrix3.sm64.modelScale=<positive-float>` calibration override.
- [ ] Rebuild local `sm64_bridge.exe` for binary protocol.
- [ ] Eclipse Java 8 clean-build.
- [ ] Runtime: Ctrl+M produces `[SM64 Bridge] Persistent session READY (30 Hz + geometry)`.
- [ ] Runtime: console produces `[SM64 Visual] Native Mario -> Matrix Model ACTIVE ...`.
- [ ] Runtime: actual Mario replaces the local RuneScape player at the same world transform.
- [ ] Runtime: idle native animation visibly changes pose over successive frames.
- [ ] Runtime: Space jump visibly uses native Mario jump pose/animation while native Y drives height.
- [ ] Runtime: Ctrl+M back to RuneScape restores the normal local-player model immediately.
- [ ] Runtime: other players remain normal RuneScape players.

#### Bundle 4.2 - Visual fidelity / animation polish

**Status:** PLANNED

- [ ] Calibrate model scale/orientation/ground anchor from Bundle 4.1 evidence.
- [ ] Decide whether exact runtime UV texture injection materially improves V1 baked atlas color.
- [ ] Add geometry interpolation only if 30 Hz pose stepping is visibly objectionable.
- [ ] Map/forward native visual events such as sounds/particles only after the body/animation path is stable.

### Phase 5 - Multiplayer / Server Authority

**Status:** PLANNED

- [ ] Define legal Mario movement/state sent to server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Combat/world interaction authority boundaries.

## Current execution state

- Phase: 4 - Mario Visual / Animation Presentation
- Phase status: ACTIVE
- Bundle: 4.1 - Direct native Mario geometry -> Matrix Model
- Bundle status: IMPLEMENTED / NEEDS TEST
- Approval state: user explicitly reprioritized Mario visuals/animations and supplied `SAP AAA` for Bundle 4.1 on 2026-10-04.
- Current checklist item: rebuild the local native sidecar, clean/build Java 8, then runtime-accept the first actual Mario replacement/animation frame.
- Current objective: show actual libsm64-animated Mario in the revision-830 world using Matrix's renderer while preserving fail-open RuneScape presentation.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Core lift/controller are runtime-proven; deeper lifecycle regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2/4 | 2.1/4.1 | NEEDS TEST | Original state bridge verified; binary geometry extension now needs local rebuild/runtime acceptance. |
| Native -> visible Matrix transform | 2 | 2.2 | VERIFIED | Native SM64 Y visibly drives 830 vertical presentation. |
| Mario native geometry transport | 4 | 4.1 | NEEDS TEST | Binary state/texture/geometry protocol implemented statically. |
| Matrix Mario visual renderer | 4 | 4.1 | NEEDS TEST | `Class159 -> Model -> Matrix scene` replacement path implemented statically. |
| Matrix terrain adapter | 3 | 3.1 | READY / DEFERRED | Explicitly moved behind Mario visual priority. |

## Decisions / new ideas

- Matrix3 is the host world/renderer/input/server architecture.
- Authentic SM64-derived logic owns Mario-mode simulation and animation pose; do not rebuild the full action/animation state machines in Java.
- Sidecar process remains transport until measurement gives a reason for JNI.
- Matrix scene/model mutation stays on Matrix's client/render ownership path even though native simulation runs on a worker.
- Direct libsm64 animated geometry is the selected first Mario visual path; imported 830 animation remapping is no longer required to prove authentic Mario animation.
- V1 keeps Matrix texture ownership intact by baking ROM atlas sampling into face albedo; exact runtime texture injection is polish, not a prerequisite for the visual proof.
- RuneScape control/presentation remains the safe default and fail-open fallback.
- Matrix terrain will later be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.

## Testing

See `docs/mario/TESTLIST.md` for the consolidated runtime gate.

## Carryover / blockers

### CARRYOVER

- Phase 1 deeper lifecycle regression where still useful.
- Bundle 2.2 Ctrl+M exit/relog/native-failure fallback regression.
- Phase 3 terrain/object collision adapter, explicitly priority-deferred.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Bridge Spike A runtime-VERIFIED against real `libsm64` + user ROM.
- Native Y -> Matrix transform runtime-VERIFIED.
- Bundle 4.1 implementation now extends the native bridge to binary state + ROM atlas + animated geometry, converts native frames into Matrix `Class159/Model`, renders them through the established scene seam, and fail-open suppresses only the local RuneScape player after a valid Mario replacement exists.

**Current phase:**

- Phase 4 - Mario Visual / Animation Presentation (`ACTIVE`).

**Active bundle:**

- Bundle 4.1 - Direct native Mario geometry -> Matrix Model (`IMPLEMENTED / NEEDS TEST`).

**Next checklist item:**

1. Pull current `main`.
2. In the existing MSYS2 MINGW64 bridge directory run `make CC=gcc CXX=g++` to rebuild `sm64_bridge.exe` for the binary geometry protocol.
3. Eclipse clean/build + launch.
4. Ctrl+M and verify actual Mario replacement + native idle/jump animation.

**Files/systems already inspected:**

- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `Client/src/main/java/game/MarioVisualRenderer.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/Class159.java`
- `Client/src/main/java/game/Player.java`
- `Client/src/main/java/game/LiveModelEditorPreview.java` as the established generated-model/render precedent.
- `native/sm64-bridge/sm64_bridge.c`
- libsm64 public geometry/atlas renderer API and test renderer.

**Do not re-scan without new evidence:**

- Local-player transform/input/viewport ownership.
- `Class159 -> Model` generated geometry ownership.
- `Class578.method6834(...)` direct scene-preview seam.
- `Player.method10696(...)` shared player model-build suppression seam.
- libsm64 geometry buffer/ROM atlas availability.
- Binary geometry protocol V1 structure unless runtime output contradicts it.

**Pending runtime verification:**

- Local native sidecar rebuild succeeds.
- Binary geometry handshake/session reaches READY.
- Matrix builds/renders actual Mario geometry.
- Local RuneScape body is replaced only after Mario render succeeds.
- Native idle/jump poses visibly animate.
- Ctrl+M restores RuneScape model cleanly.

**Important remaining uncertainty:**

- Visual scale/orientation/ground anchor and whether baked atlas face albedo is sufficient should be calibrated from the first actual Mario render rather than guessed statically.

## Next recommended work

Runtime-accept Bundle 4.1, then tune Mario scale/orientation/visual fidelity before returning to the collision adapter.
