# Matrix3 BossLabs Workstream

## Goal

Build BossLabs into a professional, creator-first Matrix3 boss-authoring and testing tool that can produce, test, iterate, persist, and reuse complete custom boss encounters without boss-specific hardcoding or duplicate engine ownership.

BossLabs is successful when a competent RSPS developer can select an NPC, author phases/attacks/mechanics/patterns/drops, spawn and test the encounter, iterate live, and save the finished content without needing to understand BossLabs Java internals, transport protocol, or storage format.

## Canonical Main-Goal Status

| Area | Status |
| --- | --- |
| Boss/NPC selection & creator shell | ⚠️ Needs runtime verification |
| Phase & attack authoring | ⚠️ Needs runtime verification |
| Testing & live iteration | ⚠️ Needs runtime verification |
| Attack Pattern authoring | ⚠️ Needs runtime verification |
| Matrix3-native Drops | ✅ Complete |
| Asset workflow | ❌ Not started |
| True Arena Layout | ❌ Not started |
| First complete boss proof | ❌ Not started |
| Second boss reuse proof | ❌ Not started |

## Authority chain

Read these in order when continuing BossLabs:

1. `AGENTS.md`
2. `docs/rs3/PROJECT.md`
3. **this file** - authoritative BossLabs execution map, phase/checklist state, carryover, and Resume Here
4. `docs/bosslabs/BOSSLABS.md` - creator/product/UX design authority
5. `docs/bosslabs/LIVE_EDITING.md` - DRAFT/LIVE/SAVED behavior
6. `docs/bosslabs/NPC_SEARCH.md` - NPC selector/search behavior
7. `docs/client-console/CLIENT_CONSOLE.md` - client tool lifecycle/UI contract

If older BossLabs prose conflicts with the phase/checklist or Resume Here state below, this file controls execution until the older prose is deliberately reconciled.

## Scope

### In scope

- Creator-first NPC/boss selection and inspection.
- BossLabs immutable boss definitions and Matrix3 combat adapter.
- Phase, attack, rotation, target, telegraph, hazard, tile-effect, transition-action, and minion authoring already supported by the runtime.
- Relative Attack Pattern authoring.
- Exact per-admin developer encounter testing.
- Matrix3-native drop-table authoring through the existing `NPCDrops`/`Drops` runtime authority.
- Asset selection/search/preview workflow where it can reuse existing Matrix3/Client Console/AnimLab/FX tooling.
- True encounter Arena Layout only when real boss content proves fixed bounds/anchors are required.
- One complete custom boss as the primary end-to-end proof, then a second smaller boss to prove reuse.

### Out of scope unless real content proves a requirement

- Alternate combat engine.
- Alternate NPC lifecycle/world engine.
- Alternate drop engine or loot roller.
- Alternate scheduler.
- Giant node editor or general-purpose scripting language.
- General world/map editor.
- Duplicate model/animation/FX editors inside BossLabs.
- Hundreds of speculative mechanics.
- Boss-specific hardcoding merely to demonstrate BossLabs.

## Architecture / ownership

- **Matrix3 owns:** NPC lifecycle, targeting, pathing, combat scheduling/damage helpers, world state, `WorldTasksManager`, death/XP, `NPCDrops`, `Drops.generateDrops()`, permissions, and existing cache/runtime authorities.
- **BossLabs owns:** creator UI, immutable BossLabs encounter content, BossLabs live registry/rollback, BossLabs testing session ownership, BossLabs-owned transient encounter tasks/minions, and BossLabs-owned persistence files.
- Boss combat persistence remains `data/bosslabs/definitions.bld`, store/wire **v8**.
- BossLabs drop overrides are independent at `data/bosslabs/drops.bld`, store/wire **v1**.
- `data/npcs/packedDrops.d` remains Matrix3 source data and is never rewritten by BossLabs.
- NPC death still obtains the current `NPCDrops` table and rolls it through the existing Matrix3 `Drops.generateDrops()` implementation. BossLabs does not roll loot separately.
- Developer world mutations remain rights-gated and use established Matrix3 world-thread paths.
- `Commands.java` is not a BossLabs integration target.

