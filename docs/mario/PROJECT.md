# Mario 64 in Matrix3 / Revision 830


## Active priority override - 2026-10-04 custom combat proof

The user supplied SAP AAA for custom native animation overlay + 830 weapon attachment.
The core proof is now runtime-accepted; remaining 4.3 work is bounded regression/calibration only. Prior steering/collision and equipment carryovers below remain preserved.

- Phase 4, Bundle 4.3: native 1H slash + right-hand socket (`RUNTIME PROOF ACCEPTED / REGRESSION CHECKS PENDING`).
- [x] Establish pinned pose/joint/matrix ownership (`verified-static`); details in `docs/n64/CUSTOM_COMBAT.md`.
- [x] Native additive torso/right-arm slash and socket export; v3 Java bridge with v1/v2 compatibility.
- [x] Equipped 830 weapon attachment, conditional F trigger, small Custom combat tab and grip controls.
- [x] Linux native build and focused protocol/curve/transform checks.
- [x] Runtime 2026-10-04: equipped revision-830 sword visibly attaches to Mario's hand. `VERIFIED`.
- [x] Runtime 2026-10-04: F triggers the custom native MARIO_COMBAT_1H_SLASH animation. `VERIFIED`.
- [ ] Finish bounded 4.3 regressions: moving slash/leg continuity, held-F no-replay, freeze/unequip/exit/relog cleanup, remote-player isolation, NPC combat integration and sustained stability.
- [ ] Tune grip/aesthetics only if the user wants visual polish before selecting another combat family.

Core Bundle 4.3 architecture is runtime-VERIFIED. No collision, damage/contact timing, 830 retargeting or stab expansion is included. Stab is not started by this verification update.


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
- A reusable alternate-character controller/input/combat foundation so later imported characters do not duplicate Mario-specific glue.
- Native SM64-derived Mario simulation through `libsm64` rather than reimplementing the full action state machine in Java.
- Binary sidecar transport for native Mario input/state, ROM-derived texture atlas data and `SM64MarioGeometryBuffers` animation geometry.
- Matrix-native Mario presentation through `Class159 -> Model` and the established Matrix renderer/scene seam.
- Temporary local-only native XYZ presentation for development/runtime proof while Matrix/RuneScape remains gameplay/server authority.
- RuneScape-authoritative NPC combat integration using the existing combat engine/stats rather than a parallel alternate-character damage model.
- Matrix terrain/object/water conversion into collision surfaces the SM64 core can consume.
- Eventual multiplayer/server validation after local behavior is stable.

### Current priority

The user explicitly reprioritized from Phase 3 collision work to a reusable imported-character foundation before adding more games. The approved current bundle introduced `AlternateCharacterController` as the shared viewport/control owner, `AlternateCharacterInputKeyboard` as the shared Matrix keyboard view, camera-relative movement input, a generic RuneScape combat-intent bridge, and a bounded Mario replacement-grace attempt for the reported idle->Space RuneScape-body pop.

Runtime evidence on 2026-10-04 narrowed the steering regression in stages. Video/diagnostics first proved the rendered Construction/RTS camera could rotate while the old sampler stayed effectively frozen near `(-0.006, 1.000)`, establishing Construction camera ownership as an upstream fault. After the live Construction camera was selected and a Mario/libsm64 180-degree camera-look correction made north-facing W/S/A/D correct, south-facing W/S/A/D still remained exactly reversed. Switching the shared RTS heading source to Construction's canonical `rtsYawRadians` path also failed to eliminate that south-facing reversal.

The current steering patch therefore supersedes dynamic Matrix-camera -> libsm64-camera interpretation for Mario. `AlternateCharacterController` still owns the generic Matrix camera-forward vector. `MarioJumpController` now converts local screen input into a Matrix world-space movement vector using the same basis Construction uses (`right=(forwardZ,-forwardX)`, `world=moveX*right + moveY*forward`). The libsm64 camera is held at neutral `(0,+1)`, and the desired Matrix world vector is encoded directly as native stick `(-worldX,-worldZ)`. This follows libsm64's actual `cameraYaw`, stick-sign, and `intendedYaw` equations and removes the heading/handedness conversion from the native boundary. Runtime acceptance is pending N/E/S/W plus live rotation.

The user also runtime-confirmed on 2026-10-04 that the 750 ms replacement grace did **not** eliminate the idle->Space RuneScape-body reappearance. Do not treat that grace as an accepted fix. The Test Console -> `N64` workspace with a `Mario 64` sub-tab and client-tick flight recorder remains available for that presentation regression after the active steering gate passes.

Mario is the first driver. Its current capability profile advertises melee only. The architecture deliberately leaves room for a later Link driver to advertise melee + ranged while reusing the same camera, keyboard and RuneScape combat pipeline rather than adding a second controller/damage system.

Bundle 2.4 local XYZ and Bundle 4.2B smoothing remain runtime accepted. Bundle 4.3's custom 1H slash + 830 hand attachment core proof is also runtime accepted; only its bounded regressions/calibration remain. Phase 3 is still the next collision architecture milestone after the current framework/presentation regression gate is accepted.

### Out of scope for the current master-controller/combat slice

- Making client-local Mario XYZ authoritative at the server.
- Replacing RuneScape clipping, pathfinding, plane ownership or server correction behavior.
- Full per-character server-enforced weapon-family restrictions; the first capability profile is client routing metadata only.
- Treating visually-correct terrain elevation as proof that the native flat-floor collision has been replaced.
- Server-authoritative imported-character movement or remote-player replication.
- JNI/in-process native loading before sidecar transport is measured under sustained runtime use.

