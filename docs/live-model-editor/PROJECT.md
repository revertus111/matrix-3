# Matrix3 Live Model Editor

## Goal

Build a Matrix3-native live model authoring workflow where a developer can right-click a real world object, resolve its revision-830 model references, create a private editable runtime clone, transform it inside the running client, preserve the authoring state as JSON, and later compile finished work into safe custom cache models.

The same transform/selection foundation is intended to become the developer-grade base for Construction Revamp fine-detail placement and reusable settlement prefabs.

## Non-negotiables

- Java 8 + Eclipse remain supported.
- Matrix3 object definitions and renderer remain authoritative.
- Live editing must not mutate shared cached models.
- Bundle 1 performs no cache writes and no server-world persistence writes.
- JSON is the editable project/source format; revision-830 model bytes are a later compiled artifact.
- Construction reuse must compose assets per settlement rather than creating a new cache model for every player's decoration layout.
- Preserve original obfuscated renderer names; add narrow documented bridges instead of renaming core classes.

## Canonical Main-Goal Status

| Area | Status | Notes |
| --- | --- | --- |
| Live object -> model resolution | ✅ Complete | Dev Inspector resolves object definition model IDs; Smelter 29394 -> model 64036 is the current reference asset. |
| Private in-world runtime model clone | ✅ Complete | Runtime-confirmed on the Smelter reference asset. Bundle 2.3A now suppresses only the selected source object's normal render so the editable model visually replaces it during the edit session; that replacement gate remains runtime-tested separately. |
| Live whole-model transforms | ✅ Complete | Runtime-confirmed in the live client; independent scale, translation and yaw visibly update the private clone. |
| JSON project save/load | ⚠️ Needs runtime verification | Authoring state saves under dev-model-projects and can be loaded back into the live preview. |
| Mesh component selection/editing | ⚠️ Needs runtime verification | Connected-part editing, Whole/Part/Multi selection, group actions/transforms, world picking, G/R/S mouse transforms, Construction replacement and reusable selection-asset export are implemented; consolidated runtime verification is pending. |
| In-client model-editor overlay | ⚠️ Needs runtime verification | RuneScape-styled owned overlay follows the Matrix3 game canvas, supports drag repositioning/live resize/scroll fallback and now leases the shared Construction RTS camera for WASD pan, wheel zoom and MMB orbit. |
| Permanent revision-830 model compiler | ❌ Not started | Requires validated encoder, model-ID allocation, backup, write/readback and hot reload. |
| Construction Detail Mode reuse | 🔵 Foundation in progress | Live Model Editor now consumes the Construction BuildPiece catalog for non-destructive part replacement; player-facing settlement Detail Mode and server-validated persistence remain future work. |

## Evidence

### verified-static

- `ObjectDefinitions.method6057(...)` is Matrix3's normal object-model factory and returns a renderer-specific `Model` copy from the definition cache path.
- `Model.method1351(..., true)` is already used by Matrix3 to produce private model copies before instance-specific mutations.
- `Model.method1464(...)`, `method1358(...)` and `method1412(...)` are used by the existing object-definition path for scale, translation and rotation semantics.
- `Class578.method6834(...)` is the established developer direct-render seam already used by Construction ghost rendering, Object Lab, Object Composite preview and rail-route preview.
- The selected Dev object target currently provides definition ID/name/world tile; exact live scene shape/type and rotation are not yet proven in that target contract.

### Important Bundle 1 limitation

Bundle 1 is a **direct-render runtime clone proof**, not a scene-object replacement.

The source scene object remains visible and authoritative. The editor clone defaults two tiles east to avoid z-fighting and can be moved to offset 0/0 to overlay the source. Proving/suppressing/replacing one exact scene object's renderer instance is a later seam if needed after this safe vertical slice is runtime accepted.

## Phase 1 - Runtime Clone Foundation

Status: NEEDS TEST

### Bundle 1.1 - Smelter runtime clone + JSON

