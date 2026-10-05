# Mario 830 Runtime Test List

## Controller / legacy foundation

- [x] Client launches/login succeeds normally.
- [x] Actual local player can visibly leave terrain through the established Matrix transform. Runtime-confirmed 2026-10-03.
- [x] Ctrl+M enables/disables Mario mode rather than making alternate behavior global.
- [ ] Logout/relog after Mario use returns to RuneScape mode with no stale height/state.
- [ ] Space behaves normally in RuneScape mode/chat contexts.

## Bridge Spike A - actual SM64-derived native state

**VERIFIED 2026-10-04.**

- [x] Build real `libsm64` + `sm64_bridge.exe` under MSYS2 MinGW64.
- [x] Use local user-owned US ROM at `native/sm64-bridge/baserom.us.z64`.
- [x] Manual sidecar `READY 1` / `PONG 1`.
- [x] Eclipse Java bridge receives real native state.
- [x] PASS gate observed a 96.50-unit native Y rise and at least one intermediate native action-state change.
- [x] No client hang/crash during the one-shot native proof.

Reference PASS:

```text
[SM64 Bridge] PASS native SM64 state: y -0.00 -> 96.50 (rise 96.50), action 205521409 -> 205521409
```

The printed final action returned to the baseline by the end of the deterministic sequence; the PASS gate had already observed an intermediate action change.

## Bridge Spike B / Bundle 2.2 - native state drives visible Matrix transform

### Implementation state

- [x] Replace one-shot `Sm64BridgeProbe` ownership with persistent `Sm64BridgeSession`.
- [x] Fixed 30 Hz native `STEP` worker.
- [x] Native process/thread publishes state only; Matrix transform mutation stays on the established client/viewport thread.
- [x] Replace Java gravity with native SM64 Y presentation in Mario mode.
- [x] One-native-tick delayed interpolation between published states.
- [x] Preserve Matrix terrain/movement Y as the temporary baseline until RuneScape collision is streamed into SM64.
- [x] Restore ground baseline and stop native session on Ctrl+M exit / local-player lifecycle change.
- [x] Auto-fallback to RuneScape mode on missing sidecar/ROM or native-session failure.
- [x] Suppress manufactured A/jump when entering Mario mode while Space is already held.

### Quick runtime acceptance

Use one client launch:

1. [x] Eclipse clean/build succeeds under Java 8 and client launches normally. Runtime-accepted 2026-10-04.
2. [x] Launch/login normally in RuneScape mode.
3. [x] Press Ctrl+M once. Console prints the Mario controller/native bridge activation lines.
4. [x] Tap Space. The visible 830 player rises/lands from the native SM64 Y path. Runtime-confirmed 2026-10-04.
5. [ ] Tap Space again after landing. A second native jump works cleanly.
6. [ ] Enter Mario mode while Space is already held: no jump occurs until Space is released and pressed again.
7. [x] Ctrl+M back to RuneScape while grounded: native session stops and normal RuneScape presentation returns. Runtime-confirmed 2026-10-04.
8. [ ] Ctrl+M back to RuneScape while airborne: player returns to the tracked Matrix ground baseline without stale height.
9. [ ] Ordinary RuneScape movement/clicking still works before and after Mario use.
10. [x] No client hang/crash observed while the persistent native session/transform path was active in the accepted runtime test.

### Failure fallback check

- [ ] Exit Mario mode.
- [ ] Temporarily rename `native/sm64-bridge/dist/sm64_bridge.exe` (or use an invalid `-Dmatrix3.sm64.bridge` path).
- [ ] Enter Mario mode.
- [x] Console reports persistent-session failure/fallback and controller returns to `RUNESCAPE`; observed during the pre-rebuild binary-protocol mismatch on 2026-10-04.
- [x] Restore/rebuild the executable afterward; normal Mario initialization recovered on 2026-10-04.

### Vertical-scale calibration

Default presentation scale is `3.0` Matrix units per native SM64 Y unit.

```text
-Dmatrix3.sm64.verticalScale=<positive-float>
```

Do not treat a tuned value as final collision scale until Phase 3 establishes the full Matrix<->SM64 XYZ/collision conversion.

