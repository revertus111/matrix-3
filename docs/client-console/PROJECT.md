# Matrix3 Client Console

## Goal

Finish the existing Matrix3 Client Console as a professional, fast, coherent developer sidebar that accelerates Matrix3 content work without becoming an alternate owner of gameplay, commands, persistence, cache decoding, or renderer behavior.

`docs/client-console/CLIENT_CONSOLE.md` remains the detailed design/UX reference. This file owns workstream phases, bundles, checklist state, main-goal status, deferred verification, and resume state.

## Canonical Main-Goal Status

| Main-goal area | Status |
| --- | --- |
| Shell & workspace foundation | ✅ Complete |
| Core developer workflows | ✅ Complete |
| Professional V2 presentation | ⚠️ Needs runtime verification |
| Tool ownership & organization | ⚠️ Needs runtime verification |
| Consolidated V2 runtime acceptance | ⚠️ Needs runtime verification |

## Scope

### In scope

- Client Console shell, rail, Dashboard, Owner, Commands, Player, Item Browser, Settings, and small specialist-tool navigation/launch surfaces.
- Dedicated Client Console navigation for specialist developer workflows such as Client Atlas and Boss Research while preserving their own authorities.
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
- `ConsolePreferences` owns Client Console workspace geometry/open/active-panel persistence; panel IDs remain generic persisted strings.
- `ConsoleTheme` owns shared Client Console presentation primitives.
- Individual panels own their controls/display formatting and call existing Matrix3/client-console bridges.
- `ClientConsoleBridge` and specialist bridges hand off to existing authoritative Matrix3 systems; they do not replace those systems.
- `Commands.java` remains server command authority and is not owned by Client Console.
- Client Atlas remains owned by the existing `game.atlas` authorities; Client Console only hosts its panel.
- Boss runtime probes/findings remain owned by `ClientConsoleBossResearchBridge` and RoTS cache evidence remains owned by `ClientConsoleRotsBridge`/`ClientConsoleRotsGfxBootstrap`; Client Console now gives those workflows one dedicated Boss Research home.
- Owner is limited to account/rights/client-state visibility and future genuine owner/admin controls. Boss/cache research no longer belongs there.

## Verified foundation

### VERIFIED

- User runtime-observed the initial V2 dashboard/icon direction and reported that it looks substantially better.
- User runtime-observed the post-Bundle 2.2 Client Console V2 finish direction and reported that it is "looking really good". Treat the overall visual direction at the user's current runtime layout as accepted; this does not substitute for the remaining interaction, persistence, focus, DPI, or bridge-regression checks.
- OpenGL is a working renderer baseline and is not part of this workstream's unfinished scope.

### verified-static

- Existing shell preserves Matrix3 Applet/canvas ownership and supports lazy panels, collapse/reopen, width clamping, active-panel persistence, and panel failure isolation.
- Dashboard reuses read-only `ClientConsoleBridge` display name/rights/player/world-position state.
- Item Browser uses a 500ms debounce and preserves bounded empty browse plus lazy thumbnail work.
- Commands uses a search-first `JList` palette while preserving `ClientConsoleBridge.queueConsoleCommand(...)` and dangerous-command confirmation.
- Client Atlas remains registered through its existing `atlas` panel ID and icon.
- Boss Research is now a dedicated lazy panel with its own icon/panel ID. It combines the pre-existing brother transform/animation/GFX/finding workflow with the pre-existing RoTS Scan/Deep Scan/Copy/Clear cache-evidence workflow.
- Owner no longer imports or hosts boss research/RoTS cache research.
- `ConsoleTheme` now centralizes title/subtitle/card/value-row/wrapped-text/combo/status/popup/menu primitives in addition to the existing button/text-field/scroll styling.
- Dashboard, Owner, Player, Settings, and Boss Research use the shared visual primitives. Settings explicitly tracks viewport width for narrow-sidebar wrapping.
- Item Browser's local popup/menu wrappers already match the same Client Console palette; they were intentionally left behaviorally untouched rather than rewriting the large panel solely for three style delegations.

## Unknown / research needed

### HYPOTHESIS

- None required for the current V2 runtime gate.

### UNKNOWN

- Final DPI/layout/input behavior across the requested resolutions/scaling values remains pending consolidated runtime acceptance.
- Runtime behavior of the newly dedicated Boss Research panel, migrated RoTS evidence controls, and `bossResearch` active-panel restore remains pending.
- Commands keyboard flow/danger confirmation, Item Browser 500ms behavior/bridges, Settings behavior, Atlas coexistence, focus handoff, and clean-restart persistence still require the consolidated gate.

## Dependencies

- Existing `ClientConsoleRotsBridge` and `ClientConsoleRotsGfxBootstrap` for RoTS cache evidence.
- Existing `ClientConsoleBossResearchBridge` for runtime probes and finding persistence.
- Existing `ConsolePreferences` string-based active-panel persistence.
- Existing Client Atlas panel/authorities, which must remain unaffected.
- `docs/client-console/v2-testlist.txt` for the consolidated runtime gate.

