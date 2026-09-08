# Client Atlas

## Goal

Build a persistent reverse-engineering system that can **systematically map the whole obfuscated 718+ Client over time** instead of repeatedly searching and guessing through decompiled source.

The primary workflow is assistant-driven semantic mapping:

```text
current compiled client
    -> static Atlas structure
    -> runtime evidence when needed
    -> semantic coverage state
    -> bounded mapping queue / structural bundle
    -> assistant investigation
    -> curated exact-ID evidence
    -> next unfinished bundle
```

The Client Console browser remains useful as a lookup/viewer/editor, but it is **not** the main mapping workflow and the user is not expected to manually reverse-engineer random obfuscated fields.

Client runtime behavior remains authoritative. Atlas must never invent semantics, rename obfuscated source, or silently promote guesses.

## Canonical Main-Goal Status

This table is the authority for user-facing Client Atlas status across chats.

| Main-goal area | Status |
| --- | --- |
| Static structural map | ✅ Complete |
| Runtime evidence/tracing | ✅ Complete |
| Persistent semantic knowledge | ✅ Complete |
| Semantic mapping/coverage engine | ✅ Complete |
| Assistant mapping queue/bundles | ✅ Complete |
| Atlas lookup/viewer | ⚠️ Needs runtime verification |
| Whole-client semantic coverage | 🟡 Foundation |

Checklist state below is the execution map. Do not derive replacement milestone rows from it.

## Verified baseline

- Phase 1 runtime gate: PASS.
- Phase 2 structural gate: `PHASE 2 STRUCTURAL CHECK: PASS`.
- Phase 2 final investigation gate: `PHASE 2 INVESTIGATION CHECK: PASS`.
- Verified current mapping baseline: 1221 compiled client classes / 33742 symbols / 325826 relationships.
- `symbols.jsonl` ~8.5 MiB / `relationships.jsonl` ~74.4 MiB at the verified structural baseline.
- Structural scan ~1.28 s.
- Investigation-index load ~946.649 ms / ~181.5 MiB approximate memory delta.
- Exact search ~0.588 ms / friendly search ~0.416 ms.
- Depth-2 verifier neighborhood: 28 nodes / 40 relationships.
- Domain candidates stay semantic `UNKNOWN` until evidence proves meaning; no automatic `LITERAL_ID` promotion.

Current Bundle 5A verified mapping fingerprint:

```text
41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26
```

Current generated Atlas data must always be rebuilt against the current compiled fingerprint before current-only search/correlation/mapping is accepted.

# Architecture / ownership

- Existing Matrix3 client runtime/cache/network/interface/input/definition systems remain authoritative.
- `game.atlas` owns generated structure, search, trace control/correlation, semantic evidence, mapping coverage, and mapping-queue planning.
- Client Console remains a UI consumer only.
- Original obfuscated identifiers remain primary IDs.
- JSONL remains the structural/evidence persistence format; no database is justified by current measurements.
- `.client-atlas/` remains Git-ignored local Atlas state and survives normal build cleaning.
- Java 8 / Eclipse remains the protected target.

Static/runtime path:

```text
compiled Matrix3 client
    -> AtlasScanner
        -> symbols.jsonl + relationships.jsonl + fingerprint
            -> AtlasInvestigationIndex
                -> search / relationship queries
                -> runtime correlation
```

Runtime evidence path:

```text
Matrix3 runtime / small existing bridge
    -> game.AtlasRuntimeBridge
        -> AtlasTraceRecorder
            -> bounded trace JSONL
                -> AtlasTraceCorrelationEngine
                    -> exact current Atlas symbols
```

Curated semantic knowledge path:

```text
exact current Atlas symbol ID
    -> AtlasEvidenceStore
        -> .client-atlas/evidence.jsonl
            -> VERIFIED / verified-static / HYPOTHESIS / UNKNOWN
            -> freshness/orphan evaluation
```

Semantic mapping path:

```text
AtlasInvestigationIndex + AtlasEvidenceStore
    -> AtlasMappingQueue
        -> per-symbol semantic coverage state
        -> owner connectivity graph
        -> deterministic bounded structural bundles
        -> next assistant mapping package
        -> .client-atlas/mapping-next.md
```

