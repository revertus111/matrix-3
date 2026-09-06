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
| Matrix3-native Drops | ⚠️ Needs runtime verification |
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
- The creator-state repair now enforces the client invariant `no successfully inspected NPC -> no editable BossLabs DRAFT`: dependent tabs and Boss-name editing are locked while empty/loading, the previous draft is cleared before a new inspection, stale inspection replies are ignored by NPC id, and missing inspection restores a clean locked state.

## Unknown / research needed

### HYPOTHESIS

- The current asset workflow can likely reuse `ClientConsoleItemBridge`-style selectors plus existing AnimLab/FX specialist tools without requiring a new cache-indexing subsystem; exact animation/GFX/projectile integration still needs a narrow scan after the runtime gate passes.

### UNKNOWN

- Full Java 8 Eclipse compile state of the accumulated BossLabs V2 changes until the user performs the consolidated build.
- Runtime correctness of the 2026-09-06 creator-state repair until the exact no-NPC/load/phase/attack sequence is retested.
- Runtime correctness/timing of weighted rotation, phase transitions, telegraph delay, hazard interval, minion lifecycle cleanup, and definition-replacement cleanup under real combat.
- Visual orientation of Attack Pattern Rotate Left/Right and Nudge Up/Down in the actual Swing canvas.
- Runtime behavior of the new drop override store across save/restart/restore/rollback.
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

No later phase may be marked COMPLETE until its earlier required gate is satisfied. Already-landed later-phase code is treated as implementation awaiting validation, not permission to skip the current gate.

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
- [ ] Eclipse Clean/build Client with Java 8.
- [ ] Eclipse Clean/build Server with Java 8.
- [ ] Open, close, and reopen BossLabs; verify no stale listener/window behavior.
- [ ] Inspect a safe normal Matrix3 NPC and a BossLabs NPC.
- [ ] Run the high-value BossLabs workflow checks listed under Testing below.
- [ ] Run required `docs/rs3/SMOKE_TEST.md` coverage after the drop startup/persistence change.

#### Bundle V2.1-B - Creator-state invariant repair

**Purpose:** Repair the runtime-proven ghost-DRAFT/empty-workspace failure before resuming the larger verification session.

**Status:** NEEDS TEST

**Runtime evidence:**

- User video on 2026-09-06 showed no selected NPC while the shell displayed editable boss/phase/attack workflow and `DRAFT: modified`; actions appeared to do nothing because the internal definition editor had no loaded DRAFT.

**Checklist / patches:**

- [x] Record runtime failure as a V2.1/V2.2 gate blocker.
- [x] Enforce `no inspected NPC -> no editable DRAFT` at BossLabsPanel.
- [x] Lock dependent tabs and Boss-name editing while no NPC is selected or inspection is loading.
- [x] Clear the previous DRAFT/inspection state before requesting another NPC.
- [x] Ignore stale inspection replies that do not match the currently requested NPC.
- [x] Restore a clean locked state after invalid/missing NPC inspection.
- [x] Add explicit visible workspace guidance and document the safe first-phase 100% -> 0% default.
- [x] Add focused creator-state regression checks to `ux-testlist.txt`.
- [ ] Eclipse Clean/build Client with Java 8.
- [ ] Repeat the exact no-NPC -> load NPC -> add phase -> add attack sequence from the failing video.
- [ ] Confirm invalid Starts at HP 1 / Ends at HP 2 produces the existing inline range error and does not mutate the phase.

### Phase V2.2 - Phases and attacks workflow

**Purpose:** Make multi-phase attack creation/tuning/testing usable without memorizing internal IDs.

**Status:** NEEDS TEST

**Gate note:** Implementation landed before V2.1 runtime verification. It may be tested in the same consolidated session, but it is not considered complete until V2.1 passes first. The first V2.2 runtime attempt was blocked by the V2.1 ghost-DRAFT state and must be retried after Bundle V2.1-B passes.

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

**Status:** NEEDS TEST / PARTIAL IMPLEMENTATION LANDED AHEAD OF GATE

#### Bundle V2.4-A - Relative Attack Pattern workspace

**Status:** NEEDS TEST

- [x] Large phase/attack-aware relative pattern workspace.
- [x] Presets, copy/paste, zoom/pan, drag paint/erase.
- [x] Undo and transform tooling.
- [ ] Runtime/UI verification of transform directions and save/reload fidelity.

#### Bundle V2.4-B - Matrix3-native Drops

**Status:** NEEDS TEST

