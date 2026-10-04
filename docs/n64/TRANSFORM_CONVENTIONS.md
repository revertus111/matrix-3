# N64 / Matrix Transform Conventions

This file is the authoritative transform/sign reference for imported N64 character presentation in Matrix3.

**Do not rediscover axis direction by guessing.** Runtime/world-facing conventions and source-local mesh conventions are documented separately because they are not interchangeable.

## Evidence labels

- `VERIFIED` = confirmed in the running client.
- `verified-static` = established directly from source/math but not yet runtime-confirmed.
- `HYPOTHESIS` = plausible but not proven.
- `UNKNOWN` = deliberately not assumed.

## Matrix presentation geometry

`VERIFIED / verified-static`

- libsm64 publishes final animated triangle geometry.
- Matrix model X receives libsm64 X.
- Matrix model Z receives libsm64 Z.
- libsm64 Y is mirrored: `matrixY = -libsm64Y`.
- The Y mirror reverses winding, so Mario visual faces are emitted A/C/B rather than A/B/C.

## Native Mario world-facing authority

`VERIFIED`

- `Sm64BridgeSession.NativeState.faceAngle` is the runtime Mario facing authority used by the equipment adapter.
- Do not infer world forward from a cross product or source-local mesh coordinate when `faceAngle` provides the independent runtime reference.
- Human-readable labels such as “+X is east” remain intentionally unstated until independently proven; the attachment contract does not require them.

## RuneScape helmet base yaw

`VERIFIED`

- Statius's full helm rendered backwards with a zero-degree Mario helmet base yaw.
- Mario helmet presentation therefore uses the accepted `180°` worn-model base correction before per-item yaw delta.
- Per-item/workbench yaw is additive on top of that base.

## Animated Mario head delta

`VERIFIED 2026-10-04`

The original V4 rigid delta was derived as:

```text
rawDelta = currentHeadBasis * transpose(referenceHeadBasis)
```

It followed Mario's motion but drove the Matrix helmet in the opposite direction at runtime.

The accepted Matrix attachment seam consumes:

```text
matrixHeadDelta = inverse(rawDelta)
                = transpose(rawDelta)
```

This is a matrix-direction correction, not another cosmetic yaw hack.

**Rule:** when an attachment follows the correct motion but in the opposite direction, check `R` versus `R^-1`, multiplication order and local/world interpretation before adding a sign or 180-degree offset.

## Head-local calibration

`verified-static`

Existing helmet calibration values are transformed through the same animated head matrix:

- local X = side seating correction;
- local Y = vertical seating correction;
- local Z = forward/back seating correction;
- yaw delta = item correction on top of the accepted base yaw.

These are Matrix attachment-space semantics. Do not conflate them with the raw SM64 face display-list axes below.

## SM64 semantic FACE local axes

`verified-static — pinned source geometry`

Protocol v2 preserves original display-list-local coordinates before libsm64 applies the animated matrix.

For the pinned Mario mixed FACE mesh:

- **local Z = face left/right** — geometry is symmetric across ±Z;
- **local +Y = face-out/front** — eye/moustache/front projection evidence points outward in +Y;
- the remaining local axis supplies face vertical extent.

These facts are used only for semantic FACE measurement:

```text
fit width  = FACE local-Z span
fit height = FACE remaining vertical span
face depth = FACE local-Y span
```

Mario's nose projects into FACE local depth. Full-helmet auto-fit deliberately does **not** enlarge helmet width just to contain that protected forward projection.

**Critical rule:** source-local +Y face-out is not automatically Matrix world forward. `faceAngle` remains world-facing truth.

## Semantic helmet coverage

`verified-static; runtime acceptance pending`

Protocol-v2 stable parts:

```text
FACE             protected
EYES             protected
MOUSTACHE        protected
CAP              removable
HAIR_SIDEBURN    removable
HAIR_BACK        removable
UNKNOWN          preserved
```

`FULL_HELM_SAFE` hides only CAP + named hair. The complete FACE mesh remains, guaranteeing the nose is preserved.

The old height/radius cylinder is now only a protocol-v1 debug fallback and is ignored while semantic metadata is available.

## Validation procedure for new transforms

Before accepting any new transform convention, check in one runtime session:

1. Idle/reference orientation.
2. Turn left/right — destination follows same direction.
3. Pitch/nod — same direction.
4. Roll/tilt — same direction.
5. Full-body flip — no inverse/doubled rotation.
6. Local XYZ correction stays attached while rotating.
7. Frozen presentation remains stable while editing.

If wrong, classify the cause before patching:

- basis handedness;
- matrix direction (`R` vs `R^-1`);
- multiplication order;
- local/world-space mismatch;
- fixed worn-model axis correction.

Do not classify a runtime transform sign as `VERIFIED` from static intuition alone.

## Developer workbench

```text
Client Console -> N64 -> Mario 64 -> Equipment Workbench
```

Runtime profile snapshots may be explicitly written to:

```text
docs/n64/MARIO_EQUIPMENT_RUNTIME.md
```

That file records calibration state. This file remains the authoritative explanation of transform/sign conventions.
