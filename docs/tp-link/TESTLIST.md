# Twilight Princess Link Test List

## Bundle 1.1 - Donor bootstrap

Status: `VERIFIED`

Use the shortest path only. Do not manually extract the whole disc.

1. Pull current `main` once.
2. Put one supported Twilight Princess GameCube North America image (`GZ2E01`) directly in the Matrix3 `native` folder when practical.
   - Supported donor formats: `.iso` / `.gcm`, `.rvz`, `.wia`, `.wbfs`, `.ciso`, `.nfs`, `.gcz`, `.tgc`.
   - `.nkit.iso` is intentionally rejected before dependency setup because the TP decomp does not document NKit v1 as a supported donor input.
3. Double-click root `Native Builder.bat`.
4. Confirm the donor-build buttons are visible, including `PREP + BUILD TP LINK`.
5. Click `PREP + BUILD TP LINK` only if the donor workspace must be rebuilt.
6. Expected image behavior:
   - if exactly one matching TP image is in `native`, the builder finds it automatically and prints its path;
   - otherwise the picker opens in `native`;
   - raw `.iso` / `.gcm` must print `Disc identity VERIFIED: GZ2E01` before setup continues.
7. Expected dependency behavior:
   - the Windows Store/App Execution Alias `python.exe` stub is ignored;
   - existing `py`, native Python, or Matrix3 MSYS2 Python is reused when functional;
   - existing PATH/MSYS2 Ninja is reused;
   - if Python or Ninja is missing, the builder installs UCRT64 Python + Ninja through the existing Matrix3 MSYS2 toolchain rather than requiring a manual install.
8. Expected setup behavior:
   - donor checkout lives under `%LOCALAPPDATA%\Matrix3\TPDecomp`, not inside the Matrix3 repository;
   - source is detached at `c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0`;
   - `orig\GZ2E01` is a directory, matching decomp-toolkit's `object_base` contract;
   - the selected image keeps its filename/extension inside `orig\GZ2E01` and is hard-linked when possible, otherwise copied once;
   - if a previous builder version left `orig\GZ2E01` as a file, the builder removes only the builder-created `orig\GZ2E01` link/copy and repairs the layout automatically;
   - `configure.py` completes;
   - `ninja` completes;
   - window ends with `TP LINK DONOR BUILD SUCCESS`.
9. Optional regression check: close and click `PREP + BUILD TP LINK` a second time.
10. Expected: it reuses the prepared local source/image and does not ask the user to locate the disc again.

## Bundle 1.1 runtime evidence

- First runtime attempt failed before disc selection because Windows resolved `python.exe` to the Microsoft Store alias and returned exit code `9009` while Ninja was missing. Builder recovery was patched.
- NKit v1 input was correctly rejected early on the next attempt.
- A supported `.ciso` was then found automatically; Python, Ninja, donor clone and pinned checkout all succeeded.
- That attempt failed at `dtk dol split` with `orig/GZ2E01/files/RELS.arc not found` because the builder had incorrectly made `orig/GZ2E01` itself the disc-image file. The object-base layout was repaired.
- Final user rerun completed the full pinned `GZ2E01` donor build.
- `CHECK config\GZ2E01\build.sha1` reported `758 files OK`.
- Build report reported all code/data `100.00% matched`; overall linking was `87.13%` (`2583 / 2608 files`).
- Builder ended with `TP LINK DONOR BUILD SUCCESS`.

## Bundle 1.2 - Link asset + animation proof

Status: `VERIFIED`

The corrected GZ2E01 left-side proof was explicitly accepted by the user. Do not rerun it unless later runtime evidence indicates a regression in the donor/visual-proof path.

### One-click test

1. Pull current `main` once.
2. Double-click root `Native Builder.bat`.
3. Confirm the `PROBE TP LINK` button is visible.
4. Click `PROBE TP LINK`.
5. Expected extraction behavior:
   - reuses the already-verified donor workspace at `%LOCALAPPDATA%\Matrix3\TPDecomp`;
   - uses decomp-toolkit VFS to extract only the `Kmdl` and `AlAnm` Link resource archives into `%LOCALAPPDATA%\Matrix3\TPLinkProof`;
   - does not copy Nintendo assets into the Matrix3 repository.
6. Expected static asset proof:
   - finds `al.bmd`, `al_head.bmd`, `al_hands.bmd`, `al_face.bmd` and the Kmdl `al_swb.bmd` wooden-sword resource;
   - selects a real WAIT-family idle BCK;
   - selects a real WALK/DASH-family locomotion BCK;
   - selects a real CUT-family sword BCK;
   - validates human Link's left hand/item pair `0x9 handL` / `0xA weaponL` and right hand/item pair `0xE handR` / `0xF weaponR`;
   - identifies `0x9/0xA` as the active sword-side pair for the GZ2E01 GameCube Link proof;
   - rejects selected BCKs that do not contain the complete human item-joint range through `0xF`.
