# Twilight Princess Link Test List

## Bundle 1.1 - Donor bootstrap

Status: `NEEDS TEST`

Use the shortest path only. Do not manually extract the whole disc.

1. Pull current `main` once.
2. Put one supported Twilight Princess GameCube North America image (`GZ2E01`) directly in the Matrix3 `native` folder when practical.
   - Supported donor formats: `.iso` / `.gcm`, `.rvz`, `.wia`, `.wbfs`, `.ciso`, `.nfs`, `.gcz`, `.tgc`.
   - `.nkit.iso` is intentionally rejected before dependency setup because the TP decomp does not document NKit v1 as a supported donor input.
3. Double-click root `Native Builder.bat`.
4. Confirm all three buttons are visible:
   - `BUILD + TEST MARIO`
   - `BUILD OOT`
   - `PREP + BUILD TP LINK`
5. Click `PREP + BUILD TP LINK`.
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
   - the selected image is hard-linked when possible, otherwise copied once;
   - `configure.py` completes;
   - `ninja` completes;
   - window ends with `TP LINK DONOR BUILD SUCCESS`.
9. Close and click `PREP + BUILD TP LINK` a second time.
10. Expected: it reuses the prepared local source/image and does not ask the user to locate the disc again.

## Known first-run evidence

- First runtime attempt failed before disc selection because Windows resolved `python.exe` to the Microsoft Store alias and returned exit code `9009` while Ninja was missing.
- The user's current file in `native` is named `Legend of Zelda, The - Twilight Princess (USA).nkit.iso`; this is a known unsupported-input blocker and should be replaced with a supported `GZ2E01` image before the next build attempt.

## Failure evidence

If it fails, send the build window log only. Do not start manually extracting folders or copying random TP files.

## Regression sanity

- Mario button still launches the Mario builder.
- OoT button still launches the OoT builder.
- Closing Native Builder still works normally.

## Not yet a runtime acceptance

A successful donor build proves only the pinned TP source/build foundation. It does **not** prove TP Link rendering, animation, movement, combat or equipment inside Matrix3. Those remain later bundle gates.