## Verified foundation

### VERIFIED

- BossLabs external UI has opened in the running client and loaded/inspected an NPC in user runtime testing.
- A prior runtime screenshot exposed the Testing-tab gating defect on a normal Matrix3 NPC; that defect was patched afterward but the correction still requires re-verification.
- 2026-09-06 runtime video verified a creator-state defect: with no NPC selected, the shell still looked editable, Boss-name typing could show `DRAFT: modified`, and phase/attack actions could appear usable while the actual definition editor had no DRAFT. This blocked the intended authoring workflow and is treated as a V2.1/V2.2 regression gate.
- After the creator-state repair, the user reported the BossLabs creator workflow was working except Drops. A 2026-09-06 runtime screenshot showed NPC `Man [1]` loaded normally and item search resolving item definitions inside Drops.
- The same screenshot/runtime report verified a Drops blocker: the Drops sub-workspace stayed `DRAFT: none` / `Reloading current live drops...`, so Add Drop could not mutate a local drop draft even though item search and NPC context were present.
- 2026-09-08 runtime retest verified the repaired Drops workflow reaches an editable DRAFT on `Man [1]`, exposes malformed legacy Matrix3 rows instead of hanging, allows correction, and adds Abyssal whip `4151` to the local DRAFT.
- 2026-09-08 runtime retest verified `Apply Drops Live` changes the live drop table and Matrix3 NPC death produces the authored Abyssal whip as a normal ground item alongside Matrix3-owned drops.
- 2026-09-08 runtime retest verified `Save & Apply Drops` persists the override across a Server restart and `Restore Matrix3` returns the original Matrix3 drop table.
- The repaired Client + Server code launched successfully in the same runtime session, providing practical compile/startup evidence for the Drops bundle.

### verified-static

- One-field NPC search uses numeric input as ID and text as name search.
- BossLabs combat registration delegates through Matrix3 `CombatScriptsHandler`; unregistered NPCs retain Matrix3 Java/default combat.
- Complete BossLabs definition persistence is versioned and isolated under `data/bosslabs`.
- Boss definition store/wire is v8 with older BossLabs read compatibility already implemented.
- Existing attack runtime includes direct/projectile attacks, weighted rotation/cooldowns, current/random-nearby targeting, target-centered telegraphed patterns, lingering hazards, tile effects, phase actions, encounter context, and encounter-owned minions.
- Testing uses an exact per-admin test NPC instance rather than world lookup by NPC ID.
- The Testing UI can spawn/reset/set HP for plain Matrix3 NPCs; BossLabs-only phase/attack/hazard/minion controls remain live-definition gated.
- Attack Pattern authoring edits the same DRAFT tile-offset data persisted by the existing v8 definition path.
- BossLabs drop editing overlays the existing `NPCDrops` map while Matrix3 `Drops.generateDrops()` remains runtime authority.
- Saved drop overrides load after packed Matrix3 drops so the packed table is available as the restore baseline.
- Matrix3 Rare/Very Rare wearable split entries are exposed back to the editor so inspection does not silently lose them.
- Duplicate drop entries are valid and preserved. For rolled rarity buckets, repeated array slots can intentionally increase selection weight; repeated `Always` slots remain repeated guaranteed drops.
- The creator-state repair enforces the client invariant `no successfully inspected NPC -> no editable BossLabs DRAFT`: dependent tabs and Boss-name editing are locked while empty/loading, the previous draft is cleared before a new inspection, stale inspection replies are ignored by NPC id, and missing inspection restores a clean locked state.
- The V2.4-B Drops repair preserves Matrix3 legacy quantity `0`, can transport structurally readable legacy rows for repair without publishing invalid max<min ranges, reports server inspection failures, reports client decode failures, and bounds missing inspect replies with a five-second client timeout.
- Drops creator controls have explicit loading/error states and the primary Add Drop / Update Drop action is fixed outside the scroll area so it remains visible.

## Unknown / research needed

### HYPOTHESIS

