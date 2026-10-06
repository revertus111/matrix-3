# Twilight Princess Link in Matrix3

## Goal

Replace the normal local RuneScape player presentation with Twilight Princess Link while Matrix3 remains the host world and gameplay authority. Reuse TP Link's real humanoid skeleton, locomotion and combat animation data instead of procedurally inventing attacks. Long term, prove a reusable high-quality foreign humanoid player framework that can support RuneScape equipment and action combat.

## Canonical Main-Goal Status

This table is the authoritative user-facing status table for this workstream across chats.

| Main-goal area | Status |
| --- | --- |
| TP source/decomp bootstrap | ✅ VERIFIED |
| TP local model + native animation/socket proof | ⚠️ Generated; corrected left-side visual acceptance still pending |
| Matrix3 TP Link presentation | ⚠️ Bundle 2.1 PARTIAL VERIFIED - in-client render works; scale calibration + motion acceptance pending |
| Matrix3 movement/controller integration | ❌ Not started for TP |
| Zelda action combat + RuneScape gameplay authority | ❌ Not started for TP |
| RuneScape equipment adaptation | ❌ Not started for TP |
| Reusable custom humanoid player framework | ❌ Not started |

## Scope

### In scope

- GameCube North America Twilight Princess target `GZ2E01`.
- zeldaret/tp as the authoritative donor-source map for TP behavior and resource relationships.
- Local user-owned disc image as a runtime/build dependency only; no copyrighted disc bytes or extracted assets are committed.
- TP Link human model/skeleton, authentic BCK animation clips, item/weapon joints, sword/shield/bow presentation and movement behavior where practical.
- Matrix3-owned input, RuneScape world/collision, networking/server authority, stats, damage, XP, equipment requirements and NPC state.
- A reusable bridge/import path rather than one-off hard-coded pose manipulation.

### Out of scope for the current presentation slice

- Shipping or committing Nintendo assets.
- Pretending zeldaret/tp is already a hostable PC/native library like libsm64.
- Porting the whole Twilight Princess engine into Matrix3.
- Replacing RuneScape server/gameplay authority with TP damage or save-state systems.
- Custom procedural sword animation authoring.
- TP movement/controller behavior, combat, or RuneScape equipment replacement inside Bundle 2.1.

## Architecture / ownership

- **Matrix3 authority:** input mode, RuneScape scene/world, collision adaptation, visible camera, multiplayer/server validation, stats, damage, XP and final gameplay rules.
- **TP donor authority:** source-level meaning of Link's skeleton/resource IDs, animation selection/state behavior, weapon/item sockets and presentation timing.
- **Asset boundary:** user-owned `GZ2E01` image remains local. Extracted/converted donor assets stay outside Git and are loaded from `%LOCALAPPDATA%`.
- **Presentation boundary:** Bundle 2.1 consumes the locally generated DMK directly in Java 8. No TP native sidecar or Python process runs with the client.
- **Important difference from Mario:** `zeldaret/tp` is a matching decompilation that rebuilds the original GameCube game; it is not a PC port and does not expose a ready `libTP` host API.

## Donor source pin

Initial source pin:

`zeldaret/tp@c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0`

Target:

`GZ2E01` - GameCube North America

The donor bootstrap keeps its checkout outside the Matrix3 Git tree at:

`%LOCALAPPDATA%\Matrix3\TPDecomp`

The local visual converter checkout remains outside Matrix3 at:

`%LOCALAPPDATA%\Matrix3\TPLinkTools\demake-engine`

Pinned visual converter:

`snuri00/demake-engine@a134ff49cc74585c6b11f881293796e45c973c75`

The generated local presentation asset consumed by Bundle 2.1 is:

`%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk`

## Verified foundation

### VERIFIED

