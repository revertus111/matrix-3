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
    - TP Link's authentic body/head/hands/face render coherently enough to verify the skeleton/animation path;
    - the idle segment animates rather than remaining bind-pose/static;
    - the walk/run segment visibly animates as locomotion;
    - the sword segment visibly plays a real TP sword cut;
    - the cyan `0x9 handL` marker follows GameCube Link's animated left hand;
    - the red `0xA weaponL` marker follows the animated left item/weapon joint;
    - `al_swb.bmd` is visibly present and remains rigidly attached to red `0xA` through the sword animation.

### Bundle 1.2 runtime evidence

- The MSYS2/UCRT64 dependency bootstrap is runtime VERIFIED: pacman installed prebuilt NumPy/Pillow and the renderer ran to completion.
- Authentic donor extraction is runtime VERIFIED: `al.bmd` has 35 joints and real `waitb.bck`, `dasha.bck`, `cutl.bck` clips were selected.
- The first successfully generated GIF was **not accepted visually**. It rendered animated Link but did not render a sword model, the only socket indicator was the red `0xF` debug cross, and the launcher incorrectly labeled successful artifact generation as `PASS`.
- The second generated proof did include the authentic `al_swb.bmd` model and both `0xE handR` / `0xF weaponR` markers. User-provided GIFs showed the dark wooden sword rigidly following that right-side test socket while the opposite arm performed the prominent sword-cut motion. This proved the diagnostic had selected the wrong item side, not that the donor/toolchain failed.
- Pinned TP source confirms human Link assigns left hand/item joints `9/10` and right hand/item joints `14/15`. Because this donor target is GZ2E01 GameCube Link, the corrected active sword proof uses left `0x9 handL` / `0xA weaponL`; right `0xE/0xF` remains the alternate right-hand/item socket.
- The corrected proof generated the local f32 `tp-link-proof.dmk`.
- User explicitly accepted the corrected proof as PASS: authentic TP Link rendered with authentic idle/walk/sword BCK animation, the 35-joint skeleton was preserved, cyan `0x9 handL` and red `0xA weaponL` followed the intended GameCube left sword side, and `al_swb.bmd` remained attached to `0xA weaponL` through the sword animation.

### Acceptance boundary

Bundle 1.2 is `VERIFIED`. Do not rerun the donor proof for this controller test.

## Bundle 2.1 - Matrix3 TP Link presentation

Status: `PARTIAL VERIFIED / CONTROLLER ACCEPTANCE PENDING`

### Runtime evidence so far

- `Ctrl+Shift+L` entered `TP_LINK` and the local DMK rendered inside revision-830.
- Runtime parsed `19821` vertices, `6607` triangles, `35` joints, `30` idle frames and `24` walk frames.
- World scale `1.0` failed as dramatically undersized; follow-up screenshots at world scale `5.0` were explicitly accepted for overall height.
- The accepted-height screenshots showed Link remains slightly too thick for the intended RuneScape equipment envelope.
- World scale `5.0` remains fixed for this pass; RuneScape-fit proportions start at `0.92 / 1.00 / 0.95`.

## Phase 3 / shared playable-controller slice

Status: `IMPLEMENTED / NEEDS RUNTIME TEST`

This pass reuses Matrix3's established shared movement/combat owners. TP Link no longer integrates X/Z inside `TpLinkController` and no longer exposes a TP-only locomotion-speed control.

### Consolidated pull/build/test

1. Pull current `main` once.
2. Eclipse: clean/build **Client** with Java 8, launch normally, and log in.
3. Open Client Console -> `N64` -> `TP Link`.
4. UI acceptance:
   - there must be **one vertically scrolling TP Link workspace**, not Presentation/Animation/Movement/Combat nested tabs;
   - no horizontal scrollbar;
   - numeric controls use compact `- / direct value / +` editors;
   - Movement must show `SHARED AlternateCharacterController` as its owner;
   - there must be **no TP-only Move speed control**.
5. Press `Ctrl+Shift+L` once.
6. Expected console includes:
   - `[Alternate Character] Controller mode: TP_LINK`
   - `[TP] Controls: shared camera-relative WASD, F authentic sword, Shift target lock`
   - `[TP] AlternateCharacterController owns movement/clipping; Matrix owns server-authoritative melee.`
   - the existing TP DMK load/ACTIVE lines.