- The current asset workflow can likely reuse `ClientConsoleItemBridge`-style selectors plus existing AnimLab/FX specialist tools without requiring a new cache-indexing subsystem; exact animation/GFX/projectile integration still needs a narrow scan after the runtime gate passes.

### UNKNOWN

- Runtime correctness/timing of weighted rotation, phase transitions, telegraph delay, hazard interval, minion lifecycle cleanup, and definition-replacement cleanup under real combat.
- Visual orientation of Attack Pattern Rotate Left/Right and Nudge Up/Down in the actual Swing canvas.
- Runtime behavior of deeper Drops controls/edge cases not required for the core milestone: one-level Undo, Apply Saved, Delete Saved Override, duplicate-slot weighting round-trip, Rare/Very Rare wearable readback, and multi-NPC store isolation.
- Whether the first complete proof boss requires true fixed Arena Layout semantics or can be completed with existing world placement plus relative attack patterns.

## Dependencies

- Eclipse + Java 8.
- Matrix3 Client + Server.
- Existing Client Console command/reply bridge.
- Existing `ClientConsoleItemBridge` for item metadata/search.
- Matrix3 `CombatScriptsHandler`, `NPCCombat`, `WorldTasksManager`, `NPCDrops`, and `Drops` authorities.
- `docs/rs3/SMOKE_TEST.md` for regression coverage.

## Development plan

The historical `BOSSLABS.md` V2 sequence remains the product roadmap, but implementation advanced ahead of the phase gates before this workstream was normalized. That history is preserved below rather than rewritten.

No later phase may be marked COMPLETE until its earlier required gate is satisfied. Already-landed later-phase code is treated as implementation awaiting validation, not permission to skip the current gate. The user explicitly reprioritized the runtime-blocking Drops defect on 2026-09-06; that V2.4-B blocker is now runtime-proven and no longer prevents resuming the earlier V2.1/V2.2 gates.

### Phase V2.0 - Creator UX authority

**Purpose:** Establish creator-first workflow, no-retyping-known-values rule, progressive disclosure, Attack Pattern vs Arena separation, and context-aware testing.

**Status:** COMPLETE

**Exit conditions:**

- `docs/bosslabs/BOSSLABS.md` is accepted as product/UX authority.
- Normal BossLabs workflow is defined around creator tasks rather than implementation classes.

### Phase V2.1 - Shell and composition cleanup

**Purpose:** Provide the direct creator-oriented BossLabs shell and lifecycle without regressions.

**Status:** NEEDS TEST

**Entry conditions:**

- V2.0 complete.

**Exit conditions:**

- Client compiles on Java 8.
- BossLabs opens/reopens cleanly.
- NPC selection/inspection works.
- No-NPC/loading states cannot expose a ghost or stale editable DRAFT.
- Direct panel composition and listener cleanup work without stale callbacks.
- Practical sizing/scrolling remains usable.
- Existing search/publish/testing paths have no regression.

#### Bundle V2.1-A - Consolidated runtime verification

**Purpose:** Validate the accumulated BossLabs implementation in one user runtime session before any more feature expansion.

**Status:** NEEDS TEST

**Checklist / patches:**

- [x] V2.1 creator shell/direct composition implemented.
- [x] Testing panel directly owned by BossLabsPanel.
- [x] Drops panel directly composed into BossLabsPanel.
- [x] Current repaired Client + Server launched successfully during the 2026-09-08 Drops runtime session.
- [ ] Open, close, and reopen BossLabs; verify no stale listener/window behavior.
- [x] Runtime-load a safe normal Matrix3 NPC after the creator-state repair; `Man [1]` loaded successfully.
- [ ] Inspect a live BossLabs NPC after applying a test definition.
- [ ] Run the remaining high-value BossLabs workflow checks listed under Testing below.
- [ ] Run required `docs/rs3/SMOKE_TEST.md` coverage after the drop startup/persistence change.

#### Bundle V2.1-B - Creator-state invariant repair

**Purpose:** Repair the runtime-proven ghost-DRAFT/empty-workspace failure before resuming the larger verification session.

**Status:** NEEDS TEST / RUNTIME IMPROVED

