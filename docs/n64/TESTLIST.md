# N64 Client Console Runtime Test List

## Workspace / navigation

- [ ] Eclipse Java 8 clean/build succeeds and the client launches normally.
- [ ] Client Console rail shows a dedicated `N64` main tab beside the other main console destinations.
- [ ] Test Console flyout no longer lists `N64`.
- [ ] Clicking the N64 main tab opens the N64 workspace directly.
- [ ] Opening N64 shows a game-level tab strip with `Mario 64` as the first sub-tab.
- [ ] Clicking the active N64 rail button again collapses the console consistently with the other main tabs.
- [ ] Switching away from N64 and back during the same client session keeps the workspace usable without creating duplicate recorders or windows.
- [ ] A saved active panel id of `n64` restores the N64 main panel through `ClientConsoleShell` normalization.

## Mario 64 live snapshot

- [ ] In RuneScape mode, Controller mode shows `RUNESCAPE`, Active character shows `NONE`, and Suppress RuneScape body shows `NO`.
- [ ] Enter Mario mode with Ctrl+M. Bridge moves to `READY` after native startup and native Sequence/Action/Animation/XYZ fields begin updating.
- [ ] Camera forward changes when the Matrix camera rotates; sampled movement reflects WASD.
- [ ] Space/F/Shift physical states reflect the held keys and forwarded A/B/Z reflect the post-entry-guard values actually sent to libsm64.
- [ ] Native frame age normally stays low while the sidecar is healthy; sequence advances without repeated gaps.

## Mario Equipment Workbench - one-pass acceptance

1. [ ] Open `N64 -> Mario 64 -> Equipment Workbench`; the original runtime recorder remains under the sibling `Runtime` tab.
2. [ ] Equip Statius's full helm and enter Mario mode; Active Equipment reports the correct item id/name and `3D head tracking` becomes `ACTIVE` after the head basis is captured.
3. [ ] Before freezing, move/idle Mario and confirm the V4 correction no longer drives the helmet opposite to Mario's animated head. Turn/nod/tilt should move in the same direction.
4. [ ] Press `Freeze pose`; Mario's visible pose stops while the libsm64 bridge remains healthy.
5. [ ] Change Scale, X, Y, Z and Yaw from the workbench using both direct text entry and -/+ controls. Helmet updates live without client restart or native bridge rebuild.
6. [ ] `Flip helmet 180°` changes only the active helmet session yaw by 180 degrees; `Reset transform` returns scale `1.0`, XYZ `0`, yaw delta `0`.
7. [ ] Enable `live head cut` with `Only cut while a helmet is equipped`; masked source triangle count becomes greater than zero.
8. [ ] While still frozen, lower/raise `Cut starts at body height %` and change `Head cut radius %`; Mario model rebuilds immediately and the visible cut changes without unfreezing.
9. [ ] Use the helmet-safe preset (`72%` / `40%`) as a starting point. Adjust until cap/hair/top-skull clipping is reduced while Mario's central face, moustache and nose remain visible.
10. [ ] Disable the mask and confirm full Mario head geometry returns immediately.
11. [ ] Re-enable mask, unequip the helmet with helmet-only masking enabled, and confirm the head cut no longer applies.
12. [ ] Unfreeze and test idle, turn/run, jump, backflip and ground-pound. Helmet should follow head translation/orientation and saved head-local offsets should stay attached to the skull.
13. [ ] Press `Save profile .md`; status reports a path ending in `docs/n64/MARIO_EQUIPMENT_RUNTIME.md` and the file contains the current item transform/mask values plus a link to `TRANSFORM_CONVENTIONS.md`.
14. [ ] `Copy markdown` places the same profile text on the clipboard.
15. [ ] Ctrl+M back to RuneScape restores the normal player body with no floating helmet, stuck freeze or head cut.

## Long-idle / sleep-state bridge-stall fix v2

1. [ ] Pull current `main`, rebuild `native/sm64-bridge/dist/sm64_bridge.exe`, and confirm the client console prints `[SM64 Bridge] sleep-guard-v2 active` when Mario mode starts. If that marker is absent, stop: an older executable is still being launched.
2. [ ] Enter Mario mode and leave Mario completely idle longer than the previous failure window (at least 90 seconds).
3. [ ] N64 -> Mario 64 sequence continues advancing throughout the idle period; frame age remains low instead of climbing into multi-second values.
4. [ ] The published runtime stream must not remain in `0x0C400202` or `0x0C000203`. When libsm64 attempts autonomous sleep, stderr may report `[SM64 Bridge] blocked autonomous sleep ... -> idle`, and the published action should return immediately to normal idle.
5. [ ] `Suppress RuneScape body` stays `YES` while Mario mode remains active and the normal RuneScape body never replaces Mario during long idle.
6. [ ] After the long idle, press Space once: Mario jumps normally and no RuneScape body appears underneath/after the jump.
7. [ ] Verify WASD movement, F/B attack input and Shift crouch/ground-pound still function after the intercepted sleep transition.

## Idle -> Space regression flight-recorder gate

1. [ ] Enter Mario mode and wait idle for several seconds.
2. [ ] Clear Events in N64 -> Mario 64.
3. [ ] Press Space once and watch for the reported RuneScape-body flash/reappearance.
4. [ ] Event log records `SPACE_DOWN` and `A_SEND_DOWN`.
5. [ ] Native `ACTION` and/or `ANIM` transitions are recorded as the jump begins.
6. [ ] If the RuneScape body appears, copy the event log immediately. The critical evidence is any `SUPPRESS_RS -> false` transition and its native sequence/frame age/Space/A state.
7. [ ] If no flash appears, keep the log long enough to confirm suppression stays continuously true through the idle -> jump transition.

## Recorder controls

- [ ] `Pause display` freezes only the Swing presentation; after resuming, newly captured runtime events appear.
- [ ] `Auto-scroll` keeps the newest event visible when enabled.
- [ ] `Copy snapshot` places the current Mario diagnostics snapshot on the clipboard.
- [ ] `Copy events` places the bounded event history on the clipboard.
- [ ] `Clear events` clears history without stopping the recorder or changing Mario runtime state.

## Regression boundary

- [ ] N64 diagnostics/workbench do not change server authority, RuneScape equipment definitions, libsm64 stepping ownership or normal RuneScape appearance outside Mario mode.
- [ ] Ctrl+M back to RuneScape restores the normal player body/input and the N64 panel reports the transition.
