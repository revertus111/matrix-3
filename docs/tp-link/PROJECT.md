# Twilight Princess Link in Matrix3

## Goal

Replace the normal local RuneScape player presentation with Twilight Princess Link while Matrix3 remains the host world and gameplay authority. Reuse TP Link's real humanoid skeleton, locomotion and combat animation data instead of procedurally inventing attacks. Long term, prove a reusable high-quality foreign humanoid player framework that can support RuneScape equipment and action combat.

## Canonical Main-Goal Status

This table is the authoritative user-facing status table for this workstream across chats.

| Main-goal area | Status |
| --- | --- |
| TP source/decomp bootstrap | ✅ VERIFIED |
| TP local model + native animation/socket proof | ✅ VERIFIED |
| Matrix3 TP Link presentation | ⚠️ Bundle 2.1 PARTIAL VERIFIED - height accepted; proportion + motion acceptance pending |
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
- TP movement/controller behavior, authoritative combat, or RuneScape equipment replacement inside Bundle 2.1.

## Architecture / ownership

- **Matrix3 authority:** input mode, RuneScape scene/world, collision adaptation, visible camera, multiplayer/server validation, stats, damage, XP and final gameplay rules.
- **TP donor authority:** source-level meaning of Link's skeleton/resource IDs, animation selection/state behavior, weapon/item sockets and presentation timing.
- **Asset boundary:** user-owned `GZ2E01` image remains local. Extracted/converted donor assets stay outside Git and are loaded from `%LOCALAPPDATA%`.
- **Presentation boundary:** Bundle 2.1 consumes the locally generated DMK directly in Java 8. No TP native sidecar or Python process runs with the client.
- **Workbench boundary:** `TpLinkWorkbench` owns developer-session presentation tuning only. It does not own movement, collision, damage, equipment or server state.
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
- Bundle 1.2 corrected GZ2E01 left-side visual proof was explicitly accepted by the user: authentic TP Link rendered with authentic idle/walk/sword BCK animation, the 35-joint skeleton was preserved, cyan `0x9 handL` and red `0xA weaponL` followed the intended GameCube left sword side, and `al_swb.bmd` remained attached to `0xA weaponL` through the sword animation.
- Bundle 2.1 first in-client run loaded `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk` successfully and rendered TP Link through Matrix3. Runtime log reported `19821` vertices, `6607` triangles, `35` joints, `idleFrames=30`, `walkFrames=24`, named `idle`/`walk` animations, and `GZ2E01 Link -> Matrix Model ACTIVE`.
- The first in-client screenshot visibly showed TP Link at the local-player presentation position, verifying the local DMK parser, skinning/model-conversion path, scene hook and basic world placement.
- User screenshots at default world scale `5.0` explicitly accepted TP Link's overall height as correct relative to the revision-830 world/player scale.
- The same `5.0` screenshots showed a narrower follow-up issue: Link's body envelope is slightly too thick for the intended RuneScape equipment fit; this is proportion calibration, not a world-scale or renderer-architecture failure.

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
- Automatic Bundle 2.1 presentation still uses authentic `idle` and `walk`; the workbench can force `idle`, `walk`, or `sword` for visual preview only. Sword preview is not authoritative combat.
- TP local-player suppression reuses the existing shared `Player.method10696(...)` gate and is fail-open: normal RuneScape presentation is suppressed only after a fresh successful TP render.
- `Ctrl+Shift+L` activates presentation-only TP mode. The whole `Ctrl+L` chord is latched until release so releasing Shift first cannot accidentally trigger OoT Link.
- World scale remains uniform at accepted default `5.0`. New width/height/depth fitting is applied to skinned vertices in TP model space before player yaw; this avoids direction-dependent body thickness.
- Initial RuneScape-fit starting proportions are `width=0.92`, `height=1.00`, `depth=0.95`; these are deliberately live-tunable and still require runtime visual acceptance.
- `N64 -> TP Link` now exposes Presentation, Animation, Movement, Combat, Materials and Diagnostics sub-tabs. Movement/combat tabs document ownership boundaries until those phases are actually implemented.
- Current material rendering still samples the DMK UV/palette into Matrix face colors. Full Matrix texture/material binding is not implemented yet.
- Current DMK mesh is triangle-expanded. Proper smoothing requires imported normals or a UV/material-aware vertex weld; the UI intentionally does not expose a fake smoothing toggle.

## Evidence still pending

### NEEDS RUNTIME TEST

- Accept/reject the initial RuneScape-fit `0.92 / 1.00 / 0.95` TP model-space proportions and tune live if needed.
- Verify the new `N64 -> TP Link` workspace appears and all live presentation controls update the renderer without restart.
- Authentic idle loop while stationary and locomotion clip switching while the Matrix player position changes.
- Forced authentic sword BCK preview from the TP Link workbench.
- Facing/yaw correctness while moving in different directions.
- `Ctrl+Shift+L` release-order regression and clean restoration to normal RuneScape presentation.
- DMK color-sampling toggle and DMK reload action.

