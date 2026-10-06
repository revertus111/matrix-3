# Twilight Princess Link in Matrix3

## Goal

Make authentic Twilight Princess Link a fully playable Matrix3 revision-830 character while Matrix3 remains the host world/gameplay authority. Reuse TP Link's real skeleton and authentic animation data, then layer RuneScape stats, equipment, NPC combat and progression onto the action presentation.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| TP source/decomp bootstrap | ✅ VERIFIED |
| TP local model + native animation/socket proof | ✅ VERIFIED |
| Matrix3 TP Link presentation | ⚠️ PARTIAL VERIFIED - render/height pass; proportion + motion acceptance pending |
| Matrix3 movement/controller integration | ⚠️ IMPLEMENTED / NEEDS RUNTIME TEST |
| Zelda action combat + RuneScape gameplay authority | ⚠️ FIRST MELEE SLICE IMPLEMENTED / NEEDS RUNTIME TEST |
| RuneScape equipment adaptation | ❌ Not started for TP |
| Matrix-native TP texture/material path | ❌ Not started |
| Proper smoothing/normals path | ❌ Not started |
| Reusable custom humanoid player framework | ❌ Not started |

## Scope / ownership

- **Matrix3 authority:** input mode, camera-relative movement, RuneScape world/collision, networking/server validation, NPC targeting, stats, accuracy, damage, cooldowns, XP and final gameplay rules.
- **TP donor authority:** authentic Link skeleton/resource IDs, BCK animation data, weapon/item sockets and presentation timing.
- **Asset boundary:** user-owned `GZ2E01` image and extracted/converted Nintendo assets stay outside Git under `%LOCALAPPDATA%`.
- **Runtime asset boundary:** Matrix3 loads `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk`; no TP native sidecar is required for the current Java controller.
- **Movement owner:** `AlternateCharacterController` remains the only imported-character WASD/camera-relative owner. TP must not create a competing movement stack.
- **Combat owner:** `AlternateCharacterCombatBridge` remains the shared imported-character target/manual-melee owner. TP supplies authentic sword presentation/contact timing only.
- **Workbench owner:** `TpLinkWorkbench` stores developer-session tuning values; it does not own server/world state.

## Donor source pin

- Source: `zeldaret/tp@c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0`
- Target: `GZ2E01` - GameCube North America
- Donor workspace: `%LOCALAPPDATA%\Matrix3\TPDecomp`
- Local converter: `%LOCALAPPDATA%\Matrix3\TPLinkTools\demake-engine`
- Converter pin: `snuri00/demake-engine@a134ff49cc74585c6b11f881293796e45c973c75`
- Matrix runtime DMK: `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk`

## Verified foundation

### VERIFIED

- Bundle 1.1 donor bootstrap completes on the user's PC using a supported `.ciso` donor.
- `CHECK config\GZ2E01\build.sha1` reported `758 files OK`; donor report showed all code/data `100.00% matched` and overall linking `87.13%` (`2583 / 2608`).
- Authentic extraction selected `al.bmd` with a 35-joint skeleton and real `waitb.bck`, `dasha.bck`, `cutl.bck` clips.
- Bundle 1.2 corrected GameCube left-side proof was explicitly accepted: authentic TP Link, authentic idle/walk/sword animation, cyan `0x9 handL`, red `0xA weaponL`, and `al_swb.bmd` rigidly following `0xA weaponL` during the sword animation.
- First Matrix3 runtime render loaded the local DMK and rendered TP Link in the revision-830 world: `19821` vertices, `6607` triangles, `35` joints, `30` idle frames, `24` walk frames.
- World scale `1.0` failed as dramatically undersized.
- Follow-up screenshots at world scale `5.0` were explicitly accepted for overall player height.

### verified-static

