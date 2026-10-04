# Mario 830 Runtime Test List

## Jump POC #1 - quick acceptance

- [ ] Eclipse clean/build succeeds under the protected Java 8 setup.
- [ ] Client launches and login succeeds normally.
- [ ] Standing still, press/release Space: the actual local player model visibly rises above terrain and returns to its original ground baseline.
- [ ] Hold Space through landing: no repeated jump occurs until Space is released and pressed again.
- [ ] Click-walk/run while airborne: normal X/Z movement continues and the player lands cleanly.
- [ ] Jump while moving uphill and downhill: no permanent hovering, burial, snap-to-wrong-height, or cumulative vertical drift.
- [ ] After landing, ordinary RuneScape movement, turning, camera and interaction input still behave normally.
- [ ] Jumping does not change the player's RuneScape plane/floor.

## Deeper regression

- [ ] Repeat jumps at multiple terrain elevations.
- [ ] Logout/relog after jumping; no stale airborne/height state remains.
- [ ] Normal movement after relog remains unchanged.
- [ ] Verify Space still types normally in chat/text entry. Current POC may also trigger a visual jump while typing because explicit Mario-mode gating is deferred to the next controller-boundary bundle.

## Relevant Matrix3 smoke coverage

From `docs/rs3/SMOKE_TEST.md`:

- [ ] Build / startup: Eclipse Java 8 clean/build and client launch.
- [ ] Login / player lifecycle: login, expected world entry, logout, relog.
- [ ] Movement / interfaces / utility: normal movement remains functional.

## Acceptance rule

Do not mark Bundle 1.1 `DONE` until the actual player visibly leaves the terrain, lands cleanly, and normal movement still works. Record failures specifically as jump direction, grounding/baseline, movement overwrite, input, or lifecycle issues so the next trace stays narrow.