Status: NEEDS TEST

Approved reference:
- Object: Smelter
- Object definition ID: 29394
- Resolved model ID: 64036

Checklist:
- [x] Add `Dev > Edit Model Live` on object right-click.
- [x] Add Inspector shortcut to the same editor.
- [x] Resolve and display all source model IDs through `DevDefinitionBridge`.
- [x] Build the selected object through Matrix3's normal ObjectDefinitions factory.
- [x] Clone the returned model privately before applying editor transforms.
- [x] Direct-render the clone in the live scene through the established Class578 seam.
- [x] Add live Scale X/Y/Z, Translate X/Y/Z and Yaw controls.
- [x] Keep object type/rotation explicit instead of guessing unverified live values.
- [x] Add preview tile X/Y offsets; default +2/0 avoids overlap with the source object.
- [x] Save JSON authoring projects under `dev-model-projects`.
- [x] Load JSON projects and restore the live authoring state.
- [x] No cache writes.
- [x] No server-world/persistent map writes.
- [ ] Eclipse/Java 8 client clean-build.
- [x] Runtime acceptance on Smelter 29394 / model 64036.
- [ ] Confirm another Smelter/source object remains visually unchanged while the clone is transformed.
- [ ] Save, hide, load JSON and confirm the same clone state returns.

## Phase 2 - Mesh Parts + In-World Selection

Status: NEEDS TEST

### Bundle 2.1 - Connected parts + per-part authoring

Status: NEEDS TEST

- [x] Detect connected components from revision-830 Class159 triangle connectivity.
- [x] Stable part list ordered by component face count.
- [x] Selected-part live highlight.
- [x] Isolate selected part.
- [x] Hide/show selected part.
- [x] Per-part X/Y/Z scale.
- [x] Per-part X/Y/Z translation.
- [x] Per-part yaw around the component centroid.
- [x] Duplicate selected component as an independently rendered part instance.
- [x] Delete selected component without mutating source cache geometry.
- [x] 64-step session undo stack plus Ctrl+Z.
- [x] JSON project v2 persists original-part edits, duplicate instances, selection and isolate state.
- [ ] Eclipse/Java 8 client clean-build.
- [ ] Runtime detect expected connected components on Smelter model 64036.
- [ ] Runtime verify selected-part highlight/isolate/hide.
- [ ] Runtime verify per-part move/rotate/scale changes only the selected component.
- [ ] Runtime verify duplicate/delete/undo.
- [ ] Save/load JSON v2 and confirm the same part edits return.

### Bundle 2.2 - In-client overlay + hover workflow

Status: NEEDS TEST

- [x] Replace the standalone decorated editor frame with an owned overlay window positioned over the Matrix3 game canvas.
- [x] Follow client move/resize and support dragging the overlay to a preferred in-client position.
- [x] Replace the generic Swing layout with a compact RuneScape-style dark/gold editor skin.
- [x] Keep target/model/part information visible without the oversized scroll-heavy layout.
- [x] Hover a part row to preview-highlight that connected component in the live world.
- [x] Clear hover preview when the pointer leaves the list; clicked selection remains the fallback highlight.
- [x] Keep click-to-select, isolate, hide/show, duplicate, delete, undo, per-part transforms, whole-clone transforms and JSON save/load in the overlay.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime verify the overlay remains attached to the game client rather than opening as an independent desktop editor window.
- [ ] Runtime verify moving the mouse across part rows changes the in-world highlight without clicking.
- [ ] Runtime verify leaving the part list restores the locked selected-part highlight.
- [ ] Runtime verify overlay drag/reposition and client move/resize tracking.
- [ ] Runtime verify all Bundle 2.1 part actions still function from the overlay.

### Bundle 2.3 - Proper live edit-session ownership + in-world picking

Status: ACTIVE / NEEDS TEST

#### Bundle 2.3A - Editor owns the target and viewport

