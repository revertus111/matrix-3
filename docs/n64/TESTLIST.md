# N64 Client Console Runtime Test List

## Temporary vanilla RS3 collision handoff

Runtime finding 2026-10-05: vanilla RS3 collision itself is `VERIFIED`, but the first continuous hybrid pass is runtime-rejected because every accepted tile visibly hitched when the collision baseline recentered. The current gate is the zero-snap pending-authority handoff.

1. [ ] `git pull origin main`, Eclipse Java 8 refresh/clean/build, launch/login once. No native sidecar rebuild is required for this Java-only collision/presentation slice.
2. [ ] Ctrl+M into Mario and tap W/A/S/D briefly. Mario must stop at genuine sub-tile positions instead of committing an immediate full tile step.
3. [ ] Hold one direction across at least 8-10 open tiles. There must be no periodic hitch, tile-centre tug, snap, pause cadence or visible rebase each time vanilla RS accepts another tile.
4. [x] Run directly into normal RuneScape collision. Vanilla collision blocks Mario from crossing scenery. `VERIFIED`; recheck after the zero-snap handoff.
5. [ ] Hold movement into a wall/solid object for several seconds. Mario may reach the legal half-tile boundary but must not visually cross it while the vanilla tile request is rejected.
6. [ ] Turn away from the blocked wall after holding into it. Mario must leave smoothly with no accumulated native-X/Z teleport or delayed snap.
7. [ ] Test repeated legal tile crossings followed by sudden direction changes. An accepted next tile must not queue additional hidden tiles before Mario reaches its shared boundary.
8. [ ] Test diagonal movement at a clipped corner. Illegal diagonal crossing must remain blocked; a legal cardinal component must not manufacture a through-corner jump.
9. [ ] Jump, backflip and ground-pound while moving into collision. Native action/animation/Y may continue, but visible X/Z must remain collision-valid and hitch-free.
10. [ ] Walk over normal terrain height changes; Matrix terrain/Y rebasing must remain stable under the native jump overlay.
11. [ ] Release movement just before, on and just after a legal tile boundary. There must be no correction toward either tile centre when movement stops.
12. [ ] Ctrl+M out and confirm normal RuneScape presentation restores to the current legal server-owned baseline without leaving Mario's local presentation offset behind.
13. [ ] Re-enter Mario mode and confirm there is no stale presentation offset, pending authority tile or unsolicited walk request.
14. [ ] Verify F/custom combat, Shift crouch/ground-pound, N64 diagnostics and sleep-guard behavior remain unchanged.

Acceptance note: this remains an interim Matrix/server collision owner. It does **not** mark the planned RuneScape-surface -> libsm64 Phase 3 collision adapter as implemented.

## Custom combat v3 - one consolidated acceptance session

### Runtime evidence accepted 2026-10-05

- [x] Equipped revision-830 sword visibly renders attached to Mario's hand in live Mario mode. `VERIFIED`.
- [x] F triggers the custom native MARIO_COMBAT_1H_SLASH animation. `VERIFIED`.
- [x] Core protocol-v3/socket/request path is active at runtime; Java cannot enter custom weapon combat without v3 + live hand socket + rendered weapon readiness.
- [x] V1 visual result: slash works but reads as a slow little punch. `VERIFIED`.
- [x] V2 was rebuilt successfully with the Native Builder but still reads as a custom punch. `VERIFIED` rejection.
- [x] V3 runtime video shows Mario folding forward and the weapon hand collapsing toward the floor. `VERIFIED` rejection.
- [x] Source follow-up confirms native B is already suppressed while custom weapon mode is active; the V3 deform is the custom pose, not stock punch leakage. `verified-static`.
- [ ] V4 visual acceptance: torso remains upright while the shoulder/forearm/wrist produce a readable one-handed sword cut.
- [ ] Moving slash / native leg continuity is still pending unless separately confirmed.
- [ ] Held-F no-replay, preview-only button behavior, freeze/unfreeze, unequip fallback and Ctrl+M/relog cleanup remain pending.
- [ ] Remote-player isolation, nearby-NPC server combat/damage/XP and sustained stability remain pending.

The first attempted acceptance run did **not** exercise v3: Windows failed to relink `dist/sm64_bridge.exe` with `Permission denied` because the executable was locked. The root Native Builder now handles the correct directory and stale-process shutdown automatically.

