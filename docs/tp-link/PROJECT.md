# Twilight Princess Link in Matrix3

## Goal

Replace the normal local RuneScape player presentation with Twilight Princess Link while Matrix3 remains the host world and gameplay authority. Reuse TP Link's real humanoid skeleton, locomotion and combat animation data instead of procedurally inventing attacks. Long term, prove a reusable high-quality foreign humanoid player framework that can support RuneScape equipment and action combat.

## Canonical Main-Goal Status

This table is the authoritative user-facing status table for this workstream across chats.

| Main-goal area | Status |
| --- | --- |
| TP source/decomp bootstrap | ✅ VERIFIED |
| TP Link model + native animation playback | ⚠️ Active - asset proof next |
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

## Unknown / research needed

### HYPOTHESIS

- A Matrix-native conversion of TP's BMD skeleton + selected BCK clips is likely the lowest-risk first presentation path because the necessary skeleton, attachment and animation semantics are already explicit in donor source.
- Existing J3D/SuperBMD tooling may reduce the amount of custom BMD/BCK decoding required, but the exact TP-compatible extraction/conversion path still needs validation against the user's successful `GZ2E01` workspace.

### UNKNOWN

- Exact local build/extraction output paths for the `Kmdl` and `AlAnm` disc archives after the successful donor bootstrap.
- Whether an existing tool can preserve all TP Link skin weights/materials/BCK semantics well enough for direct Matrix3 ingestion without a custom converter.
- Final Matrix3 render representation for TP Link and whether it should be converted into Matrix model structures or rendered through a narrow external/native/J3D presentation seam.
- Coordinate, scale, animation tick-rate and handedness conversion constants.
- The minimum subset of TP's action state machine worth reusing versus re-expressing around Matrix3 input/gameplay ownership.

## Dependencies

- User-owned Twilight Princess GameCube North America image (`GZ2E01`) in one of the donor formats documented by zeldaret/tp. A working `.ciso` donor is now runtime proven.
- Git for Windows.
- Python 3 and Ninja. The one-click bootstrap reuses valid native/MSYS2 tools or provisions UCRT64 Python + Ninja through the existing Matrix3 MSYS2 installation when missing.
- zeldaret/tp pinned source above.
- Existing Matrix3 Native Builder UI.

## Development plan

### Phase 1 - Donor bootstrap and asset proof

**Purpose:** establish a repeatable local `GZ2E01` donor build and prove one real TP Link model/animation/socket path before any broad integration.

**Status:** IN PROGRESS

**Exit conditions:**

- One-click TP donor build passes on the user's PC. `DONE / VERIFIED`.
- Exact Link body and animation asset extraction path is established.
- One authentic TP Link skeleton/model and at least one authentic BCK clip can be decoded/converted deterministically.
- Right-hand/right-item transform is preserved in the proof.

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

**Status:** ACTIVE

**Checklist / patches:**

- [ ] Establish exact local `Kmdl` and `AlAnm` asset locations from the successful donor workspace.
- [ ] Extract/convert `al.bmd` without manual whole-disc extraction.
- [ ] Extract one low-risk BCK locomotion clip and one sword-cut BCK clip.
- [ ] Preserve/verify the `0xE` right-hand and `0xF` right-item joints through conversion.
- [ ] Produce a deterministic local preview/probe before wiring Matrix3 rendering.

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
- Bundle status: ACTIVE
- Approval state: SAP AAA approved by user on 2026-10-05 for TP Link startup/bootstrap workstream
- Current checklist item: establish exact local `Kmdl` and `AlAnm` asset locations from the successful donor workspace
- Current objective: smallest deterministic `al.bmd` + authentic BCK + right-item-joint proof with no manual whole-disc extraction

## Testing

### Quick/high-value checks

1. Bundle 1.1 donor build is already runtime VERIFIED.
2. For Bundle 1.2, prefer an automated local probe/extractor over asking the user to navigate or extract the full disc manually.
3. First asset proof must identify `al.bmd`, at least one authentic BCK clip, and preserve right-hand/right-item joint identity.

### Deeper checks

- Confirm no TP source/assets/disc image appear inside the Matrix3 Git tree.
- Verify model skeleton/weights/materials survive conversion well enough for the intended rendering route.
- Verify animation frame rate, looping metadata, coordinate system and socket transforms against donor source.

## Carryover / blockers

### BLOCKED

- None for Bundle 1.1; donor bootstrap is complete.
- Bundle 1.2 is not blocked, but the exact smallest extraction/conversion route remains unproven.

## Resume Here

**Last completed:**

- Supported `.ciso` donor was automatically discovered and the complete pinned `GZ2E01` build passed.
- `758 files OK`; all donor code/data reported `100.00% matched`; builder ended `TP LINK DONOR BUILD SUCCESS`.
- Bundle 1.1 is runtime VERIFIED.

**Current phase:**

- Phase 1 - Donor bootstrap and asset proof (`IN PROGRESS`).

**Active bundle:**

- Bundle 1.2 - Link asset extraction + animation proof (`ACTIVE`).

**Next checklist item:**

- Establish exact local `Kmdl` / `AlAnm` resource access from `%LOCALAPPDATA%\Matrix3\TPDecomp` and automate the smallest Link asset proof.

**Current state / next action:**

- Donor build foundation is solved. Do not rerun bootstrap research or ask the user to manually extract the whole disc. Continue directly into real Link model/animation/socket extraction proof.

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

**Do not re-scan without new evidence:**

- Human right-hand/right-item joint identity (`0xE` / `0xF`).
- Core `Kmdl` human resource names.
- Existence of the TP sword animation family in `daAlink_ANM`.
- zeldaret/tp's status as a matching decomp rather than a ready PC library.
- Bootstrap failure history; Bundle 1.1 is now complete.

**Pending runtime verification:**

- Bundle 1.2 asset extraction/preview proof only.

**Important remaining uncertainty:**

- Exact smallest extraction/conversion route from the successful local `GZ2E01` workspace to Matrix3-compatible model + BCK animation data.

## Next recommended work

Continue Bundle 1.2: automate the smallest possible `al.bmd` + authentic BCK + right-item-joint proof from the successful local donor workspace. Do not manually extract the entire disc.
