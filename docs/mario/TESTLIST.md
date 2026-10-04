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

- [ ] On login/default RuneScape mode, pressing Space does not trigger the Mario jump.
- [ ] Press Ctrl+M once: client console/stdout reports `[Mario] Controller mode: MARIO`.
- [ ] In Mario mode, Space triggers the same working vertical jump.
- [ ] Hold Ctrl+M: the mode toggles only once; it does not oscillate every tick.
- [ ] Press Ctrl+M again while grounded: mode reports `RUNESCAPE` and Space no longer jumps.
- [ ] Press Ctrl+M while airborne: the player returns to the tracked ground baseline and Mario airborne state clears.
- [ ] Enable Mario mode while Space is already held: no manufactured jump occurs until Space is released and pressed again.
- [ ] Logout/relog after using Mario mode: the new local-player lifecycle starts in RuneScape mode with no stale jump height/state.

## Deeper regression

- [ ] Repeat jumps at multiple terrain elevations.
- [ ] Normal movement after relog remains unchanged.
- [ ] Verify Space still types normally in chat/text entry while RuneScape mode is active.

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build / startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login / player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement / interfaces / utility: normal movement remains functional.

## Acceptance rule

- The core vertical-transform proof is now runtime-confirmed: the actual player can visibly leave RuneScape terrain.
- Keep Bundle 1.1 regression carryover open until movement/slope/hold/relog checks above are accepted.
- Do not mark Bundle 1.2 complete until Mario behavior is gated behind Ctrl+M, disabling midair restores the baseline cleanly, and a relog returns to RuneScape mode.
