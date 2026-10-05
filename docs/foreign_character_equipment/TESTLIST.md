# Foreign Character Equipment Runtime Test List

## Mario helmet semantic coverage bundle

Run this as **one client session**. The semantic FACE unit correction is Java-only; if the protocol-v2 bridge was already rebuilt successfully, do **not** rebuild it again for this correction.

### Build

1. [ ] `git pull origin main`.
2. [ ] If protocol-v2 bridge has never been built on this checkout, run `make bootstrap` from `native/sm64-bridge`; otherwise keep the already-built semantic bridge.
3. [ ] Eclipse clean/build succeeds under Java 8.
4. [ ] Client launches/login succeeds normally.

### Protocol / metadata

1. [ ] Enter Mario mode with Ctrl+M.
2. [ ] Native stderr includes `semantic-geometry-v2`.
3. [ ] Java console reports `Persistent session READY (30 Hz + semantic geometry v2)`.
4. [ ] `N64 -> Mario 64 -> Equipment Workbench` reports `Bridge protocol = v2`.
5. [ ] `Semantic geometry = AVAILABLE`.
6. [ ] Native part counts show non-zero FACE and expected head-part counts; unknown/other may remain non-zero because only helmet-relevant parts are tagged.
7. [ ] Shared FACE W/H/D displays finite values.
8. [ ] Shared FACE width is in the same Matrix-space neighborhood as the established ~137-unit Mario head span, not the previous raw-local `538` value.
9. [ ] Helmet log reports `fitReference=semantic-face`.
10. [ ] Auto-fit log reports `reference=semantic-face`.

### Nose-safe full-helm coverage

1. [ ] Equip Statius's full helm.
2. [ ] With coverage `KEEP_ALL`, Mario renders his complete original head beneath the helmet.
3. [ ] Press `Full helm safe`.
4. [ ] CAP triangles disappear.
5. [ ] HAIR_SIDEBURN / HAIR_BACK triangles disappear where present.
6. [ ] FACE remains visible.
7. [ ] EYES remain visible.
8. [ ] MOUSTACHE remains visible.
9. [ ] Mario's iconic nose remains visible because the entire mixed FACE mesh is protected.
10. [ ] Masked source triangle count becomes greater than zero.
11. [ ] Press `Keep all Mario head parts`; removed geometry returns immediately, including on a frozen presentation frame.
12. [ ] Re-enable `Full helm safe`, unequip the helmet; helmet-only coverage stops immediately.
13. [ ] Re-equip and coverage resumes without stale geometry.

### Fit / transform

1. [ ] Initial Statius scale is materially closer to useful than the raw-local semantic result that produced `fit=5.20218`.
2. [ ] Semantic FACE width is converted from display-list-local units through the native posed-head scale before Matrix helmet fitting.
3. [ ] Nose depth does not force the helmet to scale larger; semantic fit width comes from FACE left/right span.
4. [ ] Freeze Pose works while bridge sequencing remains healthy.
5. [ ] Scale/X/Y/Z/yaw controls make small final corrections without rebuilding the helmet model.
6. [ ] Idle/turn motion keeps helmet attached in the accepted direction.
7. [ ] Jump/backflip/ground-pound preserve head attachment/orientation.
8. [ ] Reset/Recalc fit re-enables the mathematical baseline for the active helmet.

### Legacy compatibility / fail-open

- [ ] If an old protocol-v1 bridge is intentionally used, Mario still renders and Java reports semantic metadata unavailable rather than failing the session.
- [ ] Semantic coverage controls do not remove geometry on v1 because part ids do not exist.
- [ ] Legacy geometric cut remains an explicit debug fallback only.
- [ ] Unknown semantic ids are preserved, never hidden.
- [ ] Invalid semantic reference falls back to the established legacy fit path.
- [ ] Ctrl+M exit/re-entry leaves no floating helmet, frozen pose or stale coverage.
- [ ] Normal RuneScape appearance is unchanged outside Mario mode.

## Runtime evidence already established

- [x] Real revision-830 helmet renders on Mario.
- [x] Old Statius fit logged `referenceHeadSpan=137.25165`, `targetSpan=150.9768`, `helmetSpan=190.0`, `fit=0.7946148` and looked wrong.
- [x] Full-head containment creates an unacceptable oversized silhouette.
- [x] First V4 head delta tracked in the opposite direction; inverse/transpose correction is retained.
- [x] Protocol-v2 semantic bridge rebuilt and launched successfully on Windows.
- [x] First semantic fit exposed a unit mismatch: raw FACE local W/H/D `538/320/530` versus legacy posed head span about `137`, causing Statius `fit=5.20218` and similarly oversized helmets across multiple items.
