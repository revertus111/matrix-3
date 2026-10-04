# Mario helmet fitting: native semantic geometry

## Decision

Preserve Mario display-list identity before libsm64 flattens the mesh. Java then applies helmet coverage profiles and one shared head-local fit reference.

Full helmets should replace covered cap/hair presentation rather than scale around Mario's complete cartoon head. Mario's nose must never be removed automatically.

## Implemented V7 architecture

### Native export

Pinned libsm64 revision:

```text
fd11813208272b4271d92bd92feb8f3fdbe61be5
```

Tracked Matrix patch:

```text
native/sm64-bridge/libsm64-semantic-parts.patch
```

The patch extends the geometry stream at `process_display_list(...)`, before local vertices are transformed/flattened. Each output triangle receives:

- original display-list-local XYZ (9 floats / triangle);
- stable semantic part id (1 byte / triangle).

Binary bridge protocol v2 appends those arrays after the existing position/color/UV payload. Java still accepts protocol v1 as a fail-open legacy path.

### Stable semantic ids

```text
0 UNKNOWN
1 FACE
2 EYES
3 MOUSTACHE
4 CAP
5 HAIR_SIDEBURN
6 HAIR_BACK
```

Unknown geometry is never automatically hidden.

### Nose-safe policy

No independently named nose display list exists in the inspected Mario source. The nose is inside the mixed FACE mesh.

Therefore V7 deliberately protects:

```text
FACE
EYES
MOUSTACHE
UNKNOWN
```

`FULL_HELM_SAFE` removes only:

```text
CAP
HAIR_SIDEBURN
HAIR_BACK
```

This is intentionally conservative. If the remaining beige side/back skull still prevents a convincing full-helm silhouette, the next task is a bounded subdivision of FACE using actual local connectivity/coordinates. Do not infer an anatomical batch from source declaration order.

## Source-axis evidence

`verified-static` from the pinned Mario face geometry:

- local Z is left/right across the face (symmetric ±Z geometry);
- local +Y is face-out/front (eyes/moustache/front projection evidence);
- the remaining local axis supplies face height.

These are **mesh-local axes only**. They do not replace native `faceAngle` as runtime/world-facing authority.

## Shared fit reference

Before V7:

- `MarioEquipmentAdapter` used a captured broad animated-head reference;
- `MarioHelmetAutoFit` independently measured the current animated pose;
- those two references could disagree.

With semantic protocol v2, both use `MarioSemanticGeometry.Reference` from the original display-list-local FACE geometry.

Helmet baseline width uses FACE left/right span. FACE depth is not used to force a larger full helmet because Mario's protected nose projects along face-out depth and is intended to protrude through the helmet opening.

Helmet outer bounds/cavity fractions remain approximations. Manual workbench correction remains the visual acceptance layer.

## Native build reproducibility

`native/sm64-bridge/Makefile` now:

1. clones libsm64 when absent;
2. fetches the pinned revision;
3. resets/checkout-detaches to that exact revision;
4. verifies the tracked patch with `git apply --check`;
5. applies the patch;
6. rebuilds libsm64;
7. rebuilds/copies the Matrix sidecar runtime.

Required command:

```text
cd native/sm64-bridge
make bootstrap
```

## Evidence

### VERIFIED

- Real 830 helmets render on Mario.
- Full original-head containment produced an unacceptable oversized silhouette.
- First full animated head delta moved the helmet opposite the source head; inverse/transpose correction remains accepted.

### verified-static

- libsm64 retains display-list pointer + local Vtx coordinates before flattening.
- High/low Mario source variants contain named cap, hair, eyes, moustache and mixed face display lists.
- FACE has no independent named nose list.
- Protocol-v2 buffer sizes are bounded by the existing `SM64_GEO_MAX_TRIANGLES` / Java triangle limit.
- Frozen Java presentation frames preserve semantic arrays.
- `MarioVisualRenderer` already filters source triangles before tessellation/model construction, so no second renderer is needed.

### HYPOTHESIS

- Removing named cap/hair while preserving the complete FACE mesh will remove enough silhouette bulk that full helmets need only small transform corrections.

### UNKNOWN

- Whether a second semantic split inside FACE is needed for side/back skull.
- Final reusable helmet category defaults across multiple 830 helmet families.

## Runtime gate

After native rebuild, test Statius first:

1. protocol v2 / semantic metadata available;
2. `KEEP_ALL` baseline;
3. `FULL_HELM_SAFE` removes cap/hair;
4. FACE/eyes/moustache/nose remain;
5. shared semantic fit starts near a usable size;
6. animation attachment remains correct;
7. then repeat later with an open helm and hat/crown before promoting category defaults.
