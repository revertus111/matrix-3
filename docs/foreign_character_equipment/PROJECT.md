# Foreign Character Equipment Adapter

## Goal

Allow Matrix3/revision-830 equipment to fit imported characters such as Mario while preserving Matrix3 item/model authority and the imported character's animation/presentation authority.

Current proof target: Mario helmets.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Foreign-character attachment framework | 🔵 In Progress |
| Mario helmet proof | 🔵 In Progress — semantic coverage needs runtime acceptance |
| Multi-slot equipment attachments | ❌ Not started |
| Torso/leg fitting and deformation | ❌ Not started |
| Additional foreign-character profiles | ❌ Not started |

## Architecture / ownership

- Matrix3 item definitions and `ItemDefinitions.method7531(...)` remain worn-model authority.
- `MarioVisualRenderer` remains Mario presentation authority.
- `MarioEquipmentAdapter` remains helmet attachment/render authority.
- `MarioHeadOrientationTracker` remains animated head-orientation owner; keep the runtime-proven inverse/transpose delta convention.
- `MarioHelmetCalibrationController` remains session calibration owner.
- `MarioEquipmentWorkbench` is developer UI/facade only.
- `Sm64BridgeSession` owns the Java/native protocol and immutable native frame publication.
- No second renderer, controller, equipment definition path, or server authority is introduced.

## Current semantic helmet architecture

Protocol v2 now preserves information libsm64 previously discarded while flattening Mario:

- stable semantic part id per native triangle,
- original display-list-local triangle positions,
- flattened world/animated positions/colors/UVs remain unchanged.

Stable part ids:

```text
0 UNKNOWN
1 FACE             protected
2 EYES             protected
3 MOUSTACHE        protected
4 CAP              removable
5 HAIR_SIDEBURN    removable
6 HAIR_BACK        removable
```

`FULL_HELM_SAFE` hides only CAP + HAIR_SIDEBURN + HAIR_BACK. FACE/EYES/MOUSTACHE are never removed by that profile. Mario's nose remains inside the protected FACE mesh.

Unknown triangles are always preserved.

Protocol v1 remains readable as a fail-open legacy path, but semantic coverage is unavailable until the native bridge is rebuilt.

## Shared fit reference

V7 replaces the pose-mismatch between adapter and auto-fit when protocol v2 is present:

- both use `MarioSemanticGeometry`;
- the reference is measured from original display-list-local FACE geometry;
- source evidence establishes local Z as face left/right and local +Y as face-out/front;
- baseline helmet width uses FACE left/right width, so Mario's forward-projecting nose does not inflate helmet scale;
- uniform scale remains required;
- 830 helmet outer bounds/cavity fractions remain an approximation, not a verified inner cavity;
- manual workbench calibration remains final visual authority.

Legacy protocol v1 keeps the old animated broad-head measurement as fallback only.

## Current execution state

- Phase: 1 — Attachment foundation
- Bundle: 1.1 — Mario helmet semantic shell replacement
- Status: **NEEDS BUILD + ONE RUNTIME TEST**
- Approval: current coherent bundle approved by user with `SAP AAA` on 2026-10-04.

### Completed in this bundle

- [x] Bounded native source trace; no wider Matrix rescan.
- [x] Verified named SM64 face/cap/hair/eye/moustache display-list identity before flattening.
- [x] Keep entire mixed FACE mesh protected because nose/skull subdivision remains unsafe.
- [x] Pin libsm64 dependency revision in `native/sm64-bridge/Makefile`.
- [x] Add tracked libsm64 semantic-geometry patch.
- [x] Add binary protocol v2 semantic part ids + local triangle positions.
- [x] Keep Java protocol-v1 compatibility.
- [x] Preserve semantic metadata through presentation freeze.
- [x] Add shared semantic FACE reference for adapter + auto-fit.
- [x] Add `KEEP_ALL` and nose-safe `FULL_HELM_SAFE` coverage profiles.
- [x] Keep legacy geometric cutter only as explicit v1 fallback/debug.
- [x] Update N64 Equipment Workbench with protocol/semantic status, part counts, shared FACE W/H/D and coverage controls.

### Runtime acceptance pending

- [ ] Native `make bootstrap` succeeds against pinned libsm64 and applies the tracked patch cleanly.
- [ ] Eclipse Java 8 clean/build succeeds.
- [ ] Bridge reports semantic protocol v2.
- [ ] Workbench reports semantic geometry AVAILABLE and non-zero native part counts.
- [ ] Statius + `FULL_HELM_SAFE` removes Mario cap/hair while FACE/EYES/MOUSTACHE/nose remain.
- [ ] Helmet baseline reports `fitReference=semantic-face` and auto-fit reports `reference=semantic-face`.
- [ ] Helmet follows idle/turn/jump/backflip/ground-pound using accepted head transform convention.
- [ ] Unequip/Keep All restores Mario parts immediately with no stale model.
- [ ] Ctrl+M exit/re-entry leaves no floating helmet, frozen pose or coverage state corruption.

## Evidence

### VERIFIED

- Real revision-830 helmets render on Mario.
- V2 Statius outer-span fit produced `0.7946148` and looked wrong.
- V4 full 3D head tracking moved in the inverse direction; transpose correction is retained.
- Full-head containment produces an unacceptable oversized/egg-like silhouette.

### verified-static

- libsm64 receives display-list identity and original local vertices before flattening triangles.
- Mario source contains distinct cap, back-hair, sideburn, eyes, moustache and mixed face display lists.
- No independently named nose display list exists in the inspected source.
- Pinned face geometry shows Z symmetry across left/right; eyes/moustache/front projection establish +Y as face-out in that local mesh.
- FACE is therefore protected as a whole in the first semantic coverage implementation.

### HYPOTHESIS

- Removing cap + named hair while protecting the complete FACE mesh will remove enough silhouette bulk for full helmets to look intentional with only small transform corrections.
- Several helmet families will share a common Mario fit/coverage category, leaving unusual cosmetics as overrides.

### UNKNOWN

- Whether side/back skull geometry remaining inside FACE must later be subdivided.
- Accepted Mario transform defaults across multiple helmet families.
- Final automatic item-to-coverage-category classifier.

## Later phases

### Bundle 1.2 — Reusable attachment profiles

- [ ] Promote accepted Mario helmet defaults/categories.
- [ ] Optional per-item corrections for true exceptions.
- [ ] Add semantic sockets for neck/back/hands/feet.

### Phase 2 — Multi-slot equipment

- [ ] Amulet
- [ ] Cape
- [ ] Weapon/shield
- [ ] Gloves
- [ ] Boots

### Phase 3 — Full-body fitting

- [ ] Chest/body replacement strategy
- [ ] Legs/hip replacement strategy

### Phase 4 — Additional foreign characters

- [ ] Reuse generic adapter with character-specific semantic profiles.

## Resume Here

**Last completed:** semantic protocol/coverage/shared-fit implementation is on `main`.

**Do not rescan:** Matrix scene/controller/cache architecture; equipment/render owners are established.

**Next action:**

1. Rebuild native bridge from `native/sm64-bridge` with `make bootstrap`.
2. Eclipse Java 8 clean/build.
3. Launch once, equip Statius, Ctrl+M.
4. Open `N64 -> Mario 64 -> Equipment Workbench`.
5. Confirm protocol v2 / Semantic Geometry AVAILABLE.
6. Press `Full helm safe`.
7. Judge the actual silhouette and nose/face preservation before any more geometry subdivision.
8. Only after this test decide whether mixed FACE needs a second native semantic split.