## Architecture / ownership

- Matrix3 remains host architecture and world/server authority.
- `PlayerControllerMode` still owns deliberate local RuneScape/Mario activation for the current first driver; RuneScape remains default.
- `AlternateCharacterController` is the single client-side dispatch/input/camera foundation for imported-character drivers. Future characters plug into this owner rather than adding another viewport tick or keyboard/controller path.
- `AlternateCharacterController.ControlState` provides one shared vocabulary: camera-relative WASD movement, Space jump, F primary action and Shift modifier action. Character drivers decide how those actions map into their native/game-specific state machine.
- `AlternateCharacterInputKeyboard` is a reversible view over Matrix3's existing `Class549` keyboard owner. The original AWT listener remains installed and tracks physical keys; while an alternate character owns movement normal `method6514(...)` consumers see W/A/S/D released while the master controller reads the original raw held state through `method6518(...)`. No second keyboard listener is installed.
- `AlternateCharacterController.getCameraForward()` resolves the actual Matrix camera forward vector. Construction RTS uses Construction's canonical 14-bit RTS yaw (`getRtsMinimapYawUnits()` restored back to `rtsYaw`) because that heading is generated from the same `rtsYawRadians` that renders/pans the RTS view. Construction Free and other detached/free Class411 cameras use actual position/look geometry. Vanilla cameras use resolved viewport camera-position -> focus-position geometry, with the older 14-bit vanilla yaw derivation retained only as fallback.
- `Sm64BridgeSession` owns the persistent sidecar process, fixed 30 Hz SM64 tick, immutable control snapshots, and immutable native state/geometry publication.
- The native input snapshot carries camera-look X/Z alongside stick/A/B/Z. The binary protocol already carried those fields, so steering changes require no sidecar rebuild/protocol bump.
- `Sm64BridgeSession` publishes one-native-tick-delayed interpolated native X/Y/Z from the same previous/latest frame pair and shared interpolation alpha.
- `MarioJumpController.tick()` is retained only as the established viewport compatibility seam; it delegates to `AlternateCharacterController`, which dispatches into `MarioJumpController.tickMarioDriver()` for the Mario/libsm64 implementation.
- Mario steering no longer forwards Matrix camera heading dynamically into libsm64. The Mario driver first resolves local WASD against the shared Matrix camera basis into a desired world vector, then uses neutral libsm64 camera `(0,+1)` and stick `(-worldX,-worldZ)`. This keeps source-game movement/action physics native while removing Matrix/libsm64 camera handedness from the boundary.
- A/B/Z held while Mario mode is entered are suppressed until released so mode activation cannot manufacture a jump/attack/crouch action.
- Bundle 2.4 captures Matrix/native XYZ baselines when Mario mode starts. Native X/Z deltas are applied locally at the horizontal presentation scale while native Y retains the established positive-up -> Matrix negative-Y conversion.
- Default horizontal presentation scale is `3.0`; `-Dmatrix3.sm64.horizontalScale=<positive-float>` provides runtime calibration. The default `3.0` and direct X/Z signs are runtime accepted for the current local presentation path.
- Matrix/server corrections remain authoritative beneath the temporary presentation offset. If Matrix changes an axis externally, the presentation baseline rebases to that correction instead of fighting it. This runtime behavior is sufficient for Mario to follow differing visible RuneScape terrain elevations cleanly.
- Ctrl+M exit, native failure and local-player lifecycle replacement restore the tracked RuneScape XYZ baseline and restore the original Matrix keyboard owner.
- `AlternateCharacterCombatBridge` owns alternate-character combat intent only. It does **not** calculate damage. For the first Mario melee slice, F/B rising edge soft-targets the nearest loaded NPC within a bounded radius and sends Matrix3's stock NPC Attack packet.
- The server remains combat authority: `WorldPacketsDecoder` validates the NPC/packet and starts the existing `PlayerCombatNew(npc)` action, which keeps RuneScape target/range/pathing rules, combat definitions/stats/equipment bonuses, damage, XP and downstream death/drop behavior.
- Mario's first capability profile advertises `MELEE`; a future Link driver can advertise `MELEE` + `RANGED` against the same combat bridge. Strict server-side enforcement of character/weapon-family compatibility remains future work.
- `MarioVisualRenderer` owns local Mario visual presentation from native geometry. It consumes immutable libsm64 frames on Matrix's render thread, converts them to `Class159`, builds a normal Matrix `Model`, and renders through the established direct scene-preview seam.
- Textured Mario faces keep the accepted micro-face atlas approximation. Bundle 4.2B shares compatible coincident source-triangle boundary vertices through an angle-gated topology path so Matrix normal generation shades compatible surfaces more smoothly without indiscriminately welding hard edges together.
- `MarioVisualRenderer` allows a bounded 750 ms last-good-model grace across transient geometry/model-build gaps. Cached fallback renders do not extend the deadline. Runtime evidence now proves this grace alone does not eliminate the reported idle->Space RuneScape-body pop.
- `Mario64Diagnostics` is a read-only developer recorder attached to the established Mario client tick. It snapshots the existing controller, bridge/native frame, action/animation, sampled/forwarded controls, airborne presentation and actual `MarioVisualRenderer.shouldSuppressLocalPlayer(...)` predicate into a bounded event history. It also has a rate-limited pure-W direction probe comparing sampled camera-forward against actual native libsm64 X/Z velocity; the probe remains only as a runtime verification aid after the steering-source fixes.
- Test Console -> `N64` is the reusable developer workspace for imported N64 games; `Mario 64` is the first game sub-tab and later games should add sibling sub-tabs instead of mixing game-specific diagnostics together.
- Default smoothing threshold is `70` degrees; `-Dmatrix3.sm64.smoothAngleDegrees=0..180` provides runtime calibration. The default `70` degree path is runtime accepted.
- `Class578.method6834(...)` remains the established Matrix direct-preview render seam; Mario is another consumer rather than a second renderer.
- `Player.method10696(...)` remains the normal player model-build owner. A narrow fail-open gate suppresses only the local RuneScape appearance after `MarioVisualRenderer` has a successful replacement path. Remote players remain unchanged.
- Native-state vertical presentation uses initial `3.0` SM64-to-Matrix Y scale; `-Dmatrix3.sm64.verticalScale=<value>` can override it.
- Mario mesh scale uses initial `2.0`; `-Dmatrix3.sm64.modelScale=<value>` can override it for visual calibration.
- Matrix collision is converted into SM64 surfaces in Phase 3; until then the sidecar uses the temporary flat native floor even though Matrix presentation already follows visible terrain elevations correctly.
- Bundle 4.3 protocol v3 adds a latched combat request and same-frame native right-hand matrix; Java routes custom weapon F only when protocol v3, live hand socket and rendered weapon readiness are all present. The successful runtime slash therefore proves that core native->bridge->Java combat path is live.

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
- Bundle 4.3 runtime confirms an equipped revision-830 sword visibly attaches to Mario's hand in live Mario mode. `VERIFIED` 2026-10-04.
- Bundle 4.3 runtime confirms F triggers the custom native MARIO_COMBAT_1H_SLASH animation. `VERIFIED` 2026-10-04.
- The reported idle->Space RuneScape-body reappearance still occurs with the 750 ms replacement grace in place. `VERIFIED` regression report from the user on 2026-10-04; the grace is not an accepted fix.
- Bundle 1.3 camera-relative steering remained incorrect after the resolved vanilla camera-position -> focus-position change. `VERIFIED` regression report from the user on 2026-10-04.
- User video + diagnostics prove the rendered Construction/RTS camera can rotate while the old alternate-character sampler reports an effectively frozen camera-forward vector near `(-0.006, 1.000)`. `VERIFIED` 2026-10-04.
- After the Mario/libsm64 180-degree camera-look correction, north-facing W/S/A/D is correct while south-facing W/S/A/D is exactly reversed. `VERIFIED` 2026-10-04.
- The subsequent canonical Construction RTS-yaw source still did not eliminate the south-facing reversal. `VERIFIED` by user 2026-10-04; dynamic Matrix-camera -> libsm64-camera steering is therefore superseded for Mario.

