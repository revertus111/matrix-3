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

- [ ] In `native/sm64-bridge`, build the sidecar against real `libsm64` (`make bootstrap` from an MSYS2 MinGW 64 shell on Windows).
- [ ] Keep your own SM64 US ROM outside Git. Default local path: `native/sm64-bridge/baserom.us.z64`.
- [ ] Confirm `native/sm64-bridge/dist/sm64_bridge.exe` and `sm64.dll` exist locally.

### Native transport acceptance

- [ ] Manual optional smoke: sidecar starts and prints `READY 1`; `PING` returns `PONG 1`.
- [ ] Eclipse Java 8 clean/build still succeeds with `Sm64BridgeProbe` present.
- [ ] Launch/login normally; existing RuneScape mode behavior remains unchanged.
- [ ] Press Ctrl+M to enter Mario mode once.
- [ ] Console prints a bridge PASS resembling:

```text
[SM64 Bridge] PASS native SM64 state: y ... -> ... (rise ...), action ... -> ...
```

- [ ] No client hang/crash occurs while the background probe runs.
- [ ] The visible Matrix player is still controlled by the existing Java jump proof during Spike A; native state does **not** move the player yet.

### Bridge Spike A acceptance rule

Do not mark Bundle 2.1 complete from `READY`/`PONG` alone.

PASS requires Java to observe both:

1. native Mario Y rising above the idle baseline after the A-button sequence, and
2. at least one native action-state change during the sequence.

A local stub/protocol test is useful static verification but does not count. The process must be linked against actual `libsm64` and initialized from the user's US ROM.

## Deeper regression carryover

- [ ] Repeat Java proof jumps at multiple RuneScape terrain elevations.
- [ ] Normal movement after relog remains unchanged.
- [ ] Verify Space still types normally in chat/text entry while RuneScape mode is active.

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build / startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login / player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement / interfaces / utility: normal movement remains functional.

## Next gate after native PASS

Bridge Spike B maps the returned native Mario vertical state onto the already-VERIFIED Matrix local-player transform. That is the first test where actual SM64-derived C, rather than `MarioJumpController`'s Java gravity, causes the visible 830-side player to jump.