## Bundle 2.3 - native Mario keyboard controls

### Implementation / static gate

- [x] Matrix held-key owner remains `Class108.aClass549_1426`; no second AWT keyboard listener was added.
- [x] `Class549_Sub1.anIntArray8901` mappings used by the controller are `W=33`, `A=48`, `S=49`, `D=50`, `F=51`, `Shift=81`, `Space=83`. `verified-static`.
- [x] WASD is normalized into libsm64 analog movement input; diagonals use `0.70710677` per axis so diagonal magnitude remains 1.0.
- [x] Space -> SM64 A, F -> SM64 B, Shift -> SM64 Z.
- [x] One immutable input snapshot is published to the fixed 30 Hz worker so movement/A/B/Z values cannot be mixed across a native step.
- [x] A/B/Z are fail-safe on Mario-mode entry: an action key already held while entering must be released before it can trigger native input.
- [x] Existing binary sidecar packet already carries camera-look/stick/A/B/Z, so steering changes require **no native bridge rebuild or protocol-version change**.

### Runtime evidence - 2026-10-04

- [x] Native WASD input changes Mario into authentic movement/turning animation states. `VERIFIED` by user.
- [x] Shift/Z crouch works. `VERIFIED` by user.
- [x] Native backflip action works from the real libsm64 action state machine. `VERIFIED` by user.
- [x] Airborne Shift/Z ground-pound works. `VERIFIED` by user.
- [x] Shared-WASD conflict identified: Construction/RTS camera also moved while Mario consumed WASD. `VERIFIED` by user; addressed by Bundle 2.4.
- [ ] F/B grounded attack behavior still needs a focused runtime check.
- [ ] Native long-jump timing still needs a focused runtime check.
- [ ] Held-action mode-entry guards still need a focused runtime check.

### Historical boundary / supersession

Bundle 2.3 was intentionally input-only when first implemented. The user subsequently explicitly reprioritized and approved Bundle 2.4 to add **temporary local-only native X/Z presentation** before the full Phase 3 collision adapter. That does not transfer server, clipping, plane or pathfinding authority to libsm64.

## Bundle 2.4 - local XYZ presentation + WASD ownership

**RUNTIME VERIFIED 2026-10-04.**

### Implementation / static gate

- [x] Added a reversible wrapper around Matrix3's existing `Class549` keyboard owner; the original AWT listener remains installed and authoritative.
- [x] While Mario mode is active, normal `method6514(...)` held-key consumers see W/A/S/D as released, so Construction Free/RTS camera polling no longer competes for those keys.
- [x] Mario reads the original owner's raw held state through `method6518(...)`; Space/F/Shift and Ctrl+M continue through the existing owner.
- [x] Ctrl+M exit, native failure, and local-player lifecycle replacement restore the original Matrix keyboard owner.
- [x] `Sm64BridgeSession` publishes interpolated native X/Y/Z from one previous/latest frame pair and one shared interpolation alpha.
- [x] `MarioJumpController` captures Matrix/native XYZ baselines on Mario-mode activation and applies native X/Z deltas plus the existing native Y height as a temporary local transform.
- [x] Default horizontal presentation scale is `3.0`; `-Dmatrix3.sm64.horizontalScale=<positive-float>` provides runtime calibration.
- [x] External Matrix/server corrections rebase the tracked presentation baseline per axis rather than being overwritten as a new authority source.
- [x] Ctrl+M/fallback restores the tracked RuneScape XYZ baseline.
- [x] No Mario movement packet, RuneScape clipping/pathfinding/plane ownership, or Phase 3 collision surface streaming is introduced by this slice.
- [x] Existing `sm64_bridge.exe` binary protocol already supplies native XYZ; no native rebuild is required.

### Runtime acceptance