### verified-static

- Local player: `Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976` (`Player`).
- Player scene transform is available through `Class456.method5394().aClass240_2647` and writable through `Class456.method5395(float,float,float)`.
- `Class343.method4302(...)` is the established live viewport update seam. `MarioJumpController.tick()` remains that hook, but now delegates to `AlternateCharacterController`.
- Matrix keyboard held-state ownership is `Class108.aClass549_1426`.
- `Class549_Sub1.anIntArray8901` maps the shared controls to Matrix internal keys: W=33, A=48, S=49, D=50, F=51, Shift=81, Space=83.
- Construction Free Build and RTS camera paths both poll W/A/S/D through the shared `method6514(...)` held-key seam; arrows are separate camera movement keys and Q/E remain separate camera controls.
- `AlternateCharacterInputKeyboard` delegates the full `Class549` contract to the original owner and filters only W/A/S/D from `method6514(...)`; the master controller reads original raw held state rather than installing another listener.
- `ConstructionBuildCamera` owns the rendered Construction detached camera through `Class24.aClass411_Sub1_158` while active. That ownership does not require the normal detached-camera flags checked by the prior alternate-character sampler, so Construction activity must be checked explicitly before vanilla camera fallback.
- Construction RTS movement uses a normalized planar forward vector plus `right=(forwardZ,-forwardX)`, then resolves local input as `world=localX*right + localZ*forward`.
- Construction RTS orientation is canonically owned by `rtsYawRadians`; `applyRtsOrientation(...)` renders it as planar `(sin(yaw), cos(yaw))`, while `getRtsMinimapYawUnits()` exposes the runtime-accepted opposite-handed Matrix minimap representation `-rtsYaw` in a 14-bit turn domain.
- `Class246.method3359(...)` uses a 14-bit yaw domain; the older yaw-derived camera->focus planar direction is `(-sin(yaw), cos(yaw))`. The normal vanilla path prefers resolved camera-position -> focus-position geometry; detached Class411 cameras use their actual position/look point.
- libsm64 `sm64_mario_tick(...)` sets `camera->yaw = atan2s(camLookZ, camLookX)`, `controller.stickX = -64*input.stickX`, and `controller.stickY = 64*input.stickY`; Mario input then computes `intendedYaw = atan2s(-stickY, stickX) + cameraYaw`.
- Therefore with neutral libsm64 camera `(0,+1)`, publishing native stick `(-worldX,-worldZ)` makes `intendedYaw` point at the desired Matrix world-space movement vector. This is the current Mario steering adapter.
- `libsm64` exposes Mario input/state, collision surfaces, dynamic surface objects and already-animated `SM64MarioGeometryBuffers`.
- The existing binary bridge command already carries `camLookX`, `camLookZ`, `stickX`, `stickY`, A, B and Z, and every binary frame already returns native X/Y/Z; no native protocol version change or sidecar rebuild is required for the steering fix.
- `Sm64BridgeSession.NativePosition` uses one shared interpolation alpha for X/Y/Z, preventing presentation axes from sampling different native phases.
- Bundle 2.4 applies native X/Z only through the local player transform. No networking/clipping/pathfinding owner is changed.
- Stock client NPC action code 10 uses `OutgoingPacket.aClass312_3702`; that packet is opcode 32 and carries the NPC index/force-run flag.
- Server `WorldPacketsDecoder` routes opcode 32 into the existing `PlayerCombatNew(npc)` action after server-side validation. No new combat formula/server action is needed for the first alternate-character NPC combat proof.
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
- `Mario64Diagnostics` samples its runtime evidence on the established Mario client tick and the N64 Swing panel only reads immutable snapshots/event text on a 100 ms UI timer. The diagnostics path does not create a second input/render/native-step owner.
- The pure-W direction probe is read-only: it compares the already-sampled Matrix camera-forward vector to normalized native `vx/vz`, records `dot/cross`, and does not modify controls or movement. `verified-static`.

