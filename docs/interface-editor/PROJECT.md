# Matrix3 Interface Editor

## Goal

Build a professional Client Console Interface Editor that lets Matrix3 development discover, inspect, and safely experiment with live interface/component values without repeatedly hardcoding speculative fixes into game code.

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
- Runtime interface discovery from the actual root/subinterface state rather than guessed IDs.
- `Load Active Interface` for the most relevant newly attached foreground interface.
- `Open now` browsing for currently attached runtime interfaces.
- Searchable all-interface browser across the current cache interface-ID range.
- Searchable component list with type, parent, item, child-count, base geometry, runtime geometry, alignment, text, and sprite visibility.
- Client-thread-owned live overrides for base X/Y/W/H, runtime X/Y/W/H, alignment bytes, text, and sprite ID.
- Professional live geometry tuning with sliders, exact number fields, one-pixel nudges, and Fine/Normal/Wide ranges.
- Reversible Reset Selected / Reset Interface behavior.
- Copy Values for quickly preserving working discoveries.
- Diagnostic Wire Mesh / Pick Component as carryover tooling until the overlay has a renderer-native paint path.
- Client Console rail integration and existing workspace active-panel persistence.

### Out of scope

- Replacing Matrix3 interface decoding/layout ownership.
- Permanent cache editing in V1.
- Arbitrary editing of every obfuscated `InterfaceDefinitions` field before semantics are established.
- Server/gameplay authority changes.
- Treating a cache-range entry as OPEN unless Matrix3 runtime state says it is attached.
- Replacing the current AWT Wire Mesh with renderer-native drawing inside the V1.3 browser bundle.

## Architecture / ownership

- Matrix3 `InterfaceDefinitions` remains the interface-data authority.
- `ClientConsoleInterfaceBridge` owns the safe client-thread handoff, component snapshots, temporary live overrides, reset-to-pre-editor state, and immutable runtime interface-catalog snapshots.
- Matrix3 root interface state is read from `client.anInt8790`; attached subinterfaces are read from `client.aClass676_8760` on the client thread.
- `Class572_Sub29` supplies the attached interface ID while its inherited hash remains the parent component hash, matching the existing `SET_INTERFACE -> Class104_Sub1.method9918(...)` ownership path.
- `InterfaceEditorPanel` owns Swing presentation, target/open-interface controls, slider/nudge UX, and UI-side coalescing of rapid live geometry changes.
- `InterfaceBrowserDialog` owns searchable all-interface browsing. It consumes only immutable catalog metadata and never reads obfuscated client state directly.
- `ClientConsoleInterfaceOverlay` remains a diagnostic overlay/picker helper only; it is not interface/render authority.
- `ClientConsoleShell` owns lazy panel hosting/navigation/persistence as before.
- Overrides are temporary development state; they do not write cache/server data.
- Slider drag events are rate-limited in Swing to one queued override every 50ms before entering the existing client-thread bridge.

## Verified foundation

### VERIFIED

- Live geometry editing is useful for finding interface values without repeated hardcoded patches.
- The current AWT Wire Mesh paint path flashes the game canvas under the user's OpenGL runtime. It is therefore unsafe as an always-on default and now starts OFF.

### verified-static

- `Class512.method6083(...)` is the current Matrix3 component lookup path used by Interface Editor snapshots.
- `IncomingPacket.ROOT_INTERFACE` writes the current root interface to `client.anInt8790`.
- `IncomingPacket.SET_INTERFACE` constructs `Class572_Sub29(interfaceId, clipped)` and routes it through `Class104_Sub1.method9918(parentHash, ...)`.
- `Class104_Sub1.method9918(...)` stores the subinterface node in `client.aClass676_8760` keyed by parent hash.
- `Class676` is iterable and is the live subinterface table used by the existing Matrix3 client path.
- `Class534.aClass83Array5975.length` provides the current interface-cache group range after interface definitions initialize.
- Interface Editor component reads/writes and runtime interface discovery remain client-thread-owned through the existing `Class514.method6093(...)` flush path.
- V1.1 provides eight live geometry sliders, exact numeric entry, one-pixel nudges, and Fine +/-64, Normal +/-256, Wide +/-1024 ranges.
- V1.3 provides immutable open-interface catalog snapshots, active-interface preference, `Open now`, and searchable all-interface browsing without changing Matrix3 gameplay/server authority.

