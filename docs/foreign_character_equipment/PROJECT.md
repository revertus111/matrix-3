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
- While keyboard calibration mode is active, Mario gameplay controls are held idle and calibration keys are hidden from the established Matrix held-key camera seam. The raw keyboard delegate still supplies the calibration controller.
- Session calibration survives toggling calibration off and helmet swaps during the same client session, but is deliberately not gameplay persistence. A representative helmet sample will determine the common Mario defaults before permanent standard/per-item profiles are written.
- Helmet V4 adds `MarioHeadOrientationTracker`. Because the bridge has animated triangles rather than bones, V4 captures stable top/bottom and side landmark groups from the accepted head core and derives a full relative 3D head rotation matrix every frame.
- V4 uses the stable head-vertex centroid for attachment translation and transforms manual XYZ calibration through the same full head matrix, keeping all seating corrections head-local.
- V4 calibration freezes the Matrix-visible SM64 presentation snapshot through `Sm64BridgeSession` while the native worker continues ticking normally. The frozen frame receives a refreshed presentation timestamp so the existing stale-frame fail-open guard remains valid without changing native timing ownership.
- Runtime V4 evidence proved the first direct rigid head delta moved the helmet in the opposite direction from Mario's animated head. Because a rigid rotation's inverse is its transpose, the Matrix equipment seam now consumes `transpose(rawDelta)` instead of adding another arbitrary yaw/sign hack.
- The N64 Mario Equipment Workbench is developer tooling over the existing owners. It exposes scale/head-local XYZ/yaw, presentation freeze, profile copy/save and a live Mario head-cut preview without moving gameplay or renderer authority into Swing.
- Mario head masking is presentation-only: `MarioVisualRenderer` omits selected source triangles before building the Matrix model. Mask changes carry their own revision so they rebuild immediately even on a frozen SM64 frame.
- Current masking is intentionally geometric and tunable: height cutoff + central radius, optionally only while a helmet is equipped. The first target is cap/hair/top-skull removal while keeping Mario's face/moustache/nose visible.
- `Class261.method3572(...)` + `method3578(...)` remain the full 3x3 rotation + uniform-scale path; yaw-only remains the fail-open fallback if a valid animated head basis cannot be recovered.
- Rendering remains fail-open: no helmet, invalid definitions/models/bounds, stale Mario replacement state, or attachment/orientation failure leaves the already-working Mario presentation untouched.

## Current execution state

- Phase: 1 - Attachment foundation
- Bundle: 1.1 - Mario helmet vertical slice / Equipment Workbench
- Status: INVESTIGATION CHECKPOINT — semantic shell replacement pending
- Approval: user supplied `SAP AAA` for the combined N64 Equipment Workbench + Mario head masking + transform-convention fix on 2026-10-04.
- Current objective: implement nose-preserving semantic helmet coverage with a shared fit reference. User explicitly approved this continuation on 2026-10-04; no repeat AAA is needed inside the coherent helmet-fitting bundle. See `NATIVE_HEAD_GEOMETRY.md` for bounded source evidence and exact remaining uncertainty.

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
- [x] Runtime: Statius's full helm faces backwards on Mario with zero-degree base yaw. `VERIFIED` 2026-10-04.
- [x] V3 make `180` degrees the default Mario helmet yaw correction.
- [x] V3 add session-only per-item live scale/X/Y/Z/yaw calibration.
- [x] V3 freeze Mario movement/action input while keyboard calibration is active.
- [x] V3 suppress calibration keys from normal Matrix held-key consumers while preserving raw input for calibration.
- [x] V3 keep session calibration independent per helmet.
- [x] V4 add stable top/bottom/side head landmark groups from the captured head core.
- [x] V4 derive a relative full 3D head rotation matrix from those stable animated landmark groups.
- [x] V4 apply full head orientation to the helmet with yaw-only fallback on invalid basis data.
- [x] V4 transform manual XYZ correction through the same head orientation.
- [x] V4 use stable head centroid rather than axis-aligned bounds center for attachment translation.
- [x] V4 freeze/unfreeze the Matrix-visible SM64 snapshot during calibration without pausing the native worker.
- [x] Runtime: V4 animated head tracking works but the direct relative rotation drives the helmet in the opposite direction. `VERIFIED` by user video 2026-10-04.
- [x] V5 correct Matrix head-delta direction by consuming the inverse rigid rotation (`transpose(rawDelta)`).
- [x] V5 add `N64 -> Mario 64 -> Equipment Workbench` with live scale/head-local XYZ/yaw controls, freeze, reset, 180-degree item flip, copy and save actions.
- [x] V5 add live geometric Mario head masking with enable, helmet-only, height cutoff, radius and reset/preset controls.
- [x] V5 make mask edits rebuild Mario immediately on frozen frames through an explicit mask revision.
- [x] V5 add `docs/n64/TRANSFORM_CONVENTIONS.md` as the persistent anti-backwards source of truth.
- [x] V5 add local markdown profile export to `docs/n64/MARIO_EQUIPMENT_RUNTIME.md` when explicitly requested from the workbench.
- [ ] Runtime: corrected inverse head delta follows Mario in the same direction during idle/turn/jump/backflip/ground-pound.
- [ ] Runtime: Freeze Pose stops visible Mario animation while the native bridge stays healthy.
- [ ] Runtime: workbench scale/XYZ/yaw edits visibly update the helmet without restart or bridge rebuild.
- [ ] Runtime: head-mask controls remove cap/hair/top-skull geometry live while retaining face/moustache/nose.
- [ ] Runtime: helmet-only masking stops cutting Mario immediately when the helmet is removed.
- [ ] Runtime: markdown Save writes the current profile and Copy produces the same snapshot.
- [ ] Runtime: record accepted Statius calibration + mask values.
- [ ] Runtime: repeat calibration on several different helmet shapes and identify common/default values versus true item exceptions.
- [ ] Runtime: unequip/swap helmet updates cleanly with no stale model.
- [ ] Runtime: Ctrl+M exit/re-entry leaves no floating helmet, frozen presentation state or mask regression.

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