**Runtime evidence:**

- User video on 2026-09-06 showed no selected NPC while the shell displayed editable boss/phase/attack workflow and `DRAFT: modified`; actions appeared to do nothing because the internal definition editor had no loaded DRAFT.
- After the repair the user reported everything tested in the main creator flow was working except Drops; this is positive runtime evidence for the shell repair, but the full V2.1 gate remains open until the remaining checklist is completed.

**Checklist / patches:**

- [x] Record runtime failure as a V2.1/V2.2 gate blocker.
- [x] Enforce `no inspected NPC -> no editable DRAFT` at BossLabsPanel.
- [x] Lock dependent tabs and Boss-name editing while no NPC is selected or inspection is loading.
- [x] Clear the previous DRAFT/inspection state before requesting another NPC.
- [x] Ignore stale inspection replies that do not match the currently requested NPC.
- [x] Restore a clean locked state after invalid/missing NPC inspection.
- [x] Add explicit visible workspace guidance and document the safe first-phase 100% -> 0% default.
- [x] Add focused creator-state regression checks to `ux-testlist.txt`.
- [x] Runtime launch after repair; normal NPC loading/creator navigation works according to user retest.
- [ ] Re-run the exact no-NPC locked-state assertion when convenient.
- [ ] Confirm invalid Starts at HP 1 / Ends at HP 2 produces the existing inline range error and does not mutate the phase.

### Phase V2.2 - Phases and attacks workflow

**Purpose:** Make multi-phase attack creation/tuning/testing usable without memorizing internal IDs.

**Status:** NEEDS TEST

**Gate note:** Implementation landed before V2.1 runtime verification. It may be tested in the same consolidated session, but it is not considered complete until V2.1 passes first. The first V2.2 runtime attempt was blocked by the V2.1 ghost-DRAFT state; the post-repair user retest reports the creator flow working, but deeper phase/attack behavior remains to be verified.

**Implemented:**

- Creator-facing phase/attack names with internal IDs moved away from normal workflow.
- Progressive attack sections.
- Safe/default authoring path.
- Selection-aware `Enter Selected Phase` / `Test Selected Attack` testing.
- Dedicated Attack Pattern workspace.
- Pattern presets, copy/paste, drag paint/erase, one-level undo, rotate/mirror/nudge, geometry/timing summaries.
- Testing HP shortcuts and bounded action history.

**Exit conditions:**

- A developer can create a multi-phase BossLabs definition and test selected phases/attacks without typing internal IDs.
- Apply Live / Save & Apply / Undo / Apply Saved operate as intended.
- Attack Pattern workflow survives save/reload and visual transforms behave correctly.

### Phase V2.3 - Asset workflow

**Purpose:** Remove repeated manual lookup of animation/GFX/projectile identifiers without duplicating specialist tooling.

**Status:** PLANNED

**Entry conditions:**

- V2.1 and V2.2 runtime gates pass, or the user explicitly reprioritizes after a documented blocker.

#### Bundle V2.3-A - Shared asset selection

**Status:** READY after runtime gate

**Checklist / patches:**

- [ ] Narrow-scan existing AnimLab/FX/cache/client selector APIs; do not build a second asset index if an existing owner can serve BossLabs.
- [ ] Add direct-ID + searchable selection for the highest-value animation/GFX/projectile fields where practical.
- [ ] Add preview or open/link-to-specialist-tool actions where architecture safely supports them.
- [ ] Keep raw numeric ID entry as the power-user shortcut.
- [ ] Verify indexing/search work is off the Swing EDT.

**Exit conditions:**

- Common asset iteration no longer requires constantly leaving BossLabs to look up forgotten IDs.

### Phase V2.4 - Arena and Drops

**Purpose:** Complete encounter geometry/reward authoring needed for the first complete boss.

**Status:** NEEDS TEST / DROPS CORE COMPLETE

#### Bundle V2.4-A - Relative Attack Pattern workspace

**Status:** NEEDS TEST

- [x] Large phase/attack-aware relative pattern workspace.
- [x] Presets, copy/paste, zoom/pan, drag paint/erase.
- [x] Undo and transform tooling.
- [ ] Runtime/UI verification of transform directions and save/reload fidelity.