## Unknown / research needed

### HYPOTHESIS

- When several subinterfaces attach during one operation, preferring the newly attached non-root interface with the greatest loaded component count should usually choose the main foreground interface over small helper children.
- Pinning runtime geometry each client cycle remains the most useful direct experimentation mode for interfaces whose normal layout scripts would otherwise overwrite trial values.

### UNKNOWN

- Runtime accuracy of V1.3 active-interface selection across arbitrary interface combinations until the targeted test.
- Whether some cache interface IDs within the archive-group range are intentionally empty/unusable; Browse All deliberately exposes the numeric cache range without claiming every ID is populated.
- Whether some interfaces require editable fields beyond V1 geometry/alignment/text/sprite controls.
- Final renderer-native ownership point for a non-flashing Wire Mesh replacement.

## Dependencies

- Existing Client Console shell/theme/icons.
- Matrix3 `InterfaceDefinitions` / `Class512` component lookup.
- Matrix3 root/subinterface runtime state (`client.anInt8790`, `client.aClass676_8760`, `Class572_Sub29`).
- Existing logged-in client-cycle hook in `Class514.method6093(...)`.

## Development plan

### Phase 1 - Professional live editor foundation

**Status:** NEEDS TEST

**Purpose:** Deliver a usable live editor with low-friction interface discovery and reversible runtime experimentation.

**Exit conditions:**

- Editor opens from Client Console and remains responsive.
- `Load Active Interface` identifies the expected foreground interface in representative cases.
- `Open now` matches Matrix3's currently attached interfaces and updates when interfaces open/close.
- Browse All searches the current cache interface range and loads selected IDs.
- Component discovery/search works for interface 762 and at least one unrelated interface.
- Live geometry sliders/nudges visibly change a selected component without an apply click or action backlog.
- Exact entry and deliberate text/sprite application work.
- Reset Selected and Reset Interface restore pre-editor values.
- Wire Mesh remains off by default under OpenGL until its paint path is replaced.
- No Matrix3 gameplay/server authority regression occurs.

#### Bundle 1.1 - Inspector/editor + live tuning foundation

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Add client-thread component snapshot/override bridge. `NEEDS TEST`
- [x] Add professional searchable Client Console editor panel. `NEEDS TEST`
- [x] Add base/runtime geometry, alignment, text, and sprite controls. `NEEDS TEST`
- [x] Add Reset Selected, Reset Interface, and Copy Values. `NEEDS TEST`
- [x] Add live sliders for base/runtime X/Y/W/H. `NEEDS TEST`
- [x] Add one-pixel nudges, exact geometry entry, and Fine/Normal/Wide slider ranges. `NEEDS TEST`
- [x] Coalesce live drag writes to at most one queued client-thread override every 50ms. `NEEDS TEST`
- [x] Add diagnostic Wire Mesh / Pick Component foundation. `CARRYOVER - overlay paint path flashes under OpenGL`
- [x] Default Wire Mesh OFF after runtime flashing evidence. `NEEDS TEST`

#### Bundle 1.2 - Runtime interface discovery/browser

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Snapshot current root + attached subinterfaces on the client thread. `NEEDS TEST`
- [x] Track a preferred active foreground interface using newly attached/main-interface heuristics. `NEEDS TEST`
- [x] Add `Load Active Interface`. `NEEDS TEST`
- [x] Add live `Open now` dropdown. `NEEDS TEST`
- [x] Add searchable `Browse All Interfaces...` dialog with double-click / Load Selected. `NEEDS TEST`
- [x] Mark OPEN / ACTIVE / ROOT state without claiming closed cache IDs are open. `NEEDS TEST`
- [x] Update targeted docs/tests. `NEEDS TEST`

