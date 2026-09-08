# Matrix3 BossLabs Workstream

## Goal

Build BossLabs into a professional, creator-first Matrix3 boss-authoring and testing tool that can produce, test, iterate, persist, and reuse complete custom boss encounters without boss-specific hardcoding or duplicate engine ownership.

BossLabs is successful when a developer can select an NPC, author phases/attacks/mechanics/patterns/drops, test the encounter quickly, iterate live, and save finished content without needing to understand BossLabs Java internals, transport protocol, or storage format.

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

Read in this order when continuing BossLabs:

1. `AGENTS.md`
2. `docs/rs3/PROJECT.md`
3. **this file** - authoritative BossLabs execution map and Resume Here state
4. `docs/bosslabs/BOSSLABS.md` - creator/product/UX authority
5. `docs/bosslabs/LIVE_EDITING.md`
6. `docs/bosslabs/NPC_SEARCH.md`
7. `docs/client-console/CLIENT_CONSOLE.md`

If older BossLabs prose conflicts with the execution state below, this file controls until deliberately reconciled.

## Scope / ownership

### In scope

- Creator-first NPC selection and inspection.
- Immutable BossLabs encounter definitions.
- Phase, attack, rotation, targeting, telegraph, hazard, tile-effect, phase-action, and minion authoring already supported by runtime.
- Relative Attack Pattern authoring.
- Exact per-admin developer testing.
- Matrix3-native drop-table authoring.
- Asset selection/search/preview workflow by reusing existing specialist tools where practical.
- One complete custom boss as the primary proof, then a second smaller boss to prove reuse.
- True fixed Arena Layout only if real encounter content proves it is required.

### Out of scope unless real content proves a requirement

- Alternate combat/NPC/world/drop/scheduler engines.
- General-purpose scripting or giant node editor.
- General world/map editor.
- Duplicate animation/model/FX editors inside BossLabs.
- Boss-specific hardcoding merely to demonstrate the tool.

### Ownership

- **Matrix3 owns:** NPC lifecycle, targeting/pathing, combat scheduling/damage, world state, `WorldTasksManager`, death/XP, `NPCDrops`, `Drops.generateDrops()`, permissions, and cache/runtime authorities.
- **BossLabs owns:** creator UI, BossLabs content definitions, BossLabs live registry/rollback, exact developer test sessions, BossLabs-owned transient encounter tasks/minions, and BossLabs persistence files.
- Boss definitions remain `data/bosslabs/definitions.bld`, store/wire **v8**.
- Drop overrides remain `data/bosslabs/drops.bld`, store/wire **v1**.
- `data/npcs/packedDrops.d` is never rewritten by BossLabs.
- `Commands.java` is not a BossLabs integration target.

## Verified foundation

### VERIFIED

- BossLabs opens in the running client and can inspect normal NPCs.
- The creator-state ghost-DRAFT defect was observed at runtime, repaired, and the user subsequently reported the main creator workflow working except for the then-broken Drops tab.
- Matrix3-native Drops is runtime-proven end-to-end on `Man [1]`: editable DRAFT, item search, Add Drop, Apply Drops Live, real Matrix3 ground drop, Save & Apply across restart, and Restore Matrix3.
- The repaired Client + Server launched successfully during the Drops verification session.

### verified-static

- Numeric NPC search is ID; text search is name.
- BossLabs combat delegates through Matrix3 `CombatScriptsHandler`; unregistered NPCs keep Matrix3 Java/default combat.
- Attack runtime includes direct/projectile attacks, weighted rotation/cooldowns, current/random-nearby targeting, relative tile patterns, telegraphs, hazards, tile effects, phase actions, encounter context, and owned minions.
- Testing owns an exact per-admin spawned NPC instance rather than searching the world by NPC ID.
- Attack Pattern authoring edits the same v8 DRAFT offsets used by runtime.
- Pattern canvas now matches Matrix3 coordinates: `+X` right, `+Y` up, matching `WorldTile.transform(x, y, 0)`.
- Drops overlays `NPCDrops`; Matrix3 `Drops.generateDrops()` remains the only loot roller.
- Duplicate drop slots are preserved because repeated rolled-bucket slots can intentionally weight selection.
- Creator-state invariant is enforced: no successfully inspected NPC means no editable BossLabs DRAFT.
- Drops inspection has explicit loading/error states and bounded failure handling.
- A one-click **Run Prefab Self-Test** path is implemented in Testing.
- The prefab uses an exact disposable test NPC and an in-memory immutable BossDefinition that is passed directly to the real `BossCombatScript.executeAttackForTesting` implementation.
- The prefab does **not** register or save the temporary definition, change Drops, consume global BossLabs rollback history, or persist test content.
- The prefab executes eight deterministic checks: controlled spawn, prefab definition construction, HP/phase resolution, encounter context ownership, owned-task tracking, asymmetric tile-attack execution, encounter-owned cleanup, and global LIVE/SAVED/rollback isolation.
- Final cleanup is in a `finally` path and removes the exact controlled test session even after failure.

## Unknown / runtime verification needed