1. [x] `git pull origin main`, Eclipse Java 8 clean/build and live launch reached the Bundle 2.4 path.
2. [x] Entering Mario mode reaches the XYZ presentation path at the default scales.
3. [x] W/A/S/D no longer pan the Construction/RTS camera while Mario mode is active. `VERIFIED` by user.
4. [x] Camera arrow-key pan remains usable and Q/E rotation remains available while Mario owns WASD. `VERIFIED` by user.
5. [x] Mario physically translates away from the activation point from native X/Z instead of only playing movement animation. `VERIFIED` by user.
6. [x] W/A/S/D and diagonal direction/scale behave correctly enough for the current presentation path. `VERIFIED` by user.
7. [x] Jump while moving preserves native horizontal travel instead of snapping back to the Matrix anchor. `VERIFIED` by user.
8. [x] Backflip and ground-pound remain working with XYZ presentation. `VERIFIED` by user.
9. [x] Ctrl+M while displaced restores the tracked RuneScape XYZ baseline cleanly. `VERIFIED` by user.
10. [x] Re-entering Mario mode starts from a clean current RuneScape baseline with no stale prior displacement. `VERIFIED` by user.
11. [x] Normal Construction camera WASD control returns after Mario mode exits. `VERIFIED` by user.
12. [x] Visible Mario presentation follows different RuneScape terrain elevations correctly in live play. `VERIFIED` by user.

### Terrain-elevation interpretation

The terrain-elevation result proves the Matrix presentation/rebase path follows live RuneScape terrain correctly. It does **not** prove that libsm64 has received RuneScape collision surfaces: the native sidecar still runs against its temporary flat floor. Phase 3 remains required for native slope/wall/object/platform collision semantics.

### Horizontal-scale calibration

Default local presentation scale:

```text
-Dmatrix3.sm64.horizontalScale=3.0
```

The default `3.0` and direct native X/Z signs are runtime accepted for the current local presentation path. Collision-backed coordinate conversion remains a separate Phase 3 validation.

## Bundle 4.1 - native Mario geometry -> Matrix Model

### Implementation / static gate

- [x] Binary sidecar protocol publishes native state plus animated Mario geometry at the fixed 30 Hz simulation rate.
- [x] ROM-derived Mario RGBA atlas is transferred once during the binary handshake.
- [x] `Sm64BridgeSession` publishes immutable `GeometryFrame` / `TextureAtlas` snapshots; native worker does not mutate Matrix scene/model state.
- [x] `MarioVisualRenderer` converts the current native geometry frame into `Class159`, builds a normal Matrix `Model`, and renders through the established Matrix direct-scene seam.
- [x] V1 atlas-face approximation was implemented and runtime-proven visually lossy; Bundle 4.2 owns presentation fidelity now.
- [x] `Player.method10696(...)` suppresses only the local RuneScape appearance and only after a fresh successful Mario replacement frame exists.
- [x] Suppression is fail-open: remote players, RuneScape mode, bridge failure/not-ready state, failed model/render state, missing geometry, or native geometry older than 500 ms retain the normal RuneScape player path.
- [x] Safe suppression retry diff verified: `Player.java` contains only the intended 8-line gate/comment addition after restoration of the accidental earlier write.
- [x] `-Dmatrix3.sm64.modelScale=<positive-float>` provides runtime visual scale calibration; default is `2.0`.
- [x] Rebuilt local `native/sm64-bridge/dist/sm64_bridge.exe` with the binary geometry protocol under MSYS2 MinGW64 on 2026-10-04; the rebuilt executable reports `[--binary]` usage as expected.
- [x] Java source compiled/launched successfully enough to run the complete native Mario body/animation path in the live client on 2026-10-04.

### Consolidated runtime acceptance

1. [x] Launch/login normally in RuneScape mode and enter Mario mode successfully.
2. [x] Binary geometry session reaches the working render path; actual Mario geometry is visible in-world.
3. [x] Actual Mario appears at the local player's world transform. Runtime-confirmed by user screenshot on 2026-10-04.
4. [x] The local RuneScape body is replaced by the rendered Mario body in-world. Runtime-confirmed by user screenshot on 2026-10-04.
5. [x] Idle native animation changes Mario's pose across successive geometry frames. Runtime-confirmed by user on 2026-10-04.
6. [x] Space visibly uses Mario's native jump pose/animation while native SM64 Y drives vertical movement. Runtime-confirmed by user on 2026-10-04.
7. [ ] Other players remain normal RuneScape players while local Mario replacement is active.
8. [x] Ctrl+M back to RuneScape restores the normal local-player model and stops Mario replacement. Runtime-confirmed by user on 2026-10-04.
9. [x] Re-entering Mario mode initializes the replacement/idle path cleanly again. Runtime-confirmed by user on 2026-10-04.
10. [ ] Longer sustained runtime remains to be observed for render/model-build error spam or performance issues.

