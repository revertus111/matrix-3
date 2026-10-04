# Foreign Character Equipment Runtime Test List

## Bundle 1.1 - Mario helmet proof

### Build/start

- [ ] `git pull origin main`.
- [ ] Eclipse clean/build under Java 8 completes with no new compile errors.
- [ ] Client launches/login succeeds normally in RuneScape mode.
- [ ] No native bridge rebuild is required for this slice.

### Helmet source/model

1. [ ] Equip one normal visible revision-830 helmet before entering Mario mode.
2. [ ] Press Ctrl+M.
3. [ ] Console prints `[SM64 Equipment] Captured Mario HEAD envelope ... head3d=true` if the full head basis is available.
4. [ ] Console prints `[SM64 Equipment] HEAD orientation landmarks ...` once for the captured head topology.
5. [ ] Console prints `[SM64 Equipment] Helmet ACTIVE ... head3d=true frozen=false ...`.
6. [x] The equipped helmet is visible on Mario. Runtime-confirmed by user screenshots 2026-10-04.
7. [ ] Helmet textures/recolours/customization look consistent with its normal worn appearance.

### Runtime evidence already established

- [x] V1 rendered a real 830 helmet on Mario.
- [x] V1 looked undersized/high and visibly competed with Mario's cap/hair.
- [x] V2 Statius's full helm logged `referenceHeadSpan=137.25165`, `clearance=6.8625827`, `targetSpan=150.9768`, `helmetSpan=190.0`, `fit=0.7946148`.
- [x] V2 Statius's full helm remained visually too small/buried despite the expanded head envelope.
- [x] The Statius full helm faced backwards with the previous zero-degree base yaw.
- [x] User runtime screenshot showed the head moving while the helmet remained effectively upright/static, so yaw-only attachment was not sufficient for calibration.

### V4 animated head orientation

1. [ ] Enter Mario mode with Statius's full helm and do **not** press F6 yet.
2. [ ] Watch Mario's idle animation: helmet follows normal head bob/tilt instead of remaining rigidly upright.
3. [ ] Turn/run: helmet follows head yaw without doubling, reversing, or orbiting away from the skull.
4. [ ] Jump: helmet follows the animated head orientation.
5. [ ] Backflip: helmet pitches/rolls with the skull instead of staying upright.
6. [ ] Ground-pound: helmet remains attached and follows the head's full rotation.
7. [ ] If `head3d=false`, record the console landmark/envelope lines; yaw-only fallback should still render rather than breaking Mario.
8. [ ] No sudden 180-degree flips or handedness inversions occur while animation changes.

### V4 frozen live calibration

1. [ ] With the helmet visible, press `F6`.
2. [ ] Console prints `[SM64 Equipment Calibration] ON ... presentation=FROZEN`.
3. [ ] Mario's **visible body/head pose freezes** rather than continuing the idle head animation.
4. [ ] Mario movement/jump/attack/crouch input remains idle while calibration mode is active.
5. [ ] Calibration keys do not pan/move normal Matrix held-key camera controls while calibration mode is active.
6. [ ] `Home` decreases and `End` increases helmet scale live without restart/model rebuild.
7. [ ] Left/Right adjust head-local X.
8. [ ] Up/Down adjust head-local Y; Up should move the helmet visually upward in the frozen reference pose.
9. [ ] PageUp/PageDown adjust head-local Z.
10. [ ] `[` / `]` adjust yaw delta by 5 degrees around the helmet's head-local orientation.
11. [ ] Holding Shift makes position/scale/yaw changes 5x coarser.
12. [ ] `R` resets the current helmet's session calibration to scale `1.0`, XYZ `0`, yaw delta `0`.
13. [ ] `P` prints the current exact item calibration line.
14. [ ] Pressing `F6` again exits calibration, unfreezes Mario presentation, and keeps the current values applied for the session.
15. [ ] After unfreezing, all XYZ calibration offsets rotate with Mario's animated head rather than remaining fixed in world space.
16. [ ] Swap to another helmet and confirm it gets its own independent session calibration values.
17. [ ] Swap back to Statius and confirm its session values are retained.
18. [ ] Unequipping the helmet while calibration is active exits calibration and unfreezes presentation cleanly.
19. [ ] Ctrl+M back to RuneScape leaves no floating helmet and no stuck frozen presentation state.

### First acceptance target

Tune Statius until it visually looks correct, then send the console line:

```text
[SM64 Equipment Calibration] CURRENT item=13896 name=Statius's full helm scale=... x=... y=... z=... yawDeltaDeg=...
```

The permanent Mario helmet standard must **not** be derived from Statius alone. Repeat this calibration on several helmet families (normal/full/open/cosmetic/large silhouette). Common values become the Mario default profile; true outliers become per-item or per-category overrides.

### Masking gate after transform acceptance

- [ ] If transform/orientation placement is correct but Mario cap/hair still visibly intersects the helmet, record that as the masking gate rather than inflating scale to hide it.
- [ ] Mario's face/moustache/nose remain untouched until the dedicated masking slice.

### Equipment changes / lifecycle

- [ ] Unequip the helmet while Mario mode is active: no stale helmet remains.
- [ ] Equip a different helmet: the old cached model is replaced by the new visible helmet.
- [ ] Ctrl+M back to RuneScape: no floating helmet remains.
- [ ] Re-enter Mario mode: head capture/model attachment initializes cleanly again.
- [ ] Normal RuneScape appearance remains unchanged outside Mario mode.

### Fail-open

- [ ] Enter Mario mode with no helmet equipped: Mario still renders normally.
- [ ] Invalid/degenerate head orientation falls back to yaw-only helmet presentation rather than suppressing Mario.
- [ ] A helmet/model attachment failure does not suppress or break the working Mario body.
- [ ] No repeated per-frame exception/error spam appears from the adapter/calibration/orientation owners.

## Live calibration controls

```text
F6              calibration on/off + freeze/unfreeze visible Mario pose
Left / Right    head-local X -/+
Up / Down       head-local Y -/+
PageUp/PageDn   head-local Z +/−
Home / End      scale -/+
[ / ]           yaw delta -/+5 degrees
Shift           5x coarse step
R               reset current item session values
P               print current values
```

Normal steps: position `2.0`, scale multiplier `0.02`, yaw `5°`. Shift multiplies the step by `5`.

## JVM calibration overrides

```text
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final auto-fit multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0 world-Y diagnostic offset
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 180
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

Use the live session calibrator for visual fitting. Keep the JVM properties as global diagnostics/overrides rather than creating permanent per-item values before the multi-helmet sample is evaluated.