- One-click donor bootstrap passes on the user's PC with a supported `.ciso` donor.
- The pinned checkout built all the way through `TP LINK DONOR BUILD SUCCESS`.
- `CHECK config\GZ2E01\build.sha1` reported `758 files OK`.
- The donor progress report reported all code/data `100.00% matched`; overall linking was `87.13%` (`2583 / 2608 files`).
- Authentic donor extraction is runtime proven: `al.bmd` parsed with 35 joints and real `waitb.bck`, `dasha.bck`, and `cutl.bck` clips were selected.
- The MSYS2/UCRT64 visual dependency path has run to completion and generated the local proof artifacts.
- Bundle 2.1 first in-client run loaded `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk` successfully and rendered TP Link through Matrix3. Runtime log reported `19821` vertices, `6607` triangles, `35` joints, `idleFrames=30`, `walkFrames=24`, named `idle`/`walk` animations, and `GZ2E01 Link -> Matrix Model ACTIVE`.
- The supplied runtime screenshot visibly shows TP Link in the revision-830 world at the local-player presentation position. This verifies the local DMK parser, skinning/model-conversion path, scene hook, and basic world placement are operational.

### verified-static

- zeldaret/tp states that GameCube release code is completely matching, while not every translation unit is linked yet.
- zeldaret/tp is a decompilation, not a PC port; it requires the user's own game image and supports `GZ2E01` as GameCube North America.
- Standard green-tunic Link uses resource archive name `Kmdl`. `changeLink(...)` loads `al.bmd`, `al_head.bmd`, `al_hands.bmd` and `al_face.bmd` for the human presentation path.
- `assets/GZ2E01/res/Object/Kmdl.h` defines the important attachment joints:
  - left hand `AL_JNT_HANDL = 0x9`
  - left weapon/item `AL_JNT_WEAPONL = 0xA`
  - right hand `AL_JNT_HANDR = 0xE`
  - right weapon/item `AL_JNT_WEAPONR = 0xF`
- The corrected GZ2E01 GameCube sword proof uses `0x9 handL` / `0xA weaponL` as the active sword-side pair; `0xE/0xF` remains the alternate right-hand/item pair.
- TP has real WAIT, WALK/DASH, CUT, guard, roll, targeting, jump, bow and broader combat animation families; current work only consumes the minimal approved subset.
- Bundle 1.2 builds one combined local mesh from body/head/hands/face plus `al_swb.bmd`, with skinning and authentic BCK animation data.
- `tp-link-visual-proof.py` writes a local f32 DMK with `MESH`, optional `TEXT`, `SKEL`, `SKIN`, and named `ANIM` chunks `idle`, `walk`, and `sword`.
- `TpLinkVisualRenderer` parses that exact local representation, evaluates `world_pose * inverse_bind`, converts the skinned triangle list into Matrix `Class159`/`Model`, and renders through the established scene seam.
- Bundle 2.1 uses authentic `idle` and `walk` DMK animations only. Combat remains out of scope.
- TP local-player suppression reuses the existing shared `Player.method10696(...)` gate and is fail-open: normal RuneScape presentation is suppressed only after a fresh successful TP render.
- `Ctrl+Shift+L` activates presentation-only TP mode. The whole `Ctrl+L` chord is latched until release so releasing Shift first cannot accidentally trigger OoT Link.
- Runtime evidence at default scale `1.0` showed TP Link was dramatically undersized relative to normal revision-830 humanoids. The narrow corrective patch changes the default `matrix3.tp.modelScale` fallback to `5.0`; this calibration is verified-static until rerun.

## Evidence still pending

### NEEDS VISUAL ACCEPTANCE

- Bundle 1.2 corrected local proof: confirm the cyan `0x9 handL` marker, red `0xA weaponL` marker, and attached `al_swb.bmd` visibly follow the intended GameCube sword side through the authentic sword cut.

### NEEDS RUNTIME TEST

- Bundle 2.1 scale calibration at default `5.0`.
- Authentic idle loop while stationary and locomotion clip switching while the Matrix player position changes.
- Facing/yaw correctness while moving in different directions.
- `Ctrl+Shift+L` release-order regression and clean restoration to normal RuneScape presentation.

### UNKNOWN until runtime evidence

- Final TP-to-Matrix scale value after the `5.0` calibration rerun.
- Whether yaw needs a nonzero correction after the model is large enough to judge clearly.
- Whether rebuilding the Matrix `Model` on animation-frame changes is sufficiently smooth on the user's runtime; optimize only if profiling/runtime evidence shows a problem.
- Final TP movement/action state-machine subset and later combat/equipment integration details.