## Unknown / research needed

### HYPOTHESIS

- Sidecar binary transport is fast enough for sustained 30 Hz state + geometry + full control input; runtime measurement is required before considering JNI.
- Nearby RuneScape terrain can be represented efficiently as two SM64 collision triangles per tile inside a bounded local bubble.

### UNKNOWN

- Runtime acceptance of the Matrix-world steering adapter across north/east/south/west and rotate-while-holding-W.
- The exact condition that makes RuneScape local-player suppression drop during the idle->Space transition. The 750 ms grace did not solve it; N64 flight-recorder evidence remains required after the active steering gate.
- Runtime acceptance of the first Mario F/B -> stock RuneScape NPC combat bridge.
- Runtime UI/recorder acceptance of Test Console -> N64 -> Mario 64 under Eclipse Java 8.
- Bundle 4.3 moving-slash leg continuity, held-F no-replay, cleanup/re-entry, remote-player isolation and sustained stability remain pending.
- Final Matrix<->SM64 coordinate conversion for **collision-backed** XYZ movement. The current local presentation scale/sign is runtime accepted but does not by itself prove the native collision-space conversion.
- Final collision-bubble radius/rebuild threshold.
- Correct terrain triangle winding/material selection for RuneScape slopes when translated into SM64 surfaces.
- Runtime cost of rebuilding one Matrix `Model` per native 30 Hz animated geometry frame under sustained use.
- Runtime cost of object collision proxy extraction.
- Final client/server reconciliation model.
- Final server-aware per-character weapon/equipment capability enforcement. Mario advertises melee only on the client today; that is not yet an authoritative restriction.

## Dependencies

- Matrix3 client input/player-transform/renderer ownership.
- Existing Matrix3 client NPC action packet and server `PlayerCombatNew` combat owner.
- Local `libsm64`/`sm64_bridge` build.
- User-owned SM64 US ROM as a local runtime dependency only; never committed.
- User runtime testing for N64 diagnostics, master camera controls, replacement stability, first NPC combat proof, later collision-backed terrain/object behavior and regression acceptance.

## Development plan

### Phase 1 - Alternate Controller Foundation

**Status:** ACTIVE / NEEDS TEST

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

#### Bundle 1.3 - Universal alternate-character controller + combat bridge

**Status:** IMPLEMENTED / NEEDS TEST

