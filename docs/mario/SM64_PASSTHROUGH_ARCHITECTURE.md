# SM64 Passthrough Architecture — Matrix3 / Revision 830

## Goal

Run authentic SM64-derived Mario movement/action logic alongside Matrix3 while Matrix3 remains the host world, renderer, input owner, and eventual multiplayer/server authority.

The target is not to rewrite every Mario 64 action in Java. The target is to give a headless SM64 core the minimum world/input information it needs, let that core advance Mario's real state machine, and feed the resulting Mario state back into Matrix3.

## Architecture decision

Use a native SM64 simulation core behind a narrow bridge contract.

For the first implementation candidate, use `libsm64/libsm64`, which is built from the SM64 decomp specifically to expose Mario movement/rendering to external engines. It loads the user's own SM64 US ROM at runtime and exposes Mario input/state, surfaces, dynamic surface objects, animation state, and generated geometry through a C API.

Reference repositories:

- SM64 decomp: `https://github.com/n64decomp/sm64`
- Headless/external-engine library candidate: `https://github.com/libsm64/libsm64`

The ROM is a local runtime dependency only. Never commit ROM bytes or extracted copyrighted assets into Matrix3.

## Responsibility split

```text
                 MATRIX3 / REVISION 830
        ┌──────────────────────────────────┐
        │ input capture                    │
        │ RuneScape scene/world            │
        │ terrain + object discovery       │
        │ camera                           │
        │ rendering/presentation           │
        │ networking/server integration    │
        └───────────────┬──────────────────┘
                        │
                 SM64 Bridge API
                        │
        ┌───────────────▼──────────────────┐
        │ headless SM64 core / libsm64     │
        │                                  │
        │ Mario action state machine       │
        │ acceleration/friction            │
        │ jump families / air control      │
        │ wall/slope/ledge response         │
        │ ground pound / crouch / attacks  │
        │ swimming and surface behavior    │
        │ animation/action state           │
        └───────────────┬──────────────────┘
                        │
               position/state/geometry
                        │
        ┌───────────────▼──────────────────┐
        │ Matrix3 Mario presentation       │
        └──────────────────────────────────┘
```

### Matrix3 owns

- Local input acquisition and final player-facing mode activation.
- RuneScape terrain, objects, water/world context and scene lifecycle.
- The visible camera inside the 830 client.
- Final rendering/presentation inside the RuneScape scene.
- Normal RuneScape control whenever Mario mode is disabled.
- Eventual server validation, remote-player replication and gameplay authority.

### SM64 core owns while Mario mode is active

- Mario movement/action state transitions.
- Mario velocity/forward velocity/facing calculations.
- Ground/air action logic driven by supplied surfaces.
- Native SM64 response to slopes, floors, walls, ceilings and other supported surfaces.
- Mario action/animation state exposed back through the bridge.

### Bridge owns

- Process/native lifecycle.
- Protocol/versioning.
- Coordinate and unit conversion.
- Matrix input -> `SM64MarioInputs` conversion.
- Matrix collision/world sample -> `SM64Surface` conversion.
- `SM64MarioState` -> Matrix transform/action presentation conversion.
- Failure isolation and clean fallback to normal RuneScape control.

## Why `libsm64` fits this design

Its public API already exposes the exact primitives this project needs:

- `SM64MarioInputs`: camera look vector, analog stick X/Y, A/B/Z buttons.
- `SM64MarioState`: position, velocity, face angle, forward velocity, action, animation ID/frame and flags.
- `sm64_global_init(...)`: initialize from the user's ROM.
- `sm64_static_surfaces_load(...)`: provide collision triangles to Mario.
- `sm64_mario_create(...)` / `sm64_mario_tick(...)`: create and advance Mario.
- `sm64_surface_object_create/move/delete(...)`: represent moving/dynamic collision objects.
- floor/wall/ceiling query functions for debugging and later adapter verification.
- `SM64MarioGeometryBuffers`: optional per-tick Mario triangle/normal/color/UV output for a future direct-render path.

This means Matrix3 does not need to embed an N64 renderer or run the whole SM64 game. The native core can be treated as a Mario simulation service.

## Transport decision — sidecar first

### V1: isolated sidecar process

```text
Matrix3 Java client
      ⇅ stdin/stdout protocol
sm64_bridge.exe
      ⇅ C API
sm64.dll / libsm64
```

Use a separate native process for the first bridge proof.

Reasons:

- No new Java dependency is required; Java 8 `ProcessBuilder` is enough.
- A native crash does not immediately kill the RuneScape client.
- Protocol traffic can be logged/replayed while the bridge is being learned.
- It keeps the decompiled/native runtime clearly separated from Matrix3 ownership.
- The first correctness question is easy to isolate: did actual SM64 code advance Mario state from Matrix input?

The sidecar is a prototype transport, not a permanent performance commitment.

### Later option: JNI

If process/pipe latency or synchronization becomes a measurable problem, preserve the same logical bridge contract and replace only the transport with JNI/native calls inside the client process.

Do not move to JNI merely because it appears cleaner. Measure the sidecar first.

## Bridge protocol V1

Protocol stdout is machine-readable only. Native diagnostics go to stderr.

Initial commands:

```text
READY 1
PING
PONG 1
STEP <camLookX> <camLookZ> <stickX> <stickY> <A> <B> <Z>
STATE <x> <y> <z> <vx> <vy> <vz> <faceAngle> <forwardVelocity> <action> <animId> <animFrame> <flags>
RESET
RESET_OK
QUIT
BYE
```

The first spike uses a temporary flat native floor so transport/native-state correctness can be proven before RuneScape collision conversion is introduced.

## Tick model

`libsm64`'s own example advances Mario at 30 Hz (`1/30` second per `sm64_mario_tick`).

V1 should therefore keep native simulation on a dedicated fixed 30 Hz worker rather than tying Mario physics directly to variable render FPS.

Long-term presentation path:

```text
30 Hz SM64 simulation
        ↓
latest authoritative local Mario state
        ↓
Matrix client-frame interpolation/presentation
```

This avoids frame-rate-dependent Mario physics while allowing smooth 830 rendering.

## Coordinate adapter

Matrix3 and SM64 use different coordinate conventions. Never leak raw coordinates across the bridge without an adapter.

Known Matrix3 facts:

- Scene X/Z are horizontal world-space axes.
- Matrix3 terrain has continuous height inside a RuneScape plane.
- Local player vertical presentation uses the middle transform component; the current jump proof confirmed that decreasing this Matrix scene-Y value visually raises the player.
- A RuneScape scene tile is 512 scene units in the established client paths.

Bridge-local convention:

- SM64 X/Z = horizontal axes.
- SM64 Y = positive-up vertical axis.
- Use a local origin around Mario instead of feeding enormous absolute RuneScape world coordinates to the SM64 core.
- Keep the Matrix<->SM64 scale as an explicit constant/configuration; calibrate it from runtime movement rather than burying guessed multipliers in collision code.

Initial conceptual transform:

```text
sm64X = (matrixX - localOriginX) * scale
sm64Z = (matrixZ - localOriginZ) * scale
sm64Y = matrixVerticalToPositiveUp(matrixY, localOriginY) * scale
```

The exact sign/scale becomes `VERIFIED` only after bridge-driven movement is observed at runtime.

### Phase 3 scale rule

Phase 3 must have one authoritative Matrix<->SM64 conversion seam. Terrain triangles, Mario position/state, wall quads, object surfaces and rendered/native debug coordinates must all use that same transform.

Do not scatter independent `* 512`, `/ 3`, sign flips or other calibration constants through terrain/object code. The currently accepted local presentation scales are useful runtime evidence for presentation only; collision-space scale and winding still require their own Phase 3 acceptance.

## Matrix terrain -> SM64 surface adapter

This is the central collision strategy.

RuneScape terrain already provides a height field. For each nearby tile, sample its four height corners:

```text
A ----- B
|       |
|       |
D ----- C
```

Convert each quad into two triangles with winding chosen to produce an upward SM64 floor normal:

```text
A-C-B
A-D-C
```

Then send the local collision bubble as `SM64Surface[]`.

Conceptual flow:

```text
loaded Matrix scene / height data
          ↓
client-side collision adapter
          ↓
nearby Matrix terrain tiles
          ↓
height samples at tile corners
          ↓
2 triangles per tile
          ↓
shared coordinate/unit conversion
          ↓
SM64Surface[]
          ↓
bridge transport
          ↓
sm64_static_surfaces_load(...)
```

### Client-side scene ownership decision

Build collision surfaces from the already-loaded Matrix client scene/world data. Do not teach the native bridge how to decode Matrix cache/scene internals and do not make it pull RuneScape world data independently.

The intended boundary is:

```text
Matrix loaded scene
    -> Java collision adapter
    -> bridge collision payload
    -> libsm64 surfaces
```

