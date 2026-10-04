# N64 / Matrix Transform Conventions

This file is the authoritative transform/sign reference for imported N64 character presentation in Matrix3.

The purpose is simple: **do not rediscover axis direction by guessing.** If runtime evidence changes a convention, update this file in the same approved work.

## Evidence labels

- `VERIFIED` = confirmed in the running client.
- `verified-static` = established directly from source/math but not yet runtime-confirmed.
- `HYPOTHESIS` = plausible but not proven.
- `UNKNOWN` = deliberately not assumed.

## Current Mario presentation conventions

### libsm64 geometry -> Matrix model

`VERIFIED / verified-static`

- libsm64 publishes final animated triangle geometry, not a Matrix-compatible bone skeleton.
- Matrix builds Mario from that final geometry every native frame.
- Mario model X is copied into Matrix model X.
- Mario model Z is copied into Matrix model Z.
- libsm64 Y is mirrored for Matrix presentation: `matrixY = -libsm64Y`.
- The Y mirror reverses triangle winding, so Mario visual faces are emitted as A/C/B rather than A/B/C.

### Native facing

`VERIFIED`

- `Sm64BridgeSession.NativeState.faceAngle` is the native Mario facing authority used by the equipment adapter.
- Do **not** infer forward from a cross-product alone when `faceAngle` provides an independent runtime reference.
- Exact human-readable world labels such as “+X is east” or “+Z is north” are intentionally not asserted here until separately runtime-proven. They are not needed for the attachment contract.

### RuneScape helmet base yaw

`VERIFIED`

- Statius's full helm rendered backwards when the Mario helmet base yaw offset was `0°`.
- Mario helmet presentation therefore uses a base worn-model yaw correction of `180°` before any per-item yaw delta.
- Per-item/workbench yaw is an additive correction on top of that base.

### Animated Mario head delta

`VERIFIED 2026-10-04`

V4 originally derived the rigid head rotation mathematically as:

```text
rawDelta = currentHeadBasis * transpose(referenceHeadBasis)
```

That matrix followed Mario's motion but drove the Matrix helmet in the **opposite direction** at runtime.

For the Matrix equipment transform seam, the accepted correction is therefore:

```text
matrixHeadDelta = inverse(rawDelta)
                = transpose(rawDelta)
```

This is not a cosmetic `+180°` patch. It is the inverse rigid rotation required by the Matrix transform convention used at this attachment seam.

**Rule:** if a future attachment follows motion but moves opposite to the source joint/head, check matrix direction/order first. Do not blindly add another yaw flip.

### Head-local calibration

`verified-static`

Helmet calibration values are defined in Mario head-local space:

- X = local side offset.
- Y = local vertical offset.
- Z = local forward/back seating offset.
- Yaw delta = item correction layered on the `180°` Mario helmet base yaw.

The final local offset is transformed through the same animated head rotation used by the helmet, so seating corrections move with the skull rather than staying world-fixed.

## Mario head masking

`verified-static`

The N64 Equipment Workbench can remove Mario source triangles before `MarioVisualRenderer` builds the Matrix model.

Current mask controls are geometric and intentionally simple:

- **Cut starts at body height %**: higher values remove only the top of Mario; lowering the value removes farther down the head/body.
- **Head cut radius %**: limits the cut to the central head region instead of removing every high triangle in the animation.
- **Only while a helmet is equipped**: prevents the saved mask preview from affecting normal helmetless Mario.

The default helmet-safe preset is:

```text
start height = 72%
radius       = 40%
helmet only  = true
```

The initial workflow is to remove cap/hair/top-skull geometry while keeping the face below the cut line. Mario's nose is intentionally treated as a visual feature to preserve rather than a helmet-fit dimension.

## Validation procedure for any new transform

Before accepting a new N64/Matrix transform convention, check all of these in one runtime session:

1. Reference/idle orientation looks correct.
2. Turn left and right: destination moves the same direction as source.
3. Pitch/nod: destination pitches the same direction.
4. Roll/tilt: destination rolls the same direction.
5. Full-body flip: no inverse/doubled rotation appears.
6. Local X/Y/Z offset stays attached to the source part while it rotates.
7. Freeze the presentation and confirm calibration controls no longer fight animation.

If one axis is wrong, identify whether the problem is:

- basis handedness,
- matrix direction (`R` vs `R^-1`),
- multiplication order,
- local/world-space confusion,
- or an actual fixed model-axis correction.

Do not classify an axis/sign as `VERIFIED` until the runtime behavior proves it.

## Developer workbench

The live controls are in:

```text
Client Console -> N64 -> Mario 64 -> Equipment Workbench
```

Current runtime values can be explicitly written from the tool to:

```text
docs/n64/MARIO_EQUIPMENT_RUNTIME.md
```

That runtime file is a calibration snapshot. This file (`TRANSFORM_CONVENTIONS.md`) remains the authoritative explanation of why transforms/signs are applied the way they are.
