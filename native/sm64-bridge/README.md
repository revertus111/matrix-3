# Matrix3 SM64 Native Bridge Spike

This folder contains Bridge Spike A for `docs/mario/SM64_PASSTHROUGH_ARCHITECTURE.md`.

It does **not** modify RuneScape collision or move the Matrix player yet. Its job is to prove:

```text
Matrix3 Java
   -> native sidecar process
   -> libsm64 / SM64-derived Mario tick
   -> SM64MarioState
   -> Matrix3 Java
```

## Local-only dependencies

Do not commit any ROM file.

The default Java probe looks for:

- sidecar: `native/sm64-bridge/dist/sm64_bridge.exe` on Windows
- ROM: `native/sm64-bridge/baserom.us.z64`

Both paths can be overridden:

```text
-Dmatrix3.sm64.bridge=<path>
-Dmatrix3.sm64.rom=<path>
```

or with environment variables:

```text
SM64_BRIDGE_EXE
SM64_ROM
```

## Windows build

`libsm64` currently documents Windows builds through an **MSYS2 MinGW 64** shell.

The bridge itself does not require SDL/OpenGL because it does not run the libsm64 test renderer.

From an MSYS2 MinGW 64 terminal, inside this folder:

```bash
make bootstrap
```

`bootstrap` will:

1. clone `https://github.com/libsm64/libsm64.git` into local ignored `.deps/libsm64`,
2. run `make lib` there,
3. build `dist/sm64_bridge.exe`,
4. copy `sm64.dll` beside the executable.

`libsm64`'s own build may require Python/Git/toolchain prerequisites documented by that project.

## ROM

Use your own dumped **SM64 US** ROM.

For the default path, copy it locally as:

```text
native/sm64-bridge/baserom.us.z64
```

The folder `.gitignore` excludes ROM extensions, `.deps`, and `dist`.

## Manual protocol smoke test

After building:

```bash
./dist/sm64_bridge.exe ./baserom.us.z64
```

Expected first line:

```text
READY 1
```

Then type:

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

The reply is:

```text
STATE <x> <y> <z> <vx> <vy> <vz> <faceAngle> <forwardVelocity> <action> <animId> <animFrame> <flags>
```

## Matrix3 probe

When `Ctrl+M` enters Mario mode, `Sm64BridgeProbe` attempts one background native probe per client process.

If the sidecar/ROM are present, it:

1. launches the sidecar,
2. verifies protocol `READY`/`PONG`,
3. collects an idle baseline,
4. sends one A-button press followed by release ticks,
5. reports PASS only if native Mario Y rises and the native action changes.

Expected success log resembles:

```text
[SM64 Bridge] PASS native SM64 state: y ... -> ... (rise ...), action ... -> ...
```

This probe does **not** move the visible Matrix player. That is Bridge Spike B after the native transport proof is accepted.

## Protocol ownership

- stdout is protocol-only.
- native/libsm64 diagnostics go to stderr.
- protocol version is currently `1`.
- one `STEP` equals one native SM64 simulation tick; the long-term worker will schedule these at fixed 30 Hz.

## Temporary collision

The sidecar loads two flat `SM64Surface` triangles only.

This is intentional. Real RuneScape terrain/object conversion belongs to the Matrix collision-adapter phase after native state transport is proven.
