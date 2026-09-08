# Matrix3 Interface Editor

## Goal

Build a professional Client Console Interface Editor that lets Matrix3 development inspect and safely experiment with live interface/component values without repeatedly hardcoding speculative fixes into game code.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Client Console editor workspace | 🟡 Foundation |
| Live component inspection | 🟡 Foundation |
| Reversible live property editing | 🟡 Foundation |
| Value capture/export | 🟡 Foundation |
| Runtime acceptance & safeguards | ⚠️ Needs runtime verification |

## Scope

### In scope

- Generic Matrix3 interface/component inspection by `interface` or `interface:component` target.
- Searchable component list with type, parent, item, child-count, base geometry, runtime geometry, alignment, text, and sprite visibility.
- Client-thread-owned live overrides for base X/Y/W/H, runtime X/Y/W/H, alignment bytes, text, and sprite ID.
- Professional live geometry tuning with sliders, exact number fields, one-pixel nudges, and selectable Fine/Normal/Wide ranges.
- Live Wire Mesh visualization over the game Canvas with selected-component emphasis, optional IDs/dimensions, and optional parent links.
- One-shot Pick Component mode that selects a visible interface component from the game Canvas while consuming only the armed diagnostic click.
- Reversible Reset Selected / Reset Interface behavior.
- Copy Values for quickly preserving working discoveries.
- Client Console rail integration and existing workspace active-panel persistence.

### Out of scope

- Replacing Matrix3 interface decoding/layout ownership.
- Permanent cache editing in V1.
- Arbitrary editing of every obfuscated InterfaceDefinitions field before semantics are established.
- Server/gameplay authority changes.
- Turning the Wire Mesh into an alternate renderer or scene-picking implementation.

## Architecture / ownership

- Matrix3 `InterfaceDefinitions` remains the interface-data authority.
- `ClientConsoleInterfaceBridge` owns the safe client-thread handoff, snapshots, temporary live overrides, and reset-to-pre-editor state.
- `InterfaceEditorPanel` owns Swing presentation, slider/nudge UX, overlay controls, picker state presentation, and UI-side coalescing of rapid live geometry changes.
- `ClientConsoleInterfaceOverlay` owns read-only diagnostic drawing over `Class584.aCanvas7745` and one-shot picker hit testing from immutable Interface Editor snapshots. It does not read/write `InterfaceDefinitions` directly.
- `ClientConsoleShell` owns lazy panel hosting/navigation/persistence as before.
- Overrides are temporary development state; they do not write cache/server data.
- Slider drag events are rate-limited in Swing to one queued override every 50ms before entering the existing client-thread bridge.
- Wire Mesh drawing stops when Interface Editor is hidden; picker mode consumes only its armed left-click and is cancelled when the editor/overlay is hidden.

## Verified foundation

### VERIFIED

- Backpack runtime tracing established that current interface work needs fast live geometry experimentation rather than more guessed hardcoded component patches.

### verified-static

- `Class512.method6083(...)` is the current Matrix3 client component lookup path.
- Interface geometry uses decoded base fields plus runtime layout fields already observed by the focused Backpack trace.
- Client Console already has lazy panel hosting, shared dark-theme primitives, and a logged-in client-cycle hook at `Class514.method6093(...)`.
- Interface Editor V1 uses the client-cycle bridge for all Matrix3 reads/writes and keeps Swing as presentation/request ownership only.
- V1.1 adds eight live geometry sliders, exact numeric entry, one-pixel nudges, and Fine +/-64, Normal +/-256, Wide +/-1024 ranges without changing bridge/server authority.
- V1.2 Wire Mesh reads only immutable `InterfaceSnapshot`/`ComponentSnapshot` data and paints temporary diagnostics through the existing game Canvas; no new server or cache ownership is introduced.
- V1.2 picker hit testing prefers deepest/smallest overlapping component bounds and consumes input only while the one-shot picker is armed.

## Unknown / research needed

### HYPOTHESIS

- Pinning runtime geometry each client cycle should provide the most useful direct experimentation mode for interfaces whose normal alignment/layout scripts would otherwise overwrite trial values.
- Accumulating parent-relative runtime X/Y values should place Wire Mesh rectangles close enough to the rendered interface for interface 671 diagnostics.

### UNKNOWN

- Final runtime behavior of the V1.2 editor across arbitrary interfaces/components until the consolidated test.
- Whether the AWT Canvas diagnostic paint remains visually stable with the current OpenGL renderer under continuous redraw.
- Whether scrolled/clipped interfaces need parent scroll offsets added to the Wire Mesh transform after V1.2 runtime evidence.
- Whether some interfaces require additional editable fields beyond V1 geometry/alignment/text/sprite controls.
- Exact interface 671 values needed for the finished Backpack layout.

## Dependencies

- Existing Client Console shell/theme/icons.
- Matrix3 `InterfaceDefinitions` / `Class512` component lookup.
- Existing logged-in client-cycle hook in `Class514.method6093(...)`.
- Existing Matrix3 game Canvas at `Class584.aCanvas7745` for temporary diagnostic drawing/input observation.

