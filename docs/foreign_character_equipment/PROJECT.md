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
- Helmet translation follows the animated head anchor. Yaw follows native `faceAngle` with calibration overrides; full head pitch/roll is intentionally deferred until the helmet proof is accepted.
- Rendering is fail-open: no helmet, invalid definitions/models/bounds, stale Mario replacement state, or attachment failure leaves the already-working Mario presentation untouched.
- Mario body masking is deliberately not part of V2. If the correctly sized clearance shell still visually fights Mario's cap/hair, selective cap/hair/skull masking becomes the next presentation slice while preserving Mario's face/nose.

## Current execution state

- Phase: 1 - Attachment foundation
- Bundle: 1.1 - Mario helmet vertical slice
- Status: NEEDS TEST
- Approval: user supplied `SAP AAA` for Helmet V2 envelope fitting on 2026-10-04.
- Current objective: prove the revised full-head envelope + explicit-clearance fit produces a helmet that surrounds Mario's skull instead of sizing against the narrow cap/crown region, without changing libsm64, RuneScape equipment authority, or Mario's body renderer.

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
- [ ] Runtime: V2 helmet size and vertical placement are sensible.
- [ ] Runtime: V2 shell clears the head with little/no obvious skull clipping at idle.
- [ ] Runtime: helmet yaw matches Mario facing while turning/running.
- [ ] Runtime: head translation remains attached through jump/backflip/ground-pound.
- [ ] Runtime: determine whether full pitch/roll attachment is required for flips.
- [ ] Runtime: determine whether cap/hair/skull masking is still needed after envelope fitting.
- [ ] Runtime: unequip/swap helmet updates cleanly with no stale model.
- [ ] Runtime: Ctrl+M exit/re-entry leaves no floating helmet.

### Bundle 1.2 - Attachment profile foundation

**Status:** PLANNED AFTER 1.1

- [ ] Promote helmet-specific measurements into reusable attachment-slot/profile structures.
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

## Calibration

Helmet V2 runtime overrides:

```text
-Dmatrix3.sm64.helmetClearanceFraction=<non-negative-float>  # default 0.05 per side
-Dmatrix3.sm64.helmetMinClearance=<non-negative-float>       # default 2.0 Matrix units per side
-Dmatrix3.sm64.helmetFitPadding=<positive-float>             # default 1.0 final multiplier
-Dmatrix3.sm64.helmetVerticalOffset=<float>                  # default 0
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>                # default 0
-Dmatrix3.sm64.helmetYawFlip=true|false                      # default false
```

`helmetFitPadding` remains only as a final calibration multiplier. V2's normal anti-clipping space comes from the explicit clearance shell instead of a hidden `1.12` padding default.

## Evidence

### VERIFIED

- The first revision-830 helmet attachment renders on Mario in the live client.
- V1's helmet fit is visually too small/high and competes with Mario's existing cap/hair presentation.

### verified-static

- Normal player appearance uses revision-830 item definitions and worn raw models.
- `ItemDefinitions.method7531(...)` builds the worn raw model and applies item recolour/retexture/customization data.
- `Class261` supports runtime scale, rotation and translation transforms, so cached helmet geometry does not need per-frame rebuilding.
- libsm64 binary frames already provide animated geometry plus native `faceAngle`; no protocol/native rebuild is required for V1/V2.
- V2 reference fitting uses a broader head candidate region plus trimmed X/Z bounds and explicit per-side clearance before uniform helmet scaling.

### HYPOTHESIS

- First-frame head-region capture remains stable across libsm64's geometry stream for later animation frames.
- Default `5%` per-side clearance with a `2.0` minimum gives enough anti-clipping room without making normal helmets look oversized.
- Trimming `6%` of X/Z candidate extremes removes nose-like protrusions without cutting meaningful skull width/depth from the fit envelope.
- Native `faceAngle` sign/zero matches the revision-830 worn-model forward axis closely enough with the provided yaw calibration controls.

### UNKNOWN

- Whether V2 envelope fitting alone is enough visually or Mario cap/hair/top-skull triangles should be selectively masked beneath equipped helmets.
- Whether yaw-only orientation looks acceptable during Mario's full-body flips or the next slice must derive pitch/roll from stable head/torso geometry indices.
- Whether unusual helmets with large horns/plumes need per-item fit metadata beyond generic bounds fitting.

## Resume Here

**Last completed:**

- Helmet V1 is runtime-proven to render on Mario but is visually undersized/high in the user's screenshots.
- Helmet V2 is implemented statically on `main`.
- V2 captures a broader head candidate region, trims X/Z extremes so Mario's nose does not drive sizing, records a stable reference skull span, and expands it with explicit per-side clearance before uniform helmet scaling.
- V2 does not yet hide Mario cap/hair/skull geometry; that decision is intentionally deferred until the corrected shell fit is seen at runtime.

**Next checklist item:**

1. Pull/build with Eclipse Java 8.
2. Equip the same visible helmet used in the V1 screenshot and enter Mario mode.
3. Confirm `[SM64 Equipment] Captured Mario HEAD envelope ... referenceSpan=...`.
4. Confirm `[SM64 Equipment] Helmet ACTIVE ... referenceHeadSpan=... clearance=... targetSpan=...` and verify `targetSpan > referenceHeadSpan`.
5. Inspect front/side if possible: helmet should be materially larger than V1 and should clear the skull rather than balancing on the cap crown.
6. Turn/run and verify yaw/centering.
7. Jump/backflip/ground-pound and verify attachment translation.
8. If size is now correct but Mario cap/hair still visibly collide, advance to selective Mario head-region masking while preserving the face/nose.

**Files:**

- `Client/src/main/java/game/MarioEquipmentAdapter.java`
- `Client/src/main/java/game/Class578.java`
- `docs/foreign_character_equipment/PROJECT.md`
- `docs/foreign_character_equipment/TESTLIST.md`
- `docs/foreign_character_equipment/patchnotes.txt`
