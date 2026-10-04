# Mario Equipment Workbench

## Location

```text
Client Console -> N64 -> Mario 64 -> Equipment Workbench
```

The workbench is a developer-only live editor for fitting revision-830 equipment onto the imported Mario presentation. It does not replace Matrix equipment definitions, libsm64 animation ownership, or the normal Mario renderer.

## Active equipment / preview

The top card shows:

- Mario controller state.
- active helmet item id/name.
- 3D head-orientation availability.
- whether the visible Mario pose is frozen.
- how many libsm64 source triangles are currently removed by the head mask.

`Freeze pose` freezes the Matrix-visible SM64 snapshot while the native 30 Hz sidecar continues running. Use this before visually fitting a helmet.

## Helmet transform calibration

The workbench edits the existing per-item session calibration used by `MarioEquipmentAdapter`.

Controls:

- **Scale multiplier** — uniform final size multiplier.
- **Head-local X** — side-to-side seating.
- **Head-local Y** — vertical seating.
- **Head-local Z** — forward/back seating.
- **Yaw delta** — item-specific yaw correction on top of Mario's base helmet yaw.
- **Flip helmet 180°** — adds a one-click 180° item yaw correction for obviously reversed worn models.
- **Reset transform** — returns the active helmet's session values to scale `1.0`, XYZ `0`, yaw `0`.

Values can be typed directly or changed with the `-` / `+` buttons.

## Mario head masking

The mask is a live presentation cut applied before Mario's Matrix model is built.

- **Enable live head cut** — turns the geometric cut on/off.
- **Only cut while a helmet is equipped** — recommended default.
- **Cut starts at body height %** — lower it to remove farther down Mario's head.
- **Head cut radius %** — controls how wide the central cut volume is.
- **Helmet-safe preset** — `72%` start / `40%` radius / helmet-only.
- **Reset mask** — disables masking and restores preset defaults.

Use the cutoff to remove Mario's cap/hair/top skull first. Keep the cut above the central face so the eyes, moustache, and nose remain visible.

## Orientation diagnostics

The workbench permanently displays the accepted transform conventions rather than leaving them in chat history:

- native `faceAngle` is the Mario facing authority;
- worn helmets use a `180°` base yaw correction;
- the first V4 full-head delta was runtime-proven backwards;
- Matrix therefore consumes the inverse/transpose of that rigid head delta;
- libsm64 Y is mirrored into Matrix presentation Y.

Full evidence and anti-regression rules live in:

```text
docs/n64/TRANSFORM_CONVENTIONS.md
```

## Save / handoff

`Save profile .md` explicitly writes the current active helmet and head-mask values to:

```text
docs/n64/MARIO_EQUIPMENT_RUNTIME.md
```

This is a local developer calibration snapshot, not gameplay persistence. `Copy markdown` puts the same snapshot on the clipboard for chat/review.

## Recommended fitting workflow

1. Equip the helmet in RuneScape appearance.
2. Enter Mario mode.
3. Open `Equipment Workbench`.
4. Freeze the pose.
5. Fix obvious yaw with `Flip helmet 180°` only if required.
6. Adjust scale and head-local XYZ until the worn model seats correctly.
7. Enable the head cut and lower the start height until cap/hair/top-skull clipping disappears.
8. Keep Mario's central face/nose visible.
9. Unfreeze and check idle/turn/jump/backflip/ground-pound.
10. Save the `.md` profile when the result looks correct.