## Development plan

### Phase 1 - Professional live editor foundation

**Status:** NEEDS TEST

**Purpose:** Deliver a usable V1/V1.1/V1.2 that solves the immediate interface-debugging problem safely.

**Exit conditions:**

- Editor opens from Client Console and remains responsive.
- Component discovery/search works for interface 671 and at least one unrelated interface.
- Live geometry sliders/nudges visibly change a selected component without an apply click or action backlog.
- Wire Mesh visibly tracks useful component bounds and selected-component movement.
- Pick Component selects a visible UI component without stealing normal game input outside picker mode.
- Exact entry and deliberate text/sprite application work.
- Reset Selected and Reset Interface restore pre-editor values.
- No Matrix3 gameplay/server authority regression occurs.

#### Bundle 1.1 - V1 inspector/editor + live tuning/visualization UX

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add client-thread interface snapshot/override bridge. `NEEDS TEST`
- [x] Add professional searchable Client Console editor panel. `NEEDS TEST`
- [x] Add base/runtime geometry, alignment, text, and sprite controls. `NEEDS TEST`
- [x] Add Apply Live, Reset Selected, Reset Interface, and Copy Values foundation. `NEEDS TEST`
- [x] Add lazy rail navigation/persistence path. `NEEDS TEST`
- [x] Remove the temporary hardcoded Backpack 671 width repair so it cannot fight live editor values. `NEEDS TEST`
- [x] Add live sliders for base/runtime X/Y/W/H. `NEEDS TEST`
- [x] Add one-pixel nudges, exact geometry entry, and Fine/Normal/Wide slider ranges. `NEEDS TEST`
- [x] Coalesce live drag writes to at most one queued client-thread override every 50ms. `NEEDS TEST`
- [x] Keep text/sprite/alignment exact application and reset/copy workflows intact. `NEEDS TEST`
- [x] Add Wire Mesh overlay with all/selected-only, IDs, dimensions, and parent-link controls. `NEEDS TEST`
- [x] Add one-shot game-Canvas Pick Component and editor-selection synchronization. `NEEDS TEST`
- [x] Disable overlay/picker outside Interface Editor so normal client input/render ownership remains isolated. `NEEDS TEST`
- [x] Add targeted docs/tests. `NEEDS TEST`

#### Bundle 1.2 - Runtime acceptance

**Status:** READY

**Checklist / patches:**

- [ ] Run `docs/interface-editor/testlist.txt`, including continuous slider drag/backlog/reset, Wire Mesh visibility/alignment, and picker/input-isolation checks.
- [ ] Record any editor/layout/overlay failures as evidence-backed carryover.
- [ ] Use the editor + Wire Mesh to identify the correct Backpack 671 values and copy them for the final Backpack fix.

## Current execution state

- Phase: Phase 1 - Professional live editor foundation
- Phase status: NEEDS TEST
- Bundle: Bundle 1.2 - Runtime acceptance
- Bundle status: READY
- Approval state: V1.2 Wire Mesh + Pick Component approved by `SAP AAA` on 2026-09-08.
- Current checklist item: Pull current main and run the quick Interface Editor V1.2 Wire Mesh/picker acceptance path.
- Current objective: Prove overlay alignment, picker isolation, and fluid live tuning, then use them together to solve interface 671 without another guessed hardcoded layout patch.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Client-thread bridge | 1 | 1.1 | NEEDS TEST | Swing queues requests; Matrix3 client cycle performs reads/writes. |
| Searchable editor workspace | 1 | 1.1 | NEEDS TEST | Lazy Client Console panel with `interface[:component]` targeting. |
| Live geometry sliders/nudges | 1 | 1.1 | NEEDS TEST | Eight sliders, exact fields, 1px nudges, three ranges, 50ms UI coalescing. |
| Wire Mesh overlay | 1 | 1.1 | NEEDS TEST | Snapshot-driven Canvas diagnostics with selected/all, IDs, dimensions, parent links. |
| Pick Component | 1 | 1.1 | NEEDS TEST | One-shot Canvas hit test; only armed diagnostic click is consumed. |
| Reversible live overrides | 1 | 1.1 | NEEDS TEST | Original values captured on first override and restored on reset. |
| Value capture | 1 | 1.1 | NEEDS TEST | Copy Values exports the current working component state. |
| V1.2 runtime acceptance | 1 | 1.2 | READY | Next execution target. |

## Decisions / new ideas

### Decision log

- 2026-09-06: Build a generic Interface Editor rather than continue one-off interface 671 guesses.
- 2026-09-06: Keep V1 edits temporary/reversible and client-thread-owned; permanent cache/server writes are explicitly deferred.
- 2026-09-06: Expose known/useful fields first instead of presenting every obfuscated integer as if its semantics were understood.
- 2026-09-06: Remove the temporary automatic Backpack width override because it would conflict with manual editor experiments.
- 2026-09-08: Geometry discovery should be direct-manipulation first. Sliders and 1px nudges apply live by default; exact number entry remains available for final values.
- 2026-09-08: Rate-limit slider traffic at the Swing/editor layer rather than changing Matrix3 interface authority or adding another worker/thread owner.
- 2026-09-08: Keep text/sprite edits deliberate instead of auto-applying every keystroke.
- 2026-09-08: Wire Mesh is a read-only diagnostic consumer of Interface Editor snapshots, not a second interface renderer/data owner.
- 2026-09-08: Pick Component is deliberately one-shot and consumes only its armed left-click so ordinary Matrix3 interaction remains untouched outside diagnostic selection.
- 2026-09-08: Parent-link visualization is optional and off by default to keep the normal overlay readable; enable it when diagnosing hierarchy/overlap.