The queue is planning only. It cannot create semantic claims. It may prioritize unknown/stale/high-connectivity areas, but evidence classification remains human/assistant evidence-driven.

# Evidence and safety rules

- Exact obfuscated Atlas IDs remain primary; aliases never rename generated symbols.
- One curated record currently exists per exact subject ID.
- New/updated evidence requires a symbol present in a current `AtlasInvestigationIndex`.
- Each record stores the current Atlas fingerprint at edit time.
- Classifications are only `VERIFIED`, `verified-static`, `HYPOTHESIS`, or `UNKNOWN`.
- `VERIFIED` requires runtime confirmation.
- `verified-static` requires direct source/data proof.
- `HYPOTHESIS` is plausible but unproven.
- `UNKNOWN` remains unknown.
- Stale fingerprints and missing subjects produce warnings instead of deletion or reclassification.
- Curated evidence never changes structural ranking.
- Normal static rescans reset generated symbols/relationships only; curated evidence survives.
- Tracing remains OFF by default, bounded to 10000 stored/read events, payload-minimal, and failure-isolated.
- Definition tracing remains capped at 4000 stored events with duplicate/category filtering counted as suppression rather than drops.
- Packet payload byte arrays, credentials, arbitrary chat/text strings, arbitrary object dumps, and stack traces are not captured.
- Runtime correlation never creates semantic claims.
- Mapping queue output is bounded and exact-ID based.
- Atlas-owned `game.atlas`, Client Console `game.console`, and Atlas runtime bridge classes are excluded from semantic self-mapping.

# Completed foundations

## Phase 1 - Static knowledge foundation - DONE

- Stable symbol IDs and metadata/fingerprint workspace.
- ASM declaration scanner.
- Exact query/export.
- Standalone Atlas Control.
- Runtime verification passed.

## Phase 2 - Structural relationships + investigation - DONE

- CALLS / DYNAMIC_CALL / field reads+writes / type references / typed constants.
- Immutable in-memory investigation index.
- Ranked/friendly search with ambiguity safety.
- Bounded relationship and depth-1/2 neighborhood queries.
- Assistant export v2.
- Safe domain candidate correlation.
- Final investigation runtime gate passed.

## Phase 3 - Runtime Evidence and Knowledge - DONE

### Bundle 3A - Targeted runtime tracing - DONE / RUNTIME VERIFIED

Verified runtime ownership/seams:

```text
keyboard semantics owner: Class549_Sub1.method8081(int,char,int,int)
menu dispatcher:          Class319.method4094(Class572_Sub12_Sub10,int,int,byte)
outgoing enqueue:         Class195.method2929(Class572_Sub25,byte)
incoming decoder owner:   PacketsDecoder.method3031(Class195,byte)
incoming safe wrapper:    MaterialInformation.method1605(Class195,int)
definition cache miss:    Class639.method7568(int,int)
```

Accepted decompiled runtime edits remain surgical:

- `Class195.java`: +1 line / 0 deletions.
- `MaterialInformation.java`: +2 lines / 0 deletions.
- `Class639.java`: +1 line / 0 deletions.
- A direct `Class549_Sub1` rewrite was rejected/rolled back after unrelated decompiler churn.

Corrected runtime gate evidence:

- 6050 stored events.
- 0 hard drops.
- 6617488 intentionally suppressed noisy events.
- 4000 definition events at the category cap.
- 1267 network / 631 interface / 152 input.
- 86 keyboard / 66 menu events.
- `Correlate Latest Trace`: `Status: CURRENT`, `Accepted: true`.
- Same-launch Matrix3 smoke: PASS by user report.

### Bundle 3B - Persistent curated evidence - DONE / OFFLINE VERIFIED

- `AtlasEvidenceStore` persists aliases, claims, evidence classification, references, and fingerprints.
- Stale/orphan evidence is retained and explicitly warned.
- Curated evidence survives static rescans.
- Curated search remains separate from structural ranking.
- `AtlasEvidenceVerifier` final result: `BUNDLE 3B KNOWLEDGE CHECK: PASS`.

