# Construction Build Camera

## Purpose

Construction uses Matrix3's detached Class411 camera as the rendering carrier, while Construction owns its build-mode controls.

The player remains physically server-owned and stationary unless normal gameplay movement is explicitly triggered. The camera never becomes world-object, collision, persistence or placement authority.

## Rejected paths

### Legacy Orb interface — RUNTIME REJECTED

- `InterfaceManager.gazeOrbOfOculus()` attempts legacy root/component `475/57`.
- Current client crashes during `SET_INTERFACE` with `ArrayIndexOutOfBoundsException: 57`.
- Construction must never reopen that path.

### Generic renderer-global camera — RUNTIME REJECTED

- Writing `Class36.anInt387`, `Class572_Sub13_Sub2.anInt11451`, `Class49.anInt490`, `Class455.anInt5187` and `Class406.anInt4765` did not reproduce the live detached camera.
- Runtime produced invalid/empty-looking views.
- Construction must not return to that ownership model.

## Detached camera evidence

Runtime CAM DEBUG established that the observed moving view renders through the Class411 family while generic XYZ remained zero in that capture.

Source tracing established the detached developer camera object and lifecycle:

- Ctrl+backtick enters through `Class102_Sub5.method9948(...)`.
- Active state is `IncomingPacket.method4113(...)` -> `Class24.aBool157`.
- Detached camera object is `Class24.aClass411_Sub1_158`.
- `Class343.method4302(...)` renders that object whenever detached-camera state is active.
- `RSSocket.method7604(...)` closes it.

The first Construction control patch placed WASD/Q/E inside `Class24.method711()`. Runtime proved only camera detachment worked; the new keys did not. Targeted tracing then showed that method was not reached by the current Construction runtime path, so that patch location is rejected.

## Current implementation — live viewport tick

Opening Construction:

1. Reuses an already-active detached Class411 camera if one exists.
2. Otherwise creates the same detached `Class24.aClass411_Sub1_158` camera through `Class102_Sub5.method9948(...)`.
3. Records whether Construction owns that activation.
4. Starts Construction's live control tick.

`ConstructionBuildCamera.tick()` is called from `Class343.method4302(...)` immediately before the active Class411 transform is submitted to the scene.

The tick is guarded to once per `client.cycles`.

This keeps the camera object/render path Matrix3-owned while ensuring Construction controls run on a seam that is runtime-live.

Closing Construction:

- closes the detached camera only when Construction created it;
- preserves a detached camera that was already active before Construction opened.

## Controls — current acceptance build

Construction's live tick reads Matrix3's current keyboard state:

- **W** / Up Arrow -> forward
- **S** / Down Arrow -> backward
- **A** / Left Arrow -> strafe left
- **D** / Right Arrow -> strafe right
- **E** -> vertical direction A
- **Q** -> vertical direction B
- **Shift** -> fast step 60
- **Ctrl** -> precision step 8
- default step -> 25

Movement vectors are transformed by the detached camera's current orientation before being applied.

Mouse look uses the same Class411 quaternion/position math already present in the detached-camera code.

Q/E direction remains runtime acceptance pending.

## Modern smoothing

Acceleration/deceleration is intentionally deferred until this live control seam passes runtime acceptance.

After acceptance, smoothing belongs in `ConstructionBuildCamera` on this same Class411 object. Do not reopen camera ownership discovery.

## Placement ownership

The camera does not own placement.

- Matrix3 continues resolving the hovered world tile.
- `ConstructionGhostPreview` remains client-only and unregistered.
- Confirmed objects remain server-authoritative through the existing Dev placement / `itembrowser devspawn` path.
- If a build click still triggers Walk Here, suppress that only through the already-verified Construction/Dev menu-action seam.

## Diagnostics

No camera debug overlay is active.

Construction sends bounded one-shot diagnostics through the existing owner-only client->server command bridge. The **server console** should show:

```text
[ConstructionBuildCamera] ENTER ...
[ConstructionBuildCamera] TICK live
[ConstructionBuildCamera] INPUT W=... A=... S=... D=... Q=... E=... shift=... ctrl=...
[ConstructionBuildCamera] EXIT ...
```

Failure states may emit:

```text
[ConstructionBuildCamera] FAIL detached-camera-not-active
[ConstructionBuildCamera] FAIL tick-exception-...
```

Only the first live tick and first movement input are reported per Construction session to avoid spam.

## Accepted view roadmap

### Free Build

Current target. Detached Class411 render carrier with Construction-owned modern controls.

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

- Opening Construction automatically activates the detached Class411 camera.
- Server console receives `ENTER` and then `TICK live`.
- W/S move forward/back.
- A/D strafe.
- Shift and Ctrl change speed.
- Q/E move vertically; record if direction needs swapping.
- Existing arrow aliases move the same detached camera.
- Existing mouse-look works.
- First movement input produces the expected one-shot server-console `INPUT` state.
- Player remains physically stationary.
- Ghost hover, piece switching and rotation remain functional.
- One build click produces exactly one server-authoritative real object.
- Closing Construction restores normal camera when Construction owned the detached camera.
- Pre-existing detached camera state is preserved when Construction did not own activation.
- No `FAIL` diagnostic appears.
- No camera/render crash, duplicate ghost or scene corruption occurs.

## Classification

- `VERIFIED`: legacy Orb interface path is incompatible with the current client and rejected.
- `VERIFIED`: generic renderer-global camera implementation failed runtime acceptance and is rejected.
- `VERIFIED`: first Class24 reuse test detached the camera but Construction W/A/S/D/Q/E did not run.
- `verified-static`: detached render ownership is `Class24.aClass411_Sub1_158`, activated through `Class102_Sub5.method9948(...)` and closed through `RSSocket.method7604(...)`.
- `verified-static`: Construction controls now execute from the live `Class343.method4302(...)` viewport seam and mutate the detached Class411 position/orientation directly.
- `NEEDS TEST`: movement directions, Q/E sign, mouse-look, speed modifiers, player-stationary behavior, ghost/placement compatibility and close/reopen lifecycle.
- `UNKNOWN`: final smoothing constants and RTS/top-down/orbit presets.
