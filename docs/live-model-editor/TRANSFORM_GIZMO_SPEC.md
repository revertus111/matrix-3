# Matrix3 Live Model Editor — Professional Transform Tool

## Purpose

Bundle 2.7C turns the existing Live Model Editor transform proof-of-concept into a compact professional authoring workflow.

The viewport is the primary editing surface. The interface is a live inspector that shows exact values while the developer manipulates Whole / Part / Multi selections directly in the 3D world.

This document is the design authority for the 2.7C transform-tool work.

## Core interaction contract

### Viewport first

The normal workflow is:

1. Select Whole, Part, or Multi.
2. Choose Move, Rotate, or Scale.
3. Manipulate the selected geometry directly in the viewport.
4. Watch exact transform values update live in the compact inspector.
5. Type exact values only when precision is required.

The numeric interface must not be the only practical way to transform geometry.

### Free movement and snapping must coexist

Free movement is the default.

- Normal drag: continuous/free transform.
- Hold Ctrl during an active drag while SNAP is OFF: temporary snapping.
- Toggle SNAP ON: snapping becomes persistent.
- Hold Ctrl during an active drag while SNAP is ON: temporarily bypass snapping and move freely.

This makes it possible to move a part freely, hold Ctrl to land on an exact increment, release Ctrl, then continue free adjustment without changing tools.

#### Initial snap values

- Move snap: 16 model units.
- Rotation snap: 15 degrees.
- Scale snap: deferred until runtime use proves it is useful.

Move and angle snap values are configurable from the editor.

Snapping modifies the transform result inside the active gesture. It must not create a second correction edit after the drag.

### Selection ownership

- Whole: transform the complete editable object.
- Part: transform the selected connected component.
- Multi: transform all selected components as one selection group.

For Multi mode, move/rotate/scale gestures preserve relative offsets between selected components.

### Pivot semantics

The transform gizmo will use an explicit visible pivot.

- Whole: object/model pivot.
- Part: selected component centroid.
- Multi: shared selection centroid.

When selection context changes, the pivot/gizmo should visibly relocate. A brief highlight is enough; no complex animation is required.

For Multi rotation, V1 rotates the selection around the shared group pivot. Per-part/individual-origin rotation is deferred.

## Transform modes

### Move

Hotkey: G

The Move gizmo will expose axis handles for X/Y/Z plus a free/plane interaction.

The inspector displays Position X/Y/Z continuously during viewport manipulation.

Move snapping applies only to axes affected by the current gesture. Free ground-plane movement snaps X/Z and does not unexpectedly alter untouched Y.

### Rotate

Hotkey: R

V1 exposes only rotation axes the current Matrix3 authoring model actually supports.

Current authoring state supports yaw. Do not fake unsupported pitch/roll UI.

The inspector displays the supported rotation value continuously.

Holding Ctrl follows the Free/Snap contract above. Rotation uses the configured angle snap.

### Scale

Hotkey: V

The Scale gizmo will support per-axis and uniform/free scaling using the current scale transform model.

The inspector displays scale as developer-friendly ratios:

- 100 internal = 1.00 displayed
- 50 internal = 0.50 displayed
- 125 internal = 1.25 displayed
- 200 internal = 2.00 displayed

Linked/uniform scale state is deferred from V1. Scale snapping is also deferred.

## Transform inspector

The final compact inspector replaces duplicated Whole/Part spinner forms with one contextual inspector.

Example:

```text
TRANSFORM                         PART 8

Position
X   128        Y   0          Z   -64

Rotation
Yaw  35°

Scale
X   1.00       Y   1.00       Z   1.25
```

The inspector always reflects the authoritative runtime transform.

### Exact numeric entry

Numeric editing must have a defined transaction:

- Click/double-click value: enter exact-edit mode.
- Enter: commit.
- Tab: commit and advance.
- Escape: cancel and restore the previous valid value.
- Click away: commit only when valid; invalid/partial input reverts.
- While a numeric field owns text input, ordinary editor hotkeys must not fire.

The viewport/runtime value remains valid at all times; malformed partial text must never leak into model state.

### Numeric scrubbing

The target UX supports horizontal drag/scrub on numeric values for quick precision work.