The bridge should remain Matrix-agnostic: it accepts converted SM64 collision data and advances native Mario state. Matrix-specific heightmaps, clipping flags, object definitions/models, transforms and scene lifecycle stay on the Java/client side.

### Local collision bubble

Do not convert all of Gielinor.

Maintain only the region Mario can interact with and rebuild/recenter it when Mario approaches the current bubble edge rather than on every 30 Hz native tick.

Initial tuning target, not a locked constant:

- approximately `16x16` to `32x32` terrain tiles,
- Mario starts near the center,
- rebuild only after he crosses a smaller recenter threshold/hysteresis band,
- profile surface count and rebuild cost before choosing the permanent dimensions.

Benefits:

- bounded surface count,
- smaller coordinates,
- predictable native update cost,
- easier debugging,
- no giant one-time world conversion,
- no unnecessary full surface reload every simulation frame.

### Winding / axis validation

Triangle winding is correctness-critical because libsm64 classifies floors/walls/ceilings from surface normals.

Do not assume a generic "RuneScape Y is flipped" rule is sufficient. Matrix uses X/Z horizontally and a negative-up presentation convention on scene Y, so Phase 3 must verify the actual heightmap-to-SM64 transform and triangle winding against the live Matrix data path.

Acceptance should explicitly prove:

- flat terrain produces an upward SM64 floor normal,
- Mario stands on the surface rather than falling through or colliding from below,
- uphill/downhill slopes classify on the expected side,
- reversing one triangle for diagnosis produces the expected rejected/inverted behavior.

Any final winding order is `VERIFIED` only after this runtime test.

## Walls / clipping adapter

After terrain heightfield collision works, convert authoritative nearby Matrix tile clipping into vertical SM64 wall surfaces.

Planned representation:

- blocked tile edge -> vertical quad,
- quad -> two triangles,
- wall height only needs to be high enough to behave as a wall for Mario's relevant movement/jump envelope,
- use the same shared coordinate transform as terrain.

The exact Matrix clipping flags/edge semantics are not yet classified for this adapter. Trace only the authoritative client-side clipping representation needed by Phase 3 before implementation; do not guess flag meanings from another revision.

## Objects / moving platforms

Terrain and tile-edge walls are not enough for full SM64 behavior.

Nearby Matrix scenery should be added incrementally:

- static solid scenery -> static SM64 floor/wall/ceiling surfaces,
- bridges/roofs/platforms -> floor/ceiling triangles as required,
- moving/rotating platforms -> `SM64SurfaceObject` with transform updates,
- water -> native Mario water-level/surface behavior when applicable.

For static objects, prefer a small collision proxy when it faithfully represents gameplay collision. Only use transformed object-model triangles when the object's shape actually requires that fidelity.

When model geometry is required, apply Matrix object position, orientation and relevant scale before the shared Matrix->SM64 coordinate conversion. Do not ship raw cache-model coordinates directly to the native bridge.

Use a nearby-object broadphase/scene window so object extraction is bounded. Do not stream every loaded render triangle to libsm64 merely because it is available.

## Surface behavior/material tagging

Do not block Phase 3.1 on special materials. Start with ordinary solid terrain surfaces.

After base collision is accepted, Matrix terrain/object metadata may map into libsm64 surface behavior where useful, for example:

- water,
- slippery/ice-like terrain,
- steep/sliding surfaces,
- special platform behavior.

Treat these mappings as explicit content/adapter rules rather than inferring behavior from render appearance alone.

## Phase 3 collision implementation order

Keep the collision work vertically testable:

1. **Terrain heightfield only** — bounded client-side terrain window -> two triangles per tile -> static libsm64 surfaces.
2. **Coordinate/winding acceptance** — verify standing, slopes, jumps, landings and recenter behavior before adding another collision source.
3. **Tile-edge walls** — convert verified Matrix clipping edges into vertical quads.
4. **Static object collision** — add nearby collision proxies/model triangles only where terrain/walls are insufficient.
5. **Dynamic surfaces** — moving/rotating platforms via libsm64 surface objects.
6. **Surface types/water** — layer special SM64 behavior after ordinary geometry is reliable.

Terrain-first is deliberately the smallest high-value slice: once accepted, native Mario should respond to real RuneScape hills/slopes instead of the temporary flat floor while the later wall/object layers remain isolated.

## Mario visual presentation options

The native simulation and visible Mario are deliberately separate concerns.

### Option A — revision-830 Mario asset

