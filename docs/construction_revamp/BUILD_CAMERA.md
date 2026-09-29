# Construction Build Camera

## Purpose

Construction uses a hybrid Matrix3-native camera architecture:

- **RTS** owns only its movable pivot, yaw, pitch, zoom and input state, then feeds those inputs into Matrix3's existing vanilla `Class246.method3359(...)` camera solver.
- **Free Build** retains Matrix3's detached `Class24.aClass411_Sub1_158` / Class411 developer camera and the already-accepted free-flight controls.

The player remains server-owned and stationary unless normal gameplay movement is explicitly triggered. Neither camera mode owns world objects, collision, persistence or settlement placement.

## Rejected paths

### Legacy Orb interface — RUNTIME REJECTED

- `InterfaceManager.gazeOrbOfOculus()` attempts legacy root/component `475/57`.
- The current client crashes during `SET_INTERFACE` with `ArrayIndexOutOfBoundsException: 57`.
- Construction must not reopen that path.

### Standalone renderer-global camera owner — RUNTIME REJECTED

The Sep-17 prototype directly wrote final camera globals and attempted to become an independent camera owner. Runtime produced invalid/empty-looking views.

That failed prototype is **not** the current RTS design.

Current RTS reuses Matrix3's verified vanilla solver, `Class246.method3359(...)`, at the same viewport stage where Matrix3 already calculates the normal player camera. Construction supplies only a different focus/pivot, pitch, yaw and distance.

## Verified camera ownership

### Vanilla path

`Class343.method4302(...)` normally calls `Class246.method3359(...)`, which calculates:

- `Class36.anInt387` — camera X
- `Class572_Sub13_Sub2.anInt11451` — camera height
- `Class49.anInt490` — camera Z
- `Class455.anInt5187` — pitch
- `Class406.anInt4765` — yaw

When detached-camera state is false, `Class343` submits those normal Matrix3 camera values to the renderer.

### Detached path

- Activation: `Class102_Sub5.method9948(...)`
- Active flag: `IncomingPacket.method4113(...)` -> `Class24.aBool157`
- Camera object: `Class24.aClass411_Sub1_158`
- Close: `RSSocket.method7604(...)`

When `Class24.aBool157` is true, `Class343` renders the detached Class411 transform instead of the vanilla globals.

This branch split is the verified-static reason the old RTS render-parity workarounds could not fully reproduce normal player-camera rendering while RTS still owned the detached branch.

## Current implementation

### RTS — vanilla camera owner

`ConstructionBuildCamera.tickVanillaRtsCamera(viewportHeight)` runs in `Class343.method4302(...)`:

1. Matrix3 calculates its normal camera first.
2. Construction updates RTS pivot/yaw/pitch/zoom state.
3. Construction calls the existing `Class246.method3359(...)` with the RTS inputs.
4. Matrix3 then applies its ordinary camera shake, clamps, scene setup and normal render transform.

RTS does **not** submit a Class411 detached transform.

The detached active flag is forced false while RTS is active, even if a detached object is being retained for later Free Build use.

### Free Build — detached camera owner

The existing late `ConstructionBuildCamera.tick()` seam remains Free Build-only.

Free Build:

- lazily creates/reuses `Class24.aClass411_Sub1_158`;
- seeds the detached position from the current rendered camera when switching from RTS;
- keeps the accepted Class423_Sub2 position and Class658_Sub2 look-controller path;
- preserves W/A/S/D, Q/E vertical movement, Shift/Ctrl speeds, mouse-look, smoothing and click-stop behavior.

### Mode handoff

**RTS -> Free Build**

- restore the RTS minimap marker snapshot;
- activate/reuse the detached Class411 camera;
- seed it from the current RTS rendered camera;
- keep the detached branch active only while Free Build owns the view.

**Free Build -> RTS**

- disable the detached render-owner flag without necessarily destroying the retained object;
- reset transient RTS state;
- restore the saved RTS pivot/yaw/pitch/zoom when still valid for the loaded scene;
- resume the vanilla `Class246.method3359(...)` path.

