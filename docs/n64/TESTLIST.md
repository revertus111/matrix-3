# N64 Client Console Runtime Test List

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
