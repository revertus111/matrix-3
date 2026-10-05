# Foreign Character Equipment Adapter

## Goal

Allow Matrix3/revision-830 equipment to visually replace or augment imported-character body regions while preserving Matrix3 item/model authority and the imported character's animation/presentation authority.

Current proof target: Mario helmets as replacement head shells.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Foreign-character attachment framework | 🔵 In Progress |
| Mario helmet proof | 🔵 In Progress — replacement-shell runtime acceptance pending |
| Multi-slot equipment attachments | ❌ Not started |
| Torso/hand/foot body-part replacement | ❌ Not started |
| Additional foreign-character profiles | ❌ Not started |

## Architecture / ownership

- Matrix3 item definitions and `ItemDefinitions.method7531(...)` remain worn-model authority.
- `MarioVisualRenderer` remains Mario presentation authority.
- `MarioEquipmentAdapter` remains helmet attachment/render authority.
- `MarioHeadOrientationTracker` remains animated head-orientation owner; keep the runtime-proven inverse/transpose delta convention.
- `MarioHelmetCalibrationController` remains session helmet calibration owner.
- `MarioEquipmentWorkbench` is developer UI/facade and semantic replacement-policy owner only.
- `Sm64BridgeSession` owns Java/native protocol and immutable native frame publication.
- No second controller, equipment definition path, or server authority is introduced.

## Semantic geometry foundation

Protocol v2 preserves information libsm64 previously discarded while flattening Mario:

- stable semantic part id per native triangle,
- original display-list-local triangle positions,
- flattened animated positions/colors/UVs remain unchanged.

Stable part ids:

```text
0 UNKNOWN
1 FACE
2 EYES
3 MOUSTACHE
4 CAP
5 HAIR_SIDEBURN
6 HAIR_BACK
```

UNKNOWN is always preserved/fail-open.

Source evidence establishes face-local Z as left/right and local +Y as face-out/front. No independent nose display list exists; Mario's nose is inside the mixed FACE geometry.

## Replacement-shell direction — V8

The active design no longer requires RuneScape helmets to physically contain Mario's complete cartoon head.

The equipment model can replace the body region it visually occupies:

```text
Mario animation / pose
        ↓
semantic body region
        ↓
hide replaced Mario geometry
        ↓
render revision-830 equipment model at that socket
```

Helmet profiles now include:

### `KEEP_ALL`

No Mario head geometry is removed.

### `FULL_HELM_SAFE`

Legacy semantic-overlay proof: hides CAP + HAIR_SIDEBURN + HAIR_BACK while retaining the whole FACE/EYES/MOUSTACHE set.

### `HEAD_SHELL_CLOSED`

Every known semantic Mario head part is removed:

- FACE
- EYES
- MOUSTACHE
- CAP
- HAIR_SIDEBURN
- HAIR_BACK

The revision-830 helmet becomes the complete visible head shell.

### `HEAD_SHELL_FACE`

The helmet remains the head shell, but Mario identity is inserted through its opening:

- CAP / HAIR are removed,
- EYES and MOUSTACHE remain,
- only the front slice of mixed FACE remains,
- rear/side FACE triangles are removed using source-verified face-local +Y,
- a workbench `Mario FACE front slice %` control tunes how much of the mixed FACE survives.

The first V8 proof deliberately leaves Mario's surviving face at its native animated scale/location. Independent face scale/XYZ is deferred until this runtime test proves it is actually needed.

## Replacement-shell fit rule

Replacement shell is **not** a helmet-cavity problem.

When either replacement-shell profile is selected:

- the active helmet auto-fit is reset/re-resolved,
- `MarioHelmetAutoFit` bypasses the old 0.72/0.82 cavity proxy,
- the existing adapter outer-silhouette baseline remains the starting helmet scale,
- auto-fit manual multiplier starts at `1.0`,
- normal workbench scale/X/Y/Z/yaw remains available for visual seating.

This prevents the previous cavity logic from inflating a shell that no longer needs to contain Mario's hidden skull.

## Semantic FACE unit correction

Runtime V7 proved raw display-list-local FACE width `538` was incorrectly compared directly to Matrix helmet bounds while the posed Mario head span was about `137`.

`MarioSemanticGeometry` now derives the display-list-local -> posed-native scale from corresponding FACE triangle edge-length ratios, then applies the established Matrix Mario model scale. Semantic W/H/D should therefore be in Matrix-space scale rather than raw source units.

## Current execution state

- Phase: 1 — Attachment foundation
- Bundle: 1.1 — Mario replacement-shell helmet proof
- Status: **NEEDS ECLIPSE BUILD + ONE RUNTIME VISUAL TEST**
- Approval: user supplied `SAP AAA` for the replacement-shell continuation on 2026-10-04.
- Native rebuild: **NOT required for V8** if semantic protocol v2 is already built/running.

### Completed