7. Expected visual-tool behavior:
   - reuses the already-verified Matrix3 MSYS2/UCRT64 interpreter at `C:\msys64\ucrt64\bin\python.exe`;
   - if NumPy/Pillow imports are missing, `C:\msys64\usr\bin\pacman.exe` installs only the prebuilt UCRT64 packages `mingw-w64-ucrt-x86_64-python-numpy` and `mingw-w64-ucrt-x86_64-python-pillow`;
   - no `pip` source build, `winget`, private CPython installer, Windows Python registry discovery, or visual-proof venv is required on this path;
   - clones `snuri00/demake-engine` outside Matrix3 under `%LOCALAPPDATA%\Matrix3\TPLinkTools\demake-engine`;
   - checks out exact commit `a134ff49cc74585c6b11f881293796e45c973c75`;
   - after the pinned tool is ready, the probe should print `Using Matrix3 MSYS2/UCRT64 Python visual toolchain.` before launching the actual Link renderer.
8. Expected visual output:
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\idle.gif`
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\walk.gif`
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\sword.gif`
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.gif`
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk`
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\visual-proof.json`
   - `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\visual-summary.txt`
9. The combined `tp-link-proof.gif` should open automatically. A successful script exit ends with `TP LINK VISUAL PROOF GENERATED`, not `PASS`; generation alone is not visual acceptance.
10. Visually accept only if all of these are true:
    - TP Link's authentic body/head/hands/face render coherently enough to verify the skeleton/animation path; the software proof renderer does not need final-game material quality;
    - the idle segment animates rather than remaining bind-pose/static;
    - the walk/run segment visibly animates as locomotion;
    - the sword segment visibly plays a real TP sword cut;
    - the cyan `0x9 handL` marker follows GameCube Link's animated left hand;
    - the red `0xA weaponL` marker follows the animated left item/weapon joint;
    - `al_swb.bmd` is visibly present and remains rigidly attached to red `0xA` through the sword animation, proving the active GameCube sword seam that a replacement RuneScape weapon can use later.

### Bundle 1.2 runtime evidence

- The MSYS2/UCRT64 dependency bootstrap is runtime VERIFIED: pacman installed prebuilt NumPy/Pillow and the renderer ran to completion.
- Authentic donor extraction is runtime VERIFIED: `al.bmd` has 35 joints and real `waitb.bck`, `dasha.bck`, `cutl.bck` clips were selected.
- The first successfully generated GIF was **not accepted visually**. It rendered animated Link but did not render a sword model, the only socket indicator was the red `0xF` debug cross, and the launcher incorrectly labeled successful artifact generation as `PASS`.
- The second generated proof did include the authentic `al_swb.bmd` model and both `0xE handR` / `0xF weaponR` markers. User-provided GIFs showed the dark wooden sword rigidly following that right-side test socket while the opposite arm performed the prominent sword-cut motion. This proved the diagnostic had selected the wrong item side, not that the donor/toolchain failed.
- Pinned TP source confirms human Link assigns left hand/item joints `9/10` and right hand/item joints `14/15`. Because this donor target is GZ2E01 GameCube Link, the corrected active sword proof uses left `0x9 handL` / `0xA weaponL`; right `0xE/0xF` remains the alternate right-hand/item socket.
- The corrected proof generated the local f32 `tp-link-proof.dmk`.
- User explicitly accepted the corrected proof as PASS: authentic TP Link rendered with authentic idle/walk/sword BCK animation, the 35-joint skeleton was preserved, cyan `0x9 handL` and red `0xA weaponL` followed the intended GameCube left sword side, and `al_swb.bmd` remained attached to `0xA weaponL` through the sword animation.

### Acceptance boundary

Bundle 1.2 is `VERIFIED`. The failure classifications below remain useful only for future regression diagnosis:

- **asset/extraction failure:** missing BMD/BCK or wrong archive path;
- **decoder failure:** BMD/BCK parser/converter rejects authentic TP data;
- **geometry/material failure:** Link animates but parts/texture/mesh are too broken to validate the character;
- **animation failure:** mesh renders but BCK pose/playback is wrong;
- **socket failure:** `0x9` tracks the left hand but red `0xA` / attached `al_swb.bmd` does not remain on the intended active sword socket.

## Bundle 2.1 - Matrix3 TP Link presentation

Status: `PARTIAL VERIFIED / PROPORTION + MOTION ACCEPTANCE`

This is still presentation only. It does **not** add authoritative TP movement, damage, NPC combat, RuneScape weapon replacement, or a native TP sidecar.

### Runtime evidence so far

- `Ctrl+Shift+L` entered `TP_LINK`.
- The client loaded `C:\Users\rever\AppData\Local\Matrix3\TPLinkProof\visual\tp-link-proof.dmk`.
- Runtime parsed `19821` vertices, `35` joints, `30` idle frames and `24` walk frames.
- Matrix rendering reported `19821` vertices / `6607` triangles / `35` joints with named `idle` and `walk` animations.
- The first screenshot proved the architecture path but showed `scale=1.0` was dramatically undersized.
- The next screenshots at world scale `5.0` were explicitly accepted by the user as correct overall height.
- Those same screenshots showed Link is slightly too thick for the intended RuneScape equipment envelope.
- World scale `5.0` is therefore retained. Proportion calibration now happens in TP model space before yaw, starting at `width=0.92`, `height=1.00`, `depth=0.95`.