- [x] Add `AlternateCharacterController` as the single imported-character viewport/control dispatch owner.
- [x] Add one shared `ControlState` vocabulary for WASD/Space/F/Shift plus Matrix camera-forward.
- [x] Generalize WASD arbitration from `MarioInputKeyboard` to `AlternateCharacterInputKeyboard` while retaining Matrix's original input owner/listener.
- [x] Keep Mario behind the established viewport hook as the first `CharacterDriver`; do not add another viewport tick.
- [x] Make libsm64 consume the actual Matrix camera-forward vector through existing `camLookX/camLookZ` fields; no native rebuild required. Historical first camera-relative slice; superseded for Mario steering by the world-space adapter below.
- [x] Add character combat-style capability metadata; Mario currently advertises melee only and future Link can advertise melee+ranged.
- [x] Add `AlternateCharacterCombatBridge` that translates Mario primary melee intent into the stock NPC attack packet while preserving server `PlayerCombatNew` authority.
- [x] Trigger RuneScape combat only on the F/B rising edge rather than every held client tick.
- [x] Add bounded last-good Mario render grace for transient frame/model gaps without weakening mode/bridge fail-open behavior.
- [x] Runtime regression: the 750 ms grace does not eliminate the idle->Space RuneScape-body pop. `VERIFIED` 2026-10-04.
- [x] Add Test Console -> N64 -> Mario 64 read-only flight recorder for exact client-tick evidence. `verified-static`; runtime UI gate pending.
- [x] Reject/revert the Mario-only stick-Y inversion after runtime evidence showed it made steering broadly worse. `VERIFIED` 2026-10-04.
- [x] Replace normal vanilla yaw reconstruction with resolved camera-position -> focus-position direction; runtime evidence still reports incorrect steering. `VERIFIED` regression 2026-10-04.
- [x] Add read-only pure-W direction alignment probe (`cameraForward` vs native `vx/vz`, `dot/cross`) before any further coordinate transform change. `verified-static`.
- [x] Runtime video/diagnostics identify stale Construction-camera ownership as an upstream steering fault: rendered RTS camera rotates while sampled `cameraForward` remains frozen. `VERIFIED` 2026-10-04.
- [x] Patch `AlternateCharacterController` to recognize active Construction ownership before normal detached/vanilla fallback. `verified-static`.
- [x] Runtime classify the post-ownership Mario/libsm64 basis as 180 degrees reversed and correct only the Mario camera-look boundary; north-facing W/S/A/D became correct. `VERIFIED` 2026-10-04.
- [x] Runtime prove south-facing W/S/A/D remains exactly reversed while north is correct. `VERIFIED` 2026-10-04.
- [x] Make Construction RTS steering use Construction's canonical accepted 14-bit yaw source; runtime still reported south reversed. `VERIFIED` regression 2026-10-04.
- [x] Supersede Mario's dynamic libsm64 camera-heading interpretation with a Matrix-world steering adapter: Construction-style screen basis -> desired world vector -> neutral libsm64 camera + transformed stick. `verified-static`.
- [ ] Runtime verify W/S/A/D at north/east/south/west using the world-space adapter.
- [ ] Runtime verify live camera rotation while moving remains camera-relative; use the pure-W probe only as confirmation.
- [ ] Diagnose the exact idle->Space suppression/frame transition with the N64 recorder and patch only the proven presentation seam.
- [ ] Runtime acceptance after evidence-driven fix: idle several seconds -> Space keeps Mario visible with no RuneScape-body pop.
- [ ] Runtime: nearby simple NPC + F produces native Mario B action plus normal RuneScape server combat/damage/XP.
- [ ] Regression: Ctrl+M restores normal input/model immediately; no target/input state leaks across re-entry.

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
- [x] Map WASD to normalized libsm64 analog movement input.
- [x] Map Space -> A, F -> B, Shift -> Z.
- [x] Publish movement/A/B/Z as one immutable input snapshot to the fixed 30 Hz worker.
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

- [x] Reserve W/A/S/D from normal `method6514(...)` consumers without adding another AWT listener.
- [x] Preserve raw W/A/S/D state for alternate-character input through the original held-key owner.
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

**Status:** READY AFTER CURRENT FRAMEWORK GATE

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

#### Bundle 4.3 - Native custom combat overlay + right-hand weapon socket

**Status:** RUNTIME PROOF ACCEPTED / REGRESSION CHECKS PENDING

- [x] Native additive 1H slash overlay layered over normal libsm64 pose evaluation. `verified-static`.
- [x] Protocol-v3 combat request + same-frame right-hand socket transport. `verified-static`.
- [x] Revision-830 worn weapon model attachment through the native hand socket. `VERIFIED` 2026-10-04.
- [x] F triggers the custom native MARIO_COMBAT_1H_SLASH. `VERIFIED` 2026-10-04.
- [ ] Runtime verify moving slash preserves native leg locomotion through the complete swing/recovery.
- [ ] Runtime verify held-F cannot replay/restart the slash.
- [ ] Runtime verify freeze/unfreeze, unequip/disable fallback, Ctrl+M/re-entry and logout/relog cleanup.
- [ ] Runtime verify remote-player rendering and sustained stability remain unaffected.
- [ ] Runtime verify nearby-NPC F still reaches stock server combat/damage/XP authority.
- [ ] Grip/aesthetic calibration only as needed; no stab/retargeting expansion is included yet.

### Phase 5 - Multiplayer / Server Authority

**Status:** PLANNED

- [ ] Define legal imported-character movement/state sent to server.
- [ ] Remote-player replication/interpolation.
- [ ] Reconciliation/correction behavior.
- [ ] Server-aware character capability / weapon-family validation.
- [ ] Combat/world interaction authority boundaries beyond the stock-combat first slice.

## Current execution state