### UNKNOWN until runtime evidence

- Final RuneScape-fit width/depth values after equipment-oriented visual tuning.
- Whether yaw needs a nonzero correction.
- Whether rebuilding the Matrix `Model` on animation-frame/config changes is sufficiently smooth on the user's runtime; optimize only if profiling/runtime evidence shows a problem.
- Final TP movement/action state-machine subset and later combat/equipment integration details.
- Final Matrix-native TP texture/material and smoothing implementation.

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

**Status:** DONE / VERIFIED

#### Bundle 1.1 - One-click donor bootstrap

**Status:** DONE / VERIFIED

- [x] Pin donor repo/target.
- [x] Map human model resources, skeleton and hand/item joints.
- [x] Add one-click donor bootstrap.
- [x] Repair Python/Ninja and donor object-base runtime failures.
- [x] Complete full pinned `GZ2E01` donor build.

#### Bundle 1.2 - Link asset extraction + animation/socket proof

**Status:** DONE / VERIFIED

- [x] Deterministic local `Kmdl` / `AlAnm` extraction through decomp-toolkit VFS.
- [x] Select `al.bmd`, head/hands/face attachments and authentic WAIT / WALK-DASH / CUT BCK clips.
- [x] Validate both left `0x9/0xA` and right `0xE/0xF` hand/item pairs.
- [x] Correct active GameCube sword proof to left `0x9 handL` / `0xA weaponL`.
- [x] Attach authentic `al_swb.bmd` to `0xA weaponL` in the proof.
- [x] Generate idle/walk/sword GIFs, manifest/summary, and local f32 `tp-link-proof.dmk`.
- [x] User visually accepted the corrected left-side sword/socket proof.

### Phase 2 - Matrix3 TP Link presentation

**Status:** IN PROGRESS

#### Bundle 2.1 - Local DMK -> Matrix player presentation

**Status:** PARTIAL VERIFIED / PROPORTION + MOTION ACCEPTANCE

- [x] Add presentation-only `TP_LINK` mode without entering the existing Mario/OoT movement controller.
- [x] Load the local generated DMK from `%LOCALAPPDATA%` only. `VERIFIED`.
- [x] Parse the proven f32 mesh/texture/skeleton/skin/animation representation in Java 8. `VERIFIED` through first in-client render.
- [x] Skin and render TP Link through Matrix `Class159` / `Model` at the local player's Matrix world transform. `VERIFIED` through first in-client screenshot/log.
- [x] Select authentic idle/locomotion animation from actual local-player movement state. `verified-static`; visual transition still needs runtime acceptance.
- [x] Reuse the existing direct Matrix scene render seam. `VERIFIED`.
- [x] Reuse the existing local-player suppression seam with fail-open behavior. `verified-static`; explicit toggle/restore regression still pending.
- [x] Preserve existing `Ctrl+L` OoT Link and fix Shift release-order retrigger through one latched Ctrl+L chord. `verified-static`.
- [x] Increase TP world scale from `1.0` to `5.0`; user screenshots accepted the resulting overall height. `VERIFIED` for height.
- [x] Add model-local width/height/depth fitting, local offsets and live yaw calibration without changing world-scale ownership. `NEEDS TEST`.
- [x] Add dedicated `N64 -> TP Link` workbench with Presentation / Animation / Movement / Combat / Materials / Diagnostics tabs. `NEEDS TEST`.
- [x] Expose authentic sword BCK visual preview, animation speed, locomotion threshold/hold, DMK reload and texture-color sampling diagnostics. `NEEDS TEST`.
- [ ] Runtime accept body proportions, idle/walk transitions, sword preview, facing, and suppression/restoration/hotkey behavior.

### Phase 3 - Movement and action controller

**Status:** PLANNED

- Add `TP_LINK` as a real driver to the established shared camera-relative alternate-character controller.
- Zelda-style targeting/strafe/roll/jump presentation where compatible.
- Matrix3 remains world/collision authority unless a narrower proven donor simulation seam is justified.
- Do not create a second competing WASD/camera-relative movement owner.

### Phase 4 - Action combat + RuneScape progression

**Status:** PLANNED

- Authentic TP sword clips and contact windows.
- RuneScape Attack/Strength/Defence, NPC HP, damage, XP and requirements remain authoritative.
- RuneScape weapon attack speed drives TP presentation timing where appropriate.
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
- Bundle status: PARTIAL VERIFIED / PROPORTION + MOTION ACCEPTANCE
- Approval state: user approved the TP Link tuning/workbench continuation with `AAAA` on 2026-10-06
- Bundle 1.2 status: corrected GZ2E01 left-side model/animation/socket proof is VERIFIED; there is no remaining visual-acceptance carryover gate
- Runtime evidence: world scale `5.0` now looks correct for overall player height; Link is slightly too thick for the intended RuneScape equipment envelope
- Current implementation response: preserve world scale `5.0`, start RuneScape-fit model-local proportions at `0.92 X / 1.00 Y / 0.95 Z`, and tune live from the N64 TP Link workspace