- [x] Real revision-830 helmets render on Mario.
- [x] Animated head attachment + accepted inverse/transpose orientation correction.
- [x] Protocol-v2 semantic part IDs and local triangle positions.
- [x] Windows semantic bridge bootstrap/runtime verified.
- [x] Semantic FACE unit mismatch diagnosed and Java-side correction added.
- [x] `KEEP_ALL` and `FULL_HELM_SAFE` profiles.
- [x] `HEAD_SHELL_CLOSED` replacement profile.
- [x] `HEAD_SHELL_FACE` front-face insert profile.
- [x] Live face-front-slice percentage in N64 Equipment Workbench.
- [x] Replacement-shell mode bypasses cavity-proxy inflation and re-resolves auto-fit.
- [x] Profile export records replacement mode and face-slice diagnostics.

### Runtime acceptance pending

- [ ] Eclipse Java 8 clean/build succeeds.
- [ ] Semantic FACE width is in the expected Matrix-space neighborhood instead of raw `538`.
- [ ] `HEAD_SHELL_CLOSED` visibly removes Mario's original head while the 830 helmet remains attached.
- [ ] `HEAD_SHELL_FACE` restores a recognizable Mario face/eyes/moustache inside the helmet opening without restoring the full skull/cap/hair.
- [ ] Adjusting `Mario FACE front slice %` changes cheek/side-face retention live while frozen.
- [ ] Replacement-shell auto-fit log reports `fitMode=replacement-shell` and `manualScale=1.0` after entering shell mode/reset.
- [ ] Helmet transform controls remain usable for small shell seating corrections.
- [ ] Idle/turn/jump/backflip/ground-pound retain attachment.
- [ ] Unequip / Keep All restores Mario geometry with no stale mask.
- [ ] Ctrl+M exit/re-entry leaves no floating shell, frozen pose, or stale replacement state.

## Evidence

### VERIFIED

- Real revision-830 helmets render on Mario.
- V2 Statius outer-span fit `0.7946148` looked wrong.
- V4 direct head delta moved opposite; inverse/transpose correction is retained.
- Full-head containment can create an unacceptable oversized/egg silhouette.
- Protocol-v2 semantic bridge rebuilt/launched on Windows.
- First semantic fit used raw FACE W/H/D `538/320/530`, causing oversized 2x-8x helmet fits across multiple items.

### verified-static

- libsm64 has distinct face/cap/hair/eyes/moustache identity before flattening.
- local +Y is face-out for the mixed FACE source geometry.
- replacement-shell profiles operate only on known semantic head IDs; UNKNOWN remains preserved.
- shell mode's outer-silhouette baseline is the adapter's existing base scale and does not require an estimated inner cavity.

### HYPOTHESIS

- `HEAD_SHELL_CLOSED` will make closed/full-face helmets look intentional even when Mario's proportions are incompatible with the original wearable cavity.
- `HEAD_SHELL_FACE` will preserve enough Mario identity to look like Mario wearing the shell without needing independent face scaling.
- The default FACE front slice `55%` is only a starting value and requires visual acceptance.

### UNKNOWN

- Whether Mario face scale/XYZ needs a separate transform after the first face-insert test.
- Whether some helmet openings need per-family FACE-front percentages.
- Which 830 helmet families should default to closed shell vs face shell vs overlay.
- How much additional semantic body-part tagging is needed for torso/hands/feet.

## Later phases

### Bundle 1.2 — Reusable replacement profiles

- [ ] Promote accepted helmet family defaults.
- [ ] Add optional per-item corrections only for true exceptions.
- [ ] Define generic foreign-character replacement regions/sockets.

### Phase 2 — Multi-slot equipment

- [ ] Amulet / cape attachment.
- [ ] Weapon / shield hand sockets.
- [ ] Gloves -> hand replacement.
- [ ] Boots -> foot replacement.

### Phase 3 — Full-body replacement

- [ ] Platebody -> torso/shoulder replacement strategy.
- [ ] Platelegs -> hip/leg replacement strategy.
- [ ] Determine where rigid segmented shells are sufficient versus actual deformation/skinning.

### Phase 4 — Additional foreign characters

- [ ] Reuse generic equipment-replacement adapter with character-specific semantic profiles.

## Resume Here

**Last completed:** V8 replacement-shell Java/workbench implementation is on `main`.

**Do not rescan:** Mario controller, Matrix scene, item-definition path, semantic bridge protocol, or helmet attachment ownership. Those are established.

**Next action — one test session:**

1. `git pull origin main`.
2. Eclipse Java 8 clean/build. **Do not rebuild native bridge.**
3. Launch, equip Statius (or the helmet with the visible face opening), Ctrl+M.
4. Open `N64 -> Mario 64 -> Equipment Workbench` and Freeze Pose.
5. Confirm semantic FACE W/H/D is no longer raw `538/...` scale.
6. Press `Head shell closed`; inspect the pure 830 helmet shell on Mario's animated body.
7. Press `Head shell + Mario face`; inspect Mario face/eyes/moustache in the helmet opening.
8. Adjust `Mario FACE front slice %` only if cheeks/skull remain or too much face disappears.
9. Use helmet Scale/XYZ only for small shell seating corrections.
10. Send screenshot + the `[SM64 Equipment AutoFit]` and `[SM64 Equipment] Helmet ACTIVE` lines.

Only after that visual evidence decide whether independent Mario face scale/XYZ is necessary.