## Testing

### Quick/high-value checks

1. Open Interface Editor from the Client Console rail.
2. Load `671:27`; confirm component list/inspector populate and Wire Mesh appears over the open Backpack interface.
3. Arm Pick Component and click the visible item-grid/component area; confirm the editor selects the picked component without also firing the normal game action.
4. With picker off, click/use the game normally and confirm input is untouched.
5. Drag Runtime X and confirm both component 27 and its selected wireframe move continuously without pressing Apply.
6. Toggle selected-only, IDs, dimensions, and parent links; confirm these are diagnostic-only changes.
7. Use +/- and exact Enter/focus-loss values; confirm 1px nudges and exact values apply live.
8. Change Fine/Normal/Wide range and confirm the current value is preserved.
9. Drag quickly and confirm no delayed backlog after release.
10. Reset Selected and confirm the component + wireframe return to pre-editor state with no stale live write afterward.
11. Switch away from Interface Editor and confirm Wire Mesh/picker disappear immediately.
12. Copy Values and verify the clipboard includes interface/component/base/runtime/alignment/text/sprite values.

### Deeper checks

1. Use Pick Component across several overlapping 671 regions and confirm child/deeper widgets win over large parent containers where expected.
2. Search/filter components, then pick a hidden-by-filter component and confirm the editor makes the picked row selectable.
3. Disable Live geometry and verify manual `Apply Exact / Visual` behavior.
4. Test text override on a harmless known text component.
5. Test Reset Interface after multiple component overrides.
6. Load one unrelated interface and confirm sliders/Wire Mesh/picker remain generic.
7. Restart Client and confirm temporary overrides and diagnostic overlay state do not persist as interface/cache changes.

### Smoke/regression checks

- Normal login/render/input.
- Existing Client Console panels still open/collapse/persist normally.
- Commands/Item Browser/Settings authority unchanged.
- Backpack server storage/routing unchanged by this tool.
- With picker off or Interface Editor hidden, normal Matrix3 mouse behavior is unchanged.

## Carryover / blockers

### CARRYOVER

- Backpack final interface layout remains a separate Backpack workstream task. Use Interface Editor findings as evidence for the final minimal patch.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Interface Editor V1.2 Wire Mesh + Pick Component implemented statically on top of the V1.1 live-slider editor. Overlay controls, selected-component synchronization, optional parent links, and one-shot game-Canvas picking are present.

**Current phase:**

- Phase 1 - Professional live editor foundation (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.2 - Runtime acceptance (`READY`).

**Next checklist item:**

- Run `docs/interface-editor/testlist.txt`, starting with Wire Mesh visibility on open `671`, then one Pick Component click and one live Runtime X drag on `671:27`.

**Current state / next action:**

- Pull current main, clean/build the Client in Eclipse/Java 8, open Interface Editor + Backpack, and verify overlay -> pick -> live slider -> reset behavior in one short session.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/client-console/PROJECT.md`
- `docs/backpack/PROJECT.md`
- `ClientConsoleShell.java`
- `ConsoleTheme.java`
- `ConsoleIcons.java`
- `PlayerPanel.java`
- `ClientConsoleBridge.java`
- `ClientConsoleInterfaceBridge.java`
- `ClientConsoleInterfaceOverlay.java`
- `InterfaceEditorPanel.java`
- `Class512.java`
- `Class514.java`
- `Class584.java`
- `Canvas_Sub1.java`
- `CustomItemActionConfig.java`
- removed `BackpackInterfaceLayout.java`

**Do not re-scan without new evidence:**

- Client Console shell/lazy-panel ownership.
- Interface 671 component-lookup path already established by Backpack tracing.
- Backpack server storage/routing while testing editor-only behavior.
- Broader renderer internals unless runtime evidence shows the AWT Canvas diagnostic overlay cannot remain visible with the current renderer.

**Pending runtime verification:**

- V1.2 editor visual quality, live slider smoothness, nudge/exact entry, action-queue behavior, reset safety, Wire Mesh OpenGL visibility/alignment, parent-link usefulness, picker hit selection/input isolation, copy, generic interface handling, and active-panel persistence.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Whether the Canvas overlay remains stable/accurately aligned under the current OpenGL renderer and nested/scrolled interface layouts.
- Which exact interface 671 component values produce the correct full Backpack layout; the editor is the intended discovery path.

## Next recommended work

Run Interface Editor V1.2 acceptance, then use Wire Mesh + picker + copied working 671 values to make the final evidence-backed Backpack interface fix.
