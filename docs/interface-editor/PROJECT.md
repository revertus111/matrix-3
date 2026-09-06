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
- `InterfaceEditorPanel` owns Swing presentation only.
- `ClientConsoleShell` owns lazy panel hosting/navigation/persistence as before.
- Overrides are temporary development state; they do not write cache/server data.

## Verified foundation

### VERIFIED

- Backpack runtime tracing established that current interface work needs fast live geometry experimentation rather than more guessed hardcoded component patches.

### verified-static

- `Class512.method6083(...)` is the current Matrix3 client component lookup path.
- Interface geometry uses decoded base fields plus runtime layout fields already observed by the existing focused Backpack trace.
- Client Console already has lazy panel hosting, shared dark-theme primitives, and a logged-in client-cycle hook at `Class514.method6093(...)`.

## Unknown / research needed

### HYPOTHESIS

- Pinning runtime geometry each client cycle should provide the most useful direct experimentation mode for interfaces whose normal alignment/layout scripts would otherwise overwrite trial values.

### UNKNOWN

- Final runtime behavior of the V1 editor across arbitrary interfaces/components until the first consolidated test.
- Whether some interfaces require additional editable fields beyond V1 geometry/alignment/text/sprite controls.

## Dependencies

- Existing Client Console shell/theme/icons.
- Matrix3 `InterfaceDefinitions` / `Class512` component lookup.
- Existing logged-in client-cycle hook in `Class514.method6093(...)`.

## Development plan

### Phase 1 - Professional live editor foundation

**Status:** NEEDS TEST

**Purpose:** Deliver a usable V1 that solves the immediate interface-debugging problem safely.

**Exit conditions:**

- Editor opens from Client Console and remains responsive.
- Component discovery/search works for interface 671 and at least one unrelated interface.
- Apply Live visibly changes a selected component.
- Reset Selected and Reset Interface restore pre-editor values.
- No Matrix3 gameplay/server authority regression occurs.

#### Bundle 1.1 - V1 inspector/editor

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add client-thread interface snapshot/override bridge. `NEEDS TEST`
- [x] Add professional searchable Client Console editor panel. `NEEDS TEST`
- [x] Add base/runtime geometry, alignment, text, and sprite controls. `NEEDS TEST`
- [x] Add Apply Live, Reset Selected, Reset Interface, and Copy Values. `NEEDS TEST`
- [x] Add lazy rail navigation/persistence path. `NEEDS TEST`
- [x] Remove the temporary hardcoded Backpack 671 width repair so it cannot fight live editor values. `NEEDS TEST`
- [x] Add targeted docs/tests. `NEEDS TEST`

#### Bundle 1.2 - Runtime acceptance

**Status:** READY

**Checklist / patches:**

- [ ] Run `docs/interface-editor/testlist.txt`.
- [ ] Record any editor/layout failures as evidence-backed carryover.
- [ ] Use the editor to identify the correct Backpack 671 values and copy them for the final Backpack fix.

## Current execution state

- Phase: Phase 1 - Professional live editor foundation
- Phase status: NEEDS TEST
- Bundle: Bundle 1.2 - Runtime acceptance
- Bundle status: READY
- Approval state: V1 scan + patch approved by `SAP AAA` on 2026-09-06.
- Current checklist item: Pull current main and run the quick Interface Editor V1 acceptance path.
- Current objective: Prove the editor, then use it to solve interface 671 without another guessed hardcoded layout patch.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Client-thread bridge | 1 | 1.1 | NEEDS TEST | Swing queues requests; Matrix3 client cycle performs reads/writes. |
| Searchable editor workspace | 1 | 1.1 | NEEDS TEST | Lazy Client Console panel with `interface[:component]` targeting. |
| Reversible live overrides | 1 | 1.1 | NEEDS TEST | Original values captured on first override and restored on reset. |
| Value capture | 1 | 1.1 | NEEDS TEST | Copy Values exports the current working component state. |
| V1 runtime acceptance | 1 | 1.2 | READY | Next execution target. |

## Decisions / new ideas

### Decision log

- 2026-09-06: Build a generic Interface Editor rather than continue one-off interface 671 guesses.
- 2026-09-06: Keep V1 edits temporary/reversible and client-thread-owned; permanent cache/server writes are explicitly deferred.
- 2026-09-06: Expose known/useful fields first instead of presenting every obfuscated integer as if its semantics were understood.
- 2026-09-06: Remove the temporary automatic Backpack width override because it would conflict with manual editor experiments.

## Testing

### Quick/high-value checks

1. Open Interface Editor from the Client Console rail.
2. Load `671:27`; confirm component list/inspector populate.
3. Change one harmless runtime geometry value and Apply Live; confirm visible movement/size change.
4. Reset Selected; confirm the component returns to its pre-editor state.
5. Copy Values and verify the clipboard includes interface/component/base/runtime/alignment/text/sprite values.

### Deeper checks

1. Search/filter components.
2. Test text override on a harmless known text component.
3. Test Reset Interface after multiple component overrides.
4. Load one unrelated interface and confirm the editor remains generic.
5. Restart Client and confirm temporary overrides do not persist.

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

- Interface Editor V1 implementation and workstream documentation added statically.

**Current phase:**

- Phase 1 - Professional live editor foundation (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.2 - Runtime acceptance (`READY`).

**Next checklist item:**

- Run `docs/interface-editor/testlist.txt`, starting with `671:27`.

**Current state / next action:**

- Pull current main, clean/build the Client in Eclipse/Java 8, open Interface Editor, and verify one apply/reset cycle.

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
- `Class512.java`
- `Class514.java`
- `CustomItemActionConfig.java`
- `BackpackInterfaceLayout.java`

**Do not re-scan without new evidence:**

- Client Console shell/lazy-panel ownership.
- Interface 671 component-lookup path already established by Backpack tracing.
- Backpack server storage/routing while testing editor-only behavior.

**Pending runtime verification:**

- V1 editor visual quality, component discovery, live apply, reset, copy, generic interface handling, and active-panel persistence.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Which exact interface 671 component values produce the correct full Backpack layout; the editor is now the intended discovery path.

## Next recommended work

Run Interface Editor V1 acceptance, then use the copied working 671 values to make the final evidence-backed Backpack interface fix.
