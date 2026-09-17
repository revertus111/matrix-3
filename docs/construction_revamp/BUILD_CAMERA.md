# Construction Build Camera

## Purpose

Construction uses Matrix3's existing detached developer free-camera instead of moving the player while building.

The camera is client-side only. World movement, scene ownership and confirmed object placement remain owned by their existing Matrix3/server paths.

## Runtime evidence

### Rejected paths

1. **Legacy Orb interface**
   - Rejected at runtime.
   - `InterfaceManager.gazeOrbOfOculus()` attempted legacy root/component `475/57`.
   - Current client crashed during `SET_INTERFACE` with `ArrayIndexOutOfBoundsException: 57`.

2. **Generic renderer-global camera**
   - Rejected at runtime.
   - Writing `Class36.anInt387`, `Class572_Sub13_Sub2.anInt11451`, `Class49.anInt490`, `Class455.anInt5187` and `Class406.anInt4765` did not reproduce the working detached camera and produced invalid/empty-looking views.

### Proven detached camera

Runtime CAM DEBUG captures established that the observed moving camera view rendered through mode `1` / `CLASS411`, while generic camera XYZ remained `0,0,0` in the captured session. That proves the failed generic-global implementation was targeting the wrong active render family, but the screenshots alone do not distinguish the normal mode-1 Class411 camera from the separate developer freecam.

Source trace then established the existing developer detached-camera owner:

- Java backtick is mapped to internal key `28`.
- Ctrl is internal key `82`.
- Ctrl+backtick activates the detached camera through `Class102_Sub5.method9948(...)`.
- Active state is `IncomingPacket.method4113(...)` -> `Class24.aBool157`.
- The detached camera object is `Class24.aClass411_Sub1_158`.
- View rendering prefers that camera whenever `Class24.aBool157` is true.
- `Class24.method711()` owns its mouse-look and movement updates.
- `RSSocket.method7604(...)` closes that detached camera.

This is the authoritative Free Build camera path.

## Current implementation — Class411 Free Build reuse

Opening the Construction palette now:

1. Checks whether the existing detached free-camera is already active.
2. If not, activates it through the exact existing `Class102_Sub5.method9948(...)` path using the local player's current seed tile.
3. Records whether Construction owns that activation.
4. Leaves Matrix3's existing mouse-look and Class411 render ownership intact.

Closing the palette:

- closes the detached camera only when Construction activated it;
- leaves a pre-existing manually activated free-camera running.

No legacy interface, renderer-global camera mode, duplicate scene picker or client-owned world object is introduced.

## Controls

The original developer free-camera arrow controls remain available.

While the Construction palette is active, `Class24.method711()` additionally maps:

- **W** (internal 33) -> forward
- **S** (49) -> backward
- **A** (48) -> left
- **D** (50) -> right
- **E** (34) -> vertical direction A
- **Q** (32) -> vertical direction B
- **Shift** (81) -> fast movement, step 60
- **Ctrl** (82) -> precision movement, step 8
- default movement step -> 25
- existing mouse-look -> unchanged Matrix3 free-camera behavior

Q/E direction remains runtime acceptance pending; swap them if runtime proves the intuitive direction is reversed.

### Modern smoothing

Acceleration/deceleration is intentionally **not** layered in yet. First accept the proven Class411 path with WASD. Smooth velocity can then be added inside this same camera owner without reopening camera architecture.

## Placement ownership

The camera does not own placement.

- Hovered world tile remains resolved through Matrix3's existing scene/menu path.
- Ghost remains client-only and unregistered.
- Confirmed real objects remain server-authoritative through the existing Dev placement / `itembrowser devspawn` path.
- If a build click still moves the player, suppress action 23 only through the already-verified Construction/Dev menu-action seam after runtime evidence.

## Diagnostics

The temporary `CAM DEBUG [READ ONLY]` overlay has been removed after proving the camera owner.

Construction camera lifecycle now emits transition-only console lines:

```text
[ConstructionBuildCamera] ENTER source=Class24/Class411 ...
[ConstructionBuildCamera] EXIT ...
```

Do not add another on-screen camera diagnostic unless console output cannot establish a future failure.

## Accepted view roadmap

### Free Build

Current target. Proven Class411 detached camera plus Construction-specific modern controls.

### RTS

Elevated angled settlement overview with panning and controlled zoom.

### Top Down

Near-vertical layout view for floors, room planning and production layouts.

### Orbit / Focus

Focus/orbit around a selected tile, structure or worker.

### Player View

Return to normal Matrix3 gameplay camera without changing Construction state.

Preset values remain UNKNOWN until Free Build is runtime accepted.

## Runtime acceptance — current gate

- Opening Construction automatically activates the same detached Class411 camera as the existing developer free-camera.
- No Ctrl+backtick hotkey is required after the palette opens.
- The camera starts at a sensible position around the current player instead of an invalid/empty view.
- Existing mouse-look works.
- W/S move forward/back relative to camera orientation.
- A/D strafe correctly.
- Shift is fast and Ctrl is precision.
- Q/E move vertically; note if direction is reversed.
- The player remains physically stationary while the camera moves.
- White/translucent Construction ghost remains visible and follows hovered tiles.
- Piece switching and rotation remain correct.
- Confirmed placement still produces exactly one server-authoritative real object.
- Closing the palette returns to normal camera when Construction activated freecam.
- If freecam was manually active before opening Construction, closing the palette does not kill it.
- No `CAM DEBUG` overlay remains.
- Console receives one ENTER and one EXIT transition line per Construction camera session.
- No scene/render crash, duplicate ghost or camera corruption occurs.

## Classification

- `VERIFIED`: legacy Orb interface path crashes current client and is rejected.
- `VERIFIED`: generic renderer-global Free Build attempt failed runtime acceptance and is rejected.
- `VERIFIED`: runtime CAM DEBUG showed the observed moving view rendering through mode 1 / CLASS411 while generic XYZ remained zero in the captured session; this runtime evidence rejects generic-global ownership but does not by itself identify the separate developer-freecam object.
- `verified-static`: existing detached developer freecam is `Class24.aClass411_Sub1_158`, activated through `Class102_Sub5.method9948(...)`, tested by `IncomingPacket.method4113(...)`, updated by `Class24.method711()`, and closed by `RSSocket.method7604(...)`.
- `verified-static`: Construction now activates/reuses that exact owner and preserves a pre-existing manually activated freecam.
- `NEEDS TEST`: Construction automatic activation, WASD mappings, Shift/Ctrl speeds, Q/E vertical direction, player-stationary behavior, ghost compatibility and close/restore lifecycle.
- `UNKNOWN`: final RTS/top-down/orbit presets and transition feel.