7. Release **Shift first** while still briefly holding `Ctrl+L` from activation. Expected: mode stays `TP_LINK`, not OoT `LINK`.
8. WASD shared-controller movement:
   - with camera north, W moves Link forward relative to the camera;
   - rotate the camera east/west/south and W follows the new camera direction;
   - A/S/D work consistently;
   - existing N64 camera WASD must not also move while TP owns controls;
   - movement should behave as one shared controller path, not a TP-specific acceleration/speed profile.
9. Locomotion presentation:
   - authentic walk takes over while moving;
   - authentic idle returns after movement stops;
   - Player Fit world scale remains `5.0`.
10. Facing:
   - Link turns toward movement direction rather than sliding sideways;
   - facing smoothing is controlled by `Facing turn speed (deg/sec)` and is presentation-only;
   - if he is consistently backward/offset, tune only `Yaw correction` and report the required value.
11. Shared clipping sanity:
   - toggle `RuneScape clipping (tile authority)` once and confirm the established shared clipping mode still works;
   - turning TP mode off must restore the shared horizontal state cleanly.
12. Shift target lock:
   - release the activation chord fully, then hold Shift near an NPC;
   - TP Link should report `Target = NPC <index>` when a valid target is acquired;
   - while locked, Link should face the target and movement should use the shared target-relative basis;
   - releasing Shift clears the lock.
13. F sword action:
   - stand within melee range of an NPC and tap F once;
   - authentic `sword` BCK must play once, not loop forever;
   - the Combat card should show the current sword frame while active;
   - at the configured `Contact frame`, the shared combat bridge sends one manual melee intent;
   - expected console on a valid hit includes `[Alt Character Combat] TP_LINK immediate contact -> target=...`;
   - holding F must not spam repeated attacks; release and press again for the next swing.
14. Contact/range sanity:
   - F with no valid NPC in the forward/locked melee envelope should visibly swing but report a miss/no server contact;
   - no click-to-attack or repeating auto-combat should start.
15. Combat authority sanity:
   - Matrix/server remains authoritative for legality, range, cooldown, accuracy, damage, XP, death and drops;
   - this pass does not replace equipped RuneScape weapon visuals yet.
16. TP panel actions:
   - `Preview sword` plays the authentic sword once without damage;
   - `Stop / AUTO` returns presentation to automatic idle/walk;
   - `Reload DMK` still reloads fail-open;
   - texture-sampling toggle still changes the existing colour fallback without crashing.
17. Toggle `Ctrl+Shift+L` off after fully releasing the first chord. Expected: normal RuneScape body/control returns cleanly.
18. Mario and OoT Link regression: their existing controllers and N64 workspace remain unchanged.

### verified-static boundary

- `TP_LINK` is a distinct `AlternateCharacterController.CharacterId` and driver.
- `AlternateCharacterController` owns TP's camera-relative input mapping, frame-normalized Matrix-driven X/Z integration, `AlternateCharacterFreeMovement`, optional clipping and restore behavior.
- `TpLinkController` no longer stores `nativeX/nativeZ`, no longer owns a movement clock, and has no TP-specific movement-speed setting.
- TP targeting uses `AlternateCharacterCombatBridge.updateTargeting(...)`.
- F starts the existing authentic DMK `sword` clip once and uses `AlternateCharacterCombatBridge.requestPrimaryMeleeAttack()` only at the configured contact frame.
- RuneScape server combat remains authoritative; no TP damage formula was added.
- TP facing writes only the local Matrix player transform rotation from the shared controller's movement/target direction; yaw correction remains presentation tuning.
- The TP console is one width-tracking vertical workspace with compact direct-entry numeric controls.
- Full runtime acceptance is still required before marking the playable-controller slice VERIFIED.

## Regression sanity

- Mario button still launches the Mario builder.
- OoT button still launches the OoT builder.
- `PREP + BUILD TP LINK` and `PROBE TP LINK` remain unchanged.
- N64 -> Mario 64 existing Runtime / Custom combat / Equipment Workbench behavior is unchanged.
- N64 -> TP Link remains a sibling top-level game workspace.
- `Ctrl+L` remains the OoT Link controller toggle after the chord is fully released.
- `Ctrl+Shift+L` owns TP Link presentation + Matrix-owned action controls.

## Current acceptance boundary

Bundle 1.1 and 1.2 remain VERIFIED. Bundle 2.1 has runtime-verified TP rendering and accepted world-height scale. The current gate is one consolidated runtime pass for the flattened TP workspace, model proportions, shared-controller camera-relative WASD/facing, Shift targeting, authentic one-shot F sword/contact, and clean mode restoration.