### Consolidated workbench / remainder test

1. Pull current `main` once.
2. Confirm `%LOCALAPPDATA%\Matrix3\TPLinkProof\visual\tp-link-proof.dmk` still exists. Do not regenerate it.
3. Eclipse: clean/build the **Client** with Java 8, then launch normally and log in.
4. Open Client Console -> `N64`.
5. Confirm the established Mario workspace is still present and a top-level `TP Link` tab now exists.
6. Open `TP Link -> Presentation`.
   - expected World scale: `5.0`;
   - expected Body width: `0.92`;
   - expected Body height: `1.00`;
   - expected Body depth: `0.95`;
   - keep world scale `5.0` unless new evidence contradicts the already-accepted height;
   - tune Width/Depth live until Link looks appropriately narrow for future RuneScape equipment.
7. Press `Ctrl+Shift+L` once.
8. Expected console sequence includes:
   - `[Alternate Character] Controller mode: TP_LINK`
   - `[TP Visual] Loaded local TP Link DMK: ... swordFrames=...`
   - `[TP Visual] GZ2E01 Link -> Matrix Model ACTIVE ... scale=5.0 bodyScale=0.92/1.0/0.95 yawOffset=0.0 ...`
9. Stand still for a few seconds. Expected: authentic TP idle visibly loops.
10. Move using normal RuneScape movement. Expected: authentic TP locomotion visibly takes over while position changes, then returns to idle after stopping.
11. In `TP Link -> Animation`, verify Preview=`AUTO`, then try forced `IDLE` and `WALK`; return to `AUTO` afterward.
12. In `TP Link -> Combat`, click `Preview authentic sword BCK`.
   - expected: the authentic DMK sword clip plays visually;
   - this does **not** deal damage or change RuneScape combat state;
   - click `Return to AUTO` afterward.
13. Check at least two movement directions. Report if Link faces correctly, backward, sideways, or otherwise needs yaw calibration. If needed, change only the Yaw correction control first.
14. Hotkey regression: after TP mode activates, release **Shift first** while still briefly holding `Ctrl+L`. Expected: mode stays `TP_LINK`; it must not jump to OoT `LINK`.
15. Fully release the chord, then press `Ctrl+Shift+L` again. Expected: controller mode returns to `RUNESCAPE` and the normal local-player presentation returns.
16. Open `TP Link -> Materials`.
   - `Use DMK UV/palette color sampling` ON is the current face-colour texture fallback;
   - toggling it OFF should visibly switch to vertex-colour-only output without crashing;
   - there is intentionally no fake smoothing toggle yet because the DMK mesh is triangle-expanded and proper smoothing requires normals or a seam-aware weld.
17. Open `TP Link -> Diagnostics` and press `Reload local DMK` once.
   - expected: one reload log, then TP Link resumes rendering;
   - fail-open suppression must keep/restore the RuneScape body if the local DMK cannot be reloaded.
18. Remote players/NPCs and normal RuneScape camera/world/input should remain unaffected.

### Fail-open/static boundary

- `VERIFIED`: local generated DMK loads and renders as a Matrix model in the live client.
- `VERIFIED`: world scale `5.0` is accepted for overall TP Link height.
- `verified-static`: model-local X/Y/Z proportion fitting occurs before player yaw, preventing width/depth from becoming world-axis dependent.
- `verified-static`: initial RuneScape-fit body envelope is `0.92 / 1.00 / 0.95`; final width/depth still need user visual acceptance.
- `verified-static`: local-player suppression is shared through the existing `Player.method10696(...)` gate and only returns true after `TpLinkVisualRenderer` records a successful fresh Matrix render.
- `verified-static`: missing/invalid DMK, failed skin/model conversion, missing player transform, or render exceptions leave the RuneScape local player visible.
- `verified-static`: the renderer consumes only the local generated DMK and does not commit or ship Nintendo assets.
- `verified-static`: the workbench reads the existing `sword` ANIM chunk only for visual preview; no combat authority is transferred.
- Full Bundle 2.1 `VERIFIED` still requires body-proportion, idle/walk, sword-preview, facing, hotkey-release and restoration acceptance.

## Regression sanity

- Mario button still launches the Mario builder.
- OoT button still launches the OoT builder.
- `PREP + BUILD TP LINK` still launches donor prep/build.
- `PROBE TP LINK` launches only the local visual proof.
- N64 -> Mario 64 existing Runtime / Custom combat / Equipment Workbench behavior is unchanged.
- N64 -> TP Link appears as a sibling top-level game workspace.
- `Ctrl+L` remains the OoT Link controller toggle after the chord is fully released.
- `Ctrl+Shift+L` is TP Link presentation only.
- Closing Native Builder still works normally.

## Current acceptance boundary

Bundle 1.1 is runtime VERIFIED. Bundle 1.2 is VERIFIED from the explicitly accepted corrected GZ2E01 left-side model/animation/socket proof. Bundle 2.1 has a runtime-verified in-client render path and accepted world-height scale; the current gate is TP body proportion plus idle/walk/sword-preview/facing/hotkey/restoration/workbench acceptance.