#### Bundle V2.4-B - Matrix3-native Drops

**Status:** COMPLETE / CORE RUNTIME VERIFIED

- [x] Existing Matrix3 table inspection implementation.
- [x] Item ID/name search through existing Client Console item definitions.
- [x] Matrix3 rarity buckets and quantity ranges.
- [x] Rare-drop-table toggle.
- [x] Apply Live / Save & Apply / Apply Saved / Undo / Restore Matrix3 / Delete Saved Override implementation.
- [x] Independent atomic `drops.bld` persistence.
- [x] Rare/Very Rare gear-split readback.
- [x] Preserve duplicate slots for Matrix3 selection weighting and repeated Always entries.
- [x] Runtime failure captured: `Man [1]` originally reached Drops item search but remained `DRAFT: none` / reloading, preventing Add Drop.
- [x] Make Matrix3 legacy zero-quantity rows inspection-safe and allow structurally readable invalid ranges to load for repair without allowing publish.
- [x] Return explicit server inspection errors instead of letting world-task conversion failures disappear.
- [x] Bound client inspection waits with explicit queue/decode/timeout errors.
- [x] Add explicit DRAFT loading/error states and disable mutations while an inspection is pending.
- [x] Keep Add Drop / Update Drop fixed and visible outside the scroll area; rename `New Entry` to `New Drop` and explain the commit step.
- [x] Add focused acceptance checks 42-49 in `drops-testlist.txt`.
- [x] Repaired Client + Server launched successfully in the 2026-09-08 runtime session.
- [x] Re-open Drops on `Man [1]`; repaired workspace reaches a usable DRAFT and exposes malformed legacy rows instead of hanging.
- [x] Add Abyssal whip `4151` as a test row and confirm it appears in the DRAFT.
- [x] Apply Drops Live and confirm the authored Always drop is produced by Matrix3 NPC death as a normal ground item.
- [x] Save & Apply, restart Server, confirm the override persists, then Restore Matrix3 and confirm the original table returns.
- [ ] Optional deeper acceptance: Reload Current discard behavior, one-level Undo, Apply Saved, Delete Saved Override, duplicate-slot runtime round-trip, Rare/Very Rare wearable readback, and multi-NPC store isolation.
- [ ] Normal unrelated Matrix3 NPC drop regression remains part of the consolidated smoke pass.

#### Bundle V2.4-C - True Arena Layout

**Status:** CARRYOVER

- True fixed encounter bounds/anchors/spawn positions are intentionally deferred until the first complete boss proves they are required.
- Do not confuse this with relative Attack Pattern data.

### Phase V2.5 - First complete boss proof

**Purpose:** Prove the complete content pipeline by building, testing, killing, resetting, tuning, saving, reloading, and rewarding one complete custom boss through BossLabs.

**Status:** PLANNED

**Entry conditions:**

- V2.1/V2.2 gates pass.
- V2.3 is complete enough that asset lookup is not the dominant workflow bottleneck.
- Required V2.4 Drops/geometry are runtime-proven.

**Boss selection decision:**

- BossLabs remains generic.
- `Volcanic Warden` is a **reference/proof concept only**, not a required target and not permission for boss-specific hardcoding.
- The actual first proof boss may be any safe custom/test boss chosen when this phase begins.

**Exit conditions:**

- Full encounter authoring loop proven end-to-end.
- Phase transitions, attacks, cleanup, live iteration, persistence, testing, and Matrix3-owned drops are runtime-proven.
- Any new framework/tooling added during the boss is justified by real content friction.

### Phase V2.6 - Second boss reuse proof

**Purpose:** Prove reusable BossLabs framework behavior is actually reusable rather than accidentally tailored to the first boss.

**Status:** PLANNED

**Entry conditions:**

- V2.5 complete.

**Exit conditions:**

- A second smaller boss reuses the proven framework without copying the first encounter wholesale.

## Current execution state

