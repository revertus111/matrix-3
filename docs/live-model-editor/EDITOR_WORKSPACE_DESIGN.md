# Live Model Editor - Compact Workspace Design

## Purpose

Design Matrix3 Live Model Editor around a small ~16-inch display. The 3D world is the primary workspace; permanent editor chrome must be minimal.

## Visual direction

The long-term Editor Workspace Mode follows the annotated concept:
- **red areas** = normal gameplay HUD/interface surfaces that should disappear while authoring;
- **blue area** = a thin editor tool rail/dock.

When editing, the target is nearly full-world viewport plus a narrow right rail. One compact drawer can open next to that rail, and clicking the active tab again collapses it.

HUD suppression must use the real Matrix3 interface/root-window owner. Do not fake it with opaque screen masks or hardcoded pixel rectangles. Until that seam is verified, HUD suppression is a separately gated follow-up.

## Small-screen rules

1. Maximum viewport, minimum permanent chrome.
2. No whole-editor horizontal scrollbar.
3. No giant page-length vertical form.
4. No simultaneous duplicate Whole and Part transform blocks.
5. Only one tool drawer open at a time.
6. Parts list owns its own vertical scrollbar.
7. Common operations remain hotkey-driven.
8. Expanded drawer targets roughly 260-300 px class width and remains resizable.
9. Collapsed rail targets roughly 36-44 px.
10. Exact numeric entry remains available.

## Right tool rail

- **P** Parts / Selection
- **T** Transform
- **M** Material / Replace
- **C** Camera
- **O** Object / Source
- **S** Project / Save
- **<** Collapse/expand drawer
- **X** Exit Editor

Click another tab to swap drawers. Click the active tab again to collapse back to rail-only mode.

## Parts drawer

- Whole / Part / Multi
- Select All / Clear / Reset
- connected-component list
- Rebuild
- Isolate / Show All
- Hide
- Duplicate
- Delete
- Undo

Transform and replacement controls do not live here.

## Transform drawer

One context-sensitive inspector:
- Move [G], Rotate [R], Scale [S]
- Free [F], X/Y/Z constraints
- exact numeric scale/move/yaw
- Reset Selection
- Undo

Whole mode shows whole-clone values. Part/Multi shows selected component values in the same drawer position.

## Material drawer

- Construction source/material catalog
- Replace Selected
- Replace Matching Parts
- Restore Original

## Camera drawer

### RTS
For top-down assembly:
- WASD/arrows pan
- MMB drag orbit yaw/pitch
- wheel zoom
- Q/E yaw
- preserve existing RTS pivot/view state

### FREE
For underneath/inside alignment:
- reuse existing Construction FREE_BUILD camera
- unrestricted inspection/fly
- WASD movement
- Q/E vertical
- existing Matrix3 free-camera look behavior

One shared ConstructionBuildCamera remains authoritative.

## Focus contract

The Swing overlay must feel like an in-client tool, not a separate desktop app.

- normal editor button action -> return keyboard focus to Matrix3 Canvas;
- part-row selection -> return focus to Canvas;
- MMB world press -> explicitly reacquire Canvas focus;
- camera controls should work immediately after ordinary overlay interaction;
- numeric spinner/text entry intentionally retains focus while typing exact values;
- file chooser/text-entry workflows may hold desktop focus until completed.

Desired flow: editor action -> viewport controls immediately.

## Resize contract

- retain resize support;
- no outer horizontal scrolling;
- individual lists may scroll vertically;
- drawer dimensions remain bounded by Canvas;
- rail-only collapsed mode stays thin regardless of expanded width.

## Must preserve

Whole/Part/Multi, world/list selection, multi-select, G/R/S, constraints, exact numeric transforms, isolate/show/hide, duplicate/delete/undo, Construction replacement/restore/matches, project save/load, Save Selection export, source model metadata, type/rotation/tile offsets, resize, Exit/Escape restoration, RTS camera and FREE camera.

## Bundle 2.7A - Compact shell / focus / camera switch

- right tool rail
- one drawer at a time
- no outer horizontal scrollbar
- contextual Transform drawer
- Parts/Transform/Material/Camera/Object/Project drawers
- RTS/FREE toggle
- focus handoff to Canvas after normal tool actions
- MMB focus reacquisition
- preserve existing editor operations

## Bundle 2.7B - Dedicated HUD suppression

Gated on verified Matrix3 UI ownership seam:
- snapshot normal HUD state;
- suppress normal gameplay HUD in Editor Workspace;
- no hardcoded pixel masks;
- no permanent NIS/Legacy mode mutation;
- restore exact prior HUD state on Exit/Escape;
- verify NIS and Legacy where applicable.

## Runtime acceptance

1. Open Conveyor belt 46298.
2. Confirm narrow rail + one drawer.
3. Collapse drawer to rail only.
4. Switch P/T/M/C/O/S without outer horizontal scroll.
5. Whole -> Transform shows whole values.
6. Part/Multi -> Transform shows component values in same inspector.
7. RTS camera still works.
8. FREE camera can move under the conveyor.
9. Click editor buttons/list then use camera immediately without a sacrificial world click.
10. Exact numeric input still works.
11. Re-test isolate/duplicate/delete/undo/replace/save/export.
12. Exit Editor restores source/camera ownership.
