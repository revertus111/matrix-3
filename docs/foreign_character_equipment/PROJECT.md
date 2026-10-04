# Foreign Character Equipment Adapter

## Goal

Allow Matrix3/revision-830 equipment models to be worn by foreign character presentations such as Mario without replacing Matrix3's equipment-definition, model, renderer, or gameplay authority.

The first proof target is Mario: equip a normal revision-830 helmet, enter Mario mode, and render that exact worn helmet fitted to Mario's animated head.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Foreign-character attachment framework | 🔵 In Progress |
| Mario helmet proof | 🔵 In Progress |
| Multi-slot equipment attachments | ❌ Not started |
| Torso/leg fitting and deformation | ❌ Not started |
| Additional foreign-character profiles | ❌ Not started |

## Architecture / ownership

- Matrix3 item/equipment definitions remain the equipment-model authority.
- The active foreign-character renderer remains the character presentation/pose authority.
- The adapter renders additional Matrix `Model` instances through the established direct scene seam; it does not add a second renderer.
- V1/V2 reads the local player's already-decoded visible appearance and selects the item whose decoded `equipSlot` is helmet slot `0`.
- The adapter loads the same revision-830 worn raw model used by normal player appearance through `ItemDefinitions.method7531(...)`, including the current gender/customization object.
- Helmet raw geometry is measured once, normalized around its bounds center, converted to a Matrix `Model`, and cached until renderer/item/gender/customization changes.
- The binary libsm64 bridge publishes final animated geometry rather than a skeleton. The adapter captures stable Mario head-region vertex-stream indices from the first upright frame, then follows those same animated vertices on later frames.
- Helmet V2 separates **head tracking** from **fit sizing**. It captures a broader head candidate region, trims the outer X/Z extremes, and records that trimmed span as the reference skull envelope. This is intended to stop protrusions such as Mario's nose from inflating helmet size while leaving the nose visually untouched.
- V2 adds explicit per-side clearance around the reference head envelope: `max(minClearance, headSpan * clearanceFraction)`. The helmet target span is `headSpan + 2 * clearance`; uniform scaling preserves item proportions.
- The default clearance is `5%` of head span per side with a `2.0` Matrix-unit minimum. Example: a `64`-unit head targets about `70.4` units before the optional final fit multiplier.
- Runtime evidence from Statius's full helm proved that comparing Mario's target skull span against the helmet's **outer** model bounds can still produce a visually undersized/buried full helm (`150.98 / 190.0 = 0.7946`). This is now treated as a calibration/fit-model limitation rather than evidence that attachment itself failed.
- Helmet V3 adds `MarioHelmetCalibrationController`, a session-only live calibration owner. It applies per-item scale multiplier, head-local X/Z, Matrix Y and yaw-delta corrections on top of generic auto-fit without rebuilding the helmet model.
- V3 starts Mario helmets with a `180` degree base yaw because the runtime Statius test showed the worn model facing backwards relative to Mario.
- While calibration mode is active, Mario gameplay controls are held idle and calibration keys are hidden from the established Matrix held-key camera seam. The raw keyboard delegate still supplies the calibration controller.
- Session calibration survives toggling calibration off and helmet swaps during the same client session, but is deliberately not persisted yet. A representative helmet sample will determine the common Mario defaults before permanent standard/per-item profiles are written.
- Manual X/Z calibration is head-local and rotates through the same yaw as the helmet, preventing corrections from becoming fixed world-space offsets when Mario turns.
- Helmet translation follows the animated head anchor. Full head pitch/roll is intentionally deferred until the helmet proof is accepted.
- Rendering is fail-open: no helmet, invalid definitions/models/bounds, stale Mario replacement state, or attachment failure leaves the already-working Mario presentation untouched.
- Mario body masking is deliberately not part of V3. Once the helmet transform is visually calibrated, cap/hair/skull masking can be judged independently while preserving Mario's face/nose.

## Current execution state

- Phase: 1 - Attachment foundation
- Bundle: 1.1 - Mario helmet vertical slice
- Status: NEEDS TEST
- Approval: user supplied `SAP AAA` for live helmet calibration on 2026-10-04.
- Current objective: visually calibrate representative 830 helmets on Mario, record the scale/XYZ/yaw values that actually look correct, then promote the common values into the standard Mario helmet profile instead of guessing from one outer-bounds formula.

## Phase 1 - Attachment foundation

### Bundle 1.1 - Mario helmet vertical slice

**Status:** NEEDS TEST

