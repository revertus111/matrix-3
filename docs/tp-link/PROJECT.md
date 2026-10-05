# Twilight Princess Link in Matrix3

## Goal

Replace the normal local RuneScape player presentation with Twilight Princess Link while Matrix3 remains the host world and gameplay authority. Reuse TP Link's real humanoid skeleton, locomotion and combat animation data instead of procedurally inventing attacks. Long term, prove a reusable high-quality foreign humanoid player framework that can support RuneScape equipment and action combat.

## Canonical Main-Goal Status

This table is the authoritative user-facing status table for this workstream across chats.

| Main-goal area | Status |
| --- | --- |
| TP source/decomp bootstrap | ✅ VERIFIED |
| TP Link model + native animation playback | ⚠️ Bundle 1.2 visible proof ready - runtime test next |
| Matrix3 movement/controller integration | ❌ Not started |
| Zelda action combat + RuneScape gameplay authority | ❌ Not started |
| RuneScape equipment adaptation | ❌ Not started |
| Reusable custom humanoid player framework | ❌ Not started |

## Scope

### In scope

- GameCube North America Twilight Princess target `GZ2E01`.
- zeldaret/tp as the authoritative donor-source map for TP behavior and resource relationships.
- Local user-owned disc image as a runtime/build dependency only; no copyrighted disc bytes or extracted assets are committed.
- TP Link human model/skeleton, authentic BCK animation clips, item/weapon joints, sword/shield/bow presentation and movement behavior where practical.
- Matrix3-owned input, RuneScape world/collision, networking/server authority, stats, damage, XP, equipment requirements and NPC state.
- A reusable bridge/import path rather than one-off hard-coded pose manipulation.

### Out of scope for Phase 1

- Shipping or committing Nintendo assets.
- Pretending zeldaret/tp is already a hostable PC/native library like libsm64.
- Porting the whole Twilight Princess engine into Matrix3.
- Replacing RuneScape server/gameplay authority with TP damage or save-state systems.
- Custom procedural sword animation authoring.

## Architecture / ownership

- **Matrix3 authority:** input mode, RuneScape scene/world, collision adaptation, visible camera, multiplayer/server validation, stats, damage, XP and final gameplay rules.
- **TP donor authority:** source-level meaning of Link's skeleton/resource IDs, animation selection/state behavior, weapon/item sockets and presentation timing.
- **Asset boundary:** user-owned `GZ2E01` image remains local. Extracted/converted donor assets must stay local unless the user explicitly supplies them for transient analysis.
- **Important difference from Mario:** `zeldaret/tp` is a matching decompilation that rebuilds the original GameCube game; it explicitly is not a PC port and does not expose a ready `libTP` host API. The first integration should therefore prove model/animation import or a narrow J3D bridge before any claim of full native player passthrough.

## Donor source pin

Initial source pin:

`zeldaret/tp@c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0`

Target:

`GZ2E01` - GameCube North America

The donor bootstrap keeps its checkout outside the Matrix3 Git tree at:

`%LOCALAPPDATA%\Matrix3\TPDecomp`

This avoids polluting Matrix3 with the large external source checkout or user-owned disc image.

The Bundle 1.2 visual proof uses a second local-only external checkout:

`%LOCALAPPDATA%\Matrix3\TPLinkTools\demake-engine`

Pinned visual converter:

`snuri00/demake-engine@a134ff49cc74585c6b11f881293796e45c973c75`

The converter is MIT licensed and is never copied into the Matrix3 Git tree. The proof creates its own isolated Python environment under `%LOCALAPPDATA%\Matrix3\TPLinkTools\venv`.

## Verified foundation

### VERIFIED

- One-click donor bootstrap now passes on the user's PC with a supported `.ciso` donor.
- The pinned checkout built all the way through `TP LINK DONOR BUILD SUCCESS`.
- `CHECK config\GZ2E01\build.sha1` reported `758 files OK`.
- The donor progress report reported all code/data `100.00% matched`; overall linking was `87.13%` (`2583 / 2608 files`).
- Earlier runtime failures were diagnosed and repaired: Windows Store Python alias (`9009`), NKit-v1 input rejection, and incorrect `orig/GZ2E01` object-base layout.

### verified-static