- [x] Existing Matrix3 table inspection.
- [x] Item ID/name search through existing Client Console item definitions.
- [x] Matrix3 rarity buckets and quantity ranges.
- [x] Rare-drop-table toggle.
- [x] Apply Live / Save & Apply / Apply Saved / Undo / Restore Matrix3 / Delete Saved Override.
- [x] Independent atomic `drops.bld` persistence.
- [x] Rare/Very Rare gear-split readback.
- [x] Preserve duplicate slots for Matrix3 selection weighting and repeated Always entries.
- [ ] Java 8 compile/runtime verification.
- [ ] Save/restart/reapply/restore verification.
- [ ] Normal Matrix3 NPC drop regression verification.

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

- **Phase:** V2.1 - Shell and composition cleanup
- **Phase status:** NEEDS TEST
- **Bundle:** V2.1-B - Creator-state invariant repair
- **Bundle status:** NEEDS TEST
- **Approval state:** SAP AAA approved for the creator-state regression repair; implementation/docs are complete statically and runtime verification is pending.
- **Current checklist item:** Pull current `main`, Eclipse Clean/build Client, then repeat the exact no-NPC -> load NPC -> phase -> attack workflow from the failing video.
- **Current objective:** Prove the creator workspace can never expose a ghost/stale DRAFT before resuming the broader BossLabs verification session.

## Checklist / patch status

| Item | Phase | Bundle | Status | Notes |
| --- | --- | --- | --- | --- |
| Creator shell/direct composition | V2.1 | V2.1-B | NEEDS TEST | Runtime ghost-DRAFT bug found; shell-boundary repair landed and needs exact-sequence retest. |
| Phase/attack creator workflow | V2.2 | V2.2 | NEEDS TEST | First runtime attempt was blocked by the V2.1 empty-workspace bug; retry after V2.1-B passes. |
| Context-aware Testing workflow | V2.2 | V2.2 | NEEDS TEST | Includes plain Matrix3 spawn/reset/HP and BossLabs-specific controls. |
| Attack Pattern transforms/undo | V2.2/V2.4 | V2.4-A | NEEDS TEST | Visual direction and persistence check pending. |
| Matrix3-native Drops | V2.4 | V2.4-B | NEEDS TEST | Full implementation present; startup/live/save/restore/drop-generation tests pending. |
| Duplicate drop slot weighting correction | V2.4 | V2.4-B | NEEDS TEST | Static semantics corrected client/server/UI; runtime round-trip pending. |
| Asset workflow | V2.3 | V2.3-A | READY | Next implementation phase only after V2.1/V2.2 runtime gates pass. |
| True Arena Layout | V2.4 | V2.4-C | CARRYOVER | Build only when first boss proves fixed anchors/bounds are required. |
| First complete boss | V2.5 | V2.5 | READY after dependencies | Generic proof boss; not Volcanic-Warden-specific. |

## Decisions / new ideas

### Decision log

- BossLabs is a **generic tool first**. Reference bosses/examples must never become hardcoded requirements.
- Volcanic Warden is reference/proof material only; the actual first proof boss is chosen later.
- Larger coherent BossLabs feature packs are acceptable when they preserve ownership, rollback points, narrow logical commits, and one consolidated runtime test session.
- Runtime testing time is scarce; batch compatible checks rather than requiring repeated pull/restart cycles.
- Content drives future tooling. After the current runtime gate, finish the missing asset workflow, then move toward a complete boss instead of endlessly expanding editor features.
- Matrix3 drop rarity semantics are bucket-based. Do not invent unsupported per-item percentages.
- Duplicate drop entries are meaningful Matrix3 data and must round-trip faithfully.
- True Arena Layout is deferred until real encounter content requires fixed encounter-space semantics.
- Creator-state invariant: **no successfully inspected NPC means no editable BossLabs DRAFT**. Empty/loading/missing states must be explicit and non-interactive rather than relying on individual controls to silently no-op.

## Testing

### Immediate creator-state retest

1. `git pull origin main`.
2. Eclipse Clean/build **Client** with Java 8.
3. Open BossLabs and do not select an NPC. Confirm `DRAFT: none / LIVE: none / SAVED: none`, Boss name is disabled, and every dependent tab is locked.
4. Enter a safe NPC ID/name and Load. Confirm BossLabs returns/stays on Overview, clears any prior boss values, shows loading state, and keeps authoring locked until inspection completes.
5. After inspection, confirm the workspace explicitly unlocks, DRAFT is clean, Boss name is editable, and Phases becomes available.
6. Add the first phase. Confirm it starts at 100% and ends at 0%.
7. Change it to Starts at HP 1 / Ends at HP 2 and Save Phase. Confirm the phase is not changed and the inline phase status explains the invalid range.
8. Restore a valid range, save it, open Attacks, add an attack, and confirm the attack is selected with safe defaults instead of silently doing nothing.
9. Optional stale-response check: request NPC A then NPC B quickly and confirm A cannot replace B if its reply arrives later.
10. Enter an invalid NPC ID and confirm the tool returns to the clean locked no-NPC state.

### Quick/high-value consolidated session after the creator-state retest passes