- One-click prefab self-test runtime result on the current Client + Server.
- Window close/reopen/listener lifecycle.
- Full authored phase/attack Apply Live and selected-phase/attack testing.
- Corrected Attack Pattern visual direction versus actual in-game tile placement and v8 save/reload.
- Telegraph/hazard timing and minion cleanup under real authored combat.
- Boss-definition Save & Apply / restart persistence in the current creator workflow.
- Normal unrelated Matrix3 combat/drop smoke regression after accumulated BossLabs changes.
- Optional deeper Drops controls: Undo, Apply Saved, Delete Saved Override, duplicate-slot runtime weighting, wearable Rare/Very Rare readback, and multi-NPC isolation.

## Development plan

### Phase V2.0 - Creator UX authority

**Status:** COMPLETE

Creator-first workflow, progressive disclosure, no-retyping-known-values rule, Attack Pattern/Arena separation, and context-aware testing are the product authority.

### Phase V2.1 - Shell and composition cleanup

**Status:** NEEDS TEST

#### Bundle V2.1-A - Consolidated runtime verification

**Status:** ACTIVE / NEEDS TEST

Checklist:

- [x] Creator shell/direct composition implemented.
- [x] Testing and Drops panels directly composed.
- [x] Creator-state invariant repair implemented.
- [x] Safe normal NPC runtime loading verified.
- [x] One-click prefab self-test implemented to reduce user test time.
- [ ] **Run `Testing -> Run Prefab Self-Test` on a safe NPC and obtain PASS 8/8.**
- [ ] Open/close/reopen BossLabs and confirm no stale-window/listener behavior.
- [ ] Inspect a live BossLabs NPC after applying a real test definition.
- [ ] Run remaining high-value authored-content checks below.
- [ ] Run required `docs/rs3/SMOKE_TEST.md` coverage.

#### Bundle V2.1-B - Creator-state invariant repair

**Status:** RUNTIME IMPROVED / NEEDS FINAL GATE

- [x] No inspected NPC -> no editable DRAFT.
- [x] Dependent authoring tabs lock while empty/loading.
- [x] Previous local definition clears before another NPC inspection.
- [x] Stale inspection replies are rejected by NPC id.
- [x] Invalid/missing inspection returns to a clean locked state.
- [x] Post-repair creator navigation works in user runtime testing.
- [ ] Recheck no-NPC locked state when convenient.
- [ ] Confirm invalid phase range `Starts 1 / Ends 2` is rejected inline.

### Phase V2.2 - Phases and attacks workflow

**Status:** NEEDS TEST

Implemented:

- Creator-facing phase/attack naming with internal IDs hidden from normal workflow.
- Progressive attack editor sections and safe defaults.
- Selection-aware Enter Selected Phase / Test Selected Attack.
- Dedicated Attack Pattern workspace with presets, copy/paste, drag paint/erase, undo, rotate/mirror/nudge, and geometry/timing summaries.
- Testing HP shortcuts and bounded result history.

Exit gate:

- Author a valid phase + attack, Apply Live, spawn controlled boss, enter selected phase, and test selected attack.
- Save & Apply / restart/reinspect the definition successfully.

### Phase V2.3 - Asset workflow

**Status:** PLANNED

**Entry condition:** required V2.1/V2.2 gates pass unless explicitly reprioritized.

#### Bundle V2.3-A - Shared asset selection

- [ ] Narrow-scan existing AnimLab/FX/cache/client selector APIs.
- [ ] Reuse existing asset indexes rather than building duplicate ones.
- [ ] Add direct-ID + searchable selection for high-value animation/GFX/projectile fields.
- [ ] Add preview/open-specialist-tool actions where architecture safely supports them.
- [ ] Keep raw numeric IDs as the power-user shortcut.
- [ ] Keep indexing/search off the Swing EDT.

### Phase V2.4 - Arena and Drops

**Status:** NEEDS TEST / DROPS CORE COMPLETE

#### V2.4-A - Relative Attack Pattern workspace

**Status:** NEEDS TEST

- [x] Large relative pattern workspace.
- [x] Presets/copy-paste/zoom-pan/drag paint-erase.
- [x] Undo and transform tooling.
- [x] Static canvas/world coordinate repair: +X right, +Y up.
- [ ] Runtime direction, in-game placement, and v8 persistence verification.

#### V2.4-B - Matrix3-native Drops

**Status:** COMPLETE / CORE RUNTIME VERIFIED

- [x] Existing Matrix3 table inspection/editing.
- [x] Item ID/name search.
- [x] Matrix3 rarity buckets/quantity ranges/RDT toggle.
- [x] Apply Live / Save & Apply / Apply Saved / Undo / Restore / Delete Saved implementation.
- [x] Independent `drops.bld` persistence.
- [x] Legacy malformed-row repair workflow.
- [x] Runtime Add Drop / Apply Live / actual ground drop.
- [x] Runtime Save & Apply / restart persistence / Restore Matrix3.
- [ ] Optional deeper acceptance remains non-blocking.

#### V2.4-C - True Arena Layout

**Status:** CARRYOVER