- [x] Default the editable replacement to the source object's exact tile (preview offset 0/0).
- [x] Reopening the same object resumes the current authoring state instead of resetting/duplicating the clone.
- [x] While the edit session is active, action-23 Walk Here is consumed before RTS/Construction routing.
- [x] Normal object/NPC interaction actions are consumed while the model editor owns the viewport.
- [x] Canvas left-click input is consumed during the edit session; overlay controls remain interactive and camera-only inputs are not globally disabled.
- [x] Escape/Exit Edit pauses the runtime editor and restores the original scene object immediately.
- [x] Exact-source visual replacement hook added across Matrix3 object render implementations: the source object remains registered for clipping/world state but its normal model returns null while the editable replacement is active.
- [x] Multi-tile source matching uses the scene object's occupied tile bounds; other object families use object ID + plane + scene tile.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime verify the source Smelter disappears and only the editable replacement remains at the same tile.
- [ ] Runtime verify another Smelter using model 64036 remains visible and unchanged.
- [ ] Runtime verify canvas/minimap left-click cannot walk or interact while editing.
- [ ] Runtime verify camera controls still work.
- [ ] Runtime verify Exit Edit/Escape restores the original object with clipping/world state unchanged.
- [ ] Runtime verify reopening the same Smelter resumes the same part edits instead of creating/resetting another clone.

#### Bundle 2.3B - True in-world component picking + direct mouse transforms

Status: NEEDS TEST

- [x] Reuse Matrix3's verified Model.method1376(...) screen-hit path for connected-component picking rather than adding a second camera/raycast implementation.
- [x] Build cached component-only pick Models from the same Class159 source geometry used by the editor.
- [x] Hover the actual rendered component in the live 3D scene and preview-highlight it.
- [x] Left-click a hovered world component to lock/select it.
- [x] Keep the overlay list synchronized with world-selected components.
- [x] Direct left-drag transform workflow on the selected component.
- [x] G/R/S transform modes: Move / Rotate / Scale.
- [x] X/Y/Z axis constraints plus F for free/screen-plane movement.
- [x] One undo snapshot per mouse drag gesture rather than one undo record per mouse-motion event.
- [x] Keep normal Walk Here/world interactions suppressed while editor mouse ownership is active.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime verify screen hover chooses the expected Smelter bar/part rather than only the whole model.
- [ ] Runtime verify click locks the hovered part and the list follows the world selection.
- [ ] Runtime verify Move/Rotate/Scale drag semantics and axis constraints.
- [ ] Runtime verify camera viewing controls remain usable while editor mouse ownership is active.

#### Bundle 2.4A - Non-destructive Construction material replacement

Status: NEEDS TEST

- [x] Reuse ConstructionPlacementController's current build-piece catalog as the first shared material/object library.
- [x] Replace selected connected component with a Construction catalog object's source model geometry.
- [x] Replace All Matching uses stable source-component signatures (face count, vertex count and sorted dimensions) so repeated bars/supports can be swapped in one action.
- [x] Source component becomes hidden while the replacement is rendered; Restore returns the original component.
- [x] Replacement geometry auto-centers on the source component, auto-fits by longest dimension and auto-rotates X/Z when the dominant horizontal axis differs.
- [x] Replacement remains non-destructive and uses the selected part's existing move/rotate/scale controls for fine alignment.
- [x] JSON project v3 persists replacement object ID/type for original and duplicated part states.
- [x] World picking treats the replacement geometry as the same logical part index.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime select one Smelter side bar and replace it with a Construction catalog material.
- [ ] Runtime Replace Matches and confirm only geometrically matching repeated bars/supports are swapped.
- [ ] Runtime adjust replacement with direct mouse Move/Rotate/Scale.
- [ ] Runtime Restore and confirm the exact stock component returns.
- [ ] Runtime save/load JSON v3 and confirm replacements return without cache/world writes.

2.3B/2.4A deliberately keep replacement authoring client-local and JSON-backed. Permanent revision-830 cache compilation remains a later explicit build step.

