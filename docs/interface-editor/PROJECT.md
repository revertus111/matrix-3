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
- Reversible Reset Selected / Reset Interface behavior.
- Copy Values for quickly preserving working discoveries.
- Client Console rail integration and existing workspace active-panel persistence.

### Out of scope

- Replacing Matrix3 interface decoding/layout ownership.
- Permanent cache editing in V1.
- Arbitrary editing of every obfuscated InterfaceDefinitions field before semantics are established.
- Server/gameplay authority changes.

## Architecture / ownership

- Matrix3 `InterfaceDefinitions` remains the interface-data authority.
- `ClientConsoleInterfaceBridge` owns the safe client-thread handoff, snapshots, temporary live overrides, and reset-to-pre-editor state.
- `InterfaceEditorPanel` owns Swing presentation, slider/nudge UX, and UI-side coalescing of rapid live geometry changes.
- `ClientConsoleShell` owns lazy panel hosting/navigation/persistence as before.
- Overrides are temporary development state; they do not write cache/server data.
- Slider drag events are rate-limited in Swing to one queued override every 50ms before entering the existing client-thread bridge.

## Verified foundation

### VERIFIED

- Backpack runtime tracing established that current interface work needs fast live geometry experimentation rather than more guessed hardcoded component patches.

### verified-static

- `Class512.method6083(...)` is the current Matrix3 client component lookup path.
- Interface geometry uses decoded base fields plus runtime layout fields already observed by the focused Backpack trace.
- Client Console already has lazy panel hosting, shared dark-theme primitives, and a logged-in client-cycle hook at `Class514.method6093(...)`.
- Interface Editor V1 uses the client-cycle bridge for all Matrix3 reads/writes and keeps Swing as presentation/request ownership only.
- V1.1 adds eight live geometry sliders, exact numeric entry, one-pixel nudges, and Fine +/-64, Normal +/-256, Wide +/-1024 ranges without changing bridge/server authority.

## Unknown / research needed

### HYPOTHESIS

- Pinning runtime geometry each client cycle should provide the most useful direct experimentation mode for interfaces whose normal alignment/layout scripts would otherwise overwrite trial values.

### UNKNOWN

- Final runtime behavior of the V1.1 editor across arbitrary interfaces/components until the consolidated test.
- Whether some interfaces require additional editable fields beyond V1 geometry/alignment/text/sprite controls.
- Exact interface 671 values needed for the finished Backpack layout.

## Dependencies

- Existing Client Console shell/theme/icons.
- Matrix3 `InterfaceDefinitions` / `Class512` component lookup.
- Existing logged-in client-cycle hook in `Class514.method6093(...)`.

## Development plan

### Phase 1 - Professional live editor foundation

**Status:** NEEDS TEST

**Purpose:** Deliver a usable V1/V1.1 that solves the immediate interface-debugging problem safely.

**Exit conditions:**

- Editor opens from Client Console and remains responsive.
- Component discovery/search works for interface 671 and at least one unrelated interface.
- Live geometry sliders/nudges visibly change a selected component without an apply click or action backlog.
- Exact entry and deliberate text/sprite application work.
- Reset Selected and Reset Interface restore pre-editor values.
- No Matrix3 gameplay/server authority regression occurs.

#### Bundle 1.1 - V1 inspector/editor + live tuning UX

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
- [x] Add targeted docs/tests. `NEEDS TEST`

#### Bundle 1.2 - Runtime acceptance

**Status:** READY

**Checklist / patches:**

- [ ] Run `docs/interface-editor/testlist.txt`, including continuous slider drag/backlog/reset checks.
- [ ] Record any editor/layout failures as evidence-backed carryover.
- [ ] Use the editor to identify the correct Backpack 671 values and copy them for the final Backpack fix.

## Current execution state

