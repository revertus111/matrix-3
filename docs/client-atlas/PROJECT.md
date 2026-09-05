# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future investigations start from saved structural/runtime evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is developer/reverse-engineering tooling. Client runtime behavior remains authoritative; Atlas records and correlates evidence without renaming obfuscated symbols or inventing semantics.

## Canonical Main-Goal Status

This table is the authority for user-facing Client Atlas status across chats.

| Main-goal area | Status |
| --- | --- |
| Static client knowledge foundation | ✅ Complete |
| Static relationship mapping | ✅ Complete |
| Fast investigation/search | ✅ Complete |
| Runtime evidence/tracing | 🔵 In Progress |
| Client Console Atlas browser | ❌ Not started |
| Advanced correlation/knowledge | ❌ Not started |

Checklist/phase state below is the execution map. Do not derive replacement milestone rows from it.

## Architecture / ownership

- Atlas is tooling, not gameplay/client authority.
- Existing client runtime/cache/network/render/interface/input/definition systems remain authoritative.
- Atlas owns generated metadata, indexes, search/correlation APIs, exports, future trace records, aliases, and evidence records.
- Original obfuscated class/field/method names remain primary IDs.
- Runtime instrumentation must be explicit, bounded, switchable, and targeted.
- Human UI calls shared Atlas APIs rather than duplicating scanner/query logic.
- JSONL remains persistence authority until measurements justify another store.

## Current implementation

Package:

```text
Client/src/main/java/game/atlas/
```

Entry points:

```text
game.atlas.ClientAtlasMain
game.atlas.ClientAtlasControl
```

Normal client startup remains:

```text
game.RS3Applet
```

Compiled scan input:

```text
Client/build/classes/java/main/
```

Atlas excludes `game/atlas/**` from fingerprinting/scanning.

Dependency:

```text
org.ow2.asm:asm:9.7.1
```

Java/Eclipse target remains Java 8. The prior missing-ASM Eclipse issue was resolved by **Gradle -> Refresh Gradle Project**.

## Persistence

```text
Client/.client-atlas/
    metadata.properties
    symbols.jsonl
    relationships.jsonl
    evidence.jsonl
    phase1-check.json
    phase2-structural-check.txt
    phase2-structural-query.json
    phase2-investigation-check.txt
    phase2-assistant-export-check.json
    exports/
    traces/
```

Rules:

- `.client-atlas/` is Git-ignored and survives normal build cleaning.
- Scans replace generated symbols/relationships but preserve evidence/traces.
- Schema/fingerprint mismatch makes generated data non-current.
- Scanner publishes through temporary files and checks fingerprint before/after scan.
- Query/export refuses stale generated data.
- SQLite/native persistence remains deferred.

# Evidence

## VERIFIED

### Phase 1 - Static Atlas Foundation - 2026-09-05

- Scanner completed against **1221** compiled client classes.
- **33742** symbols generated.
- Initial structural relationships: **34053**.
- Fingerprint: `41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26`.
- Persisted metadata reopened current.
- Standalone Client Atlas Control opened successfully.
- Phase 1 one-click check passed.
- Exact `CLASS:game/Class1` UI search/export passed.

Phase 1 is **DONE**.

### Phase 2 / Bundle 2A - Structural relationships - 2026-09-05

Runtime gate:

```text
PHASE 2 STRUCTURAL CHECK: PASS
```

Verified dataset:

- Schema: **2**.
- Classes: **1221**.
- Symbols: **33742**.
- Relationships: **325826**.
- Full structural scan: **~1.28 s**.
- `symbols.jsonl`: **~8.5 MiB**.
- `relationships.jsonl`: **~74.4 MiB**.
- Exact query regression passed.
- Generated `CALLS`, `READS_FIELD`, `WRITES_FIELD`, `REFERENCES_TYPE`, and typed `CONSTANT` coverage passed.
- No automatic `LITERAL_ID` promotion.

Decision: keep JSONL + in-memory investigation index; current size/performance does not justify SQLite/native persistence.

Bundle 2A is **DONE**.

### Phase 2 / Bundle 2B - Investigation/search - 2026-09-05

Initial runtime gate through 2B.3:

```text
PHASE 2 INVESTIGATION CHECK: PASS
```

Verified baseline:

- **33742** symbols / **325826** relationships.
- Investigation-index load: **~946.649 ms**.
- Approx used-memory delta: **~181.5 MiB**.
- Exact Class1 search: **~0.588 ms**.
- Friendly Class1 search: **~0.416 ms**.
- Ambiguous `<init>` stayed unresolved with **1294** candidates.
- Fuzzy `Clas` found **29421**, displayed cap **50**.
- `CALLS`, `CALLED_BY`, `READS_FIELD`, `WRITES_FIELD`, `REFERENCES_TYPE`, typed `CONSTANT` passed.
- Depth-2 neighborhood stayed bounded at **28 nodes / 40 relationships** under **100/500** caps.

