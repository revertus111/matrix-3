# Matrix3 Visual Explorer

## Goal

Build one professional Matrix3 developer workspace for finding, browsing, identifying and tracing anything visual the client can display: interfaces, sprites, GFX, models, textures/materials, fonts/text, cursors, interaction icons, overheads and other overlays.

The defining workflow is Live Pick: point at an unknown on-screen visual, capture it, see every registered visual candidate under that pixel in draw order, then jump to the correct asset/editor/trace path instead of guessing whether it is an interface, sprite, GFX or another renderer family.

The first concrete target is the NPC hover/interaction graphic shown beside the Goblin Attack tooltip.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Client Console Visual Explorer workspace | ⚠️ Needs runtime verification |
| Unified asset-family navigation | 🟡 Foundation |
| Live Pick capture and candidate stack | 🟡 Foundation |
| Interface and sprite inspection | 🟡 Foundation |
| Cursor, interaction-overlay and overhead attribution | ❌ Not started |
| GFX, model, material, texture, font and text coverage | ❌ Not started |
| Cross-link, trace and export workflow | ❌ Not started |

## Scope

### In scope

- One lazy Test Console Visual Explorer workspace.
- Search/browse/preview paths for visual asset families.
- Reuse of existing specialist authorities such as Interface Browser/Editor rather than duplicating them.
- Client-only bounded draw-record instrumentation for Live Pick.
- Screen-space candidate stacking so overlapping visuals can be inspected in top-most draw order.
- Source labels and asset IDs sufficient to trace an unknown visual back to the correct Matrix3 path.
- Future preview/open-in-tool/copy-ID/trace-renderer actions.
- The NPC hover/Attack interaction visual as the first priority unknown.

### Out of scope

- Replacing Matrix3 renderer ownership.
- Cache mutation merely to inspect assets.
- Gameplay/combat/menu behavior changes.
- A second interface editor, item browser, model renderer or GFX owner.
- Full-frame screenshots/OCR as an asset-identification substitute.
- Broad renderer instrumentation when a narrow visual family will answer the current question.

## Architecture / ownership

- TestConsolePanel owns only lazy placement of the Visual Explorer workspace.
- VisualExplorerPanel owns developer UI/presentation and delegates existing specialist workflows.
- VisualExplorerCapture owns only opt-in capture state, a bounded current-frame draw-record list, one-click screen picking, and candidate ordering.
- Matrix3 renderer/cache/interface/menu/world systems remain authoritative.
- Render providers publish evidence after the real owner resolves final screen bounds; providers do not change draw semantics.
- Capture overhead must be effectively zero while disabled and bounded while enabled.
- Existing Interface Browser/Editor remains interface authority; Visual Explorer links to it.

## Verified foundation

### VERIFIED

- Test Console consolidation and its existing moved tool tabs are runtime verified in the current Client Console.
- The existing Interface Browser/Editor can browse the current cache/open-interface catalog.

### verified-static

- TestConsolePanel supports lazy specialist sub-tabs with failure isolation.
- ClientConsoleInterfaceBridge refreshes an interface catalog on the normal client cycle and exposes it read-only to Swing.
- Class161 is the shared abstract sprite render object with several draw entry points; exact provider hook selection remains deliberately unresolved until the sprite/cursor trace bundle.
- Class106 creates/returns Class161 sprites through renderer-owned APIs; Visual Explorer must not replace those APIs.

## Unknown / research needed

### HYPOTHESIS

- The Goblin hover/Attack graphic is likely in a cursor/interaction-overlay family and may ultimately resolve through a sprite asset, but that semantic mapping is not yet proven.

### UNKNOWN

- Exact renderer/source method that draws the Goblin hover/Attack graphic.
- Exact asset ID/type for that graphic.
- Smallest safe Class161 draw seam(s) that cover the needed sprite/cursor family across the active renderer without instrumenting unrelated draw traffic.
- Whether world overhead/headbar visuals share the same sprite provider or need a separate higher-level provider.

## Dependencies

- Client Console Test Console shell.
- Existing Interface Browser/Editor.
- Matrix3 client canvas and client-cycle counter.
- Narrow future traces for sprite/cursor/overlay/GFX/model providers.

## Development plan

### Phase 1 - Explorer foundation and first unknown visual

**Status:** ACTIVE

**Purpose:** Establish the workspace/capture architecture and use it to identify the NPC hover/Attack visual.

**Exit conditions:**

- Visual Explorer tab is runtime accepted.
- Live Pick input/candidate behavior is runtime accepted.
- At least interfaces and the relevant hover/interaction visual family have real providers.
- The Goblin hover/Attack graphic resolves to a concrete asset/type/source path or a tightly classified remaining unknown.

#### Bundle 1.1 - Workspace + capture contract

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Create authoritative Visual Explorer workstream and registry entry.
- [x] Add lazy Visual Explorer Test Console tab.
- [x] Add bounded client-only VisualExplorerCapture frame-record contract.
- [x] Add one-click canvas pick with Escape cancel and top-most candidate ordering.
- [x] Keep capture no-op while disabled and cap one frame at 1,024 draw records.
- [x] Reuse current Interface Browser and cross-link to Interface Editor.
- [x] Add UI status for asset-family coverage and explicitly mark cursor/interaction icons as next.
- [ ] Runtime verify tab layout, Interface Browser handoff, capture enable/disable, one-click suppression and Escape cancel.

#### Bundle 1.2 - Cursor / interaction visual attribution

**Status:** READY

**Purpose:** Identify the Goblin hover/Attack visual with the smallest real render-provider trace.

**Checklist / patches:**

