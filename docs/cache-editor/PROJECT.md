# RS3 CacheEditor Project

## Purpose

Build a professional standalone Java 8 cache editor for Matrix3's revision-830 cache while keeping Matrix3 as the owner of cache definitions/data rules and reusing the existing Alex FileStore runtime.

## Non-negotiables

- Java 8 + Eclipse remain supported.
- Reuse Matrix3 cache/loaders instead of introducing a second cache engine.
- Default to read-only behavior; writes require an explicit session-level Edit Mode.
- Back up existing bytes before raw replacement and verify writes by immediate readback.
- Structured definition writes stay disabled until the relevant encoder is round-trip safe.
- Each editor gets workflow-specific UX; do not build one giant reflection-driven generic editor.
- The standalone editor may use Client Console visual conventions, but it must not depend on Client-only rendering code without a proven reusable boundary.

## Canonical Main-Goal Status

| Area | Status | Notes |
| --- | --- | --- |
| Standalone launcher/cache session | COMPLETE | Opens the shared Matrix3/Alex FileStore cache session; normal 830 cache can be supplied automatically by the launch task. |
| Dark professional shell | COMPLETE | Sidebar navigation replaces the long-term top-tab layout. |
| Raw Cache inspector/export | COMPLETE | Manual Go To plus index/archive browsing, immutable loaded selection, hex preview, export. |
| Raw Cache write safety | COMPLETE | READ ONLY default, explicit Edit Mode, backup-before-write, immediate byte-for-byte readback verification. |
| Item Browser/search | NEEDS TEST | Missing-loop-brace compile regression is patched; Eclipse/Java 8 clean-build verification is pending before restoring COMPLETE. |
| Item visual preview | CARRYOVER | No renderer is connected until a clean reusable Matrix3/client rendering boundary is proven. Do not duplicate the client renderer into CacheEditor. |
| Structured Item Editor | BLOCKED | Requires a round-trip-safe ItemDefinitions encoder + verify/redecode/compare save pipeline. |
| NPC/Object editors | PLANNED | Object read-only browser/search foundation is implemented but needs runtime verification; structured editing remains planned. |
| Model editor/viewer | NEEDS TEST | Standalone Java2D model viewport, revision-830 geometry decode, raw export, and OBJ/MTL export are implemented; Eclipse/runtime verification is pending. |
| Animation/GFX tools | PLANNED | Follow model/viewer infrastructure. |
| Sprite/Interface tools | PLANNED | Later specialized asset tooling. |

## Phase 1 - Foundation

Status: COMPLETE

- [x] Standalone Java 8 CacheEditor launcher.
- [x] Shared Matrix3 Alex FileStore cache session.
- [x] Raw index/archive/file load and hex preview.
- [x] Raw export and confirmed replacement with backup.
- [x] Read-only ItemDefinitions browser/search.
- [x] Client Console separate-process launch and normal 830 cache auto-path.
- [x] Shared dark theme.

## Phase 2 - Professional Shell + Safe Editing Foundation

Status: ACTIVE

### Bundle A - Shell / Raw Safety / Item Browser Hardening

Status: NEEDS TEST

- [x] Replace crowded top-tab shell with category sidebar navigation.
- [x] Keep inactive future tools visible but disabled/planned.
- [x] Add session-level READ ONLY / EDIT MODE safety gate.
- [x] Add index/archive browsing while preserving manual index/archive/file Go To.
- [x] Bind Export/Replace to the immutable loaded file rather than mutable text fields.
- [x] Back up existing raw bytes before replacement.
- [x] Verify raw writes by immediate byte-for-byte readback.
- [x] Remove FileStore putFile return-type assumption.
- [x] Cancel obsolete item name-search workers so rapid typing does not stack stale scans.
- [x] Repair the missing loop brace introduced in the item-search cancellation patch.
- [ ] Eclipse/Java 8 clean-build confirms ItemBrowserPanel has no syntax/`entry` errors.

### Bundle B - Item Preview + Structured Editor Foundation

Status: NEXT

Priority note: the user explicitly advanced the standalone Model Viewer foundation first because CacheEditor is intended to become a standalone application. That work is tracked in Phase 3 without treating the running client renderer as a dependency.

- [ ] Trace the narrowest reusable model/item preview rendering boundary.
- [ ] If reusable rendering is clean, add a real Item Viewer preview (inventory first; male/female worn modes later).
- [ ] If no reusable boundary exists, define a dedicated preview adapter without copying the full client renderer.
- [ ] Establish editable copy / dirty-state / Save / Revert / Reload workflow.
- [ ] Prove round-trip-safe ItemDefinitions encoding before enabling Save.
- [ ] Save pipeline: decode -> edit copy -> encode -> verify -> backup -> write -> re-decode -> compare.

## Phase 3 - Model + Effect Tooling

Status: ACTIVE (explicit priority override)

- [x] Add a standalone-capable Model Viewer panel under CacheEditor rather than requiring a logged-in/running client renderer.
- [x] Add a dedicated revision-830 model geometry adapter based on the verified-static Matrix3 Class159 byte layout; do not copy the full game renderer.
- [x] Add reusable orbit/pan/zoom/reset and wireframe viewport controls.
- [x] Add raw model export from cache index 7/archive modelId/file 0.
- [x] Add Wavefront OBJ + MTL export with geometry and RuneScape face-colour materials; preserve texture ids as OBJ comments.
- [ ] Eclipse/Java 8 clean-build the Server/CacheEditor after the Model Viewer slice.
- [ ] Runtime-load representative untextured and textured revision-830 models and verify geometry/orientation/face colours.
- [ ] Add texture image/material rendering only after the standalone texture decode boundary is established; current preview intentionally falls back to face colour for textured faces.
- [ ] Model editing operations remain later work; this slice is viewer/export foundation only.
- [ ] Animation playback/viewer.
- [ ] GFX model + animation viewer/editor.

## Phase 4 - Definition Editors

Status: PLANNED

- [ ] NPC editor with model/appearance preview.
- [ ] Object editor with model/type/rotation preview.

## Phase 5 - Remaining Assets

Status: PLANNED

- [ ] Sprite image-grid/editor workflow.
- [ ] Interface component tree + visual preview/editor workflow.

## Resume Here

Run one consolidated **Eclipse/Java 8 CacheEditor gate**: clean-build Matrix3-Server (which also closes the pending ItemBrowserPanel compile gate), launch standalone CacheEditor, open Models, load representative revision-830 model ids, verify orbit/pan/zoom/wireframe, then export Raw and OBJ/MTL and reopen the OBJ in an external viewer. Current standalone preview intentionally renders textured faces with their RuneScape face-colour fallback while preserving texture ids in OBJ comments. If geometry decode fails for a real 830 model, capture the model id/error before expanding the decoder trace. After this gate, return to Phase 2 / Bundle B item-preview/editor foundation unless the user keeps Model Tooling as the active priority.
