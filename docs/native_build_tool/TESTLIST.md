# Matrix3 Native Builder runtime check

1. Pull `main`, then double-click `Native Builder.bat` from the repository root.
2. Confirm the small `Matrix3 Native Builder` window opens with `BUILD + TEST MARIO` and `BUILD OOT` buttons.
3. With no terminal preparation, click `BUILD + TEST MARIO`.
4. Confirm the helper uses `native/sm64-bridge`, stops any stale `sm64_bridge.exe`, runs the existing bootstrap target, then runs `make test-combat`.
5. Confirm the build window remains open and ends with either `MARIO BUILD + TEST SUCCESS` or `MARIO BUILD FAILED`.
6. Optional OoT check: click `BUILD OOT`; confirm it uses `native/oot-bridge`. If CMake is missing, it must report that directly instead of accidentally running another bridge build.

No manual `cd`, `make`, or current-terminal-directory setup should be required for normal use.