- zeldaret/tp states that GameCube release code is completely matching, while not every translation unit is linked yet.
- zeldaret/tp is a decompilation, not a PC port; it requires the user's own game image and supports `GZ2E01` as GameCube North America.
- zeldaret/tp documents ISO/GCM, RVZ, WIA, WBFS, CISO, NFS, GCZ and TGC donor formats. `.nkit.iso` is not in the documented input set.
- Normal human Link is driven by `daAlink_c`; source is split into focused action files including `d_a_alink_link.inc` (general human actions), `d_a_alink_cut.inc` (sword), `d_a_alink_guard.inc` (shield) and `d_a_alink_bow.inc` (bow/arrow).
- Standard green-tunic Link uses resource archive name `Kmdl`. `changeLink(...)` loads `al.bmd`, `al_head.bmd`, `al_hands.bmd` and `al_face.bmd` for the human presentation path.
- `assets/GZ2E01/res/Object/Kmdl.h` defines the main human skeleton with explicit joints. Important attachment joints are:
  - left hand `AL_JNT_HANDL = 0x9`
  - left weapon/item joint `AL_JNT_WEAPONL = 0xA`
  - right hand `AL_JNT_HANDR = 0xE`
  - right weapon/item joint `AL_JNT_WEAPONR = 0xF`
- `changeLink(...)` agrees with those resource definitions at runtime-source level by assigning human hand/item joint numbers `9`, `14`, `10`, `15` respectively.
- `d_a_alink_link.inc` exposes animated matrices through `getLeftHandMatrix()`, `getRightHandMatrix()`, `getLeftItemMatrix()` and `getRightItemMatrix()`.
- TP has a real combat animation catalog in `daAlink_ANM`, including normal vertical/left/right cuts, combo stab, normal stab, finishers, Mortal Draw, twirl/spin, jump attack, turn attacks, charge states, finishing blow and helm/head attack sequences.
- TP also has real walk/run/targeting/back/strafe, roll, side-jump, back-jump/backflip, guard, shield attack, damage, swim, climb, ladder, horse and item animations.
- Link's main body animation resources are J3D BCK animation resources cataloged through `AlAnm.h`.
- Link animation metadata supports distinct under/upper animation IDs plus left/right hand indices and face animation metadata (`daAlink_AnmData`), confirming a layered animation system rather than a single flat clip stream.
- `d_a_alink_cut.inc` owns sword-action handling and configures real sword attack collision objects; those collision/damage rules are donor reference only and must not replace Matrix3 gameplay authority.
- Bundle 1.2 now has a deterministic local extractor/probe path for `Kmdl.arc` and `AlAnm.arc` using the already-built donor image through decomp-toolkit VFS; no whole-disc manual extraction is required.
- The asset probe selects `al.bmd`, `al_head.bmd`, `al_hands.bmd`, `al_face.bmd`, one WAIT-family idle BCK, one WALK/DASH-family locomotion BCK and one CUT-family sword BCK, and validates that all selected clips reach joint `0xF`.
- The visual proof path uses a pinned J3D converter that decodes TP BMD mesh/skeleton/skin data and real BCK Hermite animation tracks, then generates local animated GIFs.
- The visible `0xF` marker is projected from the sampled right-weapon joint world transform for every rendered proof frame rather than being placed at a hard-coded screen coordinate.

## Unknown / research needed

### HYPOTHESIS

- If the Bundle 1.2 local visual proof passes on the user's real extracted TP assets, the same converted skeleton/skin/animation representation should be a viable source format for the first Matrix3 presentation slice.

### UNKNOWN

- Runtime compatibility of the pinned converter with the user's exact `GZ2E01` `al.bmd`/BCK files until `PROBE TP LINK` is run.
- Whether the first proof's body/head/hands/face attachment aliasing needs TP-specific correction after visual inspection.
- Final Matrix3 render representation for TP Link and whether it should be converted into Matrix model structures or rendered through a narrow external/native/J3D presentation seam.
- Coordinate, scale, animation tick-rate and handedness conversion constants for the revision-830 client.
- The minimum subset of TP's action state machine worth reusing versus re-expressing around Matrix3 input/gameplay ownership.

## Dependencies

- User-owned Twilight Princess GameCube North America image (`GZ2E01`) in one of the donor formats documented by zeldaret/tp. A working `.ciso` donor is now runtime proven.
- Git for Windows.
- Python 3 and Ninja for the donor bootstrap.
- Python 3.12+ for the Bundle 1.2 visual proof. Its isolated local venv installs only NumPy and Pillow for the pinned converter/renderer.
- zeldaret/tp pinned source above.
- `snuri00/demake-engine` pinned visual converter above; local-only dependency, not shipped in Matrix3.
- Existing Matrix3 Native Builder UI.

