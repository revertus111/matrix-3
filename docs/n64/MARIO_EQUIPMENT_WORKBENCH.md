# Mario Equipment Workbench

## Location

```text
Client Console -> N64 -> Mario 64 -> Equipment Workbench
```

The workbench is developer tooling over the established Mario renderer, attachment adapter and session calibration owners. It does not replace Matrix equipment definitions or libsm64 animation ownership.

## Protocol-v2 semantic geometry

The preferred workflow uses the semantic bridge.

Workbench status shows:

- bridge protocol version;
- semantic geometry availability;
- active replacement/coverage profile;
- shared semantic FACE width / height / depth;
- face-insert source triangle count;
- native part counts;
- masked source triangle count.

Protocol-v2 part ids:

```text
FACE
EYES
MOUSTACHE
CAP
HAIR_SIDEBURN
HAIR_BACK
UNKNOWN          always preserved
```

Static source evidence establishes FACE local +Y as face-out/front and local Z as left/right.

## Replacement / coverage profiles

### Keep all

No semantic Mario head geometry is removed.

Use this for before/after comparison or headgear that should sit over the original Mario head.

### Cap/hair only (`FULL_HELM_SAFE`)

Removes:

- CAP
- HAIR_SIDEBURN
- HAIR_BACK

Keeps the whole FACE/EYES/MOUSTACHE set.

This is the conservative V7 overlay profile.

### Head shell closed (`HEAD_SHELL_CLOSED`)

Removes every known semantic Mario head part:

- FACE
- EYES
- MOUSTACHE
- CAP
- HAIR_SIDEBURN
- HAIR_BACK

The revision-830 helmet becomes the complete visible head shell.

This is the correct first test for fully closed/full-face helmets because the helmet no longer needs to physically contain Mario's original skull geometry.

### Head shell + Mario face (`HEAD_SHELL_FACE`)

Uses the helmet as the head shell but restores Mario identity inside the face opening:

- CAP / HAIR stay removed;
- EYES and MOUSTACHE remain;
- only the front slice of mixed FACE remains;
- rear/side FACE geometry is removed using source-verified local +Y.

The **Mario FACE front slice %** control changes the cut plane:

- higher percentage = keep only farther-forward nose/central-face geometry;
- lower percentage = keep more cheeks/side face.

The default is `55%` and is only a starting hypothesis.

This V8 proof deliberately keeps the surviving Mario face at its normal animated scale and location. Do not add another face renderer/transform unless runtime testing proves the opening is correct but the face itself needs independent scale or XYZ.

`Apply semantic coverage only while a helmet is equipped` should normally remain enabled.

## Replacement-shell fitting

Replacement-shell profiles stop using the old estimated inner-cavity multiplier.

When entering `HEAD_SHELL_CLOSED` or `HEAD_SHELL_FACE`:

- active helmet auto-fit is reset;
- the adapter's existing outer-silhouette baseline remains the starting size;
- `MarioHelmetAutoFit` returns manual multiplier `1.0` for the shell baseline;
- normal Scale / head-local X/Y/Z / Yaw remains available for small visual corrections.

The point is no longer "make Mario's skull fit inside the helmet." The point is "make the revision-830 shell look correctly proportioned on Mario's animated body."

## Shared FACE reference

Under protocol v2 the attachment adapter and auto-fit use the same semantic FACE reference.

The first V7 runtime test exposed raw source FACE W/H/D `538/320/530` being compared directly with Matrix item-model units. `MarioSemanticGeometry` now converts the local FACE reference through the native posed-head scale and Matrix Mario model scale before fit math consumes it.

Mario's nose depth does not drive shell width.

## Helmet transform calibration

Controls:

- **Scale multiplier** — final uniform correction on the automatic baseline.
- **Head-local X / Y / Z** — seating corrections.
- **Yaw delta** — item correction on top of the established Mario helmet base yaw.
- **Flip helmet 180°** — explicit item correction only.
- **Reset / recalc fit** — clears session correction and lets auto-fit resolve again.
- **Freeze pose** — freezes Matrix-visible Mario while the native worker remains live.

## Legacy geometric cutter

The old body-height/radius cutter remains only for protocol-v1 debugging.

When semantic protocol v2 metadata is available, those geometric controls are ignored.

## Orientation truth

Authoritative transform/sign notes remain in:

```text
docs/n64/TRANSFORM_CONVENTIONS.md
```

Established rules:

- native `faceAngle` remains world-facing authority;
- Mario helmet base yaw remains the accepted worn-model correction;
- the runtime-proven V4 opposite head delta is consumed as inverse/transpose;
- source-local face axes are geometry metadata, not a substitute for runtime world-facing validation.

## Save / handoff

`Save profile .md` writes:

```text
docs/n64/MARIO_EQUIPMENT_RUNTIME.md
```

The snapshot includes helmet transform, semantic availability, replacement profile, FACE W/H/D, front-slice %, face-insert triangle count and native part counts.

## Recommended V8 workflow

1. `git pull origin main`.
2. Eclipse clean/build under Java 8. No native rebuild if semantic v2 already works.
3. Equip the target helmet and enter Mario mode.
4. Confirm protocol `v2` and semantic metadata `AVAILABLE`.
5. Freeze Pose.
6. Try **Head shell closed** first.
7. Try **Head shell + Mario face**.
8. If too much beige side/back face remains, raise `Mario FACE front slice %`.
9. If too much face disappears, lower it.
10. Use helmet Scale / X / Y / Z / Yaw only for shell seating.
11. Unfreeze and check idle/turn/jump/backflip/ground-pound.
12. Only after this visual test decide whether Mario's surviving face needs its own scale/XYZ transform.