### Bundle 2.5 - Professional selection + editor usability

Status: NEEDS TEST

- [x] Add explicit Whole / Part / Multi selection modes.
- [x] Persist a real multi-part selection set in the mesh-authoring state rather than only Swing UI selection.
- [x] Support list multi-selection plus additive/toggle world selection.
- [x] Apply move/rotate/scale drag deltas, hide, delete, duplicate, reset and direct replacement across the selected part set with one undo snapshot per action/gesture.
- [x] Highlight and isolate the full active selection rather than only one primary part.
- [x] Correct direct mouse transform direction so screen drag direction is no longer inverted against the editor's model axes.
- [x] Add Whole-mode direct G/R/S mouse editing against the existing clone transform.
- [x] Add Select All / Clear / Reset Selection controls and 1/2/3 selection-mode hotkeys.
- [x] Add project JSON v4 multi-selection persistence while retaining legacy partSelected compatibility.
- [x] Add Save Selection export under dev-model-assets as a reusable source-object/component recipe for later import/compiler work.
- [x] Make the live overlay resizable and scroll-safe so controls are not clipped on smaller client windows.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime verify on Conveyor belt 46298/models 49717+49718.

### Bundle 2.6 - Shared RTS authoring camera

Status: NEEDS TEST

- [x] Reuse ConstructionBuildCamera RTS mode instead of creating a second Live Model Editor camera controller.
- [x] Live Model Editor acquires RTS camera ownership on open and releases/restores prior camera ownership on Exit Edit/Escape.
- [x] Preserve an already-active Construction/settlement camera rather than tearing it down when the editor closes.
- [x] Keep LMB owned by model selection/transforms while MMB remains available for camera orbit.
- [x] Route world mouse-wheel input to the active RTS camera for zoom while editing.
- [x] Add MMB drag yaw + bounded pitch orbit around the existing RTS pivot.
- [x] Preserve WASD/arrow pan and Q/E yaw controls.
- [x] Persist the accepted RTS pitch together with yaw, pivot and zoom for the client session.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime verify MMB orbit + wheel zoom + WASD pan while editing Conveyor belt 46298.

### Bundle 2.7 - Professional Editor Shell

Status: ACTIVE / NEEDS TEST

Design authority: docs/live-model-editor/EDITOR_WORKSPACE_DESIGN.md

#### Bundle 2.7A - Compact shell / focus / camera switch

- [x] Replace giant page-length editor with right-side rail + one compact drawer.
- [x] Remove whole-editor horizontal scrollbar.
- [x] Add Parts / Transform / Material / Camera / Object / Project drawers.
- [x] Make Transform selection-context-sensitive.
- [x] Add RTS / FREE toggle through ConstructionBuildCamera.
- [x] Preserve RTS behavior and expose FREE_BUILD for unrestricted inspection.
- [x] Return focus to Matrix3 Canvas after ordinary button/list actions.
- [x] MMB world press explicitly reacquires Canvas focus.
- [x] Preserve exact numeric spinner/text input.
- [x] Keep resize support without outer horizontal scrolling.
- [x] Preserve existing selection/export/replacement/project actions.
- [ ] Eclipse/Java 8 clean-build.
- [ ] Runtime verify on compact 16-inch layout.

#### Bundle 2.7B - Dedicated editor HUD suppression

- [ ] Verify authoritative Matrix3 root-interface/HUD ownership seam.
- [ ] Snapshot/suppress normal gameplay HUD during Editor Workspace.
- [ ] Restore exact prior HUD state on Exit/Escape.
- [ ] No hardcoded pixel masks/cover windows.
- [ ] Verify NIS/Legacy restoration where applicable.

## Phase 3 - Professional Transform UX

Status: PLANNED

- G/R/S hotkeys.
- XYZ gizmos.
- Snapping, local/world transform modes and numeric transform entry.
- Ctrl+Z/redo/history.
- Safe project autosave/recovery.