- Phase: 1 - Alternate Controller Foundation
- Phase status: ACTIVE / NEEDS TEST
- Bundle: 1.3 - Universal alternate-character controller + combat bridge
- Bundle status: IMPLEMENTED / NEEDS TEST
- Approval state: user supplied explicit `AAA` for the master-controller/camera/fallback/combat bundle plus repeated explicit `SAP AAA` continuation approvals for this steering regression on 2026-10-04; Bundle 4.3 custom-combat proof was separately SAP-AAA approved and its core runtime proof is now accepted.
- Current checklist item: runtime-verify the Matrix-world steering adapter across north/east/south/west and live camera rotation after the active 4.3 regression/polish decision.
- Current objective: preserve the accepted 4.3 custom-combat proof, finish only its bounded regressions if desired, then return to the remaining Bundle 1.3 presentation/combat gates.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Matrix transform/input/controller foundation | 1 | 1.x | 🔵 In Progress | Master alternate-character controller implemented; runtime gate pending. |
| Universal character input/camera | 1 | 1.3 | NEEDS TEST | Mario world-space steering adapter is implemented; N/E/S/W + live-rotation acceptance pending. |
| Universal character combat bridge | 1/5 | 1.3 | NEEDS TEST | Mario melee intent routes to stock NPC attack; server `PlayerCombatNew` remains authority. |
| Passthrough architecture | 2 | 2.1 | DONE | `SM64_PASSTHROUGH_ARCHITECTURE.md`. |
| Native `sm64_bridge` sidecar | 2/4 | 2.1/4.1 | VERIFIED | State + binary geometry executable runtime-proven locally. |
| Native -> visible Matrix transform | 2 | 2.2/2.4 | VERIFIED | Native XYZ local presentation is runtime accepted; visible terrain elevation following also works. |
| Mario keyboard/action controls | 2 | 2.3 | RUNTIME PARTIAL | Movement states, crouch, backflip and ground-pound verified; long-jump/entry guards remain. |
| Mario WASD ownership | 2 | 2.4 | VERIFIED | Mario owns WASD without moving the Construction/RTS camera; generalized wrapper now replaces Mario-specific wrapper. |
| Mario native geometry transport | 4 | 4.1 | VERIFIED | Actual Mario geometry and successive native animation frames reach Matrix at runtime. |
| Matrix Mario visual renderer | 4 | 4.1 | VERIFIED CORE / REGRESSION ACTIVE | 750 ms grace did not eliminate idle->Space body pop; N64 flight recorder remains ready for exact evidence. |
| Mario visual fidelity | 4 | 4.2 | VERIFIED / POLISH CARRYOVER | Atlas micro-face v3 and shared-topology smoothing are runtime accepted. |
| Mario custom 1H combat | 4 | 4.3 | VERIFIED CORE / REGRESSION PENDING | Equipped 830 hand attachment and custom F slash runtime-proven; movement/cleanup/NPC/stability regressions remain. |
| Matrix terrain adapter | 3 | 3.1 | READY AFTER CURRENT GATE | Next architectural step after master-controller/runtime presentation acceptance. |

## Decisions / new ideas

- Matrix3 is the host world/renderer/input/server architecture.
- Authentic source-game logic owns imported-character movement/action/animation where available; do not rebuild the full source-game action state machine in Java.
- `AlternateCharacterController` is the shared imported-character controller owner. Do not create a new keyboard/camera/control stack per game.
- Shared control vocabulary is generic; each character driver maps it to source-game inputs/actions.
- Character capability profiles describe supported combat families. Mario starts `MELEE`; Link can later register `MELEE` + `RANGED` for sword/bow while using the same RuneScape combat bridge.
- Alternate-character combat sends intent into Matrix3's existing server-authoritative combat path. Do not add client damage formulas or a parallel character-specific damage engine.
- The first combat bridge intentionally reuses stock NPC attack packet/action ownership; strict server-aware character/equipment restrictions come later.
- Sidecar process remains transport until measurement gives a reason for JNI.
- Matrix scene/model mutation stays on Matrix's client/render ownership path even though native simulation runs on a worker.
- The user explicitly approved and runtime-accepted a temporary **local-only** native X/Z presentation proof before Phase 3. This changes presentation, not networking/clipping/pathfinding/server authority.
- Imported-character mode owns WASD through a reversible view of the existing Matrix keyboard owner; do not add a second keyboard listener or fork Construction camera controls.
- Construction camera ownership and its local screen basis remain the Matrix reference. Mario should consume the resulting desired world movement vector, not re-interpret Matrix camera handedness inside libsm64.
- Dynamic Matrix-camera -> libsm64-camera steering is superseded for Mario by the world-space adapter. Do not reintroduce camera-look sign flips or individual stick-axis guesses unless new evidence directly invalidates the world-space transform.
- Visible terrain elevation following is already accepted through Matrix presentation/rebasing. Phase 3 should not redo that visual behavior; it should make the native SM64 collision/action machine consume equivalent RuneScape surfaces.
- Phase 3 remains the required boundary for authentic RuneScape terrain/object collision and final movement/coordinate authority.
- Direct libsm64 animated geometry is the selected Mario visual path.
- The accepted micro-face atlas path and shared-topology smoothing remain the visual solution unless new runtime evidence regresses them.
- Bundle 4.3 proves custom articulated combat overlays + native hand sockets + real 830 weapon models can coexist with libsm64 locomotion. Do not replace this with Java triangle animation or generic 830 retargeting for the first combat families.
- RuneScape control/presentation remains the safe default and fail-open fallback.
- Test Console -> N64 is developer-only diagnostics infrastructure. Add one sub-tab per imported N64 game; game tabs observe existing owners and must not become parallel gameplay/render/input owners.
- For the idle->Space regression, collect recorder evidence before changing `MarioVisualRenderer` or `Player.method10696(...)` again.
- Matrix terrain will be adapted to local SM64 collision surfaces; do not convert all of Gielinor at once.

## Testing

See `docs/mario/TESTLIST.md` for the broader Mario runtime gate.

See `docs/n64/TESTLIST.md` for the N64 workspace, custom-combat regressions and idle->Space flight-recorder diagnostic gate.

## Carryover / blockers

### CARRYOVER