Import/convert Mario model, textures and animations into the 830 presentation pipeline, then map native `action` / `animID` / `animFrame` to the Matrix model.

Pros: integrates naturally with RuneScape rendering/caching.

### Option B — direct libsm64 geometry

`SM64MarioGeometryBuffers` can return Mario's generated triangles, normals, colors and UVs every tick. Matrix could eventually feed that geometry through a client-side dynamic render path.

Pros: native SM64 animation/geometry output with less animation retargeting.

Cons: requires a trustworthy dynamic-mesh render adapter in the 830 client.

Do not choose between these until the transport/core simulation proof succeeds.

## Input mapping

Initial mapping target:

- Matrix camera look -> `camLookX/camLookZ`
- movement keys/controller -> analog `stickX/stickY`
- Space -> A
- later attack/action key -> B
- later crouch/ground-pound key -> Z

Matrix remains the input owner. The SM64 core receives a normalized snapshot; it never installs its own keyboard/controller hooks.

## Server / multiplayer boundary

The first passthrough work is local presentation/simulation only.

Do not let the sidecar become authoritative for persistent world/player state.

Later multiplayer design should follow:

```text
local input
   ↓
SM64 prediction/simulation
   ↓
Matrix presentation
   ↓
server validation / legal movement state
   ↓
remote replication + correction
```

The exact protocol is deferred until local Mario movement and collision are stable.

## Failure behavior

- Bridge missing/not built -> Mario native probe is unavailable; normal RuneScape mode remains safe.
- ROM missing -> fail bridge initialization with a clear path/error; never download or embed a ROM automatically.
- Native process exits/crashes -> Matrix detects EOF/process death, disables native bridge use, and preserves the RuneScape client.
- Protocol mismatch -> reject rather than parsing unknown state.
- No native failure may silently mutate server authority, plane, clipping or persistence.

## Development sequence

### Bridge Spike A — transport/native core

1. Build `libsm64` externally from its source.
2. Build a tiny `sm64_bridge` sidecar linked to `libsm64`.
3. Sidecar loads the local US ROM.
4. Sidecar creates a flat two-triangle test floor and one Mario.
5. Matrix Java launches the process.
6. Java sends deterministic A-button input steps.
7. Native SM64 state returns over the protocol.
8. PASS when Java observes Mario Y/action state change caused by native SM64 code.

No RuneScape collision or visible-player movement is required for Spike A.

### Bridge Spike B — transform handoff

Use native Mario vertical state to drive the already-VERIFIED Matrix local-player transform on a flat/terrain-relative baseline.

This is the proof where an actual SM64-derived tick causes the visible RuneScape-side Mario/player to jump.

### Bridge Spike C — Matrix terrain collision

Replace the temporary flat native floor with nearby RuneScape terrain converted to `SM64Surface[]`.

PASS when native Mario can stand/run/jump on changing RuneScape terrain heights without the Java jump physics implementation owning the action.

### Bridge Spike D — world collision expansion

Add walls/object proxies, slopes, ceilings and moving surface objects as required by actual test areas.

## Evidence status

### VERIFIED

- Matrix3 can visually move the actual local player vertically above RuneScape terrain.
- The explicit RuneScape/Mario controller boundary works at runtime according to user acceptance.

### verified-static

- `libsm64` exposes the required external-engine Mario input/state/surface APIs.
- Its example advances Mario at a fixed 30 Hz and consumes caller-provided collision surfaces.
- It loads Mario texture/animation data from a user-provided US ROM at runtime.
- Matrix-side conversion -> bridge payload -> libsm64 surfaces is the selected Phase 3 ownership boundary; the native bridge should remain Matrix-agnostic.

### HYPOTHESIS

- Sidecar stdin/stdout latency is sufficient for the first 30 Hz simulation proof and potentially for development use.
- A local RuneScape terrain bubble can be represented economically as two SM64 triangles per terrain tile.
- A `16x16` to `32x32` initial terrain window with threshold-based recentering is a reasonable first profiling range.
- Tile clipping edges can be represented economically as vertical two-triangle wall quads once the authoritative Matrix clipping semantics are traced.

### UNKNOWN

- Final Matrix<->SM64 collision coordinate scale/signs.
- Final collision-bubble radius and rebuild threshold.
- Final verified terrain triangle winding after Matrix heightmap conversion.
- Exact authoritative Matrix clipping flags/edge semantics for the Mario adapter.
- Performance/fidelity tradeoff for nearby object collision extraction.
- Final multiplayer reconciliation model.
