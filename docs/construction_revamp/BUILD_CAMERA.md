# Construction Build Camera

## Purpose

Construction placement should use a dedicated detached build camera instead of moving the player around the settlement while building.

The player remains physically planted while the camera moves independently. Construction still uses Matrix3's normal scene targeting for the hovered tile, the client-only ghost for preview, and server-authoritative placement for the confirmed object.

## Ownership

- Matrix3's existing Orb of Oculus/free-camera path remains the camera authority.
- Construction owns only build-camera lifecycle, view selection and build-mode UX.
- Construction does not create a second camera physics implementation.
- Player/world movement remains Matrix3/server-owned.
- Scene picking remains Matrix3-owned; Construction only consumes the already resolved hovered tile.
- Confirmed placement remains server authoritative.

## Accepted controls and behavior

### Free Build view — first runtime target

- WASD moves the detached camera if the current Matrix3 Orb implementation exposes its expected movement controls.
- Q/E vertical movement is expected from the existing Orb mode but remains runtime acceptance pending.
- The player does not move with the camera.
- Clicking a world tile stops the build interaction at that target and places/selects there rather than intentionally walking the player.
- Existing Construction piece controls remain independent: R / Shift+R rotate the selected piece, and the current palette mouse-wheel behavior remains piece rotation until a later camera-zoom interaction is deliberately designed.
- Closing Construction build mode restores the normal Matrix3 camera/interface state.

The actual WASD/Q/E control behavior is not classified VERIFIED until tested on the current Matrix3 client.

## Accepted view roadmap

### Free Build

Detached free camera for close/manual building. This is the first implementation target and uses Matrix3's existing Orb camera path.

### RTS

Elevated angled settlement overview intended for broad placement, worker observation and later settlement management. WASD pans across the settlement while the player remains stationary.

### Top Down

Near-vertical layout view intended for floor plans, production layouts and large-area building tools.

### Orbit / Focus

Camera revolves around or focuses a selected tile, structure or later worker. This is later interaction polish.

### Player View

Return to the normal Matrix3 gameplay camera without changing settlement or placement ownership.

## Preset implementation rule

Matrix3 already exposes camera position/look/rotation/reset packet APIs, but the exact packet semantics and safe interaction with Orb freecam are not yet verified for Construction presets.

Do not hardcode guessed RTS/top-down pitch, yaw, height or packet sequences. First runtime-accept the Orb-based Free Build lifecycle, then trace only the smallest packet/client seam needed to make deterministic view presets.

## Runtime acceptance — Free Build v1

- Opening the Construction palette enters the detached Orb build camera.
- Entering the camera stops current player movement/actions and leaves the player physically in place.
- WASD moves the camera without moving the player.
- Q/E adjusts camera height if the current Matrix3 Orb implementation supports it.
- Hovered-tile tracking and the white/translucent ghost continue working while detached.
- Rotation 0-3 and piece switching continue working while detached.
- Clicking the target tile creates exactly one server-authoritative placed object and does not intentionally walk the player.
- If stock action-23 Walk Here still moves the player in Orb mode, that is runtime evidence for a narrow Construction-only click-consumption patch; do not pre-emptively replace Matrix3 input ownership.
- Closing/cancelling the palette exits build camera and restores the normal root interface/camera.
- No stuck Orb interface, camera instability, ghost duplication/flicker or scene-render regression occurs.

## Classification

- `verified-static`: `InterfaceManager.gazeOrbOfOculus()` stops the player and opens Matrix3's existing Orb camera interfaces.
- `verified-static`: the Orb close callback restores the default root interface and calls `sendResetCamera()`.
- `verified-static`: `Player.closeInterfaces()` runs that close callback.
- `verified-static`: Construction can request the mode through the existing owner-only Client Console command bridge without taking camera or movement ownership.
- `UNKNOWN` until runtime test: current-client WASD/Q/E behavior, whether normal Walk Here is suppressed by Orb mode, and the exact safe RTS/top-down preset packet semantics.