## Development plan

### Phase 1 - Donor bootstrap and asset proof

**Purpose:** establish a repeatable local `GZ2E01` donor build and prove one real TP Link model/animation/socket path before any broad integration.

**Status:** IN PROGRESS - Bundle 1.2 NEEDS TEST

**Exit conditions:**

- One-click TP donor build passes on the user's PC. `DONE / VERIFIED`.
- Exact Link body and animation asset extraction path is established.
- One authentic TP Link skeleton/model plus authentic idle/locomotion/sword BCK clips render correctly through the proof.
- Right-hand/right-item transform is visibly preserved through animation.

#### Bundle 1.1 - One-click donor bootstrap

**Status:** DONE / VERIFIED

**Checklist / patches:**

- [x] Pin and document the donor repo/target. `DONE` static.
- [x] Map core human model resources, skeleton and hand/item joints. `DONE` verified-static.
- [x] Map the first combat/locomotion animation families. `DONE` verified-static.
- [x] Add `PREP + BUILD TP LINK` to Matrix3 Native Builder. `DONE` static.
- [x] Add local-source bootstrap that selects the user's disc image, validates raw ISO/GCM ID, clones the pinned decomp outside Matrix3, prepares `orig/GZ2E01`, configures and builds. `DONE` static.
- [x] Capture first runtime bootstrap failure: Windows Store Python alias / missing Ninja. `DONE` VERIFIED evidence.
- [x] Patch dependency resolution, MSYS2 UCRT64 fallback/provisioning, native-folder image auto-detection and explicit NKit-v1 rejection. `DONE` verified-static.
- [x] Fix decomp-toolkit object-base layout so the supported donor image lives inside `orig/GZ2E01/`. `DONE` verified-static.
- [x] Rerun the complete pinned build with the supported `.ciso`. `DONE / VERIFIED`.

**Runtime tests:**

- See `docs/tp-link/TESTLIST.md`.

#### Bundle 1.2 - Link asset extraction + animation proof

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Establish deterministic local `Kmdl` and `AlAnm` resource access from the successful donor workspace through decomp-toolkit VFS. `DONE` verified-static.
- [x] Extract/select `al.bmd`, `al_head.bmd`, `al_hands.bmd` and `al_face.bmd` without manual whole-disc extraction. `DONE` verified-static.
- [x] Select authentic WAIT-family idle, WALK/DASH-family locomotion and CUT-family sword BCK clips. `DONE` verified-static.
- [x] Preserve/validate the `0xE` right-hand and `0xF` right-item joint contract and reject clips that do not reach `0xF`. `DONE` verified-static.
- [x] Add a deterministic local visual renderer producing idle/walk/sword GIFs and a combined proof GIF. `DONE` verified-static.
- [x] Add a visible per-frame marker sourced from the animated `0xF` right-weapon joint transform. `DONE` verified-static.
- [x] Add one-click `PROBE TP LINK` to Matrix3 Native Builder. `DONE` verified-static.
- [ ] Runtime acceptance: TP Link renders correctly, the authentic clips visibly animate correctly, and the red `0xF` marker remains attached to the weapon joint. `NEEDS TEST`.

### Phase 2 - Matrix3 TP Link presentation

**Status:** PLANNED

- First visible TP Link render in the revision-830 client.
- Authentic idle/walk/run clip playback.
- Fail-open local-player suppression/restoration using established Matrix ownership patterns.

### Phase 3 - Movement and action controller

**Status:** PLANNED

- Universal character-controller integration.
- Zelda-style targeting/strafe/roll/jump presentation where compatible.
- Matrix3 remains world/collision authority unless a narrower proven donor simulation seam is justified.

### Phase 4 - Action combat + RuneScape progression

**Status:** PLANNED

- Authentic TP sword clips and contact windows.
- RuneScape Attack/Strength/Defence, NPC HP, damage, XP and requirements remain authoritative.
- Bow/ranged and later magic/item styles.

### Phase 5 - RuneScape equipment + custom humanoid

**Status:** PLANNED

- Weapon/shield sockets first.
- Helm/glove/boot/cape/amulet fitting next.
- Body/legs only after a reusable humanoid fitting/retargeting method is proven.
- Evaluate TP rig as the base for an original Matrix humanoid player model rather than a permanent Link-only replacement.