#### Bundle 1.3 - Consolidated runtime acceptance

**Status:** READY

**Checklist / patches:**

- [ ] Run `docs/interface-editor/testlist.txt`, starting with active/open/all-interface browsing on interface 762.
- [ ] Verify one unrelated interface so discovery remains generic.
- [ ] Verify one live slider -> nudge -> reset cycle with Wire Mesh OFF.
- [ ] Record any active-selection/browser failures as evidence-backed carryover.
- [ ] Keep renderer-native Wire Mesh replacement separate unless it blocks ordinary editor use.

### Phase 2 - Evidence-backed editor expansion

**Status:** PLANNED

**Purpose:** Add only the higher-value capabilities justified by Phase 1 runtime use.

**Possible future bundles:**

- Renderer-native Wire Mesh / picker drawing without AWT/OpenGL contention.
- Parent-chain/clipping visualization.
- Friendly saved interface aliases/names when evidence exists.
- Recent/favorite targets.
- Permanent cache override export only after temporary editing is proven safe and useful.

## Current execution state

- Phase: Phase 1 - Professional live editor foundation
- Phase status: NEEDS TEST
- Bundle: Bundle 1.3 - Consolidated runtime acceptance
- Bundle status: READY
- Approval state: V1.3 active/open/all-interface browser approved by `SAP AAA` on 2026-09-08.
- Current checklist item: Pull current main and verify `Load Active Interface`, `Open now`, and `Browse All Interfaces...` against interface 762.
- Current objective: Prove low-friction interface discovery while keeping Wire Mesh off and preserving existing reversible editing behavior.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- |
| Client-thread bridge | 1 | 1.1 | NEEDS TEST | Swing queues requests; Matrix3 client cycle performs reads/writes/discovery. |
| Searchable editor workspace | 1 | 1.1 | NEEDS TEST | Generic `interface[:component]` targeting remains available. |
| Live geometry sliders/nudges | 1 | 1.1 | NEEDS TEST | Eight sliders, exact fields, 1px nudges, three ranges, 50ms coalescing. |
| Runtime interface catalog | 1 | 1.2 | NEEDS TEST | Root + attached subinterfaces exposed as immutable snapshots. |
| Load Active / Open now | 1 | 1.2 | NEEDS TEST | Foreground heuristic + direct open-interface loading. |
| Browse All Interfaces | 1 | 1.2 | NEEDS TEST | Searchable cache range with OPEN/ACTIVE/ROOT markers. |
| Wire Mesh overlay | 1 | 1.1 | CARRYOVER | AWT paint flashes OpenGL canvas; default OFF pending renderer-native replacement. |
| Reversible live overrides | 1 | 1.1 | NEEDS TEST | Original values captured on first override and restored on reset. |
| Value capture | 1 | 1.1 | NEEDS TEST | Copy Values exports current working component state. |
| V1.3 runtime acceptance | 1 | 1.3 | READY | Next execution target. |

## Decisions / new ideas

### Decision log

- 2026-09-06: Build a generic Interface Editor rather than continue one-off interface-value guesses.
- 2026-09-06: Keep V1 edits temporary/reversible and client-thread-owned; permanent cache writes are deferred.
- 2026-09-06: Expose known/useful fields first rather than presenting every obfuscated integer as understood.
- 2026-09-08: Geometry discovery is direct-manipulation first: sliders + 1px nudges apply live while exact entry remains available.
- 2026-09-08: Rate-limit slider traffic at the editor layer instead of changing Matrix3 interface authority.
- 2026-09-08: Keep text/sprite edits deliberate instead of auto-applying every keystroke.
- 2026-09-08: Wire Mesh remains diagnostic-only. Runtime evidence showed the AWT Canvas paint path conflicts with OpenGL, so it defaults OFF and renderer-native drawing is carryover.
- 2026-09-08: Interface discovery should use Matrix3's live root/subinterface registry rather than infer openness from loaded definitions.
- 2026-09-08: `Load Active Interface` uses a bounded heuristic: newly attached non-root interfaces are preferred, with loaded component count used to favor a main foreground interface over small helpers.
- 2026-09-08: Browse All exposes the cache ID range but only labels an interface OPEN when the runtime catalog says it is attached.