If a detached camera was already active before Construction entered, Construction temporarily disables that render branch for RTS and restores it on Construction exit when it still exists.

## RTS controls

- **W/S/A/D + arrows** — view-relative ground-plane pan
- **Q/E** — pivot-relative yaw orbit
- **MMB horizontal drag** — yaw orbit
- **MMB vertical drag** — bounded pitch
- **wheel** — orbit distance
- **Shift/Ctrl** — fast/precision modifiers
- palette speed presets — `0.50x` through `3.00x`, default `2.0x`
- minimap click — moves only the RTS pivot and preserves yaw/pitch/zoom

RTS yaw keeps the already-accepted Construction semantics: yaw 0 faces +Z and positive yaw faces +X. Matrix3's vanilla solver uses the opposite yaw sign, so the sign conversion happens only at the `Class246.method3359(...)` boundary.

RTS movement no longer depends on `Class423_Sub2` / `Class658_Sub2` to discover the camera look vector; the movement basis is derived directly from the owned RTS yaw.

## Free Build controls

- **W/S/A/D + arrows** — free-flight movement
- **Q/E** — vertical movement
- **Shift** — fast
- **Ctrl** — precision
- mouse-look — existing Class411 quaternion/look path

Movement remains time-based and normalized, with the accepted smoothing/click-stop behavior.

## Rendering and scene ownership

RTS now uses the same vanilla camera solver and renderer branch as ordinary Matrix3 gameplay.

Separate scene systems remain separate owners:

- `Class523` retains scene visibility/culling.
- Settlement RTS may provide its pivot as the scene-culling focus tile.
- The settlement visual terrain apron remains real generated terrain, not a renderer-radius/fog hack.
- Normal Matrix3 fog/visibility/radius arrays remain untouched.

The vanilla-camera migration is intended to remove the need for detached-camera render-parity workarounds, but runtime acceptance is still required before retiring any compatibility code.

## Runtime acceptance — vanilla RTS migration

Status: **IMPLEMENTED / NEEDS RUNTIME TEST**

On a fresh client pull/rebuild:

1. Enter a settlement. RTS should open without a detached/freecam render branch.
2. Verify the initial view is above terrain and approximately matches the accepted RTS pitch/zoom.
3. Pan with W/A/S/D and arrows; direction must remain camera-relative and the player must stay planted.
4. Hold movement while clicking world/chest/worker targets; camera motion must continue.
5. Orbit with Q/E and MMB; horizontal direction must match the already-accepted direction.
6. Wheel zoom through the accepted range; no blank/underside camera.
7. Pan toward the edge of the settlement and compare terrain/object/NPC/fog behavior to normal vanilla camera behavior.
8. Click several minimap positions; pivot moves while yaw/pitch/zoom remain stable.
9. Switch **RTS -> Free Build**; the view should hand off without a severe position jump and Free Build W/A/S/D/Q/E/mouse-look must still work.
10. Switch **Free Build -> RTS**; the saved RTS pivot/yaw/pitch/zoom should return and detached rendering must stop.
11. Exit the settlement; ordinary Matrix3 camera behavior must resume.
12. Re-enter and verify saved RTS view restoration remains valid for the rebuilt scene.

## Classification

- `VERIFIED`: legacy Orb interface path is incompatible and rejected.
- `VERIFIED`: the standalone direct-final-global camera prototype failed runtime acceptance and is rejected.
- `VERIFIED`: detached Class411 Free Build controls, smoothing, click-stop, placement integration and lifecycle have passed prior runtime acceptance.
- `verified-static`: Matrix3 normal camera calculation is `Class246.method3359(...)`.
- `verified-static`: `Class343` chooses between detached Class411 submission and the normal camera-global render transform.
- `verified-static`: RTS now invokes the vanilla solver before camera shake/clamp/scene setup and suppresses the detached render-owner flag.
- `verified-static`: Free Build remains on the existing late Class411 tick.
- `NEEDS RUNTIME TEST`: vanilla RTS render parity, RTS controls after migration, RTS <-> Free Build handoff, and saved-view restoration.