# Phase 4 - Atlas Lookup / Viewer

**Status: IMPLEMENTED / ACCEPTANCE DEFERRED BY EXPLICIT PRIORITY CHANGE**

The user clarified that the Browser is not the primary goal. It remains useful as a viewer/editor for already-known Atlas data, but semantic mapping now has priority.

## Bundle 4A - Browser foundation - IMPLEMENTED / NEEDS COMBINED RUNTIME GATE

- Lazy Client Console Atlas destination.
- Off-EDT current-index loading and structural search.
- Exact symbol details.
- Bounded outgoing/incoming relationship navigation.
- Curated evidence editor over `AtlasEvidenceStore`.
- Exact Atlas IDs remain visible and authoritative.
- Human-readable presentation layer added after the first live view proved raw Atlas terminology was too difficult for practical use.
- `What Atlas knows`, readable connection names, `[open]` vs `[info only]`, and readable evidence confidence labels are presentation only; semantics remain unchanged.

## Bundle 4B - Runtime evidence viewer - IMPLEMENTED / NEEDS COMBINED RUNTIME GATE

- Saved trace catalog capped to newest 100 traces.
- Existing Runtime Trace Control surfaced without duplicate recording ownership.
- Selected/latest trace correlation reuses `AtlasTraceCorrelationEngine` unchanged.
- Listing/correlation stays off the Swing EDT.

## Phase 4 acceptance - DEFERRED

The combined Browser + Runtime evidence gate is still required before Phase 4 can be marked DONE, but it is intentionally deferred and does **not** block offline semantic mapping work. When resumed, use the existing consolidated checklist in `docs/client-atlas/testlist.txt`; do not split it into small per-control cycles.

# Phase 5 - Whole-Client Semantic Mapping

**Status: ACTIVE**

## Bundle 5A - Semantic coverage + mapping queue

**Status: DONE / OFFLINE VERIFIED**

- [x] Add `AtlasMappingQueue` over the current investigation index + curated evidence.
- [x] Track every scoped symbol as current runtime-verified, static-verified, hypothesis, unknown, or stale.
- [x] Track orphan curated records separately.
- [x] Build an owner-level structural connectivity graph from exact symbol-backed relationships.
- [x] Deterministically partition scoped owners into bounded investigation bundles.
- [x] Cap bundles at 8 owners.
- [x] Prioritize stale/hypothesis/unknown areas plus structural connectivity.
- [x] Exclude Atlas/Client Console tooling from semantic self-mapping.
- [x] Export the next bundle with at most 80 unresolved priority symbols, 12 boundary owners, and 40 existing evidence summaries.
- [x] Export exact IDs + source paths where available to `.client-atlas/mapping-next.md`.
- [x] Tell the assistant to use static evidence first and request runtime evidence only when necessary.
- [x] Add `AtlasMappingVerifier` as one consolidated offline gate.
- [x] Eclipse Java 8 clean/build + `AtlasMappingVerifier` PASS.

### Bundle 5A consolidated offline gate - PASS

User-reported verifier result on 2026-09-08:

- Scan classes: 1221.
- Atlas symbols: 33742.
- Atlas relationships: 325826.
- Mapping fingerprint: `41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26`.
- Coverage partition: PASS.
- Structural owner/symbol partition: PASS.
- Bundle bounds: PASS.
- Atlas/Client Console self-exclusion: PASS.
- Deterministic queue ordering/clustering: PASS.
- Exact-ID assistant export <=256 KiB: PASS.

Coverage snapshot at first verified mapping queue run:

```text
Scoped symbols:    33742
Verified runtime:      0
Verified static:       0
Hypotheses:            0
Stale:                 0
Unknown:           33742
Orphan evidence:       0
Owners:             1221
Bundles:             393
```

First generated structural bundle:

```text
MAP-0001
seed=game/Class574
owners=8
symbols=2298
```

`mapping-next.md` remains bounded to the selected priority exact-symbol subset even when the structural owner cluster contains more symbols.

Verifier output files:

```text
Client/.client-atlas/mapping-check.txt
Client/.client-atlas/mapping-next.md
```

No Matrix3 client launch was required for 5A.

## Bundle 5B - Assistant semantic writeback + scalable knowledge store - ACTIVE / NEXT

Purpose: make whole-client mapping practical at bundle scale instead of manually saving one record at a time.

- [ ] Add validated batch evidence upsert/import keyed by exact Atlas IDs.
- [ ] Increase/rework the current 5000-record curated evidence bound because whole-client exact-symbol mapping can exceed 33000 symbols; remain explicitly bounded rather than unlimited.
- [ ] Avoid O(n^2)-style whole-file rewrite behavior when applying large mapping bundles.
- [ ] Produce a deterministic semantic snapshot/export suitable for repository/cross-chat persistence without making generated static data authoritative semantics.
- [ ] Reject unknown IDs, invalid classifications, stale fingerprints, duplicate subjects, and unproven automatic promotions.
- [ ] Let an assistant mapping bundle return many proven/hypothesis records in one validated writeback step.
- [ ] Add one consolidated offline verifier for batch apply/capacity/fingerprint/duplicate/atomicity behavior.

## Bundle 5C - Runtime-targeted mapping assistance - PLANNED

- [ ] Use saved trace/correlation data to identify which unresolved exact symbols actually participated in a behavior being investigated.
- [ ] Suggest runtime targets without auto-promoting semantics.
- [ ] Preserve existing packet/input/interface/definition safety limits.
- [ ] Add component/model/animation-specific tracing only after exact ownership is established and only when current evidence proves the need.

## Bundle 5D - Coverage/progress presentation - PLANNED

- [ ] Surface mapping coverage and next bundle in a simple viewer/control surface after the queue/writeback workflow proves useful.
- [ ] Browser remains lookup-first; do not return to manual field-by-field reverse engineering as the primary workflow.
- [ ] Add navigation/history/graph polish only when it measurably speeds mapping work.

# Phase 6 - Advanced Correlation / Revision Intelligence - BACKLOG

- [ ] Repeated-path clustering informed by semantic mappings.
- [ ] Suggested aliases remain `HYPOTHESIS` until proven.
- [ ] Reliable cache/definition crosslinks.
- [ ] Revision/fingerprint semantic diffs.
- [ ] Investigation report generation.

# Current Atlas files

Core/runtime tracing:

```text
Client/src/main/java/game/AtlasRuntimeBridge.java
Client/src/main/java/game/AtlasKeyboardObserver.java
Client/src/main/java/game/atlas/AtlasTraceControl.java
Client/src/main/java/game/atlas/AtlasTraceRecorder.java
Client/src/main/java/game/atlas/ClientAtlasTraceControl.java
```

Static/search/correlation:

```text
Client/src/main/java/game/atlas/AtlasScanner.java
Client/src/main/java/game/atlas/AtlasInvestigationIndex.java
Client/src/main/java/game/atlas/AtlasSearchEngine.java
Client/src/main/java/game/atlas/AtlasAssistantExportEngine.java
Client/src/main/java/game/atlas/AtlasTraceCorrelationEngine.java
Client/src/main/java/game/atlas/AtlasTraceCatalog.java
Client/src/main/java/game/atlas/ClientAtlasMain.java
Client/src/main/java/game/atlas/ClientAtlasControl.java
```

Curated semantic knowledge:

```text
Client/src/main/java/game/atlas/AtlasSchema.java
Client/src/main/java/game/atlas/AtlasJson.java
Client/src/main/java/game/atlas/AtlasEvidenceStore.java
Client/src/main/java/game/atlas/AtlasEvidenceVerifier.java
Client/src/main/java/game/atlas/AtlasWorkspace.java
```

Semantic mapping:

```text
Client/src/main/java/game/atlas/AtlasMappingQueue.java
Client/src/main/java/game/atlas/AtlasMappingVerifier.java
```

Client Console viewer:

