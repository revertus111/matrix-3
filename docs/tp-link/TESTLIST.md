# Twilight Princess Link Test List

## Bundle 1.1 - Donor bootstrap

Status: `VERIFIED`

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
   - `orig\GZ2E01` is a directory, matching decomp-toolkit's `object_base` contract;
   - the selected image keeps its filename/extension inside `orig\GZ2E01` and is hard-linked when possible, otherwise copied once;
   - if a previous builder version left `orig\GZ2E01` as a file, the builder removes only that local workspace link/copy and repairs the layout automatically;
   - `configure.py` completes;
   - `ninja` completes;
   - window ends with `TP LINK DONOR BUILD SUCCESS`.
9. Optional regression check: close and click `PREP + BUILD TP LINK` a second time.
10. Expected: it reuses the prepared local source/image and does not ask the user to locate the disc again.

## Runtime evidence

- First runtime attempt failed before disc selection because Windows resolved `python.exe` to the Microsoft Store alias and returned exit code `9009` while Ninja was missing. Builder recovery was patched.
- NKit v1 input was correctly rejected early on the next attempt.
- A supported `.ciso` was then found automatically; Python, Ninja, donor clone and pinned checkout all succeeded.
- That attempt failed at `dtk dol split` with `orig/GZ2E01/files/RELS.arc not found` because the builder had incorrectly made `orig/GZ2E01` itself the disc-image file. The object-base layout was repaired.
- Final user rerun completed the full pinned `GZ2E01` donor build.
- `CHECK config\GZ2E01\build.sha1` reported `758 files OK`.
- Build report reported all code/data `100.00% matched`; overall linking was `87.13%` (`2583 / 2608 files`).
- Builder ended with `TP LINK DONOR BUILD SUCCESS`.

## Regression sanity

- Mario button still launches the Mario builder.
- OoT button still launches the OoT builder.
- Closing Native Builder still works normally.

## Acceptance boundary

Bundle 1.1 is runtime VERIFIED. This proves only the pinned TP source/build foundation. It does **not** prove TP Link rendering, animation, movement, combat or equipment inside Matrix3. Those remain later bundle gates.