## Dependencies

- User-owned Twilight Princess GameCube North America image (`GZ2E01`) in a supported donor format. A working `.ciso` donor is runtime proven.
- Git for Windows.
- Python/Ninja for donor bootstrap.
- Matrix3 MSYS2/UCRT64 Python plus prebuilt NumPy/Pillow packages for the local Bundle 1.2 visual proof.
- zeldaret/tp pinned source above.
- `snuri00/demake-engine` pinned converter above; local-only dependency, not shipped in Matrix3.
- Existing Matrix3 Client on Java 8 / Eclipse.

## Development plan

### Phase 1 - Donor bootstrap and asset proof

**Purpose:** establish a repeatable local `GZ2E01` donor build and prove one real TP Link model/animation/socket path before broad gameplay integration.

**Status:** IN PROGRESS - Bundle 1.2 corrected visual acceptance remains carryover

#### Bundle 1.1 - One-click donor bootstrap

**Status:** DONE / VERIFIED

- [x] Pin donor repo/target.
- [x] Map human model resources, skeleton and hand/item joints.
- [x] Add one-click donor bootstrap.
- [x] Repair Python/Ninja and donor object-base runtime failures.
- [x] Complete full pinned `GZ2E01` donor build.

#### Bundle 1.2 - Link asset extraction + animation/socket proof

**Status:** GENERATED / NEEDS VISUAL ACCEPTANCE

- [x] Deterministic local `Kmdl` / `AlAnm` extraction through decomp-toolkit VFS.
- [x] Select `al.bmd`, head/hands/face attachments and authentic WAIT / WALK-DASH / CUT BCK clips.
- [x] Validate both left `0x9/0xA` and right `0xE/0xF` hand/item pairs.
- [x] Correct active GameCube sword proof to left `0x9 handL` / `0xA weaponL`.
- [x] Attach authentic `al_swb.bmd` to `0xA weaponL` in the proof.
- [x] Generate idle/walk/sword GIFs, manifest/summary, and local f32 `tp-link-proof.dmk`.
- [ ] User visual acceptance of the corrected left-side sword/socket proof.

### Phase 2 - Matrix3 TP Link presentation

**Status:** IN PROGRESS

#### Bundle 2.1 - Local DMK -> Matrix player presentation

**Status:** PARTIAL VERIFIED / SCALE CALIBRATION NEEDS TEST

- [x] Add presentation-only `TP_LINK` mode without entering the existing Mario/OoT movement controller.
- [x] Load the local generated DMK from `%LOCALAPPDATA%` only. `VERIFIED`.
- [x] Parse the proven f32 mesh/texture/skeleton/skin/animation representation in Java 8. `VERIFIED` through first in-client render.
- [x] Skin and render TP Link through Matrix `Class159` / `Model` at the local player's Matrix world transform. `VERIFIED` through first in-client screenshot/log.
- [x] Select authentic idle/locomotion animation from actual local-player movement state. `verified-static`; visual transition still needs runtime acceptance.
- [x] Reuse the existing direct Matrix scene render seam. `VERIFIED`.
- [x] Reuse the existing local-player suppression seam with fail-open behavior. `verified-static`; explicit toggle/restore regression still pending.
- [x] Preserve existing `Ctrl+L` OoT Link and fix Shift release-order retrigger through one latched Ctrl+L chord. `verified-static`.
- [x] Increase evidence-based default TP model scale from `1.0` to `5.0` after the first runtime screenshot showed action-figure scale. `NEEDS TEST`.
- [ ] Runtime accept corrected scale, idle/walk transitions, facing, and suppression/restoration/hotkey behavior.

### Phase 3 - Movement and action controller

**Status:** PLANNED

- Universal character-controller integration for TP after presentation is accepted.
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