- **Phase:** V2.1 - Shell and composition cleanup (resumed required gate after Drops blocker passed)
- **Phase status:** NEEDS TEST
- **Bundle:** V2.1-A - Consolidated runtime verification
- **Bundle status:** NEEDS TEST
- **Approval state:** V2.4-B Drops core workflow is runtime-proven; no new feature expansion is active.
- **Current checklist item:** Continue the consolidated BossLabs runtime pass: window lifecycle, live BossLabs NPC inspection, phase/attack authoring/testing, Attack Pattern transforms/persistence, encounter cleanup, and smoke coverage.
- **Current objective:** Close the required V2.1/V2.2 gates so V2.3 Asset Workflow can begin without carrying known workflow regressions forward.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Creator shell/direct composition | V2.1 | V2.1-B | NEEDS TEST | User reports repaired creator flow works; full shell gate still has a few checks pending. |
| Phase/attack creator workflow | V2.2 | V2.2 | NEEDS TEST | Main creator flow improved; deeper author/test/save behavior still pending. |
| Context-aware Testing workflow | V2.2 | V2.2 | NEEDS TEST | Includes plain Matrix3 spawn/reset/HP and BossLabs-specific controls. |
| Attack Pattern transforms/undo | V2.2/V2.4 | V2.4-A | NEEDS TEST | Visual direction and persistence check pending. |
| Matrix3-native Drops | V2.4 | V2.4-B | DONE | Runtime-proven: edit, Apply Live, actual Matrix3 ground drop, Save & Apply, restart persistence, and Restore Matrix3. |
| Duplicate drop slot weighting correction | V2.4 | V2.4-B | NEEDS TEST | Verified-static semantics preserved; runtime duplicate-slot round-trip is optional deeper acceptance. |
| Asset workflow | V2.3 | V2.3-A | READY | Next implementation phase only after required V2.1/V2.2 gates pass. |
| True Arena Layout | V2.4 | V2.4-C | CARRYOVER | Build only when first boss proves fixed anchors/bounds are required. |
| First complete boss | V2.5 | V2.5 | READY after dependencies | Generic proof boss; not Volcanic-Warden-specific. |

## Decisions / new ideas

### Decision log

- BossLabs is a **generic tool first**. Reference bosses/examples must never become hardcoded requirements.
- Volcanic Warden is reference/proof material only; the actual first proof boss is chosen later.
- Larger coherent BossLabs feature/repair packs are acceptable when they preserve ownership, rollback points, narrow logical commits, and one consolidated runtime test session.
- Runtime testing time is scarce; batch compatible checks rather than requiring repeated pull/restart cycles.
- Content drives future tooling. After the runtime gates, finish the missing asset workflow, then move toward a complete boss instead of endlessly expanding editor features.
- Matrix3 drop rarity semantics are bucket-based. Do not invent unsupported per-item percentages.
- Duplicate drop entries are meaningful Matrix3 data and must round-trip faithfully.
- Matrix3 legacy `Drop`/packed data permits quantity `0`; BossLabs inspection must preserve that data rather than rejecting the entire table. New/edited rows must still satisfy max >= min before publishing.
- A failed Drops inspection must terminate with a visible error. Silent world-task exceptions or indefinite `DRAFT: loading` are not acceptable creator behavior.
- A malformed legacy Matrix3 row may block publishing until corrected; future Drops polish may highlight/jump to invalid rows, but this is usability carryover and does not block the proven core drop pipeline.
- True Arena Layout is deferred until real encounter content requires fixed encounter-space semantics.
- Creator-state invariant: **no successfully inspected NPC means no editable BossLabs DRAFT**. Empty/loading/missing states must be explicit and non-interactive rather than relying on individual controls to silently no-op.

## Testing

### 2026-09-08 runtime-proven Drops checkpoint