## Phase 4 - Permanent Model Build

Status: PLANNED

- Revision-830 model encoder validation.
- Safe custom model-ID allocation.
- Bake project transforms/parts.
- Backup + cache write + byte/read-decode verification.
- Hot reload.
- Never overwrite a stock source model by default.

## Phase 5 - Construction Detail Mode

Status: PLANNED

Reuse the proven runtime selection/transform foundation for settlement decoration:
- searchable object/detail library;
- free move/rotate/scale within gameplay limits;
- surface/grid snapping;
- duplicate/delete/undo;
- settlement JSON composition and reusable prefabs;
- no per-player cache-model generation for normal decoration layouts.

## Runtime Gate

One short test session:

1. Pull and clean/build Client under Java 8.
2. Enable Dev Mode.
3. Spawn or locate Smelter 29394.
4. Right-click -> `Dev > Edit Model Live`.
5. Confirm Source model ID(s) includes 64036.
6. Confirm the runtime clone appears two tiles east.
7. Change Scale X, Move Y and Yaw; confirm the clone changes immediately in-world.
8. Set preview X/Y to 0/0 and verify it can overlay the selected source object.
9. Confirm the original source object and another Smelter are not mutated.
10. Save Project, Hide Runtime Clone, Load Project and confirm the transforms return.
11. Restart client and confirm no cache/world object was permanently changed.

### Conveyor belt source candidate

Runtime reference found in-world through Dev Inspector:

- Object: `Conveyor belt`
- Definition ID: `46298`
- Referenced model IDs: `49717, 49718`
- Reference tile observed: `3324, 3496, 0`

This is now the preferred asset-source experiment for the Construction automation belt. The immediate test is not to rebuild the full object: open 46298 in Live Model Editor, isolate the belt surface/strip, and determine whether it is a standalone connected component or one of the two source models. If separable, duplicate that belt component and translate copies end-to-end. That could provide the actual RuneScape belt visual while the Construction system owns routing/automation logic independently.

## Resume Here

**Last completed:** Bundle 1.1 runtime clone/whole-model transform proof passed in the live client. Bundle 2.1 connected-part authoring implementation is now in source.

**Current phase:** Phase 2 - Mesh Parts + In-World Selection.

**Active bundle:** Bundle 2.7 - Professional Editor Shell (`ACTIVE / NEEDS TEST`).

**Next action:** runtime-test Bundle 2.7A on Conveyor belt 46298/models 49717+49718 at the compact 16-inch layout: rail collapse/expand, P/T/M/C/O/S drawers, contextual transforms, RTS/FREE switching, focus handoff and no outer horizontal scrollbar. Bundle 2.7B HUD suppression remains gated until the real Matrix3 root-interface owner is verified.

**Files/systems already inspected:**
- `Client/src/main/java/game/ObjectDefinitions.java`
- `Client/src/main/java/game/ObjectLabPreview.java`
- `Client/src/main/java/game/ObjectCompositePreview.java`
- `Client/src/main/java/game/Class578.java`
- `Client/src/main/java/game/DevDefinitionBridge.java`
- `Client/src/main/java/game/DevModeBridge.java`
- `Client/src/main/java/game/console/DevInspectorWindow.java`

**Do not rescan without new evidence:**
- Object definition model-ID decode.
- Existing Class578 direct-render hook.
- Object Lab direct-render scene coordinate math.
- Dev Mode object ID/tile target route.

**Important uncertainty:** exact live scene object type/rotation and true one-instance renderer replacement/suppression are not yet established. Bundle 1 intentionally keeps those explicit and leaves the original object untouched. Bundle 2.3B now uses Matrix3 Model.method1376(...) for component-level screen hit testing, but runtime coordinate/selection accuracy remains NEEDS TEST. Construction replacement currently uses the existing starter Construction catalog as an authoring library; it is not yet the final player-facing material taxonomy.
