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

1. [ ] Eclipse clean/build succeeds under Java 8.
2. [ ] Launch/login normally in RuneScape mode.
3. [ ] Press Ctrl+M once. Console prints:

```text
[Mario] Controller mode: MARIO
[SM64 Bridge] Persistent session READY (30 Hz)
[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale 3.0)
```

4. [ ] Tap Space. The **visible 830 player** rises/lands. This must now come from native SM64 Y, not Java gravity.
5. [ ] Tap Space again after landing. A second native jump works cleanly.
6. [ ] Enter Mario mode while Space is already held: no jump occurs until Space is released and pressed again.
7. [ ] Ctrl+M back to RuneScape while grounded: native session stops and Space no longer drives Mario.
8. [ ] Ctrl+M back to RuneScape while airborne: player returns to the tracked Matrix ground baseline without stale height.
9. [ ] Ordinary RuneScape X/Z movement/clicking still works before, during and after this vertical-only bridge proof.
10. [ ] No client hang/crash while persistent native stepping is active.

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

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build/startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login/player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement/interfaces/utility: normal RuneScape movement remains functional.

## Next gate

After Bundle 2.2 is runtime-accepted, begin Phase 3 / Bundle 3.1: convert the nearby Matrix terrain heightfield into local `SM64Surface` triangles so the native core can stand, run and jump on real RuneScape hills instead of the temporary flat floor.
