# Zelda / OoT Phase 0 Test List

## Scope

This is the bounded NTSC-U 1.2 compatibility proof only. It does not enable Link in Matrix3 yet.

The bridge pins:

- `Cycl0o0/liboot` at `25208734c8ca388638f0ce63841e166fac9e5acb`
- `zeldaret/oot` at `269d03016cd0e3d7a0b8925e02b97a319c1d0e8d`

ROM bytes and extracted assets stay local under the user's control and are never copied into the repository.

## Prerequisites

Use an MSYS2 UCRT64 shell on Windows with:

- Git
- Python 3
- CMake
- a C11 compiler/toolchain

The supplied retail ROM must be the 32 MiB NTSC-U 1.2 image already identified as MD5 `57a9719ad547c516342e1a15d5c28c3d`.

## One-command proof

From the repository root:

```sh
cd native/oot-bridge
make probe OOT_ROM="/c/path/to/Legend of Zelda, The - Ocarina of Time (U) (V1.2) [!].z64"
```

The target clones only the two pinned source dependencies into ignored `.deps/` folders, resets them to the pinned commits, applies the tracked NTSC-U 1.2 compatibility patch, regenerates metadata from the pinned zeldaret XML, builds the native library/probe, runs liboot's ROM identifier, then runs the Link proof.

## Acceptance

Do not mark Phase 0.2/0.3 runtime-verified unless all of these are observed in the same run:

- ROM identifier reports NTSC-U 1.2 for the local image.
- Engine creation passes.
- Adult Link creation and Master Sword/Hylian Shield equipment pass.
- Adult skeleton is available and animated geometry is nonzero without truncation.
- Forward input changes Link's position.
- Link animation/action state advances.
- Child age switch and Kokiri Sword/Deku Shield equipment pass.
- Child skeleton is available and animated geometry is nonzero without truncation.
- Simulation tick advances and engine destroy passes.
- Final line is `[OoT NTSC12] RESULT: PASS`.

## Failure handling

If any check fails, copy the complete terminal output before changing code. A successful compile is not gameplay compatibility proof. A successful engine creation alone is also not enough.