## Current execution state

- Phase: 1 - Donor bootstrap and asset proof
- Phase status: IN PROGRESS
- Bundle: 1.2 - Link asset extraction + animation proof
- Bundle status: NEEDS TEST
- Approval state: SAP AAA approved by user on 2026-10-05 for Bundle 1.2 continuation
- Current checklist item: runtime acceptance of the visible Link idle/walk/sword/socket proof
- Current objective: one user test proving the authentic TP model, BCK playback and animated `0xF` weapon socket before any revision-830 renderer integration

## Testing

### Quick/high-value checks

1. Pull current `main` once.
2. Double-click root `Native Builder.bat`.
3. Click `PROBE TP LINK`.
4. First run may clone the pinned MIT visual converter and create an isolated Python venv under `%LOCALAPPDATA%\Matrix3\TPLinkTools`.
5. Expected output is `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.gif`; it should auto-open.
6. Visually verify all three segments: idle, walk/run and sword.
7. Verify the red `0xF` marker moves with Link's right weapon socket through the animations.
8. Report PASS or the exact visual/error failure. Do not manually extract donor archives.

### Deeper checks

- Confirm `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\visual-proof.json` reports three animations, a skeleton/skin and weapon socket index `15`.
- Confirm no TP source/assets/disc image or third-party converter source appears inside the Matrix3 Git tree.
- If the render is geometrically correct but materials/attachments are visually wrong, classify that separately from BCK/skeleton/socket failure.
- Matrix3 in-client rendering is not part of Bundle 1.2 acceptance.

## Carryover / blockers

### BLOCKED

- None known. Bundle 1.2 implementation is ready for the user's runtime/visual acceptance test.

## Resume Here

**Last completed:**

- Bundle 1.1 donor bootstrap is runtime VERIFIED.
- Bundle 1.2 one-click extraction, real idle/walk/sword BCK selection, local software rendering and animated `0xF` socket-marker implementation are complete at verified-static level.
- Matrix3 Native Builder now exposes `PROBE TP LINK` for the consolidated visual test.

**Current phase:**

- Phase 1 - Donor bootstrap and asset proof (`IN PROGRESS`).

**Active bundle:**

- Bundle 1.2 - Link asset extraction + animation proof (`NEEDS TEST`).

**Next checklist item:**

- Run `PROBE TP LINK` once and visually accept/reject the combined GIF.

**Current state / next action:**

- Do not rerun donor bootstrap research and do not manually extract the whole disc. Pull current `main`, open `Native Builder.bat`, click `PROBE TP LINK`, then inspect the automatically opened combined GIF.

**Files/systems already inspected:**

- zeldaret/tp README and `GZ2E01` target configuration.
- `src/d/actor/d_a_alink.cpp`
- `src/d/actor/d_a_alink_link.inc`
- `src/d/actor/d_a_alink_cut.inc`
- `src/d/actor/d_a_alink_wolf.inc` (`changeLink` resource/joint setup)
- `include/d/actor/d_a_alink.h`
- `assets/GZ2E01/res/Object/Kmdl.h`
- `assets/GZ2E01/res/Object/Alink.h`
- `assets/GZ2E01/res/Object/AlAnm.h`
- Matrix3 Native Builder scripts.
- `snuri00/demake-engine` BMD/BCK/DMK/software-preview path at the pinned commit above.

**Do not re-scan without new evidence:**

- Human right-hand/right-item joint identity (`0xE` / `0xF`).
- Core `Kmdl` human resource names.
- Existence of WAIT, WALK/DASH and CUT animation families in `AlAnm`.
- zeldaret/tp's status as a matching decomp rather than a ready PC library.
- Bootstrap failure history; Bundle 1.1 is complete.
- The local-only visual converter choice unless runtime evidence shows it cannot decode/render the actual selected TP assets.

**Pending runtime verification:**

- Bundle 1.2 visual acceptance only: authentic model appearance, authentic idle/walk/sword playback and `0xF` socket tracking.

**Important remaining uncertainty:**

- Actual visual correctness on the user's extracted TP files. Static/API compatibility is established, but this cannot be promoted to VERIFIED until the generated proof is seen running.

## Next recommended work

Run the consolidated Bundle 1.2 visual proof. If it passes, promote Bundle 1.2 to VERIFIED and start Phase 2 with the exact proven converted character/animation data rather than inventing a separate Link pipeline.
