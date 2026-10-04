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

The user explicitly moved Mario visual/animation presentation ahead of the world/collision adapter on 2026-10-04. Phase 3 remains valid planned work but is intentionally paused while Phase 4 proves and polishes the actual Mario body/animation presentation.

### Out of scope for the current visual slice

- Full RuneScape collision conversion.
- Native SM64 X/Z replacing RuneScape movement before the collision adapter exists.
- Server-authoritative Mario movement or remote-player replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.
- A backend-specific texture hack. Exact runtime UV texture injection remains deferred until a renderer-backend-neutral Matrix texture path is proven.

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
- Bundle 4.1 binary geometry-capable `sm64_bridge.exe` was rebuilt locally under MSYS2 MinGW64 and reports the expected `[--binary]` usage.
- Actual libsm64 Mario geometry is visibly rendered inside the revision-830 world at the local player position and replaces the RuneScape body.
- Native Mario idle animation visibly updates in-world across successive geometry frames.
- Native Mario jump animation visibly plays while native SM64 Y drives the visible jump.
- Ctrl+M restores the normal RuneScape local-player presentation, and re-entering Mario mode recreates the animated Mario replacement cleanly.
- V1 colour presentation visibly produces large dark/black whole-triangle artifacts despite correct geometry/animation, providing the runtime evidence for Bundle 4.2A.

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
- The original Matrix V1 colour path sampled only the three UV vertices of each triangle and collapsed the blended result into one packed-HSL face colour, so small opaque dark texture details could colour an entire Matrix triangle.
- libsm64's reference GL renderer draws Mario base colour/lighting first, then overlays the ROM texture as a separate UV-mapped pass; it does not collapse the texture into one colour per triangle.
- The established Live Model Editor generated-model path uses the same default Matrix model-build lighting values (`ambient=64`, `contrast=850`), so Bundle 4.2A changes the proven lossy colour approximation before speculating about global lighting parameters.

## Unknown / research needed

### HYPOTHESIS

- Sidecar binary transport is fast enough for sustained 30 Hz state + geometry; runtime measurement is required before considering JNI.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Final Matrix<->SM64 coordinate scale/sign calibration for full XYZ movement.
- Whether vertical scale `3.0` and model scale `2.0` are the best game-feel/visual calibration.
- Final collision-bubble radius/rebuild threshold.
- Whether native libsm64 base colour is visually sufficient for the first clean Matrix presentation or a renderer-neutral exact UV texture path is worth implementing next.
- Whether any broad darkness remains after removing the V1 atlas-face artifact; if so, Matrix lighting/normal handling is the next bounded trace.
- Runtime cost of rebuilding one Matrix `Model` per native 30 Hz animated geometry frame under sustained use.
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
- [x] Regression: grounded Ctrl+M exit restores normal RuneScape presentation/stops Mario ownership. `VERIFIED` 2026-10-04.
- [ ] Regression: airborne exit/relog lifecycle and stale-transform behavior.

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

**Status:** RUNTIME ACCEPTED / CARRYOVER REGRESSION

- [x] Select direct `SM64MarioGeometryBuffers` presentation for the first authentic Mario visual path.
- [x] Extend `sm64_bridge` with binary mode so animated geometry can cross the sidecar without text-float serialization.
- [x] Send the ROM-derived Mario RGBA atlas once during binary handshake.
- [x] Send native Mario state + used animated geometry positions/colors/UVs each fixed 30 Hz step.
- [x] Extend `Sm64BridgeSession` with immutable `GeometryFrame` and `TextureAtlas` publication.
- [x] Add `MarioVisualRenderer` and consume geometry only on Matrix's renderer/client path.
- [x] Convert each native animation frame into generated `Class159` raw triangles and build a normal Matrix `Model` through `Class106.method1755(...)`.
- [x] Render through the established `Class578.method6834(...)` scene-preview seam; no second renderer/OpenGL path.
- [x] V1 ROM atlas face-colour approximation implemented and runtime-evaluated; its visible artifact is now addressed by Bundle 4.2A.
- [x] Add local-player fail-open replacement gate in `Player.method10696(...)`; remote players are untouched and RuneScape reappears if Mario has no fresh successful render frame.
- [x] Add `-Dmatrix3.sm64.modelScale=<positive-float>` calibration override.
- [x] Rebuild local `sm64_bridge.exe` for binary protocol. `VERIFIED` 2026-10-04.
- [x] Java source builds/launches successfully enough to execute the complete native Mario body/animation path in the live client. `VERIFIED` 2026-10-04.
- [x] Runtime: binary geometry session reaches the working Matrix render path. `VERIFIED` 2026-10-04.
- [x] Runtime: actual Mario replaces the local RuneScape player at the same world transform. `VERIFIED` 2026-10-04.
- [x] Runtime: idle native animation visibly changes pose over successive frames. `VERIFIED` 2026-10-04.
- [x] Runtime: Space jump visibly uses native Mario jump pose/animation while native Y drives height. `VERIFIED` 2026-10-04.
- [x] Runtime: Ctrl+M back to RuneScape restores the normal local-player model immediately. `VERIFIED` 2026-10-04.
- [x] Runtime: re-entering Mario mode recreates the animated replacement cleanly. `VERIFIED` 2026-10-04.
- [ ] Runtime regression carryover: other players remain normal RuneScape players.
- [ ] Runtime regression carryover: sustained use has no render/model-build error spam or unacceptable performance cost.