- normal scrub: standard step
- Shift scrub: fine adjustment
- Ctrl scrub: coarse adjustment unless Ctrl is currently reserved by an active viewport snap gesture

Scrubbing is a later 2.7C checklist item after the transform inspector replaces the current spinner implementation.

### Multi-value display

If a Multi selection contains different absolute values, the inspector may display a mixed marker such as —.

Direct viewport manipulation always applies a delta and preserves relative transforms.

Entering a valid exact value applies the corresponding delta from the primary/selection transform according to the existing multi-selection authoring semantics.

## Gizmo visual behavior

The viewport gizmo is a professional editing control, not decorative UI.

Required V1 visual states:

- idle axis color
- subtle hover highlight
- clear active-drag highlight
- visible pivot marker
- compact transform readout near the gizmo during an active drag

Do not turn hover/active feedback into a shader/rendering sub-project.

Example active readout:

```text
MOVE X
+128
```

or

```text
ROTATE
45°
```

The permanent inspector remains the authoritative exact-value display.

## Undo contract

One completed manipulation gesture equals one undo entry.

- mouse-down begins one transform transaction
- mouse movement updates the live preview
- mouse-up commits one undo step

A 300-pixel drag must not produce hundreds of history entries.

Numeric scrub is one history entry from press to release.

A committed exact numeric edit is one history entry.

The current connected-part gesture system already follows this model and must remain the basis for gizmo transforms.

## Keyboard contract

Current/proposed transform workflow:

- 1 — Whole
- 2 — Part
- 3 — Multi
- G — Move
- R — Rotate
- V — Scale
- F — Free axis/plane constraint
- X / Y / Z — axis constraint
- Ctrl during viewport drag — temporary inverse of persistent Snap state
- H — hide/show selected
- Shift+H — show all
- I — Solo/Isolate selection
- Ctrl+A — select all
- Ctrl+D — duplicate
- Delete — delete
- Ctrl+Z — undo
- Ctrl+S — save project
- Ctrl+Shift+S — save selection asset
- Ctrl+O — load project
- Tab — collapse/expand editor workspace
- Escape — cancel active numeric edit first; otherwise exit editor session

Camera controls retain ownership of WASD/MMB/wheel and the established RTS/FREE camera inputs.

## Compact UI direction

The Edit drawer should become the primary workspace:

```text
WHOLE   PART   MULTI
MOVE    ROTATE SCALE
FREE      X      Y      Z

SNAP OFF [Ctrl = Snap]
Move Snap 16     Angle Snap 15°

TRANSFORM                     PART 8
Position  X ... Y ... Z ...
Rotation  Yaw ...
Scale     X ... Y ... Z ...

PARTS
...
Solo Hide Dup Delete
All Clear Reset Undo
```

The part list should consume flexible remaining height instead of forcing the transform controls to clip.

Secondary drawers remain Material, Camera/View, Object, and Project.

## V1 scope

Required:

- [x] Authoritative 2.7C specification.
- [x] Free transform remains the default.
- [x] Persistent SNAP toggle.
- [x] Ctrl temporarily enables snap when SNAP is off.
- [x] Ctrl temporarily bypasses snap when SNAP is on.
- [x] Configurable Move snap.
- [x] Configurable Angle snap.
- [x] Snap is applied inside the existing transform gesture.
- [x] Replace duplicate Whole/Part spinner UX with one contextual live inspector.
- [x] Display scale as decimal ratio while retaining current internal integer representation.
- [x] Safe exact numeric entry contract.
- [ ] Numeric value scrubbing.
- [x] Render visible pivot.
- [x] Render Move gizmo.
- [x] Render supported yaw Rotate gizmo.
- [x] Render Scale gizmo.
- [x] Move-gizmo hit testing / hover / active state.
- [x] Rotate-ring hit testing / hover / active state.
- [x] Scale-gizmo hit testing / hover / active state.
- [x] Whole / Part / Multi gizmo pivot positioning.
- [x] Multi shared-pivot yaw rotation.
- [ ] Compact active transform readout near gizmo.
- [ ] Keyboard nudge path.
- [ ] Centralized hotkey/text-entry ownership.
- [ ] Active-state highlighting in the compact UI.
- [ ] Remove obsolete duplicate legacy Parts/Transform panel methods after replacement UI is runtime-safe.