- Bundle 4.3 custom-combat regressions: moving slash/leg continuity, held-F no-replay, freeze/unequip/exit/relog cleanup, remote-player isolation, nearby-NPC combat authority and sustained stability.
- Idle->Space body-pop recorder capture/evidence-driven presentation fix after the active steering gate.
- Phase 1 deeper lifecycle regression where still useful.
- Bundle 2.2 airborne exit/relog/stale-transform regression.
- Bundle 2.3 long-jump timing and held-action entry-guard checks.
- Bundle 4.1 remote-player isolation and sustained-runtime/performance regression.
- Bundle 4.2 scale/orientation/ground-anchor polish only if later runtime evidence requires it.
- Server-aware per-character equipment/weapon-family restrictions.
- Local-only Mario XYZ versus authoritative server position remains a known limitation for combat range until later movement/server integration.

### BLOCKED

- None. Bundle 4.3 core proof is runtime accepted. Remaining 4.3 work is bounded regression/polish, not an architecture blocker.

## Resume Here

**Bundle 4.3 core proof is runtime-VERIFIED.** The equipped revision-830 sword is visibly attached to Mario's hand and F triggers the custom native MARIO_COMBAT_1H_SLASH. Do not retrace the native pose/socket/bridge architecture and do not add stab/retargeting automatically. If continuing 4.3, run only the remaining bounded regression pass in `docs/n64/TESTLIST.md`: moving slash/leg continuity, held-F no-replay, freeze/unequip/exit/relog cleanup, remote-player isolation, nearby-NPC stock-combat authority and sustained stability. Grip tuning is optional visual polish. The prior Bundle 1.3 steering/presentation checkpoint remains preserved below for return after this override.


**Last completed:**

- Bundle 4.3 core custom-combat proof is runtime-VERIFIED: equipped 830 weapon attachment is visible on Mario and F triggers the custom native 1H slash.
- Bridge Spike A runtime-VERIFIED against real `libsm64` + user ROM.
- Native Y -> Matrix transform runtime-VERIFIED.
- Actual libsm64 Mario geometry, idle and jump animations, RuneScape restore and Mario re-entry are runtime-VERIFIED.
- Atlas micro-face v3 visual fidelity is runtime-VERIFIED: texture details restored without the giant black source-triangle artifact.
- Bundle 4.2B shared-topology smoothing is runtime-VERIFIED at the default `70` degree threshold.
- Bundle 2.3 native controls are runtime-partially accepted: movement animations, crouch, backflip and ground-pound work through libsm64.
- Bundle 2.4 is runtime-VERIFIED: reversible WASD ownership, shared interpolated XYZ, visible native X/Z translation, moving actions, Ctrl+M restore/re-entry and camera-control restoration all work.
- Bundle 2.4 visible Matrix presentation also follows different RuneScape terrain elevations correctly. Do not mistake that for native SM64 collision ingestion.
- Bundle 1.3 master alternate-character dispatch/input, generic keyboard ownership and stock RuneScape NPC combat intent bridge remain implemented and need runtime acceptance.
- The speculative Mario-only stick-Y inversion was runtime-rejected and reverted.
- User video + diagnostics proved stale/wrong Construction camera heading input was one upstream steering fault.
- The Mario/libsm64 180-degree dynamic camera correction made north-facing W/S/A/D correct but south remained exactly reversed.
- Construction's canonical RTS-yaw source also failed to eliminate the south-facing reversal at runtime.
- `MarioJumpController` now supersedes dynamic Matrix-camera -> libsm64-camera steering: it resolves generic screen input into Matrix world movement using Construction's right/forward basis, holds libsm64 camera at neutral `(0,+1)`, and publishes stick `(-worldX,-worldZ)` so libsm64's own `intendedYaw` targets that Matrix world vector.
- The read-only pure-W direction probe remains available only as confirmation of travel alignment.
- User runtime evidence confirms the bounded 750 ms Mario replacement grace did not solve the idle->Space RuneScape-body pop. Do not repeat that grace-only fix.
- Test Console -> N64 -> Mario 64 flight-recorder infrastructure is implemented in `main`; its presentation-regression capture remains carryover after steering acceptance.

**Current phase:**

- Phase 1 - Alternate Controller Foundation (`ACTIVE / NEEDS TEST`).

**Active bundle:**

- Bundle 1.3 - Universal alternate-character controller + combat bridge (`IMPLEMENTED / NEEDS TEST`), with Bundle 4.3 core proof accepted as the completed priority override.

**Next checklist item:**

1. If the user wants to close Bundle 4.3 first, run the remaining custom-combat regression checks in `docs/n64/TESTLIST.md`; do not expand into a new animation family during that pass.
2. Then return to the Construction RTS/Free camera context used for the steering regression.
3. Test W/S/A/D at north, east, south and west. Every heading must remain screen-relative: W forward/up-screen, S backward/down-screen, A left, D right.
4. Hold W while rotating through the full camera circle. Mario should curve continuously with the view and never flip at south.
5. Use `[SM64 Direction]` only as confirmation if needed; the active adapter no longer sends Matrix camera heading into libsm64.
6. If steering passes, return immediately to the saved idle->Space N64 flight-recorder capture and then the F -> stock RuneScape NPC combat proof.
7. Only after Bundle 1.3 passes return the architectural main path to Phase 3.1 terrain collision.

**Files/systems already inspected:**

