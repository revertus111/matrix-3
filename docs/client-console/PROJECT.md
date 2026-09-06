# Matrix3 Client Console

## Goal

Finish the existing Matrix3 Client Console as a professional, fast, coherent developer sidebar that accelerates Matrix3 content work without becoming an alternate owner of gameplay, commands, persistence, cache decoding, or renderer behavior.

`docs/client-console/CLIENT_CONSOLE.md` remains the detailed design/UX authority. This file owns workstream phases, bundles, checklist state, main-goal status, deferred verification, and resume state.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Shell & workspace foundation | ✅ Complete |
| Core developer workflows | ✅ Complete |
| Professional V2 presentation | 🔵 In Progress |
| Tool ownership & organization | 🔵 In Progress |
| Consolidated V2 runtime acceptance | ❌ Not started |

## Scope

### In scope

- Client Console shell, rail, dashboard, Owner, Commands, Player, Item Browser, Settings, and small specialist-tool launch/navigation surfaces.
- Shared Client Console visual primitives and responsive Swing presentation.
- Client-side bridges that expose existing Matrix3 state/actions without taking ownership from Matrix3 systems.
- Consolidated developer runtime verification optimized for limited PC time.

### Out of scope

- Reimplementing command semantics, permissions, combat, player persistence, cache decoding, world lifecycle, or renderer ownership.
- A general plugin/workspace framework.
- Unrelated gameplay/content work.
- Building speculative tools that are not required by an active content workflow.

## Architecture / ownership

- `ClientConsoleShell` owns rail navigation, panel hosting, open/collapse behavior, width/layout integration, lazy panel creation, and failure isolation.
- `ConsolePreferences` owns Client Console workspace geometry/open/active-panel persistence.
- `ConsoleTheme` owns shared Client Console presentation primitives.
- Individual panels own their controls/display formatting and call existing Matrix3/client-console bridges.
- `ClientConsoleBridge` and specialist bridges hand off to existing authoritative Matrix3 systems; they do not replace those systems.
- `Commands.java` remains server command authority and must not be modified by Client Console UI polish.
- Boss/cache research belongs in dedicated Boss Research/BossLabs workflows rather than accumulating in Owner.

## Verified foundation

### VERIFIED

- User has runtime-observed the current V2 dashboard/icon direction and reported that it looks substantially better.
- OpenGL is a working renderer baseline and is not part of this workstream's unfinished scope.

### verified-static

- Existing shell preserves Matrix3 Applet/canvas ownership and supports lazy panels, collapse/reopen, width clamping, active-panel persistence, and panel failure isolation.
- Dashboard reuses read-only `ClientConsoleBridge` display name/rights/player/world-position state.
- Item Browser uses a 500ms debounce and preserves bounded empty browse plus lazy thumbnail work.
- Commands uses a search-first `JList` palette while preserving `ClientConsoleBridge.queueConsoleCommand(...)` and dangerous-command confirmation.
- Owner currently contains both the new generic `BossResearchPanel` and the older RoTS cache Scan/Deep Scan card; the new panel does not yet replace all cache-evidence functionality.
- `ConsoleTheme` centralizes basic colors/fonts/button/text-field/scroll treatment, but several panels still duplicate card/header/value-row/combo/status/popup styling.

## Unknown / research needed

### HYPOTHESIS

- None required for the current V2 Finish Bundle.

### UNKNOWN

- Final DPI/layout/input behavior of the accumulated V2 changes remains pending consolidated runtime acceptance.
- Exact visual quality of the final shared-component pass remains runtime/visual acceptance rather than a static claim.

## Dependencies

- Existing `ClientConsoleRotsBridge` and `ClientConsoleRotsGfxBootstrap` for RoTS cache evidence.
- Existing `ClientConsoleBossResearchBridge` for runtime probes and finding persistence.
- Existing `ConsolePreferences` string-based active-panel persistence.
- Existing Client Console V2 deferred runtime list.

## Development plan

### Phase 1 - Client Console foundation and core workflows

**Status:** COMPLETE

**Purpose:** Establish the working sidebar shell, workspace persistence, dark theme, Owner/Commands/Player/Item Browser/Settings workflows, and authoritative client/server bridges.

**Exit conditions:** Met by existing implementation history; preserved as baseline for V2 polish.

### Phase 2 - Professional V2 finish

**Status:** ACTIVE

**Purpose:** Make the established Client Console intentionally designed, efficiently organized, and consistent without rewriting the known-good architecture.

**Entry conditions:** Existing shell/core workflows are present and working enough to polish in-place.

**Exit conditions:**

- Navigation/dashboard/Commands/Item Browser V2 behavior implemented.
- Boss research has one coherent dedicated home and Owner no longer contains duplicate boss-specific tooling.
- Shared visual primitives remove obvious panel-by-panel Swing inconsistency without creating a UI framework.
- Consolidated V2 runtime acceptance passes.

#### Bundle 2.1 - Initial V2 interaction polish

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] Replace text rail placeholders with original Java2D icons.
- [x] Replace shell scaffold with a useful Matrix3 dashboard.
- [x] Reduce Item Browser search debounce to 500ms.
- [x] Replace the Commands button wall with a search-first command palette.
- [x] Create consolidated deferred `v2-testlist.txt` coverage.

**Runtime tests:** Deferred into the consolidated V2 acceptance session to minimize repeated client restarts.

#### Bundle 2.2 - V2 Finish Bundle

**Status:** ACTIVE

**Purpose:** Finish ownership cleanup and visual consistency as one larger compatible patch bundle before the deferred runtime session.

**Dependencies:** Bundle 2.1 implementation is present. Its pending runtime checks do not block this bundle because Bundle 2.2 preserves the same shell/bridge ownership and can be validated in the same consolidated session.

