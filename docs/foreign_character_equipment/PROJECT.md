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
- The binary libsm64 bridge publishes final animated geometry rather than a skeleton. The adapter captures stable Mario head-region vertex-stream indices from the first usable frame, then follows those same animated vertices on later frames.
- Helmet V2 separates **head tracking** from **fit sizing**. It captures a broader head candidate region, trims the outer X/Z extremes, and records that trimmed span as the reference skull envelope. This is intended to stop protrusions such as Mario's nose from inflating helmet size while leaving the nose visually untouched.
- V2 adds explicit per-side clearance around the reference head envelope: `max(minClearance, headSpan * clearanceFraction)`. The helmet target span is `headSpan + 2 * clearance`; uniform scaling preserves item proportions.
- The default clearance is `5%` of head span per side with a `2.0` Matrix-unit minimum. Example: a `64`-unit head targets about `70.4` units before the optional final fit multiplier.
- Runtime evidence from Statius's full helm proved that comparing Mario's target skull span against the helmet's **outer** model bounds can still produce a visually undersized/buried full helm (`150.98 / 190.0 = 0.7946`). This is treated as a calibration/fit-model limitation rather than evidence that attachment itself failed.
- Helmet V3 adds `MarioHelmetCalibrationController`, a session-only live calibration owner. It applies per-item scale multiplier, head-local XYZ and yaw-delta corrections on top of generic auto-fit without rebuilding the helmet model.
- V3 starts Mario helmets with a `180` degree base yaw because the runtime Statius test showed the worn model facing backwards relative to Mario.
- While calibration mode is active, Mario gameplay controls are held idle and calibration keys are hidden from the established Matrix held-key camera seam. The raw keyboard delegate still supplies the calibration controller.
- Session calibration survives toggling calibration off and helmet swaps during the same client session, but is deliberately not persisted yet. A representative helmet sample will determine the common Mario defaults before permanent standard/per-item profiles are written.
- Helmet V4 adds `MarioHeadOrientationTracker`. Because the bridge has animated triangles rather than bones, V4 captures stable top/bottom and side landmark groups from the accepted head core and derives a full relative 3D head rotation matrix every frame.
- V4 applies the head delta to the reference Mario helmet orientation, so helmet yaw/pitch/roll follow the animated skull rather than remaining upright while Mario nods, tilts or flips.
- V4 uses the stable head-vertex centroid for attachment translation and transforms manual XYZ calibration through the same full head matrix, keeping all seating corrections head-local.
- V4 calibration freezes the Matrix-visible SM64 presentation snapshot through `Sm64BridgeSession` while the native worker continues ticking normally. The frozen frame receives a refreshed presentation timestamp so the existing stale-frame fail-open guard remains valid without changing native timing ownership.
- `Class261.method3572(...)` + `method3578(...)` are used for the full 3x3 rotation basis plus uniform scale; yaw-only remains the fail-open fallback if a valid animated head basis cannot be recovered.
- Rendering remains fail-open: no helmet, invalid definitions/models/bounds, stale Mario replacement state, or attachment/orientation failure leaves the already-working Mario presentation untouched.
- Mario body masking is deliberately not part of V4. Once transform/orientation is visually accepted, cap/hair/skull masking can be judged independently while preserving Mario's face/nose.

## Current execution state

- Phase: 1 - Attachment foundation
- Bundle: 1.1 - Mario helmet vertical slice
- Status: NEEDS TEST
- Approval: user supplied `SAP AAA` for full animated head orientation + stable calibration pose on 2026-10-04.
- Current objective: runtime-prove that the helmet follows Mario's actual head pitch/roll/yaw and that F6 freezes one visible pose for easy visual calibration, then record accepted Statius and multi-helmet calibration values.

## Phase 1 - Attachment foundation

### Bundle 1.1 - Mario helmet vertical slice

**Status:** NEEDS TEST