- `Client/src/main/java/game/AlternateCharacterController.java`
- `Client/src/main/java/game/AlternateCharacterInputKeyboard.java`
- `Client/src/main/java/game/AlternateCharacterCombatBridge.java`
- `Client/src/main/java/game/MarioJumpController.java`
- `Client/src/main/java/game/Mario64Diagnostics.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `Client/src/main/java/game/MarioVisualRenderer.java`
- `Client/src/main/java/game/console/N64Panel.java`
- `Client/src/main/java/game/console/TestConsolePanel.java`
- `Client/src/main/java/game/console/TestConsoleFlyoutMenu.java`
- `Client/src/main/java/game/Class549.java`
- `Client/src/main/java/game/Class549_Sub1.java`
- `Client/src/main/java/game/Class108.java`
- `Client/src/main/java/game/ConstructionBuildCamera.java`
- `Client/src/main/java/game/Class343.java`
- `Client/src/main/java/game/Class246.java`
- `Client/src/main/java/game/Class319.java`
- `Client/src/main/java/game/OutgoingPacket.java`
- `Client/src/main/java/game/Class676.java`
- `Client/src/main/java/game/Entity.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/Class159.java`
- `Client/src/main/java/game/Class106.java`
- `Client/src/main/java/game/Player.java`
- `Server/src/main/java/com/rs/net/decoders/WorldPacketsDecoder.java`
- `Server/src/main/java/com/rs/game/player/actions/PlayerCombatNew.java`
- `native/sm64-bridge/sm64_bridge.c`
- libsm64 input/state/geometry/camera-input contract already established from the public library source/reference test.

**Do not re-scan without new evidence:**

- Bundle 4.3 native custom-animation seam, right-hand socket export, protocol-v3 request/frame layout and Matrix weapon-render ownership; core path is now runtime-proven.
- Local-player transform/input/viewport ownership.
- Matrix held-key owner and W/A/S/D/F/Shift/Space internal mappings.
- Construction camera's W/A/S/D held-key seam; Bundle 2.4 runtime accepted the shared-keyboard-owner solution.
- Construction screen-to-world basis (`right=(forwardZ,-forwardX)`, local forward/right -> world vector); this is the established Matrix reference used by the current Mario steering adapter.
- libsm64 input equations for camera yaw, stick signs, and `intendedYaw`; the current neutral-camera/world-stick adapter is derived directly from those equations.
- Stock NPC attack packet/server `PlayerCombatNew` ownership unless the first combat runtime test fails.
- Bundle 2.4 local XYZ interpolation/presentation/restore behavior unless a new runtime regression appears.
- Binary input/state packet structure; it already carries camLook X/Z + stick X/Y + A/B/Z and returns native XYZ.
- Test Console lazy-workspace/flyout architecture; N64 now uses the established `TestConsolePanel`/`TestConsoleFlyoutMenu` path.
- `Class159 -> Model` generated geometry ownership.
- `Class159.method2560(...)` coincident-vertex reuse precedent.
- `Class578.method6834(...)` direct scene-preview seam.
- `Player.method10696(...)` shared player model-build suppression seam; do not modify it again without recorder/render-time evidence.
- libsm64 geometry buffer/ROM atlas availability.
- Atlas micro-face v3 artifact cause/fix unless runtime regresses.
- Bundle 4.2B shared-boundary topology unless runtime shows a new defect.

**Pending runtime verification:**

- Bundle 4.3 moving slash/leg continuity, held-F no-replay and cleanup/re-entry regression checks.
- Bundle 4.3 remote-player isolation, nearby-NPC stock-combat authority and sustained stability.
- Matrix-world Mario steering at north/east/south/west.
- Live rotate-while-holding-W through the full camera orbit.
- Test Console -> N64 -> Mario 64 Java 8 compile/UI/recorder gate.
- Exact idle->Space suppression/native-frame event sequence using the N64 flight recorder.
- Bundle 1.3 Mario F -> stock RuneScape NPC combat/RS stats proof.
- Bundle 2.3 long-jump timing and held-entry guards.
- Remote-player isolation.
- Sustained stability / render-model error and performance behavior.
- Airborne exit/relog lifecycle carryover.

**Important remaining uncertainty:**

- Bundle 4.3's core slash + weapon-attachment architecture is no longer uncertain; it is runtime-VERIFIED. Remaining uncertainty is limited to regression coverage, exact grip polish, and stock-combat integration timing/behavior.
- The previous heading-dependent north-correct/south-reversed regression is `VERIFIED`. The active world-space adapter is `verified-static` from Construction's screen basis plus libsm64's input equations, but still needs runtime N/E/S/W/live-rotation acceptance.
- The 750 ms replacement grace is implemented but runtime-rejected as a complete idle->Space fix. The exact suppression/render condition causing the body pop is still `UNKNOWN` until the N64 recorder captures the transition.
- The current `3.0` horizontal scale/direct X/Z signs are runtime accepted for local presentation, but native collision-space conversion/winding still needs independent Phase 3 validation.
- Temporary local XYZ still runs against libsm64's flat floor and is not the server player's authoritative position. The first combat proof should be done near the underlying server position.
- Mario's melee-only capability is not yet a server-enforced equipment/style restriction.

## Next recommended work

Close Bundle 4.3 with one short regression pass if desired: attack while moving, hold F, freeze/unfreeze, unequip/re-equip, Ctrl+M/re-entry, logout/relog, remote-player sanity, nearby-NPC stock-combat authority and sustained stability. Do not add stab during that pass. After 4.3 is clean, return to the preserved Bundle 1.3 steering/body-pop gates, then Phase 3 terrain collision. The accepted custom-overlay/socket architecture can later support another Mario combat family or another imported character without replacing libsm64 locomotion.

