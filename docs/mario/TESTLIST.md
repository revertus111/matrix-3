# Mario 830 Runtime Test List

## Jump POC #1 - quick acceptance

- [ ] Eclipse clean/build succeeds under the protected Java 8 setup.
- [x] Client launches and login succeeds normally.
- [x] Standing still, press/release Space: the actual local player model visibly rises above terrain. Runtime-confirmed by user on 2026-10-03.
- [ ] Hold Space through landing: no repeated jump occurs until Space is released and pressed again.
- [ ] Click-walk/run while airborne: normal X/Z movement continues and the player lands cleanly.
- [ ] Jump while moving uphill and downhill: no permanent hovering, burial, snap-to-wrong-height, or cumulative vertical drift.
- [ ] After landing, ordinary RuneScape movement, turning, camera and interaction input still behave normally.
- [ ] Jumping does not change the player's RuneScape plane/floor.

## Controller Mode Boundary - Bundle 1.2

User accepted the controller-mode behavior at runtime on 2026-10-03.

- [x] On login/default RuneScape mode, pressing Space does not trigger the Mario jump.
- [x] Press Ctrl+M once: client reports `[Mario] Controller mode: MARIO`.
- [x] In Mario mode, Space triggers the working vertical proof.
- [x] Ctrl+M toggles back to RuneScape mode rather than making Mario behavior global.

The following lifecycle/deeper checks remain useful regression coverage even though the controller-boundary slice is accepted:

- [ ] Press Ctrl+M while airborne: the player returns to the tracked ground baseline and Mario airborne state clears.
- [ ] Enable Mario mode while Space is already held: no manufactured jump occurs until Space is released and pressed again.
- [ ] Logout/relog after using Mario mode: the new local-player lifecycle starts in RuneScape mode with no stale jump height/state.

## Bridge Spike A - actual SM64-derived native state

### One-time local setup

- [x] In `native/sm64-bridge`, build the sidecar against real `libsm64` (`make bootstrap` from an MSYS2 MinGW 64 shell on Windows). Runtime-confirmed 2026-10-04.
- [x] Keep your own SM64 US ROM outside Git. Default local path: `native/sm64-bridge/baserom.us.z64`.
- [x] Confirm `native/sm64-bridge/dist/sm64_bridge.exe` and `sm64.dll` exist locally.

### Native transport acceptance

- [x] Manual smoke: sidecar starts and prints `READY 1`; `PING` returns `PONG 1`.
- [x] Launch/login succeeds with `Sm64BridgeProbe` present.
- [x] Existing RuneScape/Mario controller-mode activation still works.
- [x] Press Ctrl+M to enter Mario mode once.
- [x] Console prints a real bridge PASS from actual `libsm64` + user ROM:

```text
[SM64 Bridge] PASS native SM64 state: y -0.00 -> 96.50 (rise 96.50), action 205521409 -> 205521409
```

- [x] No client hang/crash occurred while the background probe ran.
- [x] Java observed an intermediate native action-state change during the deterministic sequence; the displayed final action returned to the baseline action by the end of the test.
- [ ] The visible Matrix player remains on the temporary Java jump proof until Bridge Spike B explicitly replaces Mario-mode vertical physics with returned native state.

### Bridge Spike A acceptance rule

**VERIFIED on 2026-10-04.**

PASS required Java to observe both:

1. native Mario Y rising above the idle baseline after the A-button sequence, and
2. at least one native action-state change during the sequence.

The real runtime produced a 96.50-unit native Y rise and the probe PASS gate succeeded while linked against actual `libsm64` and initialized from the user's US ROM. `READY`/`PONG` alone were not used as acceptance.

## Bridge Spike B - native state drives visible Matrix transform

- [ ] Replace Mario-mode vertical simulation ownership from `MarioJumpController` Java gravity to the persistent native bridge state.
- [ ] Advance the native simulation on a fixed 30 Hz step.
- [ ] Convert native SM64 Y into the established Matrix local-player vertical transform convention.
- [ ] Preserve RuneScape mode as the safe default and cleanly stop/reset native state when leaving Mario mode.
- [ ] Runtime prove that pressing Space/A causes actual SM64-derived C state to visibly move the 830 player.

## Deeper regression carryover

- [ ] Repeat Java proof jumps at multiple RuneScape terrain elevations.
- [ ] Normal movement after relog remains unchanged.
- [ ] Verify Space still types normally in chat/text entry while RuneScape mode is active.

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build / startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login / player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement / interfaces / utility: normal movement remains functional.

## Next gate

Bridge Spike B maps returned native Mario state onto the already-VERIFIED Matrix local-player transform. That is the first test where actual SM64-derived C, rather than `MarioJumpController`'s Java gravity, causes the visible 830-side player to jump.
