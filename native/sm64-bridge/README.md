# Matrix3 SM64 Native Bridge

This folder contains the native sidecar used by the Mario passthrough workstream in `docs/mario/SM64_PASSTHROUGH_ARCHITECTURE.md`.

Current flow:

```text
Matrix3 Java / Sm64BridgeSession
   -> stdin/stdout protocol
   -> sm64_bridge.exe
   -> libsm64 / SM64-derived Mario tick
   -> SM64MarioState
   -> Matrix3 client-thread presentation
```

The sidecar still uses temporary flat collision. Real RuneScape terrain/object surfaces are Phase 3.

## Local-only dependencies

Do not commit any ROM file.

Default paths:

- sidecar: `native/sm64-bridge/dist/sm64_bridge.exe` on Windows
- ROM: `native/sm64-bridge/baserom.us.z64`

Overrides:

```text
-Dmatrix3.sm64.bridge=<path>
-Dmatrix3.sm64.rom=<path>
-Dmatrix3.sm64.verticalScale=<positive-float>
```

or environment variables:

```text
SM64_BRIDGE_EXE
SM64_ROM
```

## Windows build

Use an **MSYS2 MinGW64** shell.

Inside this folder:

```bash
make bootstrap CC=gcc CXX=g++
```

`bootstrap` will:

1. clone `https://github.com/libsm64/libsm64.git` into ignored `.deps/libsm64`,
2. build the shared library,
3. build `dist/sm64_bridge.exe`,
4. copy `sm64.dll` beside it.

The bridge does not use libsm64's SDL/OpenGL test renderer.

## ROM

Use your own dumped **SM64 US** ROM and place it locally as:

```text
native/sm64-bridge/baserom.us.z64
```

The folder `.gitignore` excludes ROM extensions, `.deps`, and `dist`.

## Manual protocol smoke test

```bash
./dist/sm64_bridge.exe ./baserom.us.z64
```

Expected:

```text
READY 1
```

Then:

```text
PING
```

Expected:

```text
PONG 1
```

A simulation tick is:

```text
STEP <camLookX> <camLookZ> <stickX> <stickY> <A> <B> <Z>
```

Example A-button press:

```text
STEP 0 -1 0 0 1 0 0
```

Reply:

```text
STATE <x> <y> <z> <vx> <vy> <vz> <faceAngle> <forwardVelocity> <action> <animId> <animFrame> <flags>
```

## Matrix3 persistent session

When Ctrl+M enters Mario mode, `Sm64BridgeSession` launches one sidecar process and keeps it alive for that Mario-mode session.

The Java worker:

- verifies `READY` / `PONG`,
- stabilizes a fresh idle native state,
- advances one `STEP` every `1/30` second,
- publishes latest/previous native state to the Matrix client thread,
- never writes Matrix scene/player state directly.

`MarioJumpController.tick()` remains the established client-thread hook and now maps native SM64 Y to the visible Matrix player transform. Java gravity is no longer Mario-mode vertical-physics authority.

Expected activation logs:

```text
[SM64 Bridge] Persistent session READY (30 Hz)
[SM64 Bridge] Native state -> Matrix transform ACTIVE (Y scale 3.0)
```

Space maps to native A for the current vertical proof. RuneScape X/Z movement, plane, clipping, pathfinding and server authority remain Matrix-owned until later phases deliberately replace those seams.

Leaving Mario mode or changing local-player lifecycle stops the sidecar session and restores the tracked Matrix ground baseline. Native startup/runtime failure automatically falls back to RuneScape mode.

## Protocol ownership

- stdout is protocol-only.
- native/libsm64 diagnostics go to stderr.
- protocol version is `1`.
- one `STEP` equals one native SM64 simulation tick.
- Java schedules persistent gameplay stepping at fixed 30 Hz.

## Temporary collision

The sidecar currently loads two flat `SM64Surface` triangles.

This is intentional. Phase 3 replaces them with a bounded local collision bubble generated from Matrix terrain/object data.