- Phase: Phase 1 - Professional live editor foundation
- Phase status: NEEDS TEST
- Bundle: Bundle 1.2 - Runtime acceptance
- Bundle status: READY
- Approval state: V1.1 live-slider upgrade approved by `SAP AAA` on 2026-09-08.
- Current checklist item: Pull current main and run the quick Interface Editor V1.1 live-slider acceptance path.
- Current objective: Prove fluid live tuning/reset behavior, then use it to solve interface 671 without another guessed hardcoded layout patch.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Client-thread bridge | 1 | 1.1 | NEEDS TEST | Swing queues requests; Matrix3 client cycle performs reads/writes. |
| Searchable editor workspace | 1 | 1.1 | NEEDS TEST | Lazy Client Console panel with `interface[:component]` targeting. |
| Live geometry sliders/nudges | 1 | 1.1 | NEEDS TEST | Eight sliders, exact fields, 1px nudges, three ranges, 50ms UI coalescing. |
| Reversible live overrides | 1 | 1.1 | NEEDS TEST | Original values captured on first override and restored on reset. |
| Value capture | 1 | 1.1 | NEEDS TEST | Copy Values exports the current working component state. |
| V1.1 runtime acceptance | 1 | 1.2 | READY | Next execution target. |

## Decisions / new ideas

### Decision log

- 2026-09-06: Build a generic Interface Editor rather than continue one-off interface 671 guesses.
- 2026-09-06: Keep V1 edits temporary/reversible and client-thread-owned; permanent cache/server writes are explicitly deferred.
- 2026-09-06: Expose known/useful fields first instead of presenting every obfuscated integer as if its semantics were understood.
- 2026-09-06: Remove the temporary automatic Backpack width override because it would conflict with manual editor experiments.
- 2026-09-08: Geometry discovery should be direct-manipulation first. Sliders and 1px nudges apply live by default; exact number entry remains available for final values.
- 2026-09-08: Rate-limit slider traffic at the Swing/editor layer rather than changing Matrix3 interface authority or adding another worker/thread owner.
- 2026-09-08: Keep text/sprite edits deliberate instead of auto-applying every keystroke.

## Testing

### Quick/high-value checks

1. Open Interface Editor from the Client Console rail.
2. Load `671:27`; confirm component list/inspector populate.
3. Drag Runtime X and confirm component 27 moves continuously without pressing Apply.
4. Use +/- and exact Enter/focus-loss values; confirm 1px nudges and exact values apply live.
5. Change Fine/Normal/Wide range and confirm the current value is preserved.
6. Drag quickly and confirm no delayed backlog after release.
7. Reset Selected and confirm the component returns to its pre-editor state with no stale live write afterward.
8. Copy Values and verify the clipboard includes interface/component/base/runtime/alignment/text/sprite values.

### Deeper checks

1. Search/filter components.
2. Disable Live geometry and verify manual `Apply Exact / Visual` behavior.
3. Test text override on a harmless known text component.
4. Test Reset Interface after multiple component overrides.
5. Load one unrelated interface and confirm the editor remains generic.
6. Restart Client and confirm temporary overrides do not persist.

### Smoke/regression checks

- Normal login/render/input.
- Existing Client Console panels still open/collapse/persist normally.
- Commands/Item Browser/Settings authority unchanged.
- Backpack server storage/routing unchanged by this tool.

## Carryover / blockers

### CARRYOVER

- Backpack final interface layout remains a separate Backpack workstream task. Use Interface Editor findings as evidence for the final minimal patch.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Interface Editor V1.1 live geometry tuning UX implemented: sliders, exact values, 1px nudges, three ranges, and 50ms UI-side live-write coalescing.

**Current phase:**

- Phase 1 - Professional live editor foundation (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.2 - Runtime acceptance (`READY`).

**Next checklist item:**

- Run `docs/interface-editor/testlist.txt`, starting with a live Runtime X drag on `671:27`.

**Current state / next action:**

- Pull current main, clean/build the Client in Eclipse/Java 8, open Interface Editor, and verify live slider -> nudge -> reset behavior before deeper 671 tuning.

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
- `InterfaceEditorPanel.java`
- `Class512.java`
- `Class514.java`
- `CustomItemActionConfig.java`
- removed `BackpackInterfaceLayout.java`

**Do not re-scan without new evidence:**

- Client Console shell/lazy-panel ownership.
- Interface 671 component-lookup path already established by Backpack tracing.
- Backpack server storage/routing while testing editor-only behavior.

**Pending runtime verification:**

- V1.1 editor visual quality, live slider smoothness, nudge/exact entry, action-queue behavior, reset safety, copy, generic interface handling, and active-panel persistence.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Which exact interface 671 component values produce the correct full Backpack layout; the editor is the intended discovery path.

## Next recommended work

Run Interface Editor V1.1 acceptance, then use the copied working 671 values to make the final evidence-backed Backpack interface fix.
