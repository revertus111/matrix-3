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
3. [ ] Console prints `[SM64 Equipment] Captured Mario HEAD envelope ... referenceSpan=...`.
4. [ ] Console prints `[SM64 Equipment] Helmet ACTIVE item=... autoFit=... manualScale=... yawOffsetDeg=...`.
5. [x] The equipped helmet is visible on Mario. Runtime-confirmed by user screenshots 2026-10-04.
6. [ ] Helmet textures/recolours/customization look consistent with its normal worn appearance.

### Runtime evidence already established

- [x] V1 rendered a real 830 helmet on Mario.
- [x] V1 looked undersized/high and visibly competed with Mario's cap/hair.
- [x] V2 Statius's full helm logged `referenceHeadSpan=137.25165`, `clearance=6.8625827`, `targetSpan=150.9768`, `helmetSpan=190.0`, `fit=0.7946148`.
- [x] V2 Statius's full helm remained visually too small/buried despite the expanded head envelope.
- [x] The Statius full helm faced backwards with the previous zero-degree base yaw.

### V3 live calibration

1. [ ] Equip Statius's full helm and enter Mario mode.
2. [ ] Press `F6`.
3. [ ] Console prints `[SM64 Equipment Calibration] ON item=13896 name=Statius's full helm` plus the controls summary.
4. [ ] Mario movement/jump/attack/crouch input remains idle while calibration mode is active.
5. [ ] Calibration keys do not pan/move normal Matrix held-key camera controls while calibration mode is active.
6. [ ] New default 180-degree helmet yaw makes Statius face the same direction as Mario. If still offset, tune yaw with `[` / `]` and report the final delta.
7. [ ] `Home` decreases and `End` increases helmet scale live without restart/model rebuild.
8. [ ] Left/Right adjust helmet-local X.
9. [ ] Up/Down move the helmet vertically; Up should move it visually upward.
10. [ ] PageUp/PageDown adjust helmet-local Z.
11. [ ] `[` / `]` adjust yaw delta by 5 degrees.
12. [ ] Holding Shift makes position/scale/yaw changes 5x coarser.
13. [ ] `R` resets the current helmet's session calibration to scale `1.0`, XYZ `0`, yaw delta `0`.
14. [ ] `P` prints the current exact item calibration line.
15. [ ] Pressing `F6` again exits calibration while keeping the current values applied for the session.
16. [ ] Turn Mario after exiting calibration: X/Z correction remains attached to the helmet/head rather than staying fixed in world space.
17. [ ] Swap to another helmet and confirm it gets its own independent session calibration values.
18. [ ] Swap back to Statius and confirm its session values are retained.
19. [ ] Unequipping the helmet while calibration is active exits calibration cleanly.
20. [ ] Ctrl+M back to RuneScape leaves no floating helmet and no stuck calibration mode.

### First acceptance target

Tune Statius until it visually looks correct, then send the console line:

```text
[SM64 Equipment Calibration] CURRENT item=13896 name=Statius's full helm scale=... x=... y=... z=... yawDeltaDeg=...
```

The permanent Mario helmet standard must **not** be derived from Statius alone. Repeat this calibration on several helmet families (normal/full/open/cosmetic/large silhouette). Common values become the Mario default profile; true outliers become per-item or per-category overrides.

### Attachment / animation after calibration

- [ ] Mario idle animation does not detach the helmet.
- [ ] Turning/running keeps the helmet centered on the head.
- [ ] Jump keeps the helmet attached.
- [ ] Backflip keeps the helmet translated with the head; note whether missing pitch/roll is visibly unacceptable.
- [ ] Ground-pound keeps the helmet translated with the head.
- [ ] If transform placement is correct but Mario cap/hair still visibly intersects the helmet, record that as the masking gate rather than inflating scale to hide it.

### Equipment changes / lifecycle

- [ ] Unequip the helmet while Mario mode is active: no stale helmet remains.
- [ ] Equip a different helmet: the old cached model is replaced by the new visible helmet.
- [ ] Ctrl+M back to RuneScape: no floating helmet remains.
- [ ] Re-enter Mario mode: head capture/model attachment initializes cleanly again.
- [ ] Normal RuneScape appearance remains unchanged outside Mario mode.

### Fail-open

- [ ] Enter Mario mode with no helmet equipped: Mario still renders normally.
- [ ] A helmet/model attachment failure does not suppress or break the working Mario body.
- [ ] No repeated per-frame exception/error spam appears from the adapter/calibration controller.

## Live calibration controls

```text
F6              calibration on/off
Left / Right    local X -/+
Up / Down       Matrix Y up/down
PageUp/PageDn   local Z +/−
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
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 180
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

Use the live session calibrator for visual fitting. Keep the JVM properties as global diagnostics/overrides rather than creating permanent per-item values before the multi-helmet sample is evaluated.
