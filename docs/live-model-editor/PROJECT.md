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
| Private in-world runtime model clone | ⚠️ Needs runtime verification | Bundle 1 direct-renders a second private model clone through Matrix3's existing developer scene-render seam. |
| Live whole-model transforms | ⚠️ Needs runtime verification | Scale X/Y/Z, model translation and yaw are implemented on the private clone. |
| JSON project save/load | ⚠️ Needs runtime verification | Authoring state saves under dev-model-projects and can be loaded back into the live preview. |
| Mesh component selection/editing | ❌ Not started | Connected-component detection, isolate/highlight and part transforms follow the Bundle 1 gate. |
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
- [ ] Runtime acceptance on Smelter 29394 / model 64036.
- [ ] Confirm another Smelter/source object remains visually unchanged while the clone is transformed.
- [ ] Save, hide, load JSON and confirm the same clone state returns.

## Phase 2 - Mesh Parts + In-World Selection

Status: PLANNED

- Connected mesh-component detection.
- Part list and isolate/hide/highlight.
- In-world hover/pick of a component.
- Selected-part visual feedback.
- Per-part move/rotate/scale.
- Delete/hide/duplicate and undo stack.

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

**Last completed:** Bundle 1.1 implementation, static ownership trace, and corrective Class578 scene-render hook.

**Current phase:** Phase 1 - Runtime Clone Foundation.

**Active bundle:** Bundle 1.1 - Smelter runtime clone + JSON (`NEEDS TEST`).

**Next action:** run the consolidated runtime gate above. If it passes, start Phase 2 with connected-component detection and isolate/highlight before attempting in-world part picking.

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

**Important uncertainty:** exact live scene object type/rotation and true one-instance renderer replacement/suppression are not yet established. Bundle 1 intentionally keeps those explicit and leaves the original object untouched.