Do not invent fixed encounter-space semantics until the first real boss proves bounds/anchors/spawn positions are required.

### Phase V2.5 - First complete boss proof

**Status:** PLANNED

Build, test, kill, reset, tune, save, reload, and reward one complete generic custom boss through BossLabs. `Volcanic Warden` is reference material only, not a hardcoded target.

### Phase V2.6 - Second boss reuse proof

**Status:** PLANNED

Build a smaller second boss using the proven framework to verify BossLabs is genuinely reusable.

## Prefab self-test policy

The BossLabs prefab is the first implementation of the project-wide prefab/self-test discipline now recorded in `AGENTS.md`.

Fast path:

1. Pull and Clean/build Client + Server.
2. Load a safe ordinary NPC such as `Man [1]`.
3. Open Testing.
4. Click **Run Prefab Self-Test** once.
5. Treat `Prefab self-test PASS 8/8` as the first-line confidence gate.

A PASS proves the eight deterministic runtime plumbing checks only. It does not replace manual UI lifecycle, visual pattern-orientation, persistence/restart, multiplayer, or required smoke testing.

The existing BossLabs command bridge caps encoded action text at 80 characters. This is acceptable for the prefab gate because the PASS/FAIL count and failing-step label are placed at the beginning of the response; optional descriptive text may truncate.

Focused acceptance: `docs/bosslabs/prefab-selftest-testlist.txt`.

## High-value manual checks after prefab PASS

Keep these short; only test what the prefab cannot prove:

1. Close/reopen BossLabs and reload the NPC.
2. Create one valid phase + attack and Apply Live.
3. Spawn controlled boss -> Enter Selected Phase -> Test Selected Attack.
4. Paint an asymmetric pattern; confirm tile above target reports `Y +1`, then test Nudge/Rotate and actual in-game placement.
5. Exercise one telegraph/hazard and one minion action; Clear Hazards + Minions / Reset.
6. Save & Apply, restart/reinspect, and confirm definition/pattern persistence.
7. Confirm an unrelated normal Matrix3 NPC keeps original combat/drops.
8. Run required smoke coverage.

## Decisions

- BossLabs is generic first; reference bosses must not become framework hardcoding.
- Larger coherent patches are acceptable when ownership, rollback, commits, and consolidated testing remain clean.
- User runtime time is scarce; prefab/self-tests should absorb repeatable checks wherever practical.
- Prefab tests must use real owning APIs, disposable state, deterministic PASS/FAIL reporting, and guaranteed cleanup; they complement rather than replace smoke/manual acceptance.
- Content drives future tooling. After required gates, finish the asset workflow and move toward the first complete boss rather than endlessly expanding the editor.
- Matrix3 drop rarity semantics are bucket-based; do not invent unsupported per-item percentages.
- True Arena Layout remains deferred until content requires it.

## Carryover / blockers

### Carryover

- True fixed Arena Layout: revisit during first-boss work only if required.
- Drops invalid-row highlighting / Jump to Invalid: usability polish only.
- Optional deeper Drops acceptance.

### Blocker

- V2.3 feature progression remains blocked by required V2.1/V2.2 runtime verification and required Attack Pattern/smoke checks.

## Resume Here

**Last completed:**

- Matrix3-wide prefab/self-test engineering rule added to `AGENTS.md`.
- BossLabs one-click prefab self-test implemented and statically audited.
- Prefab uses an exact disposable test NPC, exact in-memory definition, real BossCombatScript attack path, harmless asymmetric pattern, encounter cleanup, and global-state isolation checks.
- Matrix3-native Drops remains runtime-proven complete.

**Current phase:** V2.1 - Shell and composition cleanup (`NEEDS TEST`).

**Active bundle:** V2.1-A - Consolidated runtime verification (`ACTIVE / NEEDS TEST`).

**Next checklist item:**

- **Run `Testing -> Run Prefab Self-Test` on a safe NPC. Expected first-line result: `Prefab self-test PASS 8/8`.**

**After prefab PASS:**

- Perform only the short manual checks the prefab cannot cover: window lifecycle, real authored definition Apply/Test, visual Attack Pattern placement/transforms, hazard/minion behavior, definition persistence, and smoke regression.
- Once V2.1/V2.2 and required V2.4-A checks pass, begin V2.3 Asset Workflow.

**Do not re-scan without new evidence:**

- Matrix3 drop rates/runtime ownership or BossLabs Drops core pipeline.
- BossLabs definition v8 ownership.
- Exact Testing NPC ownership.
- Attack Pattern coordinate ownership (`WorldTile.transform(x, y, 0)`; client +Y now renders up).
- Prefab isolation design unless runtime output contradicts it.

**Important uncertainty:**

- Runtime result of the new prefab self-test.
- Runtime correspondence between corrected pattern canvas and actual in-game placement.
- Exact best reuse path for animation/GFX/projectile selection in V2.3.
- Whether true Arena Layout is needed by the first proof boss.

## Next recommended work

**Run the one-click BossLabs prefab self-test. If it passes, finish the few manual checks the prefab cannot cover; then move to V2.3 Asset Workflow and the first complete boss.**