## Testing

See `docs/interface-editor/testlist.txt` for the targeted V1.3 acceptance path.

### Quick/high-value checks

1. Open Bank/Backpack interface 762.
2. Open Interface Editor; confirm Wire Mesh is OFF and no flashing occurs.
3. Click Load Active Interface; expect 762.
4. Confirm Open now contains 762 plus the root/helper interfaces.
5. Browse All -> search 762 -> double-click; expect 762 to load.
6. Close/reopen 762 and confirm Open now updates.
7. Perform one harmless Runtime X drag, +/- nudge, then Reset Selected.

### Smoke/regression checks

- Normal login/render/input.
- Existing Client Console panels still open/collapse/persist normally.
- Interface discovery/browser is client tooling only; no server/gameplay authority change.
- Wire Mesh OFF does not flash the OpenGL canvas.
- Temporary live overrides still disappear after reset/restart.

## Carryover / blockers

### CARRYOVER

- Renderer-native Wire Mesh replacement: current AWT paint path flashes under OpenGL. Do not re-enable by default until the renderer-native owner is established and tested.
- Parent-chain/clipping visualization can build on the future renderer-native overlay if still useful.

### BLOCKED

- None. The browser/editor can proceed with Wire Mesh disabled.

## Resume Here

**Last completed:**

- Interface Editor V1.3 active/open/all-interface discovery implemented statically. Runtime interface state now comes from Matrix3's root/subinterface owners, and Wire Mesh defaults OFF after verified OpenGL flashing.

**Current phase:**

- Phase 1 - Professional live editor foundation (`NEEDS TEST`).

**Active bundle:**

- Bundle 1.3 - Consolidated runtime acceptance (`READY`).

**Next checklist item:**

- Run the short V1.3 discovery test: open 762 -> Load Active -> inspect Open now -> Browse All search 762 -> one live slider/reset check.

**Current state / next action:**

- Pull current main, clean/build Client in Eclipse/Java 8, launch normally, and test discovery first with Wire Mesh left OFF.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/interface-editor/PROJECT.md`
- `ClientConsoleInterfaceBridge.java`
- `InterfaceEditorPanel.java`
- `InterfaceBrowserDialog.java`
- `ClientConsoleInterfaceOverlay.java`
- `PacketsDecoder.java` ROOT_INTERFACE / SET_INTERFACE paths
- `Class104_Sub1.method9918(...)`
- `Class676.java`
- `Class572.java`
- `Class572_Sub29.java`
- `Class534.java`
- `Class83.java`

**Do not re-scan without new evidence:**

- Root/subinterface runtime ownership (`client.anInt8790`, `client.aClass676_8760`).
- Existing component snapshot/live-override ownership.
- Broader renderer internals until the renderer-native Wire Mesh carryover is explicitly resumed.

**Pending runtime verification:**

- Active-interface heuristic accuracy.
- Open-now add/remove behavior.
- Browse-All cache range/search/load behavior.
- Existing slider/nudge/reset behavior after browser integration.
- Wire Mesh remains safely off by default.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Whether the active-interface heuristic needs a stronger foreground/parent-priority rule after real runtime testing.

## Next recommended work

Run Interface Editor V1.3 acceptance. Patch only evidence-backed discovery/selection failures; keep the OpenGL Wire Mesh renderer replacement as a separate future bundle unless the user explicitly resumes it.