- [x] Reuse the existing Mario direct-render seam instead of adding another renderer.
- [x] Add `MarioEquipmentAdapter` as isolated equipment-presentation ownership.
- [x] Read the visible local-player helmet from decoded appearance state.
- [x] Load the normal revision-830 worn helmet raw model through `ItemDefinitions.method7531(...)`.
- [x] Preserve item gender/customization inputs.
- [x] Measure/normalize the worn helmet model once and cache its Matrix `Model`.
- [x] Capture stable Mario head vertex indices from the first upright geometry frame.
- [x] Follow the captured head vertices on successive native animation frames.
- [x] V1 auto-fit cached helmet uniformly from Mario-head span / helmet-model span.
- [x] Apply native facing yaw and render after successful Mario body replacement.
- [x] Keep all failures isolated/fail-open to the working Mario body.
- [x] Runtime: equipped helmet visibly appears on Mario. `VERIFIED` by user screenshot 2026-10-04.
- [x] Runtime: V1 attachment exposed an undersized/high fit with Mario's cap/hair still visibly competing with the helmet. `VERIFIED` by user screenshots 2026-10-04.
- [x] V2 broaden the upright head capture from the upper crown toward the full skull/face region.
- [x] V2 trim reference X/Z extremes so protrusions such as the nose do not drive fit size.
- [x] V2 add explicit per-side head clearance and fit the helmet to the expanded target envelope.
- [x] Runtime: Statius's full helm V2 still renders too small/buried because generic fit compares against its 190-unit outer span; logged final fit was `0.7946148`. `VERIFIED` 2026-10-04.
- [x] Runtime: Statius's full helm faces backwards on Mario. `VERIFIED` 2026-10-04.
- [x] V3 make `180` degrees the default Mario helmet yaw correction.
- [x] V3 add session-only per-item live scale/X/Y/Z/yaw calibration.
- [x] V3 freeze Mario movement/action input while calibration is active.
- [x] V3 suppress calibration keys from normal Matrix held-key consumers while preserving raw input for calibration.
- [x] V3 rotate manual X/Z offsets through helmet yaw so corrections remain head-local.
- [ ] Runtime: F6 toggles calibration cleanly and Mario remains stationary while calibrating.
- [ ] Runtime: live scale/XYZ/yaw controls visibly update the helmet without restart/rebuild.
- [ ] Runtime: print and report accepted Statius calibration values.
- [ ] Runtime: repeat calibration on several different helmet shapes and identify common/default values versus true item exceptions.
- [ ] Runtime: helmet yaw matches Mario facing while turning/running after the 180-degree correction.
- [ ] Runtime: head translation remains attached through jump/backflip/ground-pound.
- [ ] Runtime: determine whether full pitch/roll attachment is required for flips.
- [ ] Runtime: determine whether cap/hair/skull masking is still needed after transform calibration.
- [ ] Runtime: unequip/swap helmet updates cleanly with no stale model.
- [ ] Runtime: Ctrl+M exit/re-entry leaves no floating helmet.

### Bundle 1.2 - Attachment profile foundation

**Status:** PLANNED AFTER 1.1

- [ ] Promote accepted Mario helmet defaults into reusable attachment-slot/profile structures.
- [ ] Add optional per-item correction records only for helmets that materially deviate from the common profile.
- [ ] Add neck/back/hand/foot anchor definitions for amulet, cape, weapons/shields, gloves and boots.
- [ ] Keep character-specific measurements separate from generic 830 item-model loading/caching.

## Phase 2 - Multi-slot equipment

**Status:** PLANNED

- [ ] Amulet.
- [ ] Cape.
- [ ] Weapon/shield.
- [ ] Gloves.
- [ ] Boots.

## Phase 3 - Full-body fitting

**Status:** PLANNED

- [ ] Chest fitting strategy.
- [ ] Leg/hip fitting strategy.
- [ ] Evaluate body-part hiding versus cage/deformation fitting for full armour.

## Phase 4 - Additional foreign characters

**Status:** PLANNED

- [ ] Extract reusable foreign-character profile contract after Mario proves the attachment architecture.
- [ ] Add additional imported-character profiles without duplicating the Matrix equipment loader/renderer path.

## Live calibration

Press `F6` while Mario mode is active and a helmet is equipped.

```text
Left / Right   = local X -/+
Up / Down      = Matrix Y up/down
PageUp/PageDn  = local Z +/−
Home / End     = scale -/+
[ / ]          = yaw delta -/+ 5 degrees
Shift          = 5x step size
R              = reset current helmet session values
P              = print current values
F6             = exit calibration (values keep applying this session)
```