## Deferred

Not part of the first professional vertical slice:

- linked-scale toggle
- scale snapping
- Local / World transform orientation switch
- individual-origin Multi rotation
- full XYZ rotation until the Matrix3 authoring state supports it correctly
- advanced typed math expressions such as +16, -32, *2
- redo/history browser
- autosave/recovery
- fancy gizmo shaders/effects

## Architecture boundaries

- Matrix3 renderer and object/model ownership remain authoritative.
- Do not mutate shared cached models.
- Do not write permanent cache model bytes in 2.7C.
- Reuse the existing LiveModelEditorPreview / LiveModelEditorParts gesture and transform APIs.
- The Swing overlay owns inspector/configuration UI, not 3D model state.
- Gizmo rendering/picking must use the smallest Matrix3-native render seam available; do not introduce a second camera or general rendering framework.
- Java 8 + Eclipse remain the protected target.

## Runtime acceptance — 2.7C-A snap foundation

1. Pull and Eclipse/Java 8 clean-build Client.
2. Open Conveyor belt 46298 in Live Model Editor.
3. Select a Part and choose Move / Free.
4. Leave SNAP OFF and drag: movement is continuous.
5. Start a drag, hold Ctrl, and confirm the affected position values jump to the Move Snap increment.
6. Release Ctrl while still dragging and confirm movement immediately returns to free/continuous.
7. Toggle SNAP ON and drag: movement stays snapped.
8. While SNAP ON, hold Ctrl during the drag and confirm movement becomes temporarily free.
9. Change Move Snap from 16 to another value and confirm the next snapped drag uses the new increment.
10. Choose Rotate, change Angle Snap, and confirm snapped yaw uses that increment.
11. Confirm Scale remains free/unsnapped in this patch.
12. In Multi mode, confirm snapped movement preserves relative offsets.
13. Ctrl+Z after a Part/Multi drag must undo the whole drag as one gesture.
14. Exit/reopen the editor and confirm normal source-object/camera ownership behavior is unchanged.

## Implementation checkpoint

### 2.7C-B — contextual live inspector

Implemented / NEEDS BATCHED RUNTIME TEST:

- one visible contextual inspector for Whole / Part / Multi;
- Position X/Y/Z and Yaw update from authoritative runtime state;
- Scale displays as decimal ratios while internal state stays percent-based;
- Enter commits exact values;
- Tab commits through focus transfer;
- Escape restores the pre-edit value;
- invalid/partial blur input reverts rather than entering model state;
- ordinary editor hotkeys are suppressed while a numeric text field owns focus;
- duplicate Swing Ctrl+Z ownership was removed so text editing cannot accidentally undo geometry;
- the part list can consume flexible remaining drawer height.

Part/Multi exact numeric commits already use the existing one-action undo path. Whole exact-value undo remains tied to the older whole-transform history limitation and is not being falsely marked as unified history.

### Next

2.7C-C should add the first visible Matrix3-native viewport pivot + Move gizmo before Rotate/Scale.


### 2.7C-C — native Move gizmo

Implemented / NEEDS BATCHED RUNTIME TEST:

- Part pivot comes from the connected component's detected source centroid plus its current move delta.
- Multi pivot is the arithmetic mean of all selected edited component centers.
- Whole uses the object/model origin as its pivot.
- `Class106.method1792(...)` is the verified-static Matrix3 3D-to-screen projection seam.
- `Class106.method1730(...)` and `method1725(...)` are the renderer-native line and filled-rectangle primitives used for gizmo handles.
- X / Y / Z handles are normalized to a fixed 56-pixel screen length so camera zoom does not make them unusably tiny/huge.
- The pivot center is a Free-move handle.
- Hover changes the handle to yellow; active drag changes it to white.
- Gizmo hit testing wins over component world-picking while the pointer is on a handle.
- Axis dragging projects mouse movement onto the visible handle direction, so dragging along the rendered X/Y/Z handle drives that authoring axis.
- Existing Free/Snap inverse-Ctrl behavior is reused without a second transform path.
- Part/Multi gizmo drags reuse `LiveModelEditorParts.beginGesture/endGesture`, preserving one drag = one undo record.
- The gizmo is drawn at the end of the existing Class578 developer-preview pass rather than through `Canvas.getGraphics()` or another Swing overlay.