- VERIFIED: `Man [1]` Drops now reaches a usable DRAFT instead of remaining on `DRAFT: none` / endless reload.
- VERIFIED: malformed legacy Matrix3 range data is visible and blocks publishing until corrected rather than silently failing inspection.
- VERIFIED: item search resolves Abyssal whip `4151`, Add Drop mutates the local DRAFT, and Apply Drops Live changes the live drop source to BossLabs.
- VERIFIED: killing the NPC after Apply Drops Live produces the authored Abyssal whip as a normal ground item through Matrix3's existing drop path.
- VERIFIED: Save & Apply persists the override across a Server restart.
- VERIFIED: Restore Matrix3 returns the original Matrix3 drop table and removes the live BossLabs override.
- Non-blocking deeper acceptance remains for Reload Current discard behavior, one-level Undo, Apply Saved, Delete Saved Override, duplicate-slot runtime weighting, Rare/Very Rare wearable readback, and multi-NPC store isolation.

### Remaining creator-state checks

1. Open BossLabs with no NPC selected and confirm `DRAFT: none / LIVE: none / SAVED: none`, Boss name disabled, dependent tabs locked.
2. Load a safe NPC and confirm authoring unlocks only after inspection.
3. Add a first phase and confirm default 100% -> 0%.
4. Set Starts at HP 1 / Ends at HP 2 and confirm inline validation rejects the range.
5. Restore a valid range, add an attack, and confirm safe defaults/selection.

### Quick/high-value consolidated session

1. Verify normal login/Client Console behavior and BossLabs close/reopen lifecycle.
2. Confirm plain Matrix3 Testing allows Spawn Boss Here, Reset Encounter, and Set HP; BossLabs-only controls remain disabled until a live BossLabs definition exists.
3. Create a simple valid phase + attack, Apply Live, spawn the controlled test boss, enter the selected phase, and test the selected attack.
4. Exercise a patterned attack: drag-paint/erase, Undo, rotate, mirror, nudge, Apply Live, and confirm intended visual direction/tiles.
5. Exercise one telegraph/hazard and one minion action; use Clear Hazards + Minions and Reset Encounter.
6. Confirm an unrelated normal Matrix3 NPC still uses its original combat and drop behavior.
7. Run required `docs/rs3/SMOKE_TEST.md` coverage after the startup/drop-persistence change.

### Deeper checks when time allows

1. Multiple phase transitions with On Exit/On Enter actions.
2. Weighted attack rotation/cooldowns/repeat prevention.
3. Random-nearby target behavior with multiple players.
4. Minion death vs cleanup semantics.
5. Hazard duration/interval timing and definition-replacement cleanup.
6. Drops Rare/Very Rare wearable readback and rare-drop-table toggle.
7. Save two drop overrides, update/delete one, confirm the other persists unchanged.
8. Full focused lists remain under `docs/bosslabs/testlist.txt`, `creator-workflow-pack-testlist.txt`, `arena-workspace-testlist.txt`, `drops-testlist.txt`, and `ux-testlist.txt`.

### Smoke/regression checks

- Because BossLabs Drops changes startup/persistence and the live `NPCDrops` table, run the full relevant `docs/rs3/SMOKE_TEST.md` pass after the consolidated BossLabs tests.
- Login, command/chat, normal NPC combat, normal NPC drops, Client Console, and BossLabs regressions are blockers.

## Carryover / blockers

### CARRYOVER

- **Task:** True encounter Arena Layout.
- **Phase/bundle:** V2.4 / V2.4-C.
- **Current state:** Relative Attack Pattern authoring exists; fixed encounter-space semantics are intentionally not invented yet.
- **Remaining work:** Only define bounds/anchors/spawn positions if the first complete boss proves they are necessary.
- **Next action:** Re-evaluate during V2.5 content work.

- **Task:** Drops invalid-row navigation polish.
- **Phase/bundle:** V2.4 / future polish only if useful.
- **Current state:** Invalid legacy rows are surfaced and safely block publish, but the creator must currently locate the row manually.
- **Remaining work:** Consider invalid-row highlighting/count and Jump to Invalid only if this remains a real workflow annoyance.
- **Next action:** Do not interrupt the current V2.1/V2.2 verification gate for this polish.

### BLOCKED

- V2.3 feature progression remains blocked by the required V2.1/V2.2 runtime gates and remaining required Attack Pattern/smoke verification.
- The V2.4-B Drops core workflow is no longer a blocker.

