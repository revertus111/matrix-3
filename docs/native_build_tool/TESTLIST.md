# Matrix3 Native Builder runtime check

## VERIFIED 2026-10-05

- [x] Double-clicking `Native Builder.bat` opens the launcher.
- [x] `BUILD + TEST MARIO` runs the Mario bridge build without manual terminal setup.
- [x] The Mario helper reaches the existing combat test and reports `PASS`.
- [x] The build window ends with `MARIO BUILD + TEST SUCCESS` and remains open for inspection.

## Remaining optional check

1. Optional OoT check: click `BUILD OOT`; confirm it uses `native/oot-bridge`. If CMake is missing, it must report that directly instead of accidentally running another bridge build.

No manual `cd`, `make`, or current-terminal-directory setup is required for normal Mario native builds.
