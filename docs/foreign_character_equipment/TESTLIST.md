# Foreign Character Equipment Runtime Test List

## Bundle 1.1 - Mario helmet proof

### Build/start

- [ ] `git pull origin main`.
- [ ] Eclipse clean/build under Java 8 completes with no new compile errors.
- [ ] Client launches/login succeeds normally in RuneScape mode.

### Helmet source/model

1. [ ] Equip one normal visible revision-830 helmet before entering Mario mode.
2. [ ] Press Ctrl+M.
3. [ ] Console prints `[SM64 Equipment] Captured Mario HEAD anchor ...`.
4. [ ] Console prints `[SM64 Equipment] Helmet ACTIVE item=...` for the equipped helmet.
5. [ ] The exact equipped helmet is visible on Mario rather than the RuneScape player body.
6. [ ] Helmet textures/recolours/customization look consistent with its normal worn appearance.

### Fit / attachment

- [ ] Helmet size is plausible on Mario's head.
- [ ] Helmet center/height does not visibly float far above or clip deeply through the head.
- [ ] Mario idle animation does not detach the helmet.
- [ ] Turning/running keeps the helmet centered on the head.
- [ ] Helmet yaw follows Mario facing; if it is mirrored or rotated, record the direction/error before changing code.
- [ ] Jump keeps the helmet attached.
- [ ] Backflip keeps the helmet translated with the head. V1 currently uses yaw-only orientation, so note whether missing pitch/roll is visibly unacceptable.
- [ ] Ground-pound keeps the helmet translated with the head.

### Equipment changes / lifecycle

- [ ] Unequip the helmet while Mario mode is active: no stale helmet remains.
- [ ] Equip a different helmet: the old cached model is replaced by the new visible helmet.
- [ ] Ctrl+M back to RuneScape: no floating helmet remains.
- [ ] Re-enter Mario mode: head capture/model attachment initializes cleanly again.
- [ ] Normal RuneScape appearance remains unchanged outside Mario mode.

### Fail-open

- [ ] Enter Mario mode with no helmet equipped: Mario still renders normally.
- [ ] A helmet/model attachment failure does not suppress or break the working Mario body.
- [ ] No repeated per-frame exception/error spam appears from the adapter.

## Calibration only if needed

```text
-Dmatrix3.sm64.helmetFitPadding=<positive-float>      # default 1.12
-Dmatrix3.sm64.helmetVerticalOffset=<float>          # default 0
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>        # default 0
-Dmatrix3.sm64.helmetYawFlip=true|false              # default false
```

Use these only to classify first-pass fit/orientation. Do not add per-item overrides until generic fitting has been runtime evaluated across several helmets.
