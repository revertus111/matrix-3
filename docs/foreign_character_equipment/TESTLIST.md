# Foreign Character Equipment Runtime Test List

## Bundle 1.1 - Mario helmet proof

### Build/start

- [ ] `git pull origin main`.
- [ ] Eclipse clean/build under Java 8 completes with no new compile errors.
- [ ] Client launches/login succeeds normally in RuneScape mode.
- [ ] No native bridge rebuild is required for this bundle.

### Runtime evidence already established

- [x] V1 rendered a real revision-830 helmet on Mario.
- [x] V1 looked undersized/high and visibly competed with Mario's cap/hair.
- [x] V2 Statius's full helm logged `referenceHeadSpan=137.25165`, `clearance=6.8625827`, `targetSpan=150.9768`, `helmetSpan=190.0`, `fit=0.7946148`.
- [x] V2 Statius's full helm remained visually too small/buried despite the expanded head envelope.
- [x] Statius's full helm faced backwards with zero-degree base yaw, so Mario helmet base yaw was changed to `180°`.
- [x] User runtime screenshot showed the head moving while the helmet remained effectively upright/static, proving yaw-only attachment was insufficient.
- [x] V4 full animated head tracking follows Mario's head but drove the helmet in the opposite direction. User runtime video 2026-10-04.

## Combined V5 workbench acceptance

Run this as one client session.

1. [ ] Equip Statius's full helm and enter Mario mode.
2. [ ] Open `Client Console -> N64 -> Mario 64 -> Equipment Workbench`.
3. [ ] Active Equipment reports Statius's item id/name and `3D head tracking = ACTIVE` after head capture.
4. [ ] Before freezing, watch idle/turn motion: the corrected inverse/transpose head delta moves the helmet in the **same** direction as Mario's skull rather than the opposite direction seen in V4.
5. [ ] Jump, backflip and ground-pound once. Helmet follows yaw/pitch/roll with no inverse or doubled rotation.
6. [ ] Press `Freeze pose`; visible Mario stops on one presentation frame while the libsm64 bridge remains healthy.
7. [ ] Edit Scale, head-local X/Y/Z and Yaw from the workbench. Both direct text entry and -/+ buttons update the helmet live without restart/model rebuild.
8. [ ] `Flip helmet 180°` changes only the active helmet's session yaw correction; `Reset transform` restores scale `1.0`, XYZ `0`, yaw delta `0`.
9. [ ] Enable `live head cut` with `Only cut while a helmet is equipped` checked.
10. [ ] Masked source triangle count becomes greater than zero and Mario's visible head geometry changes.
11. [ ] While still frozen, change `Cut starts at body height %` and `Head cut radius %`; the Mario model rebuilds immediately on the same frozen native sequence.
12. [ ] Tune the cut until cap/hair/top-skull clipping is materially reduced while Mario's central face/moustache/nose remain visible.
13. [ ] Disable mask and verify full head geometry returns immediately.
14. [ ] Re-enable mask, unequip the helmet, and verify helmet-only masking stops cutting Mario.
15. [ ] Re-equip the helmet, restore the accepted mask, unfreeze and repeat idle/turn/jump/backflip/ground-pound.
16. [ ] Press `Save profile .md`; status reports `docs/n64/MARIO_EQUIPMENT_RUNTIME.md` and the saved file contains transform + mask values and references `TRANSFORM_CONVENTIONS.md`.
17. [ ] `Copy markdown` copies the same snapshot.
18. [ ] Ctrl+M back to RuneScape leaves no floating helmet, frozen presentation or head cut.

### First acceptance target

When Statius looks correct, save/copy the workbench profile and send the values/screenshot. The permanent Mario helmet standard must **not** be derived from Statius alone; repeat later on several helmet families (normal/full/open/cosmetic/large silhouette).

## Keyboard calibration fallback

The existing F6 workflow remains available:

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

## JVM diagnostic overrides

```text
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final auto-fit multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0 world-Y diagnostic offset
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 180
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

## Lifecycle / fail-open

- [ ] Enter Mario mode with no helmet equipped: Mario still renders normally.
- [ ] Invalid/degenerate head orientation falls back to yaw-only helmet presentation rather than suppressing Mario.
- [ ] A helmet/model attachment failure does not suppress or break the working Mario body.
- [ ] Head mask disabled means the Mario visual path matches the unmasked behavior.
- [ ] Normal RuneScape appearance remains unchanged outside Mario mode.
- [ ] No repeated per-frame exception/error spam appears from the adapter/calibration/orientation/workbench owners.