## Resume Here

**Last completed:**

- Runtime-proven the repaired Matrix3-native Drops core pipeline end-to-end on `Man [1]`.
- Verified local DRAFT editing, `Apply Drops Live`, actual Matrix3 ground-drop generation, `Save & Apply Drops`, Server restart persistence, and `Restore Matrix3`.
- Reclassified Matrix3-native Drops as a completed canonical milestone while preserving deeper edge-case acceptance as non-blocking follow-up.

**Current phase:**

- V2.1 - Shell and composition cleanup (`NEEDS TEST`; resumed required gate after the Drops blocker passed).

**Active bundle:**

- V2.1-A - Consolidated runtime verification (`NEEDS TEST`).

**Next checklist item:**

- Continue the short consolidated BossLabs runtime pass: close/reopen lifecycle, inspect a live BossLabs NPC, author/test a simple phase + attack, verify Testing controls, verify Attack Pattern transforms/save-reload, exercise hazard/minion cleanup, then run the required smoke coverage.

**Current state / next action:**

- Do **not** re-open the Drops architecture unless a new regression appears; the core pipeline is runtime-proven.
- Finish the remaining V2.1/V2.2 and required V2.4-A verification.
- After V2.1/V2.2 and required pattern/smoke checks pass, start V2.3 Asset Workflow, then move to the first complete generic boss.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/bosslabs/PROJECT.md`
- `docs/bosslabs/drops-testlist.txt`
- `Client/src/main/java/game/console/bosslabs/BossLabsDropPanel.java`
- `Client/src/main/java/game/console/bosslabs/BossLabsDropClientBridge.java`
- `Client/src/main/java/game/console/bosslabs/BossLabsDropDraftDefinition.java`
- `Server/src/main/java/com/rs/game/npc/bosslabs/BossLabsDropCommandBridge.java`
- `Server/src/main/java/com/rs/game/npc/bosslabs/BossLabsDropDefinition.java`
- `Server/src/main/java/com/rs/game/npc/bosslabs/BossLabsDropPublisher.java`
- `Server/src/main/java/com/rs/game/npc/bosslabs/BossLabsDropWireCodec.java`
- Matrix3 `Drop`, `Drops`, `NPCDrops` amount/readback semantics
- Existing BossLabs command/reply ownership already proven by working NPC search/inspection in the same runtime session

**Do not re-scan without new evidence:**

- Matrix3 drop bucket rates and drop-runtime authority.
- BossLabs drop persistence/store architecture.
- BossLabs Drops DRAFT/load/apply/save/restart/restore core workflow; runtime-proven on 2026-09-08.
- BossLabs combat authority boundaries.
- Existing definition wire/store v8 ownership.
- Existing Testing exact-instance ownership.
- The main creator-state failure path established by the 2026-09-06 video.
- Drops item-index/search path; runtime verified to resolve and author item `4151`.

**Pending runtime verification:**

- Remaining creator-state/phase/attack checks.
- BossLabs window lifecycle and direct composition.
- Testing spawn/reset/HP/selected phase/selected attack.
- Pattern transform visual directions and save/reload.
- Encounter hazard/minion cleanup.
- Boss definition persistence/restart.
- Normal unrelated Matrix3 NPC combat/drop regression.
- Full required smoke test after startup/drop persistence changes.
- Optional Drops edge cases: one-level Undo, Apply Saved, Delete Saved Override, duplicate-slot weighting round-trip, Rare/Very Rare wearable readback, and multi-NPC store isolation.

**Blockers:**

- Required V2.1/V2.2 runtime verification and remaining required Attack Pattern/smoke checks before V2.3.

**Important remaining uncertainty:**

- Exact best reuse path for animation/GFX/projectile selection until V2.3 narrow scan.
- Whether true Arena Layout is actually required by the first proof boss.

## Next recommended work

**Resume the consolidated V2.1/V2.2 BossLabs runtime verification; Drops no longer blocks progress.**

After those required gates and the remaining pattern/smoke checks pass, start V2.3 Asset Workflow, then move toward the first complete generic boss.