```text
Client/src/main/java/game/console/AtlasPanel.java
Client/src/main/java/game/console/AtlasWorkspacePanel.java
Client/src/main/java/game/console/AtlasRuntimeEvidencePanel.java
Client/src/main/java/game/console/ClientConsoleShell.java
Client/src/main/java/game/console/ConsoleIcons.java
```

Small runtime seams:

```text
Client/src/main/java/game/DevDefinitionBridge.java
Client/src/main/java/game/DevModeBridge.java
Client/src/main/java/game/Class195.java
Client/src/main/java/game/MaterialInformation.java
Client/src/main/java/game/Class639.java
```

# Testing state

- Phase 1: runtime verified.
- Phase 2: runtime verified.
- Bundle 3A tracing/correlation: runtime verified, smoke PASS.
- Bundle 3B evidence/knowledge: offline verifier PASS.
- Phase 4 Browser/Runtime viewer: implementation complete, final combined acceptance deferred by explicit priority change.
- Bundle 5A mapping queue: **DONE / OFFLINE VERIFIED** with `BUNDLE 5A MAPPING QUEUE CHECK: PASS`.
- First verified queue state: 33742 scoped symbols, 1221 owners, 393 deterministic bundles, all 33742 symbols initially UNKNOWN, first bundle `MAP-0001` seeded at `game/Class574`.
- No Phase 1/2/3 regression gate was required for 5A because it adds offline planning/export tooling only and does not modify runtime hooks or gameplay behavior.

# Carryover / blockers

## CARRYOVER

- Phase 4 combined Browser + Runtime evidence acceptance gate.
- Component-specific decoded interface payload values only if current packet metadata proves insufficient.
- Exact animation loader instrumentation after ownership is established.
- Exact model/cache loader instrumentation after ownership is established.
- Verify >200 streaming exact-query truncation when naturally encountered.

## BLOCKERS

- None for Bundle 5B implementation.

# Resume Here

**Last completed checkpoint:**

- Phases 1-3 foundations are verified.
- Phase 4 Browser + Runtime viewer implementation exists; its final combined acceptance is deferred because manual browsing is not the primary Atlas goal.
- Bundle 5A semantic coverage/queue is **DONE / OFFLINE VERIFIED**.
- `AtlasMappingVerifier` passed every queue/coverage/bounds/determinism/self-exclusion/export check.
- Initial queue snapshot contains 33742 UNKNOWN scoped symbols across 1221 owners and 393 bundles.
- First generated bundle is `MAP-0001`, seed `game/Class574`, 8 owners / 2298 total symbols; assistant export remains bounded to its priority exact-symbol subset.

**Current phase:**

- **Phase 5 - Whole-Client Semantic Mapping / ACTIVE**

**Active/next bundle:**

- **Bundle 5B - Assistant semantic writeback + scalable knowledge store / NEXT**

**Current/next work:**

1. Build the full compatible 5B batch-write/capacity/snapshot/validation bundle before requesting another user test.
2. Keep exact Atlas IDs and current fingerprint as the writeback authority.
3. Preserve `VERIFIED` / `verified-static` / `HYPOTHESIS` / `UNKNOWN` discipline; no automatic promotion.
4. Add one consolidated offline 5B verifier rather than per-subitem tests.
5. After 5B passes, begin actual assistant investigation/writeback of `MAP-0001` and continue queue-first through the remaining bundles.

**Do not re-scan/re-discover without new evidence:**

- Phase 1/2 scanner/search architecture,
- resolved keyboard/menu/network/interface/Class639 runtime ownership,
- Bundle 3A runtime tracing/correlation gate,
- Bundle 3B evidence freshness/persistence architecture,
- Bundle 5A queue partitioning/bounds/self-exclusion behavior,
- Client Console Atlas shell/viewer seams,
- unrelated server/gameplay systems.

**Pending runtime/offline verification:**

- None for Bundle 5A.
- Phase 4 combined viewer runtime gate remains deferred and should be merged into a future Client Console acceptance session, not run now.
- Bundle 5B will define its own single consolidated offline gate after implementation.

# Next recommended work

**Implement the complete Bundle 5B scalable assistant semantic writeback bundle, then verify it once before beginning real `MAP-0001` semantic mapping.**