1. Start Server + Client and verify normal login/Client Console behavior.
2. Open BossLabs, inspect a safe ordinary Matrix3 NPC, close/reopen BossLabs, and inspect again.
3. Confirm plain Matrix3 Testing allows Spawn Boss Here, Reset Encounter, and Set HP; BossLabs-only controls remain disabled until a live BossLabs definition exists.
4. Create a simple valid phase + attack, Apply Live, spawn the controlled test boss, enter the selected phase, and test the selected attack.
5. Exercise a patterned attack: drag-paint/erase, Undo, rotate, mirror, nudge, Apply Live, and confirm the intended visual direction/tiles.
6. Exercise one telegraph/hazard and one minion action; use Clear Hazards + Minions and Reset Encounter.
7. In Drops, load an NPC with an existing Matrix3 table and verify entries/rarities/amounts load.
8. Add the same item twice to one rolled rarity bucket and verify both rows remain; Apply Drops Live and reload current state to confirm both slots survive.
9. Test Drops Undo and Restore Matrix3 on that NPC.
10. Save & Apply a safe BossLabs boss/drop override, restart the server, and verify both saved boss content and saved drop override reload.
11. Confirm an unrelated normal Matrix3 NPC still uses its original combat and drop behavior.

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

### BLOCKED

- Feature progression into V2.3 is blocked until the creator-state repair and V2.1/V2.2 runtime gates pass.
- No known server/runtime code blocker is introduced by the current repair; it is client-only and awaiting user verification.

## Resume Here

**Last completed:**

- Patched the runtime-proven BossLabs ghost-DRAFT/empty-workspace failure at the client shell boundary.
- Added explicit empty/loading/loaded creator states, stale-inspection rejection, clean missing-NPC recovery, focused UX regression checks, and this workstream-state update.

**Current phase:**

- V2.1 - Shell and composition cleanup (`NEEDS TEST`).

**Active bundle:**

- V2.1-B - Creator-state invariant repair (`NEEDS TEST`).

**Next checklist item:**

- User pulls current `main`, Eclipse Clean/builds Client, and repeats the exact no-NPC -> load NPC -> phase -> attack flow from the 2026-09-06 failing video.

**Current state / next action:**

- Do **not** start V2.3 or another feature pack before this repair is runtime-proven.
- If the focused retest passes, continue the consolidated V2.1/V2.2/Testing/Pattern/Drops verification session.
- If another creator-state defect appears, fix it inside V2.1-B before testing deeper mechanics.
- After V2.1 and V2.2 pass in order, record any V2.4 results already gathered and start V2.3 Asset Workflow.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/bosslabs/PROJECT.md`
- `docs/bosslabs/BOSSLABS.md`
- `docs/bosslabs/ux-testlist.txt`
- `Client/src/main/java/game/console/bosslabs/BossLabsPanel.java`
- `Client/src/main/java/game/console/bosslabs/BossLabsDefinitionEditor.java`
- BossLabs Testing/Pattern/Drops panels and existing bridge ownership from prior work

**Do not re-scan without new evidence:**

- Matrix3 drop bucket semantics and drop-runtime authority.
- BossLabs drop persistence/bridge architecture.
- BossLabs combat authority boundaries.
- Existing definition wire/store v8 ownership.
- Existing Testing exact-instance ownership.
- The creator-state failure path established by the 2026-09-06 video and `BossLabsPanel` inspection.

**Pending runtime verification:**

- Java 8 Client compile for the creator-state repair.
- Empty/no-NPC workspace locking and DRAFT:none invariant.
- Loading-state clearing/locking and stale-inspection rejection.
- Valid NPC unlock and clean DRAFT state.
- First-phase 100% -> 0% default and invalid-range feedback.
- Add Attack gating/selection after a phase exists.
- BossLabs window lifecycle and direct composition.
- Testing spawn/reset/HP/selected phase/selected attack.
- Pattern transform visual directions and save/reload.
- Encounter hazard/minion cleanup.
- Boss definition persistence/restart.
- Drop live apply/rollback/restore/save/restart.
- Duplicate drop slot round-trip.
- Normal Matrix3 combat/drop regression.
- Full required smoke test after startup/drop persistence changes.

**Blockers:**

- Focused creator-state runtime verification.

**Important remaining uncertainty:**

- Whether the shell-boundary repair fully resolves the confusing no-op workflow seen in the video or exposes a second phase-editor-specific UX defect afterward.
- Exact best reuse path for animation/GFX/projectile selection until V2.3 narrow scan.
- Whether true Arena Layout is actually required by the first proof boss.

## Next recommended work

**Runtime-test Bundle V2.1-B using the exact failing creator sequence.**

After it passes, resume the consolidated V2.1/V2.2 verification. V2.3 Asset Workflow remains the next implementation phase only after those gates pass.
