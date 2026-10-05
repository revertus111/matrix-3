# Twilight Princess Link Test List

## Bundle 1.1 - Donor bootstrap

Status: `NEEDS TEST`

Use the shortest path only. Do not manually extract the whole disc.

1. Pull current `main` once.
2. Double-click root `Native Builder.bat`.
3. Confirm all three buttons are visible:
   - `BUILD + TEST MARIO`
   - `BUILD OOT`
   - `PREP + BUILD TP LINK`
4. Click `PREP + BUILD TP LINK`.
5. On first run, select the user's Twilight Princess GameCube North America disc image (`GZ2E01`).
6. Expected for raw `.iso` / `.gcm`: the build window prints `Disc identity VERIFIED: GZ2E01` before setup continues.
7. Expected setup behavior:
   - donor checkout lives under `%LOCALAPPDATA%\Matrix3\TPDecomp`, not inside the Matrix3 repository;
   - source is detached at `c8fa8c9e2aab72cf4e5db0e5d1c84a9ea6ee6eb0`;
   - Ninja is reused when present, or installed user-locally through Python when missing;
   - the selected image is hard-linked when possible, otherwise copied once;
   - `python configure.py` completes;
   - `ninja` completes;
   - window ends with `TP LINK DONOR BUILD SUCCESS`.
8. Close and click `PREP + BUILD TP LINK` a second time.
9. Expected: it reuses the prepared local source/image and does not ask the user to locate the disc again.

## Failure evidence

If it fails, send the build window log only. Do not start manually extracting folders or copying random TP files.

## Regression sanity

- Mario button still launches the Mario builder.
- OoT button still launches the OoT builder.
- Closing Native Builder still works normally.

## Not yet a runtime acceptance

A successful donor build proves only the pinned TP source/build foundation. It does **not** prove TP Link rendering, animation, movement, combat or equipment inside Matrix3. Those remain later bundle gates.
