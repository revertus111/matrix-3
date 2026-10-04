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
- V1 reads the local player's already-decoded visible appearance and selects the item whose decoded `equipSlot` is helmet slot `0`.
- V1 loads the same revision-830 worn raw model used by normal player appearance through `ItemDefinitions.method7531(...)`, including the current gender/customization object.
- Helmet raw geometry is measured once, normalized around its bounds center, converted to a Matrix `Model`, and cached until renderer/item/gender/customization changes.
- The binary libsm64 bridge publishes final animated geometry rather than a skeleton. V1 therefore captures the vertex-stream indices belonging to Mario's head from the first upright frame, then follows those same animated vertices on later frames.
- Helmet scale is calculated from live Mario head horizontal span versus the cached helmet worn-model horizontal span. Uniform scale is used so item proportions are preserved.
- Helmet translation follows the animated head anchor. Yaw follows native `faceAngle` with calibration overrides; full head pitch/roll is intentionally deferred until the helmet proof is accepted.
- Rendering is fail-open: no helmet, invalid definitions/models/bounds, stale Mario replacement state, or attachment failure leaves the already-working Mario presentation untouched.

## Current execution state

- Phase: 1 - Attachment foundation
- Bundle: 1.1 - Mario helmet vertical slice
- Status: NEEDS TEST
- Approval: user supplied `AAA` on 2026-10-04.
- Current objective: prove one real revision-830 helmet can auto-fit to Mario and follow his animated head without changing libsm64, the RuneScape equipment system, or Mario's existing body renderer.

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
- [x] Auto-fit the cached helmet uniformly from Mario-head span / helmet-model span.
- [x] Apply native facing yaw and render after successful Mario body replacement.
- [x] Keep all failures isolated/fail-open to the working Mario body.
- [ ] Runtime: equipped helmet visibly appears on Mario.
- [ ] Runtime: helmet size and vertical placement are sensible.
- [ ] Runtime: helmet yaw matches Mario facing while turning/running.
- [ ] Runtime: head translation remains attached through jump/backflip/ground-pound.
- [ ] Runtime: determine whether full pitch/roll attachment is required for flips.
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

V1 runtime overrides:

```text
-Dmatrix3.sm64.helmetFitPadding=<positive-float>      # default 1.12
-Dmatrix3.sm64.helmetVerticalOffset=<float>          # default 0
-Dmatrix3.sm64.helmetYawOffsetDegrees=<float>        # default 0
-Dmatrix3.sm64.helmetYawFlip=true|false              # default false
```

These are calibration controls, not separate equipment definitions.

## Evidence

### verified-static

- Normal player appearance uses revision-830 item definitions and worn raw models.
- `ItemDefinitions.method7531(...)` builds the worn raw model and applies item recolour/retexture/customization data.
- `Class261` supports runtime scale, rotation and translation transforms, so cached helmet geometry does not need per-frame rebuilding.
- libsm64 binary frames already provide animated geometry plus native `faceAngle`; no protocol/native rebuild is required for V1.

### HYPOTHESIS

- First-frame head-region capture is stable across libsm64's geometry stream for later animation frames.
- Default helmet fit padding `1.12` is visually close enough for the first proof.
- Native `faceAngle` sign/zero matches the revision-830 worn-model forward axis closely enough with the provided yaw calibration controls.

### UNKNOWN

- Whether yaw-only orientation looks acceptable during Mario's full-body flips or the next slice must derive pitch/roll from stable head/torso geometry indices.
- Whether unusual helmets with large horns/plumes need per-item fit metadata beyond generic bounds fitting.

## Resume Here

**Last completed:**

- Helmet V1 is implemented statically on `main`.
- `MarioEquipmentAdapter` loads/caches the visible 830 worn helmet, captures/follows Mario head geometry, auto-fits it, and renders it after the successful Mario body pass.
- `Class578.method6834(...)` now invokes the equipment adapter immediately after `MarioVisualRenderer.render(...)`.

**Next checklist item:**

1. Pull/build with Eclipse Java 8.
2. Equip a visible normal helmet before entering Mario mode.
3. Ctrl+M and confirm `[SM64 Equipment] Captured Mario HEAD...` followed by `[SM64 Equipment] Helmet ACTIVE...`.
4. Inspect size/height/facing at idle and while turning/running.
5. Jump/backflip/ground-pound and check whether the helmet stays on the head; note that V1 follows head translation and yaw, while full pitch/roll is intentionally still a runtime decision.
6. Unequip/swap the helmet and verify no stale helmet remains.
7. Ctrl+M out/in and verify no floating/stale attachment.

**Files:**

- `Client/src/main/java/game/MarioEquipmentAdapter.java`
- `Client/src/main/java/game/Class578.java`
- `docs/foreign_character_equipment/PROJECT.md`
- `docs/foreign_character_equipment/TESTLIST.md`
- `docs/foreign_character_equipment/patchnotes.txt`
