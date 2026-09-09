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
| Item Browser/search | COMPLETE | Background definition index, exact-ID lookup, debounced name search, stale search cancellation, Item Browser-style result/detail layout. |
| Item visual preview | CARRYOVER | No renderer is connected until a clean reusable Matrix3/client rendering boundary is proven. Do not duplicate the client renderer into CacheEditor. |
| Structured Item Editor | BLOCKED | Requires a round-trip-safe ItemDefinitions encoder + verify/redecode/compare save pipeline. |
| NPC/Object editors | PLANNED | Build after shared editor/save infrastructure is proven. |
| Model editor/viewer | PLANNED | Likely dependency for richer item/NPC/object previews. |
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

Status: COMPLETE

- [x] Replace crowded top-tab shell with category sidebar navigation.
- [x] Keep inactive future tools visible but disabled/planned.
- [x] Add session-level READ ONLY / EDIT MODE safety gate.
- [x] Add index/archive browsing while preserving manual index/archive/file Go To.
- [x] Bind Export/Replace to the immutable loaded file rather than mutable text fields.
- [x] Back up existing raw bytes before replacement.
- [x] Verify raw writes by immediate byte-for-byte readback.
- [x] Remove FileStore putFile return-type assumption.
- [x] Cancel obsolete item name-search workers so rapid typing does not stack stale scans.

### Bundle B - Item Preview + Structured Editor Foundation

Status: NEXT

- [ ] Trace the narrowest reusable model/item preview rendering boundary.
- [ ] If reusable rendering is clean, add a real Item Viewer preview (inventory first; male/female worn modes later).
- [ ] If no reusable boundary exists, define a dedicated preview adapter without copying the full client renderer.
- [ ] Establish editable copy / dirty-state / Save / Revert / Reload workflow.
- [ ] Prove round-trip-safe ItemDefinitions encoding before enabling Save.
- [ ] Save pipeline: decode -> edit copy -> encode -> verify -> backup -> write -> re-decode -> compare.

## Phase 3 - Model + Effect Tooling

Status: PLANNED

- [ ] Model viewer/editor with reusable preview controls.
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

Start with **Phase 2 / Bundle B**. First prove the preview renderer ownership boundary using the smallest relevant client/server/tool file set. Do not reconnect or duplicate client rendering blindly. If preview remains blocked, continue independently with the ItemDefinitions round-trip encoder investigation and dirty-state editor shell, but do not enable structured saves until byte-for-byte/semantic round-trip validation is proven.