**Checklist / patches:**

- [x] Scan remaining V2 ownership/presentation work and establish bundle boundaries.
- [ ] Add dedicated lazy Boss Research navigation/panel ownership.
- [ ] Consolidate old RoTS Scan/Deep Scan evidence into Boss Research without losing functionality.
- [ ] Remove boss-specific/research duplication from Owner.
- [ ] Expand `ConsoleTheme` with small shared card/header/value-row/combo/status/popup helpers.
- [ ] Apply shared presentation cleanup across Dashboard, Owner, Player, Settings, Boss Research, and Item Browser context menus where beneficial.
- [ ] Update design authority/patchnotes/deferred tests for final V2 structure.
- [ ] Perform narrow static verification of changed files and Java 8 compatibility.
- [ ] Mark implementation `NEEDS TEST` and prepare one consolidated runtime session.

**Runtime tests:** Use `docs/client-console/v2-testlist.txt` after this bundle; do not interrupt implementation for per-patch launches unless static evidence exposes a blocker.

### Phase 3 - Consolidated runtime acceptance

**Status:** PLANNED

**Purpose:** Validate the accumulated V2 bundle in one efficient Matrix3 launch/test session.

**Entry conditions:** Phase 2 implementation complete.

**Exit conditions:**

- V2 deferred test list passes or failures are recorded as targeted carryover.
- No regression in gameplay input, renderer ownership, commands, Item Browser bridges, Settings, workspace persistence, BossLabs/CacheEditor launches, or Boss Research workflows.

#### Bundle 3.1 - V2 acceptance session

**Status:** PLANNED

**Checklist / patches:**

- [ ] Run quick visual/navigation/dashboard/Commands/Item Browser/Boss Research checks.
- [ ] Run focus, resize/DPI, persistence, and authority regression checks.
- [ ] Record runtime evidence and close/carry over failures precisely.

## Current execution state

- Phase: Phase 2 - Professional V2 finish
- Phase status: ACTIVE
- Bundle: Bundle 2.2 - V2 Finish Bundle
- Bundle status: ACTIVE
- Approval state: SAP AAA approved for the complete Bundle 2.2 implementation scope.
- Current checklist item: Add dedicated lazy Boss Research navigation/panel ownership.
- Current objective: Finish the remaining V2 ownership and presentation work before one consolidated runtime session.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Icon rail | 2 | 2.1 | NEEDS TEST | Implemented; deferred runtime acceptance. |
| Developer dashboard | 2 | 2.1 | NEEDS TEST | Implemented; user reports improved appearance. |
| Item Browser 500ms search | 2 | 2.1 | NEEDS TEST | Implemented; deferred performance/runtime check. |
| Commands palette | 2 | 2.1 | NEEDS TEST | Implemented; deferred keyboard/runtime check. |
| Boss Research ownership migration | 2 | 2.2 | ACTIVE | Preserve both runtime-probe findings and old RoTS cache evidence. |
| Shared visual primitives | 2 | 2.2 | READY | Small helpers only; no UI framework. |
| V2 consolidated acceptance | 3 | 3.1 | READY | One runtime session after implementation bundle. |

## Decisions / new ideas

### Decision log

- 2026-09-06: Switch Client Console work to bundle-first execution. Scan first, group compatible changes by ownership/files/dependencies/runtime tests, then implement the approved bundle without stopping for tiny AAA gates.
- 2026-09-06: Do not delete Owner's old RoTS cache research until equivalent Scan/Deep Scan/Copy/Clear evidence access exists in the dedicated Boss Research workflow.
- 2026-09-06: Use one dedicated Boss Research panel rather than leaving boss research embedded in Owner.
- 2026-09-06: Shared styling expansion must remain small and presentation-only; no plugin/component framework is approved.
- 2026-09-06: Pinned/recent tools, renderer telemetry, and other optional dashboard expansion are backlog only and do not belong in the V2 Finish Bundle.

## Testing

### Quick/high-value checks

Use `docs/client-console/v2-testlist.txt` as the primary consolidated V2 acceptance path.

### Deeper checks

Use the historical `docs/client-console/testlist.txt` only when a V2 failure points to a deeper subsystem regression.

### Smoke/regression checks

- Normal login/render/input.
- Commands still route through server command authority.
- Item Browser Inventory/Bank bridge behavior unchanged.
- Settings authority/persistence unchanged.
- Workspace geometry/open/active-panel persistence unchanged.
- Boss Research runtime probes/findings and RoTS Scan/Deep Scan remain available after migration.

## Carryover / blockers

### CARRYOVER

- Runtime verification for Bundle 2.1 is intentionally deferred into Phase 3 to save user PC time.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Remaining V2 work scanned and normalized into the larger V2 Finish Bundle.

**Current phase:**

- Phase 2 - Professional V2 finish.

**Active bundle:**

- Bundle 2.2 - V2 Finish Bundle.

**Next checklist item:**

- Add dedicated lazy Boss Research navigation/panel ownership, then migrate the legacy RoTS cache evidence card into that dedicated workflow before cleaning Owner.

**Current state / next action:**

- SAP AAA is approved for the full bundle. Continue implementation without requesting another approval between listed checklist items unless scope materially changes.
- Files already inspected: `ClientConsoleShell.java`, `ConsolePreferences.java`, `ConsoleTheme.java`, `OwnerPanel.java`, `BossResearchPanel.java`, `DashboardPanel.java`, `PlayerPanel.java`, `SettingsPanel.java`, `ItemBrowserPanel.java`, `CLIENT_CONSOLE.md`, patch/test docs.
- Do not rescan unrelated client/server systems. Existing research bridges are sufficient for this bundle.
- Pending runtime verification: accumulated V2 icon/dashboard/search/command-palette plus this finish bundle.