- [ ] Trace from the existing menu/interaction entry path into the hover/cursor visual owner.
- [ ] Identify the smallest draw seam and asset identity source.
- [ ] Register only the required cursor/interaction visual draws with VisualExplorerCapture.recordDraw(...).
- [ ] Use Live Pick on the Goblin hover graphic and record the concrete type/ID/source.
- [ ] Add preview/open/copy-ID action appropriate to the resolved asset family.

#### Bundle 1.3 - Sprite browser + generic sprite provider

**Status:** READY

**Checklist / patches:**

- [ ] Add direct sprite ID browse/search/preview through existing cache/renderer APIs.
- [ ] Map loaded sprite identity to a stable inspectable asset ID where source evidence permits.
- [ ] Add bounded generic sprite draw records only at proven shared seams.
- [ ] Add copy ID / open preview / trace source actions.

### Phase 2 - Broader visual families

**Status:** PLANNED

- GFX + models.
- Materials + textures.
- Fonts + text.
- NPC/player overhead/headbar/overlay attribution.

### Phase 3 - Professional cross-linking

**Status:** PLANNED

- Highlight bounds.
- Freeze/cycle overlapping candidates.
- Copy stable IDs/source details.
- Open in owning editor/browser.
- Trace renderer/source context.
- Export/copy compact evidence.

## Current execution state

- Phase: Phase 1 - Explorer foundation and first unknown visual
- Phase status: ACTIVE
- Bundle: Bundle 1.1 - Workspace + capture contract
- Bundle status: NEEDS TEST
- Approval state: AAA approved
- Current checklist item: Runtime verify Bundle 1.1 when convenient.
- Current objective: Establish the reusable capture shell, then trace the Goblin hover/Attack visual in Bundle 1.2.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Workstream + Test Console workspace | 1 | 1.1 | NEEDS TEST | Static implementation complete. |
| Bounded Live Pick capture contract | 1 | 1.1 | NEEDS TEST | No render provider is hooked yet. |
| Existing Interface Browser handoff | 1 | 1.1 | NEEDS TEST | Reuses current authority. |
| Cursor/interaction visual provider | 1 | 1.2 | READY | First priority is Goblin hover/Attack graphic. |
| Generic sprite browser/provider | 1 | 1.3 | READY | Follow proven cursor trace; avoid broad guessing. |

## Decisions / new ideas

### Decision log

- Visual Explorer replaces the idea of a standalone Interface Viewer; interfaces are one asset family inside the broader tool.
- Live Pick returns multiple overlapping candidates in draw order rather than assuming one pixel belongs to one system.
- Existing specialist tools remain authoritative and are cross-linked instead of duplicated.
- Cursor/interaction icons are prioritized before broad sprite instrumentation because they answer the immediate Construction worker-hover design question.
- Capture is opt-in and bounded; disabled state adds no draw-record work.

## Testing

### Quick/high-value checks

1. Test Console -> Visual Explorer loads without affecting existing tabs.
2. Browse All Interfaces opens the existing browser.
3. Live Pick arms, captures one canvas point, suppresses that single game click, then disarms.
4. Escape cancels an armed pick.
5. Disable Capture restores ordinary input.

### Smoke/regression checks

- Existing Test Console Con Revamp / Interfaces / Atlas / Boss Research tabs still lazy-load.
- No server, cache mutation, combat or world state changes are introduced by Bundle 1.1.

## Carryover / blockers

### CARRYOVER

- Task: Actual hover/Attack asset identification.
- Phase/bundle: Phase 1 / Bundle 1.2.
- Current state: Capture shell exists; exact renderer/source seam is still UNKNOWN.
- Remaining work: Narrow trace from menu/interaction ownership into cursor/hover rendering and publish real draw records.
- Likely files/systems: Class572_Sub12_Sub10, current menu selection/interaction visual path, relevant Class161 sprite draw owner.
- Next action: Trace only the active hover/cursor path before touching generic sprite rendering.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Implemented Bundle 1.1 Visual Explorer workspace, bounded capture contract, one-click picker and Interface Browser handoff.

**Current phase:**

- Phase 1 - Explorer foundation and first unknown visual.

**Active bundle:**

- Bundle 1.1 - Workspace + capture contract (NEEDS TEST).

**Next checklist item:**

- Runtime-check Bundle 1.1 when convenient, then enter Bundle 1.2 cursor/interaction visual attribution.

**Current state / next action:**

- The tool can capture a canvas point and resolve any registered draw records, but no renderer provider is hooked yet. Next source work traces the Goblin hover/Attack visual specifically and feeds that path into the existing capture contract.

**Files/systems already inspected:**

- AGENTS.md
- docs/rs3/PROJECT.md
- docs/rs3/WORKSTREAMS.md
- docs/rs3/WORKSTREAM_TEMPLATE.md
- docs/client-console/PROJECT.md
- ClientConsoleShell.java
- TestConsolePanel.java
- InterfaceBrowserDialog.java
- InterfaceEditorPanel.java
- ClientConsoleInterfaceBridge.java
- Class106.java
- Class161.java
- Class572_Sub12_Sub10.java
- InterfaceDefinitions.java

**Do not re-scan without new evidence:**

- Client Console top-level shell ownership.
- Existing Interface Browser/Editor ownership.
- Generic Class106 renderer surface.
- Full Class161 method family; Bundle 1.2 traces the specific hover/cursor owner first.

**Pending runtime verification:**

- Visual Explorer tab loads/layouts correctly.
- Interface Browser handoff.
- One-click pick suppression/capture.
- Escape cancel.
- Disable/normal-input restoration.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Exact asset family/ID/render owner for the Goblin hover/Attack visual remains UNKNOWN until Bundle 1.2.

## Next recommended work

Trace and instrument the Goblin hover/Attack interaction visual as the first real Live Pick provider.