#### Bundle 4.2 - Visual fidelity / animation polish

**Status:** ACTIVE / NEEDS TEST

- [x] Runtime-identify V1 giant dark/black whole-triangle colour artifacts. `VERIFIED` 2026-10-04.
- [x] Trace the artifact to the lossy atlas-UV-to-single-face-colour approximation. `verified-static`.
- [x] Compare libsm64 reference rendering: native base colour/lighting pass plus separate UV texture overlay. `verified-static`.
- [x] Bundle 4.2A: default Matrix face albedo now uses libsm64 native base material/light colour only; old atlas-face bake is diagnostic-only via `-Dmatrix3.sm64.debugAtlasFaceBake=true`.
- [ ] Runtime-accept Bundle 4.2A: black whole-triangle artifacts materially reduced/gone while idle/jump/restore still work.
- [ ] If broad darkness remains after 4.2A, trace Matrix generated-model lighting/normal handling before changing ambient/contrast.
- [ ] Calibrate model scale/orientation/ground anchor from clean readable runtime evidence.
- [ ] Decide whether exact renderer-neutral runtime UV texture injection materially improves native-base-colour presentation.
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
- Bundle: 4.2 - Visual fidelity / animation polish
- Bundle status: ACTIVE / NEEDS TEST
- Approval state: Bundle 4.1 was approved/runtime-accepted for core presentation; user supplied `SAP AAA` for the Bundle 4.2A rendering-fidelity fix on 2026-10-04.
- Current checklist item: runtime-test the native-base-colour rendering correction once; only if broad darkness remains, trace Matrix lighting/normal handling.
- Current objective: make the already-working native Mario body visually clean/readable without disturbing proven geometry, animation, movement, replacement, or renderer ownership.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | NEEDS TEST | Core lift/controller are runtime-proven; deeper lifecycle regression remains. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2/4 | 2.1/4.1 | VERIFIED | Original state bridge and rebuilt binary-geometry executable are runtime-proven locally. |
| Native -> visible Matrix transform | 2 | 2.2 | VERIFIED | Native SM64 Y visibly drives 830 vertical presentation. |
| Mario native geometry transport | 4 | 4.1 | VERIFIED | Actual Mario geometry and successive native animation frames reach Matrix at runtime. |
| Matrix Mario visual renderer | 4 | 4.1 | VERIFIED / CARRYOVER REGRESSION | Body replacement, idle, jump, restore and re-entry work; remote-player/stability checks remain. |
| Mario visual fidelity | 4 | 4.2 | NEEDS TEST | Native-base-colour correction implemented; black whole-triangle artifact test pending. |
| Matrix terrain adapter | 3 | 3.1 | READY / DEFERRED | Explicitly moved behind Mario visual priority. |

## Decisions / new ideas