### Visual fail-open acceptance

- [x] A native startup/protocol failure leaves the normal RuneScape player/control path available rather than trapping the client in Mario mode. Runtime-observed during the stale-sidecar binary-protocol mismatch on 2026-10-04.
- [x] Restoring the correct sidecar allows Mario mode to initialize normally again.
- [ ] If a native/visual failure can be induced after Mario has already rendered, confirm the RuneScape player reappears once the replacement frame is no longer fresh/usable.

## Bundle 4.2A - render colour + atlas detail fidelity

### Runtime evidence

- [x] V1 atlas-to-one-face bake produced large dark/black whole-triangle patches. `VERIFIED` 2026-10-04.
- [x] Native-base-colour fallback removed the giant black whole-triangle artifact. `VERIFIED` by user screenshot 2026-10-04.
- [x] Native-base-colour fallback also removes texture-only details such as Mario's eyes/facial details. `VERIFIED` by user screenshot/comment 2026-10-04.
- [x] Atlas micro-face v3 restores Mario's texture details while keeping the giant black source-triangle artifact gone. `VERIFIED` by user 2026-10-04.

### Static evidence / implementation

- [x] V1 collapsed sparse ROM texture detail into one packed-HSL colour per source triangle, explaining why dark texels contaminated whole polygons. `verified-static`.
- [x] libsm64 GL3 reference rendering uses `mix(baseColor, texture.rgb, texture.a)` with the atlas sampled as a real UV texture. `verified-static`.
- [x] libsm64 reference texture state is `GL_CLAMP_TO_EDGE` + `GL_LINEAR`; the old Matrix sampler incorrectly wrapped UVs. `verified-static`.
- [x] libsm64 emits `(1,1)` for all three UVs when texturing is disabled for a source triangle; Matrix now leaves those faces at native base colour. `verified-static`.
- [x] Textured SM64 source triangles are now tessellated only for presentation and atlas-sampled per micro-face; default is 4 subdivisions per edge (16 micro-faces per textured source triangle).
- [x] Atlas sampling now uses clamp-to-edge and bilinear RGBA sampling before the libsm64 base/texture alpha mix.
- [x] Matrix output is budgeted under the 16-bit generated-model vertex/index ceiling; subdivision automatically reduces if a frame would exceed the budget.
- [x] `-Dmatrix3.sm64.textureSubdivisions=0..4` controls the fidelity/cost tradeoff. `0` is the native-base-colour fallback.
- [x] `-Dmatrix3.sm64.debugAtlasFaceBake=true` forces the coarse one-face diagnostic path for A/B comparison.

### Runtime acceptance - atlas micro-face v3

1. [x] Pull/build/launch reached the atlas micro-face v3 runtime path on 2026-10-04.
2. [x] Mario renders through the micro-face fidelity path successfully. Runtime-confirmed by user on 2026-10-04.
3. [x] Eyes/facial/hat/clothing atlas details return. Runtime-confirmed by user on 2026-10-04.
4. [x] The former giant black whole-triangle patches do **not** return. Runtime-confirmed by user on 2026-10-04.
5. [x] Existing native idle animation remains part of the previously accepted Mario presentation path.
6. [x] Existing native jump animation remains part of the previously accepted Mario presentation path.
7. [x] Existing Ctrl+M restore/re-entry remains part of the previously accepted Mario presentation path.
8. [ ] Longer sustained use/performance remains carryover; watch for FPS hitching or render/model-build spam.

If detail is good but performance is poor, retry with `-Dmatrix3.sm64.textureSubdivisions=2` or `3` before changing architecture. If detail is still insufficient at `4`, the next fidelity step is a true renderer-neutral Matrix UV texture path rather than increasing generated geometry indefinitely.

