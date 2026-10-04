# Foreign Character Equipment Runtime Test List

## Bundle 1.1 - Mario helmet proof

### Build/start

- [ ] `git pull origin main`.
- [ ] Eclipse clean/build under Java 8 completes with no new compile errors.
- [ ] Client launches/login succeeds normally in RuneScape mode.

### Helmet source/model

1. [ ] Equip one normal visible revision-830 helmet before entering Mario mode.
2. [ ] Press Ctrl+M.
3. [ ] Console prints `[SM64 Equipment] Captured Mario HEAD envelope ... referenceSpan=...`.
4. [ ] Console prints `[SM64 Equipment] Helmet ACTIVE item=... referenceHeadSpan=... clearance=... targetSpan=...` for the equipped helmet.
5. [x] The equipped helmet is visible on Mario. Runtime-confirmed by user screenshots 2026-10-04.
6. [ ] Helmet textures/recolours/customization look consistent with its normal worn appearance.

### V2 fit / clearance

V1 runtime evidence: the helmet rendered successfully but looked undersized/high and visually competed with Mario's existing cap/hair. V2 changes only fit measurement/clearance; Mario body masking is intentionally deferred until this corrected shell is evaluated.

- [ ] `targetSpan` in the ACTIVE log is greater than `referenceHeadSpan`.
- [ ] Default clearance is roughly `5%` of the reference head span per side, or at least `2.0` Matrix units per side.
- [ ] Helmet is materially larger/better seated than the V1 screenshot.
- [ ] Helmet clears the main skull envelope with little/no obvious clipping at idle.
- [ ] Mario's nose remains visually untouched and does not make the helmet oversized.
- [ ] Helmet center/height does not visibly float far above or clip deeply through the head.
- [ ] Mario idle animation does not detach the helmet.
- [ ] Turning/running keeps the helmet centered on the head.
- [ ] Helmet yaw follows Mario facing; if it is mirrored or rotated, record the direction/error before changing code.
- [ ] Jump keeps the helmet attached.
- [ ] Backflip keeps the helmet translated with the head. V2 still uses yaw-only orientation, so note whether missing pitch/roll is visibly unacceptable.
- [ ] Ground-pound keeps the helmet translated with the head.
- [ ] If scale/placement are now correct but cap/hair still visibly intersect the helmet, record that as the masking gate rather than increasing helmet scale again.

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
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 0
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

Use these only to classify first-pass fit/orientation. Do not add per-item overrides until generic fitting has been runtime evaluated across several helmets.