Final combined 2B.4 + 2B.5 local gate was then run after the assistant-export/domain-correlation patches. The user reported:

```text
PHASE 2 INVESTIGATION CHECK: PASS
```

That final PASS runtime-confirms the updated verifier assertions for:

- assistant export metadata/source locations/caps,
- resolved search context,
- relationship-command export,
- ambiguous search remaining unresolved,
- atomic assistant verification export,
- numeric domain candidate lookup,
- `762:7` same-symbol constant co-occurrence,
- bounded domain candidate/relationship output,
- domain semantics remaining `UNKNOWN`,
- zero automatic `LITERAL_ID` promotion,
- assistant-domain export preserving UNKNOWN/no-promotion state.

Therefore **2B.4 and 2B.5 are runtime-verified and Phase 2 is DONE**.

## verified-static ownership facts

- `AtlasWorkspace` owns workspace/schema/current checks.
- `AtlasFingerprint` owns client-build fingerprinting and excludes Atlas classes.
- `AtlasScanner` owns static bytecode scanning.
- `AtlasQueryEngine` owns exact streaming query/export.
- `AtlasStructuralVerifier` owns Bundle 2A verification.
- `AtlasInvestigationIndex` owns immutable in-memory acceleration over current JSONL.
- `AtlasSearchEngine` owns ranked/friendly symbol resolution.
- `AtlasRelationshipQueryEngine` owns bounded relationship/neighborhood queries.
- `AtlasAssistantExportEngine` owns bounded machine-readable investigation packages.
- `AtlasDomainCorrelationEngine` owns safe domain-hint candidate correlation over typed constants only.
- `AtlasInvestigationVerifier` owns the consolidated Bundle 2B gate and does not rescan classes.
- `ClientAtlasControl` is the standalone human control surface over shared Atlas APIs.

# Static evidence model

## Symbol record

```text
id
kind
owner
name
descriptor
signature
compiledPath
sourcePath
access
```

## Relationship record

```text
fromId
type
target
sourcePath
sourceLine
opcode
occurrenceCount
detail
```

Structural relationship meanings:

- direct invocation -> `CALLS`
- invokedynamic -> `DYNAMIC_CALL`
- GETFIELD/GETSTATIC -> `READS_FIELD`
- PUTFIELD/PUTSTATIC -> `WRITES_FIELD`
- reliable JVM type evidence -> `REFERENCES_TYPE`
- raw literal -> typed `CONSTANT`

Raw constants never become interface/animation/model/opcode/etc. IDs merely because a domain query used that label.

# Phase 2 completed capabilities

## Investigation/search

Supported examples:

```text
Class387
Class387.method4844
CLASS:game/Class387
calls <symbol>
called-by <symbol>
reads <symbol>
written-by <field>
references <type>
constant 762
neighbors <symbol> depth=1
neighbors <symbol> depth=2
```

Safety:

- ambiguous candidates are surfaced, never silently selected,
- fuzzy results never auto-resolve,
- normal relationship results cap at **500** edges,
- neighborhoods cap at **100 nodes / 500 edges**,
- source path/line/opcode/occurrence evidence is preserved where available.

## Assistant export

Assistant JSON/export v2 supports plain searches, relationship commands, and domain queries.

Caps:

- **50** candidates,
- **200** relationships,
- **250** relevant symbols.

Resolved plain searches include bounded depth-1 context. Ambiguous searches do not traverse as resolved. Export includes schema/fingerprint/current-snapshot metadata and explicit truncation state.

## Safe domain correlation

Supported examples include:

```text
interface 762
component 7
762:7
animation 1234
model 5678
packet NPC_OP1
```

Rules:

- requested domain is a search hint only,
- single values search normalized typed constants,
- `762:7` uses same-source-symbol constant co-occurrence,
- candidate output caps at **50 symbols / 200 relationships**,
- result semantic status is `UNKNOWN`,
- `LITERAL_ID promoted: false`,
- queries do not write domain semantics into generated data.

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status: DONE**

- [x] 1A.1 Targeted implementation discovery.
- [x] 1A.2 Schema + persistence skeleton.
- [x] 1A.3 Bytecode scanner MVP.
- [x] 1A.4 Basic query/export CLI.
- [x] 1A.5 Standalone Atlas Control.
- [x] Phase 1 runtime gate.