- Human Link uses left `0x9 handL` / `0xA weaponL` and right `0xE handR` / `0xF weaponR`; corrected GZ2E01 sword proof uses the left pair.
- The local DMK contains named `idle`, `walk`, and `sword` animation chunks.
- `TpLinkVisualRenderer` evaluates DMK skin matrices and renders a Matrix `Model` through the established scene seam.
- Local-player suppression remains fail-open and only suppresses the normal local RS body after a fresh successful TP render.
- `Ctrl+Shift+L` toggles `TP_LINK`; the whole Ctrl+L family is latched until release, preserving the Shift-first regression fix.
- Accepted world scale remains uniformly `5.0`; model width/height/depth fitting happens in TP model space before player yaw. Initial RS-fit envelope: `0.92 / 1.00 / 0.95`.
- Current material path samples DMK UV/palette colour into Matrix face colours. Full Matrix texture/material binding is not implemented.
- Current DMK geometry is triangle-expanded; correct smoothing still needs imported normals or a seam-aware weld.

## Development plan

### Phase 1 - Donor bootstrap and asset proof

**Status:** DONE / VERIFIED

#### Bundle 1.1 - One-click donor bootstrap

**Status:** DONE / VERIFIED

- [x] Pin donor source/target.
- [x] Build the verified GZ2E01 donor workspace.
- [x] Repair Python/Ninja/object-base runtime failures.

#### Bundle 1.2 - Link extraction + animation/socket proof

**Status:** DONE / VERIFIED

- [x] Deterministic Kmdl/AlAnm extraction.
- [x] Authentic idle/walk/sword BCK selection.
- [x] 35-joint skeleton proof.
- [x] Correct GameCube sword side to `0x9 handL / 0xA weaponL`.
- [x] Attach authentic `al_swb.bmd` to `0xA weaponL`.
- [x] Generate local DMK and visual proof.
- [x] User accepted corrected visual/socket proof.

### Phase 2 - Matrix3 TP Link presentation

**Status:** IN PROGRESS / PARTIAL VERIFIED

#### Bundle 2.1 - Local DMK -> Matrix player presentation

- [x] `TP_LINK` activation and direct Matrix scene rendering.
- [x] Local DMK parse/skinning/model conversion runtime proven.
- [x] Fail-open normal-player suppression.
- [x] World scale `5.0` accepted for height.
- [x] Model-local width/height/depth fitting and live yaw/offset tuning.
- [x] Authentic automatic idle/walk plus authentic sword presentation path.
- [ ] Runtime accept `0.92 / 1.00 / 0.95` starting proportions or record tuned width/depth values.
- [ ] Runtime accept idle/walk switching and facing in live movement.
- [ ] Runtime accept normal-player restoration and hotkey-release regression.

### Phase 3 - Movement and action controller

**Status:** IMPLEMENTED / NEEDS RUNTIME TEST

#### Bundle 3.1 - TP playable controller

- [x] Add distinct `AlternateCharacterController.CharacterId.TP_LINK`.
- [x] Add `TpLinkController` to the established shared driver tick.
- [x] Reuse the shared camera-relative WASD sample.
- [x] Reuse `AlternateCharacterFreeMovement` for continuous movement and optional RuneScape tile-authority clipping.
- [x] Normalize TP movement speed against a 60 Hz baseline so client frame rate does not directly double/halve movement speed.
- [x] Add live move-speed tuning.
- [x] Turn the local Matrix player toward movement direction with live turn-speed smoothing.
- [x] Reuse shared Shift target acquisition; when locked, Link faces the target and movement uses the shared target-relative basis.
- [ ] Runtime accept WASD direction at north/east/west/south camera headings.
- [ ] Runtime accept movement speed and turn speed.
- [ ] Runtime accept target-lock facing/strafe behavior.

### Phase 4 - Action combat + RuneScape progression

**Status:** FIRST MELEE SLICE IMPLEMENTED / NEEDS RUNTIME TEST

#### Bundle 4.1 - Authentic TP sword -> Matrix manual melee

- [x] F rising edge starts the authentic DMK `sword` clip once.
- [x] Holding F cannot continuously re-arm the same swing; release/repress is required.
- [x] Configurable contact frame gates the one server-authoritative melee request.
- [x] Shared combat bridge resolves locked/forward NPC target and sends the existing manual-melee intent.
- [x] No TP damage formula, click-to-attack loop or duplicate NPC search was added.
- [x] Preview sword action can run without sending damage.
- [ ] Runtime calibrate sword contact frame.
- [ ] Runtime verify one F swing -> one server melee cycle when in range.
- [ ] Runtime verify out-of-range/no-target swings do not start auto-combat.
- [ ] Later: RuneScape equipped-weapon attack speed drives TP presentation timing.
- [ ] Later: ranged/bow, shield/guard, magic/items.