Preferred developer workflow is now:

```text
Client Console -> N64 -> Mario 64 -> Equipment Workbench
```

The older F6 keyboard calibrator remains available:

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

Normal keyboard step sizes: `2.0` Matrix units for position, `0.02` for scale multiplier, `5` degrees for yaw. Hold Shift for `5x` coarse adjustment.

The printed/workbench yaw delta is added to the Mario helmet base yaw of `180` degrees.

## Head-mask defaults

```text
mask enabled   = false
helmet only    = true
start height   = 72%
radius         = 40%
```

Lower the start height to remove farther down Mario's head. Increase radius to cut a wider central region. The initial presentation target is cap/hair/top-skull removal while retaining Mario's central face and nose.

## JVM calibration overrides

```text
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 Matrix units per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final auto-fit multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0 world-Y diagnostic offset
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 180
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

These remain global diagnostics/overrides. Use the N64 workbench for normal visual fitting.

## Evidence

### VERIFIED

- The first revision-830 helmet attachment renders on Mario in the live client.
- V1's helmet fit is visually too small/high and competes with Mario's existing cap/hair presentation.
- V2 Statius's full helm uses `referenceHeadSpan=137.25165`, `targetSpan=150.9768`, `helmetSpan=190.0`, producing `fit=0.7946148`; visually it remains too small/buried.
- Statius's full helm is backwards relative to Mario with a zero-degree base yaw.
- User screenshot after V3 showed Mario's animated head moving independently while the helmet remained effectively upright/static, requiring full head orientation before meaningful calibration.
- V4 full 3D tracking follows the head but the first direct relative rotation drives the helmet in the opposite direction. User runtime video 2026-10-04.

### verified-static

- Normal player appearance uses revision-830 item definitions and worn raw models.
- `ItemDefinitions.method7531(...)` builds the worn raw model and applies item recolour/retexture/customization data.
- `Class261.method3572(...)` writes a full 3x3 transform basis, `method3578(...)` applies scale to that basis, and `method3580(...)` adds translation.
- libsm64 binary frames provide final animated geometry plus native state; no native protocol rebuild is required for the workbench/masking bundle.
- A rigid rotation inverse is its transpose; the V5 correction therefore reverses the proven-wrong delta direction without inventing another per-axis sign.
- V2 reference fitting uses a broader head candidate region plus trimmed X/Z bounds and explicit per-side clearance before uniform helmet scaling.
- `MarioVisualRenderer` can omit selected source triangles before tessellation/model conversion while leaving `MarioEquipmentAdapter` on the original unmasked geometry stream.
- Workbench masking is developer-only presentation state and does not mutate item definitions, cache data, server state or native simulation.

### HYPOTHESIS

- Stable head landmark vertex-stream indices remain semantically consistent enough across libsm64 animation frames to recover a visually stable rigid head basis after the delta-direction correction.
- A representative sample of normal/full/cosmetic helmets will cluster around a reusable Mario helmet scale/seat standard, leaving only unusual silhouettes as per-item overrides.
- Superseded: height/radius masking is not accepted as a nose-safe shell replacement. Source inspection found no explicit nose protection; see `NATIVE_HEAD_GEOMETRY.md`.

### UNKNOWN

- The accepted standard Mario scale/X/Y/Z values across multiple 830 helmet families.
- Which helmet families require true per-item or per-category overrides rather than one common profile.
- Whether later full-face helmets need a richer mask classifier than the initial height/radius cut.

## Resume Here

**2026-10-04 approved continuation / investigation checkpoint**

- V6 measured cavity-proxy auto-fit is present in source; earlier checklist omitted it.
- User reports the current full-helmet silhouette remains unacceptable. Do not request another round of tuning the same body-height cut as the architectural solution.
- Read `NATIVE_HEAD_GEOMETRY.md` for inspected paths, native display-list export seam, evidence classifications, implementation bundle and tests.
- Native cap/hair/eye/moustache display-list identity exists before flattening. Nose/skull separation inside mixed face-part geometry remains UNKNOWN.
- Next bounded trace: actual mixed face-part local vertices/connectivity in the pinned upstream source; establish protected nose/face before automatic skull removal. No broader Matrix scan.
- Then implement native metadata, coverage profiles, shared head-local measurement and workbench together. Keep existing controller/render ownership and accepted yaw/transpose conventions.
- Current runtime code is unchanged by this checkpoint. No pull/build/runtime test is needed for documentation alone.
- Approval persists for the coherent helmet-fitting bundle. Remaining issue is evidence, not missing approval.