## Testing

The authoritative steps are in `docs/tp-link/TESTLIST.md`.

### Bundle 2.1 quick path

1. Pull current `main` once.
2. Eclipse Java 8 clean/build Client; launch and log in.
3. Open Client Console -> N64 and confirm a top-level `TP Link` tab exists beside the established Mario workspace.
4. Press `Ctrl+Shift+L` once and expect `TP_LINK` plus `GZ2E01 Link -> Matrix Model ACTIVE ... scale=5.0 bodyScale=0.92/1.0/0.95 ...`.
5. In TP Link -> Presentation, tune Width/Depth live if needed while leaving World scale at `5.0` unless new height evidence contradicts the accepted screenshot.
6. Stand still, walk, and verify automatic authentic idle/walk transitions.
7. In Animation/Combat, force the authentic sword BCK preview, then return Preview to `AUTO`.
8. Check facing in at least two directions; use yaw correction only if evidence shows an offset.
9. Release Shift first once while the activation chord is still briefly held; TP mode must remain active.
10. Fully release, toggle `Ctrl+Shift+L` again, and verify the normal RuneScape local player returns.
11. Optional diagnostic: toggle DMK texture-color sampling and press Reload local DMK; neither action may break fail-open player restoration.

## Carryover / blockers

### BLOCKED

- None known. The active work is presentation/proportion acceptance, not architecture/toolchain recovery.

### Carryover

- None from Bundle 1.2. The corrected left-side sword/socket visual proof is accepted and VERIFIED.
- Full TP movement, authoritative combat, RuneScape equipment conversion, Matrix-native TP texture binding and proper smoothing remain later planned phases.

## Resume Here

**Last completed:**

- Bundle 1.1 donor bootstrap is runtime VERIFIED.
- Bundle 1.2 extraction/tool execution, local artifact generation, authentic idle/walk/sword animation proof, and corrected left `0x9 handL` / `0xA weaponL` sword/socket proof are VERIFIED by explicit user visual acceptance.
- Bundle 2.1 local DMK loading and actual TP Link rendering inside Matrix3 are runtime VERIFIED.
- First runtime log: `vertices=19821`, `triangles=6607`, `joints=35`, `idleFrames=30`, `walkFrames=24`, `scale=1.0`, `yawOffset=0.0`.
- Follow-up screenshots at world scale `5.0` accepted overall Link height; remaining visible envelope issue is slight excess width/depth.
- Live TP Link workbench and model-local proportion controls are implemented verified-static for the next runtime pass.

**Current phase:**

- Phase 2 - Matrix3 TP Link presentation (`IN PROGRESS`).

**Active bundle:**

- Bundle 2.1 - Local DMK -> Matrix player presentation (`PARTIAL VERIFIED / PROPORTION + MOTION ACCEPTANCE`).

**Next checklist item:**

- Pull/build once, open N64 -> TP Link, accept/tune `0.92 / 1.00 / 0.95` proportions, then finish idle/walk/sword-preview/facing/toggle acceptance in the same session.

**Current state / next action:**

- Do not rerun donor bootstrap or TP proof generation. The local DMK is loading correctly. Preserve world scale `5.0` unless new evidence disproves the accepted height. Use the new TP Link workspace for proportion and presentation tuning.

**Files/systems already inspected for Bundle 2.1:**

- root `AGENTS.md`
- `Client/src/main/java/game/TpLinkVisualRenderer.java`
- `Client/src/main/java/game/TpLinkWorkbench.java`
- `Client/src/main/java/game/console/TpLinkPanel.java`
- `Client/src/main/java/game/console/TpLinkN64Extension.java`
- `Client/src/main/java/game/console/ClientConsoleShell.java`
- `Client/src/main/java/game/console/N64Panel.java`
- `Client/src/main/java/game/AlternateCharacterController.java`
- `Client/src/main/java/game/Class261.java`
- existing `PlayerControllerMode`, scene render and local-player suppression seams
- `native/tools/tp-link-visual-proof.py`
- pinned demake DMK f32 format used by the local proof

**Do not re-scan without new evidence:**

- TP donor bootstrap history; Bundle 1.1 is complete.
- Active GameCube sword-side identity `0x9 handL` / `0xA weaponL`.
- Existing Matrix direct-scene render seam and shared local-player suppression seam.
- DMK `idle` / `walk` / `sword` f32 animation naming/format.
- Accepted world scale `5.0` height unless later runtime evidence contradicts it.
- TP Bundle 2.1 authority: presentation/tuning only; movement/combat/equipment ownership does not transfer to the workbench.

## Next recommended work

Runtime-test the new TP Link workbench and proportion controls. Once Bundle 2.1 presentation is accepted, start Phase 3 by wiring `TP_LINK` into the existing shared camera-relative alternate-character controller, then move into authentic TP action-state presentation and RuneScape-authoritative combat.