### Phase 5 - RuneScape equipment + custom humanoid

**Status:** PLANNED

- Weapon replacement on verified `0xA weaponL` first.
- Shield socket next.
- Helm/glove/boot/cape/amulet fitting.
- Body/legs only after a reusable humanoid-fitting method is proven.
- Evaluate TP rig as a reusable Matrix humanoid base rather than a permanent Link-only replacement.

## Client Console / TP workspace

**Current implementation:** one responsive vertically scrolling `N64 -> TP Link` workspace. The previous nested Presentation/Animation/Movement/Combat/Materials/Diagnostics tab strip was removed because it was cramped and inconsistent with the established Client Console UI.

Current cards:

- TP Link runtime status
- Player fit
- Movement
- Sword combat
- Animation / render
- Fine placement
- Diagnostics

Controls use the same compact direct-entry `- / value / +` style as the existing N64 console. The workspace tracks viewport width and has no horizontal scrolling.

## Current execution state

- Active phase: **Phase 3 / Bundle 3.1 plus Phase 4 / Bundle 4.1 runtime acceptance**
- Presentation state: world-scale height VERIFIED; proportions/idle-walk/facing still need acceptance
- Controller state: implemented verified-static; runtime test pending
- Sword/manual-melee state: implemented verified-static; contact/runtime test pending
- UI state: flattened responsive workspace implemented verified-static; runtime visual acceptance pending
- Approval: user gave `AAA` on 2026-10-06 for the corrective playable-controller + Client Console slice

## Testing

Authoritative steps: `docs/tp-link/TESTLIST.md`.

### One-session quick path

1. `git pull origin main`.
2. Eclipse Java 8 clean/build Client and log in.
3. Open Client Console -> N64 -> TP Link; confirm one scrolling page with no nested tab wall.
4. Toggle `Ctrl+Shift+L`.
5. Verify camera-relative WASD at multiple camera headings.
6. Verify Link faces movement direction and walk/idle presentation follows movement.
7. Hold Shift near an NPC; verify target lock and target-facing movement.
8. Tap F in melee range; authentic sword should play once and one manual melee request should occur at the contact frame.
9. Tap F with no valid target; animation should still play but no auto-combat starts.
10. Tune only move speed, turn speed, yaw correction, contact frame, width/depth if runtime evidence requires it.
11. Toggle TP off and verify normal RuneScape body/control returns.

## Carryover / blockers

### BLOCKED

- None known before runtime testing.

### Remaining later work

- RuneScape weapon visual replacement on `0xA weaponL`.
- RuneScape weapon attack-speed -> TP animation timing.
- Jump/roll/guard only after authentic clips/state choices are added; do not invent procedural TP actions.
- Full Matrix-native TP textures/materials.
- Proper normals/smoothing path.
- Broader RuneScape equipment adaptation.

## Resume Here

**Last completed verified work:**

- Bundle 1.1 donor bootstrap VERIFIED.
- Bundle 1.2 corrected model/animation/left-sword socket proof VERIFIED.
- TP Link live Matrix render VERIFIED.
- World scale `5.0` height VERIFIED.

**Current implementation awaiting runtime:**

- flattened TP Client Console workspace;
- RS-fit model proportions `0.92 / 1.00 / 0.95`;
- distinct TP shared-controller driver;
- camera-relative WASD + shared clipping;
- smoothed movement/target facing;
- Shift target lock;
- F authentic sword one-shot + contact-frame manual melee.

**Next checklist item:**

Pull/build once and run the consolidated controller/UI/combat test in `TESTLIST.md`. Do not rerun donor bootstrap or TP proof generation.

**Do not re-scan without new evidence:**

- Bundle 1.1 donor/bootstrap history.
- Bundle 1.2 GZ2E01 left sword-side identity `0x9 handL / 0xA weaponL`.
- DMK idle/walk/sword naming/format.
- Existing Matrix direct render seam and fail-open suppression seam.
- Shared camera-relative WASD owner.
- Shared imported-character target/manual-melee owner.
- Accepted world scale `5.0` height.
