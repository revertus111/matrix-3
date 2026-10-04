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

## Long-idle / sleep-state bridge-stall fix

1. [ ] Rebuild `native/sm64-bridge/dist/sm64_bridge.exe` from the patched bridge source before launching Matrix3.
2. [ ] Enter Mario mode and leave Mario completely idle longer than the previous failure window (at least 90 seconds).
3. [ ] N64 -> Mario 64 sequence continues advancing throughout the idle period; frame age remains low instead of climbing into multi-second values.
4. [ ] Mario does not settle into persistent action `0x0C000203`; the bridge may log `Sleep state ... -> idle to preserve frame streaming` when the autonomous sleep transition is intercepted.
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

- [ ] N64 diagnostics do not alter Ctrl+M lifecycle, movement, jump, attack/crouch controls, Mario rendering, RuneScape combat, sidecar stepping or server authority.
- [ ] Ctrl+M back to RuneScape restores the normal player body/input and the N64 panel reports the transition.
