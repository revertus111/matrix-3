# Construction Build Camera

## Purpose

Construction placement should use a dedicated detached build camera instead of moving the player around the settlement while building.

The player remains physically planted while the camera moves independently. Construction still uses Matrix3's normal scene targeting for the hovered tile, the client-only ghost for preview, and server-authoritative placement for the confirmed object.

## Ownership

- Construction owns only build-camera lifecycle, view selection and build-mode UX.
- Player/world movement remains Matrix3/server-owned.
- Scene picking remains Matrix3-owned; Construction only consumes the already resolved hovered tile.
- Confirmed placement remains server authoritative.
- The current Matrix3 client camera/input owner for a safe detached modern camera is still UNKNOWN and must be verified before implementation.
- Do not use the legacy Orb-of-Oculus interface path for Construction.

## Runtime finding — legacy Orb path rejected

Opening the Construction palette with the first Build Camera v1 implementation runtime-crashed the current client.

Observed fatal chain:

`SET_INTERFACE -> Class104_Sub1.method9918(...) -> Class512.method6083(...) -> Class83.method1256(...) -> ArrayIndexOutOfBoundsException: 57`

`InterfaceManager.gazeOrbOfOculus()` attempts to install interface component `57` under legacy root `475`. The current Matrix3 client/cache does not expose that component layout safely, so the legacy Orb root/interface path is incompatible with the active client.

Classification:

- `VERIFIED`: opening Construction through the Orb-backed bridge crashes the current client with `ArrayIndexOutOfBoundsException: 57` during `SET_INTERFACE` decoding.
- `verified-static`: `Class83.method1256(...)` indexes the interface component array from the lower 16 bits of the parent UID, matching the failing component index.
- `verified-static`: `InterfaceManager.gazeOrbOfOculus()` installs the legacy `475/57` interface mapping that triggers the failing packet path.
- `VERIFIED`: the ghost renderer diagnostics before the exception are not the crash source; the fatal exception is the interface attachment path.
- `VERIFIED`: the later `ConnectException` occurs after the client failure and is not the initiating crash.

The Construction client bridge no longer sends the Orb enter/exit command. The legacy server command remains unused and must not be called by Construction.

## Accepted controls and behavior

### Free Build view — first implementation target

- WASD moves the detached camera horizontally.
- Shift increases movement speed.
- Ctrl provides precision/slow movement where practical.
- Q/E lowers/raises the camera.
- Right-mouse drag rotates/looks around the scene.
- Camera movement should use acceleration/deceleration rather than instantaneous full-speed steps where the current Matrix3 camera owner permits it cleanly.
- The player does not move with the camera.
- Clicking a world tile stops camera movement immediately and selects/places at that target rather than walking the player.
- Existing Construction piece controls remain independent: R / Shift+R rotate the selected piece, and the current palette mouse-wheel behavior remains piece rotation until camera zoom is deliberately integrated.
- Closing Construction build mode restores normal Matrix3 camera behavior without swapping to an incompatible legacy root interface.

## Accepted view roadmap

### Free Build

Detached modern free camera for close/manual building. This remains the first camera implementation target.

### RTS

Elevated angled settlement overview intended for broad placement, worker observation and later settlement management. WASD pans across the settlement while the player remains stationary.

### Top Down

Near-vertical layout view intended for floor plans, production layouts and large-area building tools.

### Orbit / Focus

Camera revolves around or focuses a selected tile, structure or later worker. This is later interaction polish.

### Player View

Return to the normal Matrix3 gameplay camera without changing settlement or placement ownership.

## Implementation rule

Do not hardcode guessed obfuscated camera fields, stale historical mappings, legacy Orb interface components, RTS/top-down pitch/yaw values, or packet sequences.

The next implementation step is a bounded trace of the current Matrix3 client camera/input owner. The attempted exact searches for the previous `FreeMove`/`VK_W` identifiers and camera-position breadcrumbs did not expose a trustworthy current seam, so the trace is intentionally stopped rather than broadened speculatively.

Once the current owner is established, implement the smallest client-side Free Build lifecycle around it and keep player movement, scene picking, ghost preview and confirmed placement ownership unchanged.

## Runtime acceptance — crash-fix checkpoint

- Opening the Construction palette no longer sends `itembrowser constructioncamera enter`.
- Opening the Construction palette no longer switches to legacy root `475` or crashes on component `57`.
- The existing palette, hover tracking, ghost preview and confirmed placement path behave exactly as before the Build Camera experiment.
- Closing the palette requires no Orb reset because Orb mode was never entered.

## Runtime acceptance — future Free Build v1

- Opening Construction activates the newly verified detached camera without changing the root interface.
- WASD/Shift/Ctrl/Q/E controls operate on the camera only.
- The player remains physically stationary.
- Hovered-tile tracking and the white/translucent ghost continue working while detached.
- Rotation 0-3 and piece switching continue working while detached.
- Clicking the target tile zeroes camera movement and creates exactly one server-authoritative placed object without walking the player.
- Closing/cancelling Construction restores normal camera behavior.
- No camera instability, ghost duplication/flicker, interface regression or scene-render regression occurs.

## Classification

- `VERIFIED`: the legacy Orb-interface implementation is incompatible with the current Matrix3 client and is rejected for Construction.
- `VERIFIED`: Construction no longer invokes that interface path after the crash-safety patch.
- `UNKNOWN`: exact current-client camera/input owner for modern WASD detached movement.
- `UNKNOWN`: exact safe implementation seam for deterministic RTS/top-down/orbit presets.