- [x] Reuse the existing Mario direct-render seam instead of adding another renderer.
- [x] Add `MarioEquipmentAdapter` as isolated equipment-presentation ownership.
- [x] Read the visible local-player helmet from decoded appearance state.
- [x] Load the normal revision-830 worn helmet raw model through `ItemDefinitions.method7531(...)`.
- [x] Preserve item gender/customization inputs.
- [x] Measure/normalize the worn helmet model once and cache its Matrix `Model`.
- [x] Capture stable Mario head vertex indices from the first usable geometry frame.
- [x] Follow the captured head vertices on successive native animation frames.
- [x] V1 auto-fit cached helmet uniformly from Mario-head span / helmet-model span.
- [x] Apply native facing yaw and render after successful Mario body replacement.
- [x] Keep all failures isolated/fail-open to the working Mario body.
- [x] Runtime: equipped helmet visibly appears on Mario. `VERIFIED` by user screenshot 2026-10-04.
- [x] Runtime: V1 attachment exposed an undersized/high fit with Mario's cap/hair still visibly competing with the helmet. `VERIFIED` by user screenshots 2026-10-04.
- [x] V2 broaden the head capture toward the full skull/face region.
- [x] V2 trim reference X/Z extremes so protrusions such as the nose do not drive fit size.
- [x] V2 add explicit per-side head clearance and fit the helmet to the expanded target envelope.
- [x] Runtime: Statius's full helm V2 still renders too small/buried because generic fit compares against its 190-unit outer span; logged final fit was `0.7946148`. `VERIFIED` 2026-10-04.
- [x] Runtime: Statius's full helm faces backwards on Mario. `VERIFIED` 2026-10-04.
- [x] V3 make `180` degrees the default Mario helmet yaw correction.
- [x] V3 add session-only per-item live scale/X/Y/Z/yaw calibration.
- [x] V3 freeze Mario movement/action input while calibration is active.
- [x] V3 suppress calibration keys from normal Matrix held-key consumers while preserving raw input for calibration.
- [x] V3 keep session calibration independent per helmet.
- [x] V4 add stable top/bottom/side head landmark groups from the captured head core.
- [x] V4 derive a relative full 3D head rotation matrix from those stable animated landmark groups.
- [x] V4 apply full head orientation to the helmet with yaw-only fallback on invalid basis data.
- [x] V4 transform manual XYZ correction through the same head orientation.
- [x] V4 use stable head centroid rather than axis-aligned bounds center for attachment translation.
- [x] V4 freeze/unfreeze the Matrix-visible SM64 snapshot during F6 calibration without pausing the native worker.
- [ ] Runtime: F6 freezes Mario's visible body/head pose and Mario remains stationary while calibrating.
- [ ] Runtime: live scale/XYZ/yaw controls visibly update the helmet against the frozen pose without restart/model rebuild.
- [ ] Runtime: normal idle/head motion keeps helmet locked to Mario's skull after F6 exits.
- [ ] Runtime: turning/running follows head yaw without doubled or reversed rotation.
- [ ] Runtime: jump/backflip/ground-pound follow head pitch/roll rather than leaving the helmet upright.
- [ ] Runtime: print and report accepted Statius calibration values.
- [ ] Runtime: repeat calibration on several different helmet shapes and identify common/default values versus true item exceptions.
- [ ] Runtime: determine whether cap/hair/skull masking is still needed after orientation/transform calibration.
- [ ] Runtime: unequip/swap helmet updates cleanly with no stale model.
- [ ] Runtime: Ctrl+M exit/re-entry leaves no floating helmet or frozen presentation state.

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

Press `F6` while Mario mode is active and a helmet is equipped. V4 freezes the visible Mario pose while calibration is active; the native sidecar itself remains running.

```text
Left / Right   = head-local X -/+
Up / Down      = head-local Y -/+
PageUp/PageDn  = head-local Z +/−
Home / End     = scale -/+
[ / ]          = yaw delta -/+ 5 degrees
Shift          = 5x step size
R              = reset current helmet session values
P              = print current values
F6             = exit calibration/unfreeze pose (values keep applying this session)
```

Normal step sizes: `2.0` Matrix units for position, `0.02` for scale multiplier, `5` degrees for yaw. Hold Shift for `5x` coarse adjustment.

The printed `yawDeltaDeg` is added to the Mario helmet base yaw of `180` degrees. Example: `yawDeltaDeg=-10` means final base yaw correction `170` degrees before animated head delta is applied.

## JVM calibration overrides

