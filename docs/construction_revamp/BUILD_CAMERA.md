# Construction Build Camera

## Purpose

Construction placement uses a dedicated detached build camera instead of moving the player around the settlement while building.

The player remains physically planted while the camera moves independently. Construction still uses Matrix3's normal scene targeting for the hovered tile, the client-only ghost for preview, and server-authoritative placement for the confirmed object.

## Ownership

- Construction owns only build-camera lifecycle, view selection and build-mode UX.
- Player/world movement remains Matrix3/server-owned.
- Scene picking remains Matrix3-owned; Construction only consumes the already resolved hovered tile.
- Confirmed placement remains server authoritative.
- The legacy Orb-of-Oculus interface path is permanently rejected for Construction.
- Free Build uses the current Matrix3 renderer camera globals only while the Construction palette is active, then restores the exact previous mode/transform.

## Runtime finding — legacy Orb path rejected

The first Orb-backed attempt runtime-crashed the current client.

Observed fatal chain:

`SET_INTERFACE -> Class104_Sub1.method9918(...) -> Class512.method6083(...) -> Class83.method1256(...) -> ArrayIndexOutOfBoundsException: 57`

`InterfaceManager.gazeOrbOfOculus()` attempts to install interface component `57` under legacy root `475`. That layout is incompatible with the active client/cache and is no longer used by Construction.

## Current Free Build implementation

Verified-static renderer mapping from `Class343.method4302(...)`:

- X: `Class36.anInt387`
- Y/height: `Class572_Sub13_Sub2.anInt11451`
- Z: `Class49.anInt490`
- pitch: `Class455.anInt5187`
- yaw: `Class406.anInt4765`

Matrix3 modes 1/2/4/6 have dedicated camera update paths. Free Build temporarily uses a Construction-only generic mode so the renderer consumes the verified camera globals without root-interface changes. The prior raw camera mode and transform are snapshotted on entry and restored on exit.

### Controls

- WASD: horizontal movement relative to current camera yaw.
- Shift: fast movement.
- Ctrl: precision movement.
- Q/E: vertical movement.
- Right-mouse drag: yaw/pitch camera look.
- Pitch is clamped to Matrix3's verified 1024-3072 range.
- Movement accelerates/decelerates instead of snapping instantly to full speed.
- Left-clicking the game canvas immediately zeros camera velocity.
- In Paint mode, a valid left-click places from Matrix3's already-resolved hovered tile and consumes that canvas event so it does not intentionally become Walk Here.
- Existing Construction R / Shift+R / mouse-wheel piece rotation remains unchanged.

Paint confirmation still calls `DevSpawnPlacement.placeActive(...)`, so the existing owner-only server `itembrowser devspawn` path remains the only real object authority. Free Build does not perform a second scene pick.

The implementation uses no server camera packet, no player teleport, no legacy Orb/root interface, and no second scene picker.

## Accepted view roadmap

### Free Build

Detached modern free camera for close/manual building. Implemented; runtime acceptance pending.

### RTS

Elevated angled settlement overview intended for broad placement, worker observation and later settlement management. WASD pans across the settlement while the player remains stationary.

### Top Down

Near-vertical layout view intended for floor plans, production layouts and large-area building tools.

### Orbit / Focus

Camera revolves around or focuses a selected tile, structure or later worker.

### Player View

Return to the normal Matrix3 gameplay camera without changing settlement or placement ownership.

## Runtime acceptance — Free Build v1

- Opening the Construction palette no longer changes root interface or crashes.
- Free Build activates automatically with the palette.
- WASD moves the camera horizontally without moving the player.
- Shift increases speed and Ctrl slows movement for precision.
- Q/E move vertically in the expected directions.
- Right-mouse drag rotates yaw/pitch smoothly and respects the pitch clamp.
- Releasing movement keys decelerates cleanly.
- Left-click immediately stops camera velocity.
- In Paint mode, left-clicking a valid hovered tile places once through the existing server-authoritative path and the player remains stationary.
- Confirm exactly one real object is created per click; if the consumed AWT event still reaches Matrix3's action-23 dispatcher on this client, treat that as a runtime regression and move suppression to the verified menu-action seam.
- Hovered-tile tracking and the white/translucent ghost continue working while detached.
- Rotation 0-3 and Wooden fence/Floor decoration/Door switching continue working while detached.
- Closing/cancelling Construction restores the prior Matrix3 camera mode/transform.
- No camera instability, ghost duplication/flicker, interface regression or scene-render regression occurs.

## Classification

- `VERIFIED`: legacy Orb interface is incompatible with the current client and rejected for Construction.
- `verified-static`: generic Matrix3 scene rendering consumes the five mapped camera globals above.
- `verified-static`: Free Build snapshots/restores previous raw camera state and does not invoke the server Orb bridge.
- `verified-static`: Free Build Paint confirmation consumes the existing hovered tile and still routes real placement through `DevSpawnPlacement`/server `devspawn`.
- `NEEDS TEST`: Free Build movement direction/speed, vertical sign, mouse-look feel, camera restoration, consumed-click behavior and ghost compatibility.
- `UNKNOWN`: whether a consumed AWT Paint click fully suppresses Matrix3 action 23 on the active runtime; promote only after testing.
- `UNKNOWN`: exact deterministic RTS/top-down/orbit preset values; do not hardcode them until Free Build runtime behavior is accepted.