## Bundle 4.2B - shared-topology smoothing

**CORE VISUAL RUNTIME VERIFIED 2026-10-04.**

### Evidence / implementation

- [x] Runtime comparison shows the working Matrix Mario is visibly more faceted/triangular than the SM64 gameplay reference. `VERIFIED` by user screenshots 2026-10-04.
- [x] The generated Matrix path previously emitted three independent vertices for every output face, leaving compatible neighboring source faces disconnected. `verified-static`.
- [x] `Class159` supports coincident vertex reuse; generated Mario now uses shared boundary topology instead of unconditional per-face duplication. `verified-static`.
- [x] Only coincident boundary vertices from source faces whose geometric normals fall within the smoothing threshold are welded; genuine sharper edges remain split.
- [x] Default smoothing threshold is `70` degrees and can be calibrated without another patch through `-Dmatrix3.sm64.smoothAngleDegrees=0..180`.
- [x] Atlas micro-face colour/detail logic is unchanged; smoothing alters generated topology only.
- [x] ACTIVE log now reports `matrixVertices=` and `smoothAngle=` for the generated frame.

### Runtime acceptance

1. [x] Pull/build/launch reached the shared-topology build.
2. [x] Mario retains the accepted atlas details with no giant black-triangle regression. `VERIFIED` by user video.
3. [x] Face, nose, cap, gloves, arms and body read visibly rounder/less harshly faceted. `VERIFIED` by user video.
4. [x] No obvious melted hard-edge/mesh-collapse failure is visible in the accepted clip.
5. [x] Existing animation remains visibly active in the accepted clip.
6. [ ] Reconfirm Space jump animation after smoothing only if a later regression appears.
7. [ ] Console `matrixVertices=` / `smoothAngle=70.0` log observation remains optional diagnostic carryover.
8. [ ] Longer sustained FPS/performance observation remains carryover.

### Model-scale calibration

Default Mario mesh scale remains `2.0`:

```text
-Dmatrix3.sm64.modelScale=<positive-float>
```

## Alternate-character master controller / camera / combat bridge

**Status: IMPLEMENTED / NEEDS TEST**

### Static/runtime evidence gate

- [x] `AlternateCharacterController` is the single viewport-dispatch owner for imported-character drivers; Mario now runs as a driver behind the established `MarioJumpController.tick()` compatibility seam.
- [x] Shared control vocabulary centralizes WASD movement, Space jump, F primary action, Shift modifier and camera-forward sampling. Future character drivers consume this state rather than installing another keyboard/controller path.
- [x] `AlternateCharacterInputKeyboard` replaces the Mario-specific wrapper while preserving Matrix3's original keyboard listener/owner and the accepted WASD arbitration behavior.
- [x] Matrix camera-forward X/Z is supplied to the character driver: Class411 detached/free views use actual position/look geometry; vanilla views normally use resolved viewport camera-position -> focus-position geometry with yaw retained only as fallback.
- [x] Construction RTS uses Construction's canonical heading source before detached-camera reconstruction; Construction Free/other detached cameras retain the position/look fallback. `verified-static`.
- [x] Existing native protocol already carries camera-look/stick floats; **no `sm64_bridge.exe` rebuild is required**.
- [x] User runtime evidence first classified a full 180-degree steering reversal with W/S and A/D both reversed together. `VERIFIED` 2026-10-04.
- [x] A Mario-only dynamic camera-look inversion made north-facing controls correct, but south-facing controls still reversed all four directions. `VERIFIED` 2026-10-04.
- [x] The canonical RTS-yaw source still did not eliminate the south-facing reversal. `VERIFIED` by user; dynamic Matrix-camera -> libsm64-camera interpretation is therefore superseded for Mario steering.
- [x] `MarioJumpController` now resolves screen-relative input into a Matrix world-space vector using the same basis as Construction: `right=(forwardZ,-forwardX)`, `world=moveX*right + moveY*forward`. `verified-static`.
- [x] Mario now publishes a fixed neutral libsm64 camera `(0,+1)` and encodes the desired Matrix world vector directly as native stick `(-worldX,-worldZ)`. This matches libsm64's actual `cameraYaw`, stick sign, and `intendedYaw` equations, removing dynamic camera handedness from the native boundary. `verified-static`.
- [x] `Mario64Diagnostics` retains the read-only pure-W alignment probe as a verification aid.
- [x] `AlternateCharacterCombatBridge` does not calculate client damage. Mario's F/B rising edge sends the stock Matrix3 NPC attack packet (opcode 32) to the nearest loaded NPC within 12 tiles.
- [x] Server-side `WorldPacketsDecoder` still validates the NPC and enters the existing `PlayerCombatNew(npc)` owner, preserving RuneScape combat stats/definitions, target/range/pathing rules, damage/XP and downstream NPC death/drop behavior.
- [x] Mario currently advertises `MELEE` only through the character capability profile. Link can later advertise `MELEE` + `RANGED` without adding another controller/combat pipeline.
- [x] `MarioVisualRenderer` has a 750 ms last-good-model grace path for transient native geometry/model-build gaps; cached fallback renders do not extend that deadline and mode/bridge loss remains immediate fail-open.