## Phase 2 - Static Relationship and Investigation Map

**Status: DONE**

### Bundle 2A - Structural relationships - DONE

- [x] Relationship schema/source locators.
- [x] Calls + field access scanning.
- [x] Type references + constants.
- [x] Structural verification + size metrics.

### Bundle 2B - Investigation search - DONE

- [x] 2B.1 In-memory investigation index.
- [x] 2B.2 Ranked/friendly search.
- [x] 2B.3 Relationship queries + bounded neighborhoods.
- [x] 2B.1-2B.3 runtime gate.
- [x] 2B.4 Assistant-oriented export.
- [x] 2B.5 Safe initial domain correlation.
- [x] Final combined Bundle 2B runtime gate.

## Phase 3 - Runtime Evidence and Knowledge

**Status: ACTIVE**

### Bundle 3A - Targeted runtime tracing

**Status: ACTIVE**

- [ ] **3A.0 Targeted runtime-tracing architecture discovery** - identify the smallest safe existing menu/input/network/interface/definition hooks and trace storage path before instrumentation.
- [ ] **3A.1 Trace-session lifecycle** - start/stop/name/save, bounded buffers, failure isolation.
- [ ] **3A.2 Menu/input hooks**.
- [ ] **3A.3 Packet metadata hooks**.
- [ ] **3A.4 Interface/component hooks**.
- [ ] **3A.5 Definition/cache/model/animation/GFX hooks**.
- [ ] **3A.6 Correlate runtime events back to Atlas symbols**.
- [ ] **Bundle 3A runtime gate** - one short controlled trace proving tracing can be enabled, saved, correlated, and fully disabled.

### Bundle 3B - Evidence/knowledge

**Status: PLANNED**

- [ ] External aliases/notes.
- [ ] Evidence classification/supporting references.
- [ ] Fingerprint stale-evidence warnings.
- [ ] Preserve curated knowledge across rescans.

## Phase 4 - Client Console Atlas Browser

**Status: PLANNED**

- [ ] Search panel.
- [ ] Symbol detail/relationship navigation.
- [ ] Evidence/alias editor.
- [ ] Trace browser/controls.
- [ ] Optional bounded relationship graph.

## Phase 5 - Advanced Correlation

**Status: BACKLOG**

- [ ] Repeated-path clustering.
- [ ] Suggested aliases remain `HYPOTHESIS` until proven.
- [ ] Reliable cache/definition crosslinks.
- [ ] Revision/fingerprint diffs.
- [ ] Investigation report generation.

# Testing

- Phase 1: runtime-verified.
- Phase 2A: runtime-verified.
- Phase 2B final combined gate: runtime-verified by user-reported PASS on 2026-09-05.
- Do **not** request another Phase 2 search/structural gate without contradictory evidence or a relevant Phase 2 implementation change.
- Phase 3 tracing tests must be short, explicit, and action-scoped; no broad always-on trace test.

# Carryover / blockers

## CARRYOVER

- On a natural future client source change + rebuild boundary, confirm cached/streaming queries refuse stale generated data until rebuilt.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.
- Destructive schema-mismatch simulation remains unnecessary for normal gates; static guards exist.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- None.

# Resume Here

**Last completed checkpoint:**

- **Phase 2 / Bundle 2B final combined runtime gate - PASS. Phase 2 DONE.**

**Current phase:**

- **Phase 3 - Runtime Evidence and Knowledge / ACTIVE**

**Active bundle:**

- **Bundle 3A - Targeted runtime tracing / ACTIVE**

**Current/next checklist item:**

- **3A.0 Targeted runtime-tracing architecture discovery.**

**Verified dataset baseline:**

- 1221 class files
- 33742 symbols
- 325826 relationships
- ~8.5 MiB symbols JSONL
- ~74.4 MiB relationships JSONL
- ~1.28 s full structural scan
- ~946.649 ms investigation-index load
- ~181.5 MiB approximate load memory delta
- ~0.588 ms exact symbol search
- ~0.416 ms friendly symbol search
- depth-2 verifier neighborhood: 28 nodes / 40 relationships

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- unrelated server/gameplay systems,
- Phase 2 scanner/search architecture,
- Client Console internals before Phase 4.

**Phase 3 discovery boundary:**

Inspect only the smallest existing client paths needed to establish reusable runtime hooks for menu/input, packets, interfaces, and high-value definition/cache activity. Do not instrument broadly until 3A.0 establishes ownership and safety.

**Pending runtime verification:**

- None from Phase 2.
- Phase 3 verification begins only after a 3A implementation slice exists.

# Next recommended work

**3A.0 Targeted runtime-tracing architecture discovery.**
