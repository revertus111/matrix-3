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
7. [ ] Ctrl+M back to RuneScape while grounded: native session stops and Space no longer drives Mario.
8. [ ] Ctrl+M back to RuneScape while airborne: player returns to the tracked Matrix ground baseline without stale height.
9. [ ] Ordinary RuneScape X/Z movement/clicking still works before, during and after this vertical-only bridge proof.
10. [x] No client hang/crash observed while the persistent native session/transform path was active in the accepted runtime test.

### Failure fallback check

Do this only after the normal jump test passes:

- [ ] Exit Mario mode.
- [ ] Temporarily rename `native/sm64-bridge/dist/sm64_bridge.exe` (or use an invalid `-Dmatrix3.sm64.bridge` path).
- [ ] Enter Mario mode.
- [ ] Console reports persistent-session failure/fallback and controller returns to `RUNESCAPE` without changing plane/X/Z or leaving stale Y.
- [ ] Restore the executable name/path afterward.

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
- [x] V1 samples the ROM atlas into per-triangle Matrix face albedo; exact runtime UV texture injection remains deferred polish.
- [x] `Player.method10696(...)` suppresses only the local RuneScape appearance and only after a fresh successful Mario replacement frame exists.
- [x] Suppression is fail-open: remote players, RuneScape mode, bridge failure/not-ready state, failed model/render state, missing geometry, or native geometry older than 500 ms retain the normal RuneScape player path.
- [x] Safe suppression retry diff verified: `Player.java` contains only the intended 8-line gate/comment addition after restoration of the accidental earlier write.
- [x] `-Dmatrix3.sm64.modelScale=<positive-float>` provides runtime visual scale calibration; default is `2.0`.
- [ ] Rebuild local `native/sm64-bridge/dist/sm64_bridge.exe` so the runtime binary matches the new binary geometry protocol.
- [ ] Eclipse Java 8 clean/build after pulling the complete Bundle 4.1 slice.

### Consolidated runtime acceptance

Use one client launch after rebuilding the native sidecar and Eclipse clean/build:

1. [ ] Launch/login normally in RuneScape mode; local and remote RuneScape players render normally.
2. [ ] Press Ctrl+M. Console prints:

```text
[Mario] Controller mode: MARIO
[SM64 Bridge] Persistent session READY (30 Hz + geometry)
[SM64 Visual] Native Mario -> Matrix Model ACTIVE ...
```

3. [ ] Actual Mario appears at the local player's world transform.
4. [ ] The local RuneScape body is hidden only after Mario is visibly rendering; no persistent double-body presentation remains after the first successful replacement frame.
5. [ ] Idle native animation changes Mario's pose across successive geometry frames.
6. [ ] Tap Space: native Mario jump pose/animation is visible while native SM64 Y still drives vertical movement.
7. [ ] Other players remain normal RuneScape players while local Mario replacement is active.
8. [ ] Press Ctrl+M back to RuneScape: normal local-player model returns immediately and Mario replacement stops.
9. [ ] Re-enter Mario mode and confirm replacement still initializes cleanly a second time.
10. [ ] No client hang/crash or sustained render/model-build error spam during the visual test.

### Visual fail-open acceptance

After the normal visual path passes:

- [ ] While in RuneScape mode, temporarily use an invalid `-Dmatrix3.sm64.bridge` path or rename the sidecar as described in the Bundle 2.2 failure test.
- [ ] Attempt Mario mode. The normal RuneScape player remains visible; no invisible local player is left behind.
- [ ] Restore the sidecar/path and confirm Mario mode can initialize normally again.
- [ ] If a native/visual failure can be induced after Mario has rendered, confirm the RuneScape player reappears once the replacement frame is no longer fresh/usable.

### Model-scale calibration

Default Mario mesh scale is `2.0`.

If Mario is visibly too large/small, test without another code patch using:

```text
-Dmatrix3.sm64.modelScale=<positive-float>
```

Do not finalize orientation, ground anchor, interpolation, or texture-injection polish until this first direct native-body gate passes.

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build/startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login/player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement/interfaces/utility: normal RuneScape movement remains functional.
- [ ] Player rendering: local RuneScape presentation restores cleanly after Mario mode and remote players remain unaffected.

## Next gate

Bundle 4.1 is implemented and verified-static. Rebuild the native sidecar, Eclipse Java 8 clean/build, then run the consolidated visual acceptance above. Phase 3 collision remains intentionally deferred until the actual Mario body/animation path is runtime-accepted.