- Matrix3 is the host world/renderer/input/server architecture.
- Authentic SM64-derived logic owns Mario-mode simulation and animation pose; do not rebuild the full action/animation state machines in Java.
- Sidecar process remains transport until measurement gives a reason for JNI.
- Matrix scene/model mutation stays on Matrix's client/render ownership path even though native simulation runs on a worker.
- Direct libsm64 animated geometry is the selected first Mario visual path; imported 830 animation remapping is no longer required to prove authentic Mario animation.
- The V1 atlas-to-one-face-colour approximation is rejected as the default after runtime evidence showed whole-triangle dark artifacts. Native libsm64 base colour is the clean fallback while a true renderer-neutral UV texture path remains optional polish.
- RuneScape control/presentation remains the safe default and fail-open fallback.
- Matrix terrain will later be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.

## Testing

See `docs/mario/TESTLIST.md` for the consolidated runtime gate.

## Carryover / blockers

### CARRYOVER

- Phase 1 deeper lifecycle regression where still useful.
- Bundle 2.2 airborne exit/relog/stale-transform regression.
- Bundle 4.1 remote-player isolation and sustained-runtime/performance regression.
- Phase 3 terrain/object collision adapter, explicitly priority-deferred.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Bridge Spike A runtime-VERIFIED against real `libsm64` + user ROM.
- Native Y -> Matrix transform runtime-VERIFIED.
- Bundle 4.1 binary sidecar rebuild runtime-VERIFIED.
- Actual libsm64 Mario geometry visibly renders inside Matrix3 and replaces the local RuneScape body. `VERIFIED` 2026-10-04.
- Native Mario idle and jump animations visibly work in-world. `VERIFIED` 2026-10-04.
- Ctrl+M restores RuneScape presentation and clean Mario re-entry works. `VERIFIED` 2026-10-04.
- Bundle 4.2A colour-fidelity patch implemented: native libsm64 base colour is now the default Matrix face albedo; old atlas-face bake is diagnostic-only.

**Current phase:**

- Phase 4 - Mario Visual / Animation Presentation (`ACTIVE`).

**Active bundle:**

- Bundle 4.2 - Visual fidelity / animation polish (`ACTIVE / NEEDS TEST`).

**Next checklist item:**

1. `git pull origin main`, Eclipse Java 8 clean/build, launch once.
2. Ctrl+M and confirm Mario still replaces the local player and idles.
3. Confirm the previous giant black/dark whole-triangle patches are materially reduced or gone.
4. Tap Space and confirm native jump animation still renders correctly.
5. Ctrl+M out/in and confirm RuneScape restore + Mario re-entry still work.
6. If Mario remains broadly too dark after the whole-triangle artifact is gone, stop there; next patch should trace Matrix lighting/normal handling instead of stacking brightness guesses.

**Files/systems already inspected:**

- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `Client/src/main/java/game/MarioVisualRenderer.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/Class159.java`
- `Client/src/main/java/game/Class106.java`
- `Client/src/main/java/game/Player.java`
- `Client/src/main/java/game/LiveModelEditorPreview.java` as the established generated-model/render and default-lighting precedent.
- `native/sm64-bridge/sm64_bridge.c`
- libsm64 `src/gfx_adapter.c` geometry/colour/normal output and reference `test/gl20/gl20_renderer.c` two-pass Mario rendering.

**Do not re-scan without new evidence:**

- Local-player transform/input/viewport ownership.
- `Class159 -> Model` generated geometry ownership.
- `Class578.method6834(...)` direct scene-preview seam.
- `Player.method10696(...)` shared player model-build suppression seam.
- libsm64 geometry buffer/ROM atlas availability.
- Binary geometry protocol V1 structure unless runtime output contradicts it.
- V1 atlas-face-bake artifact cause unless Bundle 4.2A runtime output contradicts the static trace.

**Pending runtime verification:**

- Bundle 4.2A native-base-colour visual result.
- Remote-player isolation.
- Sustained stability / render-model error and performance behavior.
- Airborne exit/relog lifecycle carryover.

**Important remaining uncertainty:**

- Whether removing the lossy atlas-face bake is sufficient to make Mario visually clean/readable. If broad darkness persists, the next unknown is Matrix generated-model lighting/normal handling, not the already-proven geometry/animation bridge.
- Model scale/orientation/ground anchor should be calibrated only after the colour/shading silhouette is readable.

## Next recommended work

Runtime-test Bundle 4.2A once. If the giant black whole-triangle artifacts are fixed, calibrate scale/orientation/ground anchor next; if broad darkness remains, do one bounded Matrix lighting/normal trace before any further render tuning.
