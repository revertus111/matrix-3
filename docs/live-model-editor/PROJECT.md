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
| Private in-world runtime model clone | ✅ Complete | Runtime-confirmed on the Smelter reference asset: the private clone renders in the live scene without replacing the source object. |
| Live whole-model transforms | ✅ Complete | Runtime-confirmed in the live client; independent scale, translation and yaw visibly update the private clone. |
| JSON project save/load | ⚠️ Needs runtime verification | Authoring state saves under dev-model-projects and can be loaded back into the live preview. |
| Mesh component selection/editing | ⚠️ Needs runtime verification | Bundle 2.1 adds connected-component editing; Bundle 2.2 moves the editor into an owned in-client overlay and adds list-hover live preview. True 3D world hover/click picking remains Bundle 2.3 carryover. |
| In-client model-editor overlay | ⚠️ Needs runtime verification | RuneScape-styled owned JWindow follows the Matrix3 game canvas, supports drag repositioning and hover-preview rows. Bundle 2.3A adds true edit-session ownership: input lock, same-target resume and source-object visual replacement. |
| Permanent revision-830 model compiler | ❌ Not started | Requires validated encoder, model-ID allocation, backup, write/readback and hot reload. |
| Construction Detail Mode reuse | ❌ Not started | Future player-facing placement/composition uses the shared transform/selection foundation and settlement persistence. |

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

#### Bundle 2.3B - True in-world component picking

Status: CARRYOVER inside the approved Bundle 2.3 workstream

- [ ] Trace the narrowest Matrix3 screen-to-model/triangle picking seam.
- [ ] Hover the actual rendered component in the live 3D scene and highlight it without using the list.
- [ ] Left-click the hovered world component to select it.
- [ ] Keep the overlay list synchronized with the world-selected component.
- [ ] Route G/R/S and future gizmos through the editor-owned viewport without restoring normal Walk Here.

2.3A is a dependency gate for 2.3B: world picking must operate against the single visible editable replacement, not an original+clone overlap.

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

## Resume Here

**Last completed:** Bundle 1.1 runtime clone/whole-model transform proof passed in the live client. Bundle 2.1 connected-part authoring implementation is now in source.

**Current phase:** Phase 2 - Mesh Parts + In-World Selection.

**Active bundle:** Bundle 2.3A - Editor owns the target and viewport (`NEEDS TEST`).

**Next action:** one consolidated 2.3A runtime gate on Smelter 29394/model 64036: confirm only one Smelter is visible at the source tile, left-click cannot walk/interact, camera remains usable, reopen resumes the same edit state, and Exit Edit/Escape restores the original. If this gate passes, continue 2.3B under the existing AAA with true world component hover/click picking.

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

**Important uncertainty:** exact live scene object type/rotation and true one-instance renderer replacement/suppression are not yet established. Bundle 1 intentionally keeps those explicit and leaves the original object untouched. Bundle 2.2 adds list-hover preview only; it does not claim true screen-to-component 3D picking. The world-picking seam remains explicitly deferred to Bundle 2.3.
