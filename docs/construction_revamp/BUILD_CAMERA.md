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
- **E** -> vertical up
- **Q** -> vertical down
- **Shift** -> fast max speed
- **Ctrl** -> precision max speed
- mouse look -> existing Class411 quaternion/look path

Movement input is normalized before it is transformed by the detached camera orientation, so diagonal input does not receive a free speed boost.

## Modern smoothing

Implemented on the verified live Class411 control seam:

- movement is time-based from `System.nanoTime()`, with delta time clamped to 5-50 ms to avoid stalls causing camera jumps;
- normal speed = 1250 camera units/sec;
- Shift fast speed = 3000 units/sec;
- Ctrl precision speed = 400 units/sec;
- acceleration uses exponential response `10.0`;
- deceleration uses exponential response `7.0`;
- velocity is world-space, so releasing input coasts smoothly instead of stopping instantly;
- near-zero velocity settles to zero to prevent a permanent micro-drift.

These constants are `NEEDS TEST` for feel and can be tuned without reopening camera ownership discovery.

### Click-to-stop

Matrix3 action 23 is the verified ground-click seam.

While Construction Free Build is active:

1. action 23 calls `ConstructionBuildCamera.stopMovement()`;
2. all camera velocity is zeroed immediately;
3. if a movement key is still held, movement remains latched off until all movement keys are released once;
4. an armed Paint placement is still confirmed through the existing server-authoritative Dev placement path;
5. the action is consumed so the same click does not become player Walk Here.

Outside Construction Free Build, normal Dev Paint behavior remains unchanged.

## Placement ownership

The camera does not own placement.

- Matrix3 continues resolving the hovered world tile.
- `ConstructionGhostPreview` remains client-only and unregistered.
- Confirmed objects remain server-authoritative through the existing Dev placement / `itembrowser devspawn` path.
- Free Build now owns action 23 while active: it stops camera momentum, optionally confirms Paint placement and consumes the action so the player remains planted.

## Diagnostics

No camera debug overlay is active.

Construction sends bounded one-shot diagnostics through the existing owner-only client->server command bridge. The **server console** should show:

```text
[ConstructionBuildCamera] ENTER ...
[ConstructionBuildCamera] TICK live
[ConstructionBuildCamera] INPUT W=... A=... S=... D=... Q=... E=... shift=... ctrl=...
[ConstructionBuildCamera] STOP click
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

Runtime VERIFIED in the user's acceptance sweep:

- Opening Construction automatically activates the detached Class411 camera.
- W/S move forward/back.
- A/D strafe.
- Shift and Ctrl change speed.
- Q/E move vertically with the accepted direction.
- Existing mouse-look works.
- Closing Construction restores the normal camera when Construction owned the detached camera.
- Reopening Construction starts Free Build cleanly.

Runtime VERIFIED in the smoothed Free Build acceptance run:

- acceleration ramps smoothly from rest;
- releasing input decelerates cleanly without excessive drift or jitter;
- diagonal input is normalized;
- Shift fast and Ctrl precision remain usable;
- clicking ground while moving stops camera motion immediately;
- if a movement key is still held, click-stop stays latched until release/re-press;
- the player remains planted on the build click instead of Walk Here;
- one Paint click creates exactly one server-authoritative real object;
- the white/translucent ghost remains stable while detached;
- no camera/render instability or ghost duplication/flicker appeared in the acceptance run.

Still pending explicit edge verification:

- server console receives the complete `ENTER -> TICK -> INPUT -> STOP click -> EXIT` sequence without a `FAIL` state;
- existing arrow aliases move the same detached camera;
- pre-existing detached camera state is preserved when Construction did not own activation;
- full ghost interaction completeness remains tracked in the separate ghost checklist (all rotations/piece switches/terrain/cancel-stale-hover cases).

## Classification

- `VERIFIED`: legacy Orb interface path is incompatible with the current client and rejected.
- `VERIFIED`: generic renderer-global camera implementation failed runtime acceptance and is rejected.
- `VERIFIED`: first Class24 reuse test detached the camera but Construction W/A/S/D/Q/E did not run.
- `verified-static`: detached render ownership is `Class24.aClass411_Sub1_158`, activated through `Class102_Sub5.method9948(...)` and closed through `RSSocket.method7604(...)`.
- `verified-static`: Construction controls now execute from the live `Class343.method4302(...)` viewport seam and mutate the detached Class411 position/orientation directly.
- `VERIFIED`: automatic Construction activation, W/S/A/D movement, Shift/Ctrl speed modifiers, Q/E vertical movement, mouse-look, normal-camera restore and close/reopen lifecycle passed the user's runtime sweep.
- `verified-static`: smoothing is now time-based/world-velocity-driven on the live Class411 tick, and Free Build action 23 now owns click-to-stop + Walk Here suppression without changing server-authoritative placement.
- `VERIFIED`: smoothing feel, normalized diagonals, Shift/Ctrl under smoothing, click-to-stop latch, planted-player build click, exactly-one authoritative Paint placement/no Walk Here, detached ghost stability and combined camera/render stability passed the user's runtime acceptance run.
- `NEEDS TEST`: full server-console diagnostic sequence, arrow aliases, pre-existing-freecam preservation and the separate ghost completeness checklist.
- `UNKNOWN`: final RTS/top-down/orbit preset values and transition feel.