## Development plan

### Phase 1 - Client Console foundation and core workflows

**Status:** COMPLETE

**Purpose:** Establish the working sidebar shell, workspace persistence, dark theme, Owner/Commands/Player/Item Browser/Settings workflows, and authoritative client/server bridges.

**Exit conditions:** Met by existing implementation history; preserved as baseline for V2 polish.

### Phase 2 - Professional V2 finish

**Status:** NEEDS TEST

**Purpose:** Make the established Client Console intentionally designed, efficiently organized, and consistent without rewriting the known-good architecture.

**Entry conditions:** Existing shell/core workflows are present and working enough to polish in-place.

**Exit conditions:**

- V2 navigation/dashboard/Commands/Item Browser behavior passes consolidated runtime checks.
- Boss Research dedicated ownership and migrated RoTS cache-evidence workflow pass runtime checks without feature loss.
- Owner contains no duplicated boss-specific tooling.
- Shared presentation remains readable/responsive across the requested window sizes/scaling targets.
- No regression in Matrix3 input, renderer ownership, Commands authority, Item Browser bridges, Settings, Atlas, or workspace persistence.

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

**Status:** NEEDS TEST

**Purpose:** Finish ownership cleanup and visual consistency as one larger compatible implementation bundle before the deferred runtime session.

**Checklist / patches:**

- [x] Scan remaining V2 ownership/presentation work and establish bundle boundaries.
- [x] Add dedicated lazy Boss Research navigation/panel ownership while preserving the concurrently-added Client Atlas panel.
- [x] Consolidate old RoTS Scan/Deep Scan/Copy/Clear evidence into Boss Research without changing specialist bridge/thread ownership.
- [x] Remove boss-specific/research duplication from Owner.
- [x] Expand `ConsoleTheme` with small shared card/header/value-row/wrapped-text/combo/status/popup/menu helpers.
- [x] Apply shared presentation cleanup across Dashboard, Owner, Player, Settings, and Boss Research. Item Browser's already-matching local popup wrappers are intentionally retained to avoid high-risk full-file churn for no visual gain.
- [x] Update workstream authority, patchnotes, and deferred tests for the final V2 structure.
- [x] Perform narrow static source verification for ownership paths, panel registration/persistence, bridge preservation, and Java 8-compatible language/API usage.
- [x] Mark implementation `NEEDS TEST` and prepare one consolidated runtime session.

**Static verification notes:**

- No server/gameplay/Commands.java files were changed by this bundle.
- Boss Research uses the same `ClientConsoleBossResearchBridge`, `ClientConsoleRotsBridge`, and `ClientConsoleRotsGfxBootstrap` calls that already owned the workflows before migration.
- RoTS Scan/Deep Scan still execute on daemon worker threads and return completed UI updates through Swing EDT ownership.
- `ClientConsoleShell` adds `bossResearch` alongside the existing `atlas` panel and includes it in lazy creation, normalization, selected-state display, and generic active-panel persistence.
- Source uses Java 8-compatible Swing/AWT/lambda constructs only. A local Eclipse compile/runtime launch is intentionally deferred to the consolidated user test session because repository access here is connector-based rather than a local checkout.

#### Bundle 2.3 - Consolidated V2 runtime gate

**Status:** NEEDS TEST

**Purpose:** Validate all accumulated V2 implementation in one efficient Matrix3 launch/test session.

**Checklist / patches:**

- [x] Record user acceptance of the final V2 visual direction at the current runtime layout.
- [ ] Run the remaining visual/navigation/dashboard/Commands/Item Browser/Boss Research/Owner/Settings/Atlas checks in `v2-testlist.txt`, including resize/DPI coverage not established by the general visual acceptance.
- [ ] Run focus, persistence, bridge-authority, and restart checks from the same list.
- [ ] Record runtime evidence and convert any failures into targeted carryover rather than broad rescans.

## Current execution state

- Phase: Phase 2 - Professional V2 finish
- Phase status: NEEDS TEST
- Bundle: Bundle 2.3 - Consolidated V2 runtime gate
- Bundle status: NEEDS TEST
- Approval state: SAP AAA approved. Current code bundle is implemented; user has visually accepted the final V2 direction, while deeper runtime verification remains intentionally open.
- Current checklist item: Continue the consolidated runtime gate beyond general visual acceptance.
- Current objective: Validate interactions, focus, bridge authority, Boss Research migration, resize/DPI behavior, and persistence without adding more feature scope first.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Icon rail | 2 | 2.1 | NEEDS TEST | Current visual direction accepted; tooltip/interaction/DPI checks remain. |
| Developer dashboard | 2 | 2.1 | NEEDS TEST | Appearance accepted by user; live refresh/resize/persistence checks remain. |
| Item Browser 500ms search | 2 | 2.1 | NEEDS TEST | Implemented; performance/runtime/bridge check remains. |
| Commands palette | 2 | 2.1 | NEEDS TEST | Implemented; keyboard/danger-confirm/bridge runtime check remains. |
| Boss Research ownership migration | 2 | 2.2 | NEEDS TEST | Visual direction accepted; runtime probes/findings + RoTS cache evidence still need functional verification. |
| Owner cleanup | 2 | 2.2 | NEEDS TEST | Visual direction accepted; live refresh/no-duplication check remains. |
| Shared visual primitives | 2 | 2.2 | NEEDS TEST | Current appearance accepted; minimum-width and DPI acceptance remain. |
| V2 consolidated acceptance | 2 | 2.3 | NEEDS TEST | General visual acceptance recorded; functional/regression gate remains. |