Normal step sizes: `2.0` Matrix units for position, `0.02` for scale multiplier, `5` degrees for yaw. Hold Shift for `5x` coarse adjustment.

The printed `yawDeltaDeg` is added to the Mario helmet base yaw of `180` degrees. Example: `yawDeltaDeg=-10` means final yaw correction `170` degrees.

## JVM calibration overrides

```text
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 Matrix units per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final auto-fit multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 180
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

These remain global calibration inputs. The V3 session controller layers per-item visual corrections on top without persistence.

## Evidence

### VERIFIED

- The first revision-830 helmet attachment renders on Mario in the live client.
- V1's helmet fit is visually too small/high and competes with Mario's existing cap/hair presentation.
- V2 Statius's full helm uses `referenceHeadSpan=137.25165`, `targetSpan=150.9768`, `helmetSpan=190.0`, producing `fit=0.7946148`; visually it remains too small/buried.
- Statius's full helm is backwards relative to Mario with the previous `0` degree yaw offset.

### verified-static

- Normal player appearance uses revision-830 item definitions and worn raw models.
- `ItemDefinitions.method7531(...)` builds the worn raw model and applies item recolour/retexture/customization data.
- `Class261` supports runtime scale, rotation and translation transforms, so cached helmet geometry does not need per-frame rebuilding.
- libsm64 binary frames already provide animated geometry plus native `faceAngle`; no protocol/native rebuild is required for helmet calibration.
- V2 reference fitting uses a broader head candidate region plus trimmed X/Z bounds and explicit per-side clearance before uniform helmet scaling.
- `Class549_Sub1.anIntArray8901` supplies the internal key mappings used by the V3 calibration controller; the existing alternate-character keyboard wrapper provides the raw held-state delegate and the proven held-key suppression seam.
- V3 session calibration is presentation-only and does not modify item definitions, equipment state, libsm64 state, server authority, or cache data.

### HYPOTHESIS

- A representative sample of normal/full/cosmetic helmets will cluster around a reusable Mario helmet scale/seat standard, leaving only unusual silhouettes as per-item overrides.
- First-frame head-region capture remains stable across libsm64's geometry stream for later animation frames.
- Yaw-only orientation may be sufficient for normal movement but may still need pitch/roll during full flips.

### UNKNOWN

- The accepted standard Mario scale/X/Y/Z values across multiple 830 helmet families.
- Which helmet families require true per-item or per-category overrides rather than one common profile.
- Whether calibrated helmet placement alone is enough visually or Mario cap/hair/top-skull triangles should be selectively masked beneath equipped helmets.
- Whether full head pitch/roll is required for backflip/ground-pound presentation.

## Resume Here

**Last completed:**

- Helmet attachment is runtime-proven.
- Statius V2 runtime data proved the generic outer-span fit still shrinks that full helm to `0.7946148` and the helmet faces backwards.
- Helmet V3 live calibration is implemented on `main`.
- Mario helmet base yaw is now `180` degrees.
- `MarioHelmetCalibrationController` provides session scale/X/Y/Z/yaw tuning and prints exact per-item values.
- Calibration mode freezes Mario controls and suppresses calibration keys from the normal held-key camera seam.
- Session values keep applying after F6 exits calibration, but are intentionally not persisted yet.

**Next checklist item:**

1. Pull/build with Eclipse Java 8; no native bridge rebuild.
2. Equip Statius's full helm, enter Mario mode, press `F6`.
3. Confirm Mario stops responding to gameplay controls while calibration is active.
4. Use `End` to enlarge, arrows/PageUp/PageDown to seat, and `[`/`]` only if the new 180-degree base yaw still needs correction. Hold Shift for coarse changes.
5. Press `P` or `F6` when it looks right and send the `[SM64 Equipment Calibration]` values.
6. Repeat with several differently shaped helmets before promoting any scale/seat numbers to the permanent Mario standard.
7. After the transform standard is accepted, decide cap/hair/skull masking and then move to reusable attachment profiles.

**Files:**

- `Client/src/main/java/game/MarioEquipmentAdapter.java`
- `Client/src/main/java/game/MarioHelmetCalibrationController.java`
- `Client/src/main/java/game/AlternateCharacterInputKeyboard.java`
- `Client/src/main/java/game/AlternateCharacterController.java`
- `Client/src/main/java/game/Class578.java`
- `docs/foreign_character_equipment/PROJECT.md`
- `docs/foreign_character_equipment/TESTLIST.md`
- `docs/foreign_character_equipment/patchnotes.txt`
