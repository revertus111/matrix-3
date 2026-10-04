# Mario Equipment Workbench

## Location

```text
Client Console -> N64 -> Mario 64 -> Equipment Workbench
```

The workbench is developer tooling over the established Mario renderer, attachment adapter and session calibration owners. It does not replace Matrix equipment definitions or libsm64 animation ownership.

## Protocol-v2 semantic geometry

The preferred workflow requires the rebuilt semantic bridge.

Workbench status shows:

- bridge protocol version;
- semantic geometry availability;
- active coverage profile;
- shared semantic FACE width / height / depth;
- protected part counts;
- removable part counts;
- masked source triangle count.

Protocol-v2 part ids:

```text
FACE             protected
EYES             protected
MOUSTACHE        protected
CAP              removable
HAIR_SIDEBURN    removable
HAIR_BACK        removable
UNKNOWN          always preserved
```

The complete mixed FACE mesh is protected. Mario's nose is inside FACE and therefore cannot be removed by `FULL_HELM_SAFE`.

## Coverage controls

### Keep all Mario head parts

No semantic head geometry is removed.

Use this for hats/crowns or for before/after comparison.

### Full helm safe

Removes only:

- CAP
- HAIR_SIDEBURN
- HAIR_BACK

Keeps FACE, EYES, MOUSTACHE and all UNKNOWN geometry.

This is deliberately conservative. If runtime testing proves some beige side/back skull inside FACE still needs removal, subdivide FACE later using source-local mesh evidence; do not return to a whole-body cylinder as the normal solution.

`Apply semantic coverage only while a helmet is equipped` should normally remain enabled.

## Shared fit reference

Under protocol v2 the attachment adapter and auto-fit use the same display-list-local FACE reference.

Static source evidence establishes:

- FACE local Z = left/right;
- FACE local +Y = face-out/front;
- the remaining local axis supplies FACE height.

Helmet baseline width therefore comes from FACE left/right width. Mario's forward-projecting nose depth does not force the helmet larger.

The final helmet scale remains uniform. Revision-830 helmet outer bounds/cavity ratios are still approximations, so visual calibration remains useful.

## Helmet transform calibration

Controls:

- **Scale multiplier** — final uniform correction on the automatic baseline.
- **Head-local X / Y / Z** — seating corrections.
- **Yaw delta** — item correction on top of the established Mario helmet base yaw.
- **Flip helmet 180°** — explicit item correction only.
- **Reset / recalc fit** — clears session correction and lets the auto-fit baseline resolve again.
- **Freeze pose** — freezes Matrix-visible Mario while the native worker remains live.

## Legacy geometric cutter

The old body-height/radius cutter remains only for protocol-v1 debugging.

When semantic protocol v2 metadata is available, those geometric controls are ignored.

Do not use the old cutter as the normal full-helmet solution; it cannot guarantee nose/face protection.

## Orientation truth

Authoritative transform/sign notes remain in:

```text
docs/n64/TRANSFORM_CONVENTIONS.md
```

Important established rules:

- native `faceAngle` remains world-facing authority;
- Mario helmet base yaw remains the accepted worn-model correction;
- the runtime-proven V4 opposite head delta is consumed as inverse/transpose;
- source-local face axes are geometry metadata, not a replacement for runtime world-facing validation.

## Save / handoff

`Save profile .md` writes:

```text
docs/n64/MARIO_EQUIPMENT_RUNTIME.md
```

The snapshot includes helmet transform, protocol/semantic availability, coverage profile, FACE W/H/D and part counts. `Copy markdown` copies the same data.

## Recommended V7 workflow

1. Rebuild the native bridge with `make bootstrap`.
2. Equip the target helmet and enter Mario mode.
3. Confirm protocol `v2` and semantic metadata `AVAILABLE`.
4. For a full helmet, press `Full helm safe`.
5. Judge the silhouette before changing scale.
6. Freeze Pose.
7. Use Scale / X / Y / Z / Yaw only for small final seating corrections.
8. Unfreeze and check idle/turn/jump/backflip/ground-pound.
9. Compare `Keep all` vs `Full helm safe` if geometry looks suspicious.
10. Save/copy the profile once the result is visually accepted.
