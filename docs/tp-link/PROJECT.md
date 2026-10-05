# Twilight Princess Link in Matrix3

## Goal

Replace the normal local RuneScape player presentation with Twilight Princess Link while Matrix3 remains the host world and gameplay authority. Reuse TP Link's real humanoid skeleton, locomotion and combat animation data instead of procedurally inventing attacks. Long term, prove a reusable high-quality foreign humanoid player framework that can support RuneScape equipment and action combat.

## Canonical Main-Goal Status

This table is the authoritative user-facing status table for this workstream across chats.

| Main-goal area | Status |
| --- | --- |
| TP source/decomp bootstrap | ⚠️ Needs runtime verification |
| TP Link model + native animation playback | ❌ Not started |
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

- None yet for TP inside Matrix3. Runtime/build acceptance is still pending.

### verified-static

- zeldaret/tp states that GameCube release code is completely matching, while not every translation unit is linked yet.
- zeldaret/tp is a decompilation, not a PC port; it requires the user's own game image and supports `GZ2E01` as GameCube North America.
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
- Existing J3D/SuperBMD tooling may reduce the amount of custom BMD/BCK decoding required, but the exact TP-compatible extraction/conversion path still needs validation against the user's `GZ2E01` build.

### UNKNOWN

- Exact local build/extraction output paths for the `Kmdl` and `AlAnm` disc archives after the first successful donor bootstrap.
- Whether an existing tool can preserve all TP Link skin weights/materials/BCK semantics well enough for direct Matrix3 ingestion without a custom converter.
- Final Matrix3 render representation for TP Link and whether it should be converted into Matrix model structures or rendered through a narrow external/native/J3D presentation seam.
- Coordinate, scale, animation tick-rate and handedness conversion constants.
- The minimum subset of TP's action state machine worth reusing versus re-expressing around Matrix3 input/gameplay ownership.

## Dependencies

- User-owned Twilight Princess GameCube North America image (`GZ2E01`).
- Git for Windows.
- Python 3.
- Ninja; the one-click bootstrap attempts a documented user-local pip install when Ninja is missing.
- zeldaret/tp pinned source above.
- Existing Matrix3 Native Builder UI.

## Development plan

### Phase 1 - Donor bootstrap and asset proof

**Purpose:** establish a repeatable local `GZ2E01` donor build and prove one real TP Link model/animation/socket path before any broad integration.

**Status:** NEEDS TEST

**Entry conditions:**

- User has a local `GZ2E01` disc image.
- SAP AAA granted for TP Link workstream startup.

**Exit conditions:**

- One-click TP donor build passes on the user's PC.
- Exact Link body and animation asset extraction path is established.
- One authentic TP Link skeleton/model and at least one authentic BCK clip can be decoded/converted deterministically.
- Right-hand/right-item transform is preserved in the proof.

#### Bundle 1.1 - One-click donor bootstrap

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Pin and document the donor repo/target. `DONE` static.
- [x] Map core human model resources, skeleton and hand/item joints. `DONE` verified-static.
- [x] Map the first combat/locomotion animation families. `DONE` verified-static.
- [x] Add `PREP + BUILD TP LINK` to Matrix3 Native Builder. `DONE` static.
- [x] Add local-source bootstrap that selects the user's disc image, validates raw ISO/GCM ID, clones the pinned decomp outside Matrix3, prepares `orig/GZ2E01`, configures and builds. `DONE` static.
- [ ] Run the one-click bootstrap on the user's PC and record PASS/FAIL. `NEEDS TEST`.

**Runtime tests:**

- See `docs/tp-link/TESTLIST.md`.

#### Bundle 1.2 - Link asset extraction + animation proof

**Status:** READY

**Checklist / patches:**

- [ ] Establish exact local `Kmdl` and `AlAnm` asset locations from the successful donor workspace.
- [ ] Extract/convert `al.bmd` without manual whole-disc extraction.
- [ ] Extract one low-risk BCK locomotion clip and one sword-cut BCK clip.
- [ ] Preserve/verify the 0xE right-hand and 0xF right-item joints through conversion.
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
- Phase status: NEEDS TEST
- Bundle: 1.1 - One-click donor bootstrap
- Bundle status: NEEDS TEST
- Approval state: SAP AAA approved by user on 2026-10-05
- Current checklist item: run `PREP + BUILD TP LINK` once on the user's PC
- Current objective: prove the pinned `GZ2E01` donor checkout/build without manual disc extraction or uploading the ISO to ChatGPT

## Testing

### Quick/high-value checks

1. Root `Native Builder.bat` opens with Mario, OoT and TP Link buttons.
2. First TP click prompts for a supported disc image, accepts the user's `GZ2E01`, and ends `TP LINK DONOR BUILD SUCCESS`.
3. Second TP click reuses the prepared local image/workspace without another picker.

### Deeper checks

- Confirm the local donor checkout is exactly the pinned commit.
- Confirm no TP source/assets/disc image appear inside the Matrix3 Git tree.
- After bootstrap PASS, inspect generated/extracted local asset paths before adding a converter.

## Carryover / blockers

### BLOCKED

- TP Link visual integration cannot be considered started until the donor build and real asset path are verified on the user's PC.

## Resume Here

**Last completed:**

- Static donor-source audit mapped TP human Link model resources, explicit skeleton/weapon joints, layered animation metadata and combat animation families.
- One-click TP donor bootstrap is staged in Matrix3 Native Builder.

**Current phase:**

- Phase 1 - Donor bootstrap and asset proof (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.1 - One-click donor bootstrap (`NEEDS TEST`).

**Next checklist item:**

- User runs `Native Builder.bat` -> `PREP + BUILD TP LINK` and sends only the build window if it fails.

**Current state / next action:**

- Validate the donor bootstrap once. On PASS, immediately move to the smallest real `al.bmd` + BCK + right-item-joint proof.

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

**Pending runtime verification:**

- One-click donor clone/configure/build.

**Blockers:**

- No Matrix3 TP asset/runtime proof until the local donor bootstrap succeeds.

**Important remaining uncertainty:**

- Exact smallest extraction/conversion route from the successful local `GZ2E01` workspace to Matrix3-compatible model + BCK animation data.

## Next recommended work

Run the one-click TP donor bootstrap. On PASS, immediately continue with Bundle 1.2 and build the smallest possible `al.bmd` + authentic BCK + right-item-joint proof; do not manually extract the entire disc.