```text
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 Matrix units per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final auto-fit multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0 world-Y diagnostic offset
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 180
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

These remain global calibration inputs. The V4 session controller layers per-item visual corrections on top without persistence.

## Evidence

### VERIFIED

- The first revision-830 helmet attachment renders on Mario in the live client.
- V1's helmet fit is visually too small/high and competes with Mario's existing cap/hair presentation.
- V2 Statius's full helm uses `referenceHeadSpan=137.25165`, `targetSpan=150.9768`, `helmetSpan=190.0`, producing `fit=0.7946148`; visually it remains too small/buried.
- Statius's full helm is backwards relative to Mario with the previous `0` degree yaw offset.
- User screenshot after V3 still shows Mario's animated head moving independently while the helmet remains effectively upright/static, requiring full head orientation before meaningful calibration.

### verified-static

- Normal player appearance uses revision-830 item definitions and worn raw models.
- `ItemDefinitions.method7531(...)` builds the worn raw model and applies item recolour/retexture/customization data.
- `Class261.method3572(...)` writes a full 3x3 transform basis, `method3578(...)` applies scale to that basis, and `method3580(...)` adds translation; this supports a full head attachment matrix without Euler-only composition.
- libsm64 binary frames provide final animated geometry plus native state; no native protocol rebuild is required for V4.
- V2 reference fitting uses a broader head candidate region plus trimmed X/Z bounds and explicit per-side clearance before uniform helmet scaling.
- `Class549_Sub1.anIntArray8901` supplies the internal key mappings used by the live calibration controller; the existing alternate-character keyboard wrapper provides the raw held-state delegate and held-key suppression seam.
- V4 session calibration/presentation freeze is presentation-only and does not modify item definitions, equipment state, server authority, cache data, or native simulation timing.

### HYPOTHESIS

- Stable head landmark vertex-stream indices remain semantically consistent enough across libsm64 animation frames to recover a visually stable rigid head basis.
- A representative sample of normal/full/cosmetic helmets will cluster around a reusable Mario helmet scale/seat standard, leaving only unusual silhouettes as per-item overrides.

### UNKNOWN

- Runtime sign/handedness acceptance of the first full 3D head basis during extreme backflip/ground-pound poses.
- The accepted standard Mario scale/X/Y/Z values across multiple 830 helmet families.
- Which helmet families require true per-item or per-category overrides rather than one common profile.
- Whether calibrated helmet placement alone is enough visually or Mario cap/hair/top-skull triangles should be selectively masked beneath equipped helmets.

## Resume Here

**Last completed:**

- Helmet attachment is runtime-proven.
- Statius V2 runtime data proved the generic outer-span fit still shrinks that full helm to `0.7946148` and the helmet faces backwards.
- Helmet V3 live calibration is implemented and uses a 180-degree Mario helmet base yaw.
- Helmet V4 is implemented statically on `main`.
- `MarioHeadOrientationTracker` captures stable head landmark groups and supplies a relative yaw/pitch/roll matrix from final libsm64 geometry.
- `MarioEquipmentAdapter` composes that full head delta with the 180-degree reference helmet orientation and transforms all manual XYZ offsets head-locally.
- F6 calibration freezes Matrix's visible SM64 geometry/state snapshot while the sidecar continues ticking normally; F6 off, mode exit and bridge stop clear the freeze.
- Session values keep applying after F6 exits calibration, but are intentionally not persisted yet.

**Next checklist item:**

1. Pull/build with Eclipse Java 8; no native bridge rebuild.
2. Equip Statius's full helm and enter Mario mode normally before pressing F6.
3. Watch normal idle/head movement for a few seconds: the helmet should follow the head instead of staying upright/static.
4. Press `F6`; confirm Mario's visible body/head pose freezes and the console says `presentation=FROZEN`.
5. Tune with End/Home + local XYZ controls; the target should now remain visually still.
6. Press `P` and send the `[SM64 Equipment Calibration] CURRENT ...` line when Statius looks right.
7. Press `F6` to unfreeze, then turn/jump/backflip/ground-pound and verify full head orientation follows without double yaw or axis inversion.
8. Repeat on several helmet shapes before promoting any scale/seat values to permanent defaults.
9. After transform/orientation acceptance, decide selective cap/hair/skull masking while preserving Mario's face/nose.

**Files:**

- `Client/src/main/java/game/MarioEquipmentAdapter.java`
- `Client/src/main/java/game/MarioHeadOrientationTracker.java`
- `Client/src/main/java/game/MarioHelmetCalibrationController.java`
- `Client/src/main/java/game/Sm64BridgeSession.java`
- `Client/src/main/java/game/AlternateCharacterInputKeyboard.java`
- `Client/src/main/java/game/AlternateCharacterController.java`
- `Client/src/main/java/game/Class578.java`
- `docs/foreign_character_equipment/PROJECT.md`
- `docs/foreign_character_equipment/TESTLIST.md`
- `docs/foreign_character_equipment/patchnotes.txt`