## Decisions / new ideas

### Decision log

- 2026-09-06: Switch Client Console work to bundle-first execution. Scan first, group compatible changes by ownership/files/dependencies/runtime tests, then implement the approved bundle without stopping for tiny AAA gates.
- 2026-09-06: Do not delete Owner's old RoTS cache research until equivalent Scan/Deep Scan/Copy/Clear evidence access exists in the dedicated Boss Research workflow.
- 2026-09-06: Use one dedicated Boss Research panel rather than leaving boss research embedded in Owner.
- 2026-09-06: Preserve Client Atlas as a separate concurrently-developed panel/workstream; Boss Research is added alongside it rather than replacing or absorbing it.
- 2026-09-06: Shared styling expansion remains small and presentation-only; no plugin/component framework is approved.
- 2026-09-06: Do not rewrite the 35k Item Browser solely to replace three already-matching private menu-style wrappers when no narrow patch operation is available; stability beats cosmetic deduplication.
- 2026-09-06: Pinned/recent tools, renderer telemetry, and other optional dashboard expansion remain backlog only and do not belong in the V2 Finish Bundle.
- 2026-09-06: User visually accepted the final V2 direction at the current runtime layout. Do not use that broad visual approval as evidence that focus, DPI, command/item/settings bridges, Boss Research migration, or persistence have passed.

## Testing

### Quick/high-value checks

Use `docs/client-console/v2-testlist.txt` as the primary consolidated V2 acceptance path. General visual appearance at the user's current runtime layout is accepted; prioritize the still-unverified interaction, authority, resize/DPI, and persistence checks.

### Deeper checks

Use the historical `docs/client-console/testlist.txt` only when a V2 failure points to a deeper subsystem regression. Use the Client Atlas/BossLabs specialist test lists for deeper specialist-tool failures.

### Smoke/regression checks

- Normal login/render/input.
- Commands still route through server command authority.
- Item Browser Inventory/Bank bridge behavior unchanged.
- Settings authority/persistence unchanged.
- Workspace geometry/open/active-panel persistence unchanged, including `bossResearch` and existing `atlas` IDs.
- Boss Research runtime probes/findings and RoTS Scan/Deep Scan remain available after migration.
- Owner remains read-only and free of duplicated boss-specific tooling.

## Carryover / blockers

### CARRYOVER

- General post-Bundle 2.2 visual direction is runtime-accepted at the user's current layout.
- Remaining Bundle 2.3 interaction, authority, resize/DPI, and persistence verification is intentionally left for the consolidated test session.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- User visually accepted the final Client Console V2 direction after Bundle 2.2; workstream state updated without adding more feature scope.

**Current phase:**

- Phase 2 - Professional V2 finish (`NEEDS TEST`).

**Active bundle:**

- Bundle 2.3 - Consolidated V2 runtime gate (`NEEDS TEST`).

**Next checklist item:**

- Continue `docs/client-console/v2-testlist.txt` with the still-unverified interaction, Boss Research migration, focus, resize/DPI, bridge-authority, and persistence checks when runtime time is available.

**Current state / next action:**

- Do not add more Client Console V2 feature scope before the consolidated gate unless a separate urgent workstream explicitly changes priority.
- General visual quality at the user's current runtime layout is accepted; do not waste time re-litigating the same appearance direction.
- Files already inspected/changed for Bundle 2.2: `ClientConsoleShell.java`, `ConsoleIcons.java`, `ConsoleTheme.java`, `OwnerPanel.java`, `BossResearchPanel.java`, `DashboardPanel.java`, `PlayerPanel.java`, `SettingsPanel.java`, Client Console workstream/patch/test docs.
- `ItemBrowserPanel.java` was inspected but intentionally not rewritten for menu-style delegation because its existing menu palette already matches and the available GitHub write path would require replacing the full large file.
- Do not rescan unrelated client/server systems; existing specialist bridges are sufficient.
- Pending runtime verification: command/item/settings functionality, Boss Research migration, Atlas coexistence, focus handoff, minimum-width/DPI behavior, and clean-restart persistence.
