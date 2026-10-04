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
3. [x] Press Ctrl+M once. Console prints:

```text
[Mario] Controller mode: MARIO
[SM64 Bridge] Persistent session READY (30 Hz)
[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale 3.0)
```

Runtime-confirmed by user on 2026-10-04.

4. [x] Tap Space. The **visible 830 player** rises/lands from the native SM64 Y path rather than the removed Java-gravity implementation. User accepted the Bundle 2.2 path as working on 2026-10-04.
5. [ ] Tap Space again after landing. A second native jump works cleanly.
6. [ ] Enter Mario mode while Space is already held: no jump occurs until Space is released and pressed again.
7. [x] Ctrl+M back to RuneScape while grounded: native session stops and normal RuneScape presentation returns. Runtime-confirmed during Bundle 4.1 acceptance on 2026-10-04.
8. [ ] Ctrl+M back to RuneScape while airborne: player returns to the tracked Matrix ground baseline without stale height.
9. [ ] Ordinary RuneScape X/Z movement/clicking still works before, during and after this vertical-only bridge proof.
10. [x] No client hang/crash observed while the persistent native session/transform path was active in the accepted runtime test.

### Failure fallback check

Do this only after the normal jump test passes:

- [ ] Exit Mario mode.
- [ ] Temporarily rename `native/sm64-bridge/dist/sm64_bridge.exe` (or use an invalid `-Dmatrix3.sm64.bridge` path).
- [ ] Enter Mario mode.
- [x] Console reports persistent-session failure/fallback and controller returns to `RUNESCAPE`; observed during the pre-rebuild binary-protocol mismatch on 2026-10-04.
- [x] Restore/rebuild the executable afterward; normal Mario initialization recovered on 2026-10-04.

### Vertical-scale calibration

Default presentation scale is `3.0` Matrix units per native SM64 Y unit.

If the native jump is visibly too tall/short, test without another code patch using:

```text
-Dmatrix3.sm64.verticalScale=<positive-float>
```

Do not treat a tuned value as final collision scale until Phase 3 establishes the full Matrix<->SM64 XYZ/collision conversion.

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

- [x] A native startup/protocol failure leaves the normal RuneScape player/control path available rather than trapping the client in Mario mode. Runtime-observed during the stale-sidecar binary mismatch on 2026-10-04.
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

### Model-scale calibration

Default Mario mesh scale remains `2.0`:

```text
-Dmatrix3.sm64.modelScale=<positive-float>
```

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build/startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login/player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement/interfaces/utility: normal RuneScape movement remains functional.
- [ ] Player rendering: local RuneScape presentation restores cleanly after Mario mode and remote players remain unaffected.

## Next gate

Bundle 4.2A atlas micro-face v3 is runtime-accepted for visual fidelity: texture details are restored and the giant black whole-triangle artifact remains fixed. Next visual work is scale/orientation/ground-anchor calibration, with sustained performance and remote-player isolation left as carryover regression checks.