Known runtime gate:
- definition model mirroring (`aBool5647`) is not duplicated in projection math until its point-transform semantics are proven;
- terrain contour deformation is not duplicated in projection math;
- those assets may show pivot/axis offset until runtime evidence establishes the exact mapping;
- Conveyor 46298 on its normal flat placement is the primary 2.7C-C acceptance asset.


### Runtime report — 2026-09-26

Conveyor belt 46298 was runtime-tested after 2.7C-C.

VERIFIED:
- contextual inspector is visible and usable;
- selected part values are shown in the interface;
- native pivot/Move gizmo renders on the selected conveyor component;
- handle sizing/alignment is usable in the live scene;
- the user reported the rest of the tested editor behavior working.

FAILED:
- Move snap did not produce the intended snapped interaction.
- Scale viewport interaction was also reported nonfunctional; the user clarified this immediately after the initial runtime report.

Bundle 2.7C-D supersedes the failed Snap path.
Bundle 2.7C-E supersedes the failed pre-gizmo Scale interaction.

### 2.7C-D — corrected snapping + yaw Rotate gizmo

Implemented / NEEDS BATCHED RUNTIME TEST:

- Move snapping now quantizes the transform delta from the gesture start rather than repeatedly rounding the final absolute transform.
- The start transform remains stable for the full mouse-down -> mouse-up transaction.
- Position snap therefore advances in exact configured increments relative to the drag start.
- Angle snap uses the same gesture-start delta rule.
- The Swing input gate now maintains an editor Ctrl latch in addition to MouseEvent modifier state so temporary Snap/Free behavior does not depend on every Canvas drag frame carrying the modifier bit.
- Opening/closing the editor clears the Ctrl latch.
- Rotate mode renders one yaw ring around the same Whole/Part/Multi pivot used by Move.
- The yaw ring uses the projected X/Z basis so its screen orientation follows the current camera/model projection instead of being an unrelated desktop circle.
- Ring hover is yellow; active rotation is white; normal yaw ring uses the Y-axis green.
- Rotation begins only when the yaw ring is hit, and the mouse angular delta around the projected pivot drives yaw.
- Angle snapping is applied to the yaw delta before commit.
- Multi yaw now rotates selected component centers around the shared selection pivot and applies the same yaw delta to each component.
- The shared-pivot rule lives in LiveModelEditorParts' selection-delta layer, so direct Multi rotation and exact Multi yaw editing do not diverge into different semantics.
- Part/Multi rotation continues to use the existing beginGesture/endGesture transaction: one drag remains one undo snapshot.

Deferred unchanged:
- Scale snapping remains deferred.
- Full pitch/roll rotation remains unsupported until the Matrix3 authoring model gains real XYZ rotation state.


### 2.7C-E — repaired Scale interaction + native Scale gizmo

Implemented / NEEDS BATCHED RUNTIME TEST:

- Scale mode now has an explicit native viewport gizmo instead of relying on an invisible direct-drag-only interaction.
- X / Y / Z scale handles reuse the same projected Whole/Part/Multi pivot and basis as Move.
- Axis endpoints use larger square handles to distinguish Scale from Move.
- The center pivot is a larger uniform-scale handle.
- Hover uses yellow and active drag uses white, consistent with Move/Rotate.
- Scale hit testing owns the pointer before mesh component picking, matching the established gizmo-first input rule.
- Axis Scale projects mouse motion onto the visible axis-handle direction.
- Center uniform Scale is intentionally screen-relative: right/up grows, left/down shrinks.
- The fallback direct-scale drag direction was normalized to the same right/up-grow convention.
- Part/Multi scaling continues through the existing beginGesture/endGesture transaction so one drag remains one undo record.
- Scale values continue to clamp to the existing 0.10x–4.00x internal range (10–400 percent).
- The inspector continues to display those values as decimal ratios (1.00, 1.25, etc.).
- Scale snapping remains deliberately deferred; the Snap controls currently apply only to Move and Rotate.
- Multi Scale continues to apply the same scale delta to each selected part while preserving their existing relative center offsets. Shared-pivot spatial expansion/contraction is not added implicitly in this bundle.