- Active phase: 2 - Matrix3 TP Link presentation
- Active bundle: 2.1 - Local DMK -> Matrix player presentation
- Bundle status: PARTIAL VERIFIED / SCALE CALIBRATION NEEDS TEST
- Approval state: SAP AAA approved by user on 2026-10-05 for this coherent presentation bundle
- Carryover: Bundle 1.2 corrected left-side local visual acceptance is still pending and must not be mislabeled VERIFIED
- Runtime evidence: first in-client TP render succeeded at scale `1.0`; Link was visibly far too small
- Current objective: rerun once at default scale `5.0`, then accept/reject scale, idle/locomotion, facing and replacement behavior in the same session

## Testing

The authoritative steps are in `docs/tp-link/TESTLIST.md`.

### Bundle 2.1 quick path

1. Pull current `main` once.
2. Eclipse Java 8 clean/build Client; launch and log in.
3. Press `Ctrl+Shift+L` once.
4. Expect `TP_LINK`, local DMK load, and `GZ2E01 Link -> Matrix Model ACTIVE ... scale=5.0 ...`.
5. Compare TP Link height against nearby normal humanoids; report whether `5.0` is too small, close, or too large.
6. Check idle while still and locomotion while the normal RuneScape player position changes.
7. Check facing while walking in more than one direction.
8. Release Shift first once while the chord is still briefly held; TP mode must remain active.
9. Toggle `Ctrl+Shift+L` again after fully releasing the first chord; normal RuneScape local-player presentation must return.

## Carryover / blockers

### BLOCKED

- None known. The active issue is visual scale calibration, not architecture/toolchain failure.

### Carryover

- Bundle 1.2 corrected left-side sword/socket visual proof still needs explicit user acceptance. This is separate from Bundle 2.1 Matrix rendering.

## Resume Here

**Last completed:**

- Bundle 1.1 donor bootstrap is runtime VERIFIED.
- Bundle 1.2 extraction/tool execution and local artifact generation are runtime VERIFIED; corrected left `0x9/0xA` visual acceptance remains pending.
- Bundle 2.1 local DMK loading and actual TP Link rendering inside Matrix3 are runtime VERIFIED from the first in-client run.
- First runtime log: `vertices=19821`, `triangles=6607`, `joints=35`, `idleFrames=30`, `walkFrames=24`, `scale=1.0`, `yawOffset=0.0`.
- First screenshot showed the TP model was dramatically undersized; default presentation scale is now `5.0` for the next calibration run.

**Current phase:**

- Phase 2 - Matrix3 TP Link presentation (`IN PROGRESS`).

**Active bundle:**

- Bundle 2.1 - Local DMK -> Matrix player presentation (`PARTIAL VERIFIED / SCALE CALIBRATION NEEDS TEST`).

**Next checklist item:**

- Pull/build once and rerun TP presentation at `scale=5.0`; judge size, idle/walk transition, facing and toggle restoration in one session.

**Current state / next action:**

- Do not rerun donor bootstrap or TP proof generation. The local DMK is loading correctly. Pull current `main`, Java 8 clean/build the Client, launch, toggle `Ctrl+Shift+L`, and inspect the calibrated in-client model.

**Files/systems already inspected for Bundle 2.1:**

- root `AGENTS.md`
- `Client/src/main/java/game/TpLinkVisualRenderer.java`
- `Client/src/main/java/game/PlayerControllerMode.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/MarioVisualRenderer.java`
- `Client/src/main/java/game/Player.java` suppression seam
- `native/tools/tp-link-visual-proof.py`
- pinned demake DMK f32 format used by the local proof

**Do not re-scan without new evidence:**

- TP donor bootstrap history; Bundle 1.1 is complete.
- Active GameCube sword-side identity `0x9 handL` / `0xA weaponL`.
- Existing Matrix direct-scene render seam and shared local-player suppression seam.
- DMK `idle` / `walk` f32 animation naming/format.
- TP Bundle 2.1 ownership: presentation only; no combat/movement/equipment expansion until runtime evidence.

## Next recommended work

Runtime-test the `5.0` scale calibration. If the size is close, finish yaw/idle-walk/toggle acceptance and close Bundle 2.1. If size is still off, adjust only `matrix3.tp.modelScale` from the visual evidence; do not reopen the renderer architecture.