### World-space steering runtime acceptance

Use one client launch; no native rebuild:

1. [ ] `git pull origin main`, Eclipse Java 8 clean/build, launch/login normally.
2. [ ] Enter Mario mode with Construction RTS/Free camera active.
3. [ ] Facing north: W = forward/up-screen, S = backward/down-screen, A = left, D = right.
4. [ ] Facing south: W = forward/up-screen, S = backward/down-screen, A = left, D = right.
5. [ ] Rotate east/west and confirm the same screen-relative W/S/A/D behavior.
6. [ ] Hold W while continuously rotating the camera through the full circle; Mario curves with the live view with no north/south flip.
7. [ ] Optional diagnostic sanity: pure-W native velocity should align with the sampled Matrix forward after Mario settles.
8. [ ] Existing visual smoothing/textures/XYZ movement remain intact.
9. [ ] Let Mario sit idle for several seconds, then tap Space. Mario remains the visible replacement throughout the jump; the normal RuneScape body must not flash/reappear on the transition.
10. [ ] Ctrl+M still restores the RuneScape body immediately; the 750 ms grace must never keep Mario visible after mode/bridge ownership ends.
11. [ ] Stand near one simple attackable NPC while the server-side RuneScape player is still near that NPC; tap F once. Mario performs native B/punch behavior and the console prints `[Alt Character Combat] MELEE -> stock NPC attack index=...`.
12. [ ] The NPC enters normal RuneScape combat and receives normal server-owned hits; confirm normal Attack/Strength-style combat behavior/XP rather than a client-only fake hit.
13. [ ] Hold F: the bridge must not spam a new stock attack packet every client tick; only the F rising edge starts/restarts the server combat action.
14. [ ] Move far enough that the local-only Mario presentation no longer matches the server position, then treat combat range/pathing as **Phase 3/server-authority carryover**, not as proof that client-local XYZ is authoritative.
15. [ ] Ctrl+M out/in after combat; normal RuneScape input/combat remains usable and alternate-character target state does not leak across sessions.

### Known first-slice boundary

- The current Mario capability profile is client-side routing metadata, not a server-enforced equipment restriction. The stock server combat engine can still derive style/bonuses from the player's real RuneScape equipment; strict per-character weapon-family enforcement belongs in the later server-aware character capability layer.
- Native Mario XYZ is still local presentation. Combat is authoritative at the server player's RuneScape position until Phase 3 and later multiplayer/server-authority work establish a validated movement handoff.

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build/startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login/player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement/interfaces/utility: normal RuneScape movement remains functional.
- [ ] Player rendering: local RuneScape presentation restores cleanly after Mario mode and remote players remain unaffected.
- [ ] Combat: stock NPC attack remains functional outside alternate-character mode.

## Next gate

Pull/build once and verify N/E/S/W plus rotate-while-holding-W. The new adapter no longer sends Matrix camera heading into libsm64; it sends the desired Matrix world movement direction directly. If steering passes, return immediately to the saved idle-to-jump replacement regression and one nearby NPC F/punch -> stock RuneScape combat proof.