1. [ ] `git pull origin main`.
2. [ ] Double-click root `Native Builder.bat`, then click `BUILD + TEST MARIO`; wait for `MARIO BUILD + TEST SUCCESS`.
3. [ ] Launch/login once.
4. [ ] Equip a normal one-handed sword, longsword or scimitar; Ctrl+M into Mario.
5. [ ] Console startup reports `combat-socket-v3`; N64 -> Mario 64 -> Custom combat shows RIGHT_HAND AVAILABLE and weapon ID/name.
6. [ ] Sword follows the right wrist while idle/turning/running. Adjust grip scale/XYZ/angles if needed; the bounds-based starting grip is NOT visually verified.
7. [ ] Press F once: V4 should finish in about half a second. Mario's torso must stay upright; the right shoulder should carry the main sweep, with only a restrained forearm bend and wrist roll. The weapon must no longer dive toward the floor or fold Mario's upper body.
8. [ ] Run and press F: legs continue native running; sword follows hand through the complete swing and recovery.
9. [ ] Play 1H slash button previews only (no new server attack request); optional equipped-weapon override is explicit.
10. [ ] Freeze in Equipment Workbench: body and sword freeze together. Unfreeze before further attacks.
11. [ ] Unequip or disable custom combat: current native F/punch behavior returns. Re-equip/re-enable without F: no unsolicited slash.
12. [ ] Ctrl+M out/re-enter; logout/relog: no floating/stale weapon, normal RuneScape body restored.
13. [ ] WASD at different headings, jump, backflip, ground-pound, helmet/head-shell workbench and ordinary remote-player rendering remain correct.
14. [ ] Nearby-NPC F still uses existing server combat rules; visual blade contact timing is not part of this proof.
15. [ ] Long idle: native frames continue and sleep guard remains active; no model-build error spam.

Older v1/v2 sidecar: Java connects with original STEP layout, custom combat reports native v3 required, native Mario remains usable. Older Java cannot use the v3 sidecar; update both together.

## Earlier semantic/helmet acceptance checks (retained)

## One required V7 rebuild / acceptance session

### Build

1. [ ] `git pull origin main`.
2. [ ] From `native/sm64-bridge`, run `make bootstrap`.
3. [ ] Confirm the pinned libsm64 dependency is rebuilt and the semantic bridge executable/runtime library are copied into `dist`.
4. [ ] Eclipse Java 8 clean/build succeeds.
5. [ ] Launch/login normally.

### Semantic bridge

1. [ ] Enter Mario mode with Ctrl+M.
2. [ ] Native stderr prints `[SM64 Bridge] semantic-geometry-v2 + sleep-guard-v2 active`.
3. [ ] Java prints `[SM64 Bridge] Persistent session READY (30 Hz + semantic geometry v2)`.
4. [ ] Open `Client Console -> N64 -> Mario 64 -> Equipment Workbench`.
5. [ ] `Bridge protocol` shows `v2`.
6. [ ] `Semantic geometry` shows `AVAILABLE`.
7. [ ] `Shared FACE W/H/D` contains finite values.
8. [ ] Protected part counts show FACE and the current eye/moustache geometry; removable counts show available CAP / SIDEBURN / BACK HAIR geometry.

### Nose-safe full helmet proof

1. [ ] Equip Statius's full helm.
2. [ ] Start with `Keep all Mario head parts`; Mario's original head remains complete.
3. [ ] Press `Full helm safe`.
4. [ ] Mario's tagged CAP and named hair geometry disappear.
5. [ ] FACE, EYES and MOUSTACHE remain.
6. [ ] Mario's nose remains because the complete mixed FACE mesh is protected.
7. [ ] Masked source triangle count is greater than zero.
8. [ ] Press `Keep all Mario head parts`; removed pieces return immediately.
9. [ ] Freeze Pose, switch between Keep All / Full Helm Safe, and confirm the same frozen frame rebuilds immediately.
10. [ ] With `Apply semantic coverage only while a helmet is equipped` enabled, unequip Statius and confirm all Mario head parts return.

### Fit / animation

1. [ ] Console helmet log says `fitReference=semantic-face`.
2. [ ] Auto-fit log says `reference=semantic-face`.
3. [ ] Initial scale is based on shared FACE width rather than full cartoon-head containment.
4. [ ] Freeze Pose and use Scale / head-local X/Y/Z / Yaw only for small final corrections.
5. [ ] Unfreeze and verify idle/turn/run follows the accepted head direction.
6. [ ] Jump/backflip/ground-pound keep the helmet attached with no inverse/doubled rotation.
7. [ ] `Reset / recalc fit` restores the mathematical baseline for the active helmet.
8. [ ] `Save profile .md` records semantic protocol, coverage, part counts and transform values.

## Protocol-v1 fail-open

- [ ] An intentionally old v1 bridge still allows Mario to render.
- [ ] Workbench reports semantic metadata unavailable rather than crashing.
- [ ] Semantic coverage does not hide anonymous triangles on v1.
- [ ] The old height/radius geometric cutter is available only as an explicit v1 debug fallback.
- [ ] Unknown semantic part ids are always preserved.

## Long-idle / sleep-state regression

- [ ] Leave Mario idle for at least 90 seconds.
- [ ] Native sequence keeps advancing and frame age stays low.
- [ ] Published stream does not remain in `ACT_START_SLEEPING (0x0C400202)` or `ACT_SLEEPING (0x0C000203)`.
- [ ] RuneScape body suppression remains active throughout Mario mode.
- [ ] Jump/movement/attack/crouch still work after the long idle.

## Recorder / lifecycle regression

- [ ] Runtime recorder Clear/Copy/Pause/Auto-scroll controls still work.
- [ ] Ctrl+M back to RuneScape restores normal player body/input.
- [ ] No floating helmet, frozen pose, stale coverage, or Mario masking remains after exit.
- [ ] Re-enter Mario mode and semantic metadata/helmet attachment initialize cleanly.
- [ ] Normal RuneScape appearance/server authority remain unchanged outside Mario mode.
