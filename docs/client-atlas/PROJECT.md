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
- Atlas owns generated metadata, indexes, search/correlation APIs, exports, trace records, aliases, and evidence records.
- Original obfuscated class/field/method names remain primary IDs.
- Runtime instrumentation must be explicit, bounded, switchable, targeted, and failure-isolated.
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

Java/Eclipse target remains Java 8. ASM dependency remains `org.ow2.asm:asm:9.7.1`.

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
- Scanner checks fingerprint before/after scan.
- Query/export refuses stale generated data.
- `AtlasWorkspace.tracesDirectory()` is the trace storage authority for Phase 3.
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
- Phase 1 one-click check and exact Class1 UI search/export passed.

Phase 1 is **DONE**.

### Phase 2 - Static Relationship and Investigation Map - 2026-09-05

Structural gate:

```text
PHASE 2 STRUCTURAL CHECK: PASS
```

Investigation gate, including final 2B.4 assistant-export and 2B.5 safe-domain assertions:

```text
PHASE 2 INVESTIGATION CHECK: PASS
```

Verified baseline:

- Schema **2**.
- **1221** classes.
- **33742** symbols.
- **325826** relationships.
- Full structural scan **~1.28 s**.
- `symbols.jsonl` **~8.5 MiB**.
- `relationships.jsonl` **~74.4 MiB**.
- Investigation-index load **~946.649 ms**.
- Approx load memory delta **~181.5 MiB**.
- Exact search **~0.588 ms** / friendly search **~0.416 ms**.
- Depth-2 verifier neighborhood **28 nodes / 40 relationships** under **100/500** caps.
- CALLS/CALLED_BY/read/write/type/constant directions passed.
- Ambiguous and fuzzy search safety passed.
- Assistant export metadata/source/caps/atomic output passed.
- Safe domain correlation and `762:7` same-symbol co-occurrence passed.
- Domain semantics remained `UNKNOWN`; zero automatic `LITERAL_ID` promotion.

Phase 2 is **DONE**.

## verified-static ownership facts

- `AtlasWorkspace` owns workspace/schema/current checks and `.client-atlas/traces/`.
- `AtlasFingerprint` owns client-build fingerprinting and excludes Atlas classes.
- `AtlasScanner` owns static bytecode scanning.
- `AtlasQueryEngine` owns exact streaming query/export.
- `AtlasStructuralVerifier` owns Bundle 2A verification.
- `AtlasInvestigationIndex` owns immutable in-memory acceleration over current JSONL.
- `AtlasSearchEngine` owns ranked/friendly symbol resolution.
- `AtlasRelationshipQueryEngine` owns bounded relationship/neighborhood queries.
- `AtlasAssistantExportEngine` owns bounded machine-readable investigation packages.
- `AtlasDomainCorrelationEngine` owns safe domain-hint candidate correlation over typed constants only.
- `AtlasInvestigationVerifier` owns the consolidated Bundle 2B gate.
- `ClientAtlasControl` is the standalone human control surface over shared Atlas APIs.

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

Examples:

```text
interface 762
component 7
762:7
animation 1234
model 5678
packet NPC_OP1
```

Requested domains are hints only. Results remain `UNKNOWN` and never promote `LITERAL_ID` merely from a query label.

# Phase 3 runtime tracing architecture

## 3A.0 Targeted runtime-tracing architecture discovery - verified-static

Purpose: establish the smallest safe runtime observation seams before adding instrumentation. No runtime source was changed in 3A.0.

### Core ownership decision

Future runtime hooks use a **one-way `game.AtlasRuntimeBridge` seam** into a single recorder under `game.atlas`.

Why:

- several useful packet/runtime fields are package-private in `game`,
- the bridge can extract only neutral primitive metadata while runtime classes retain ownership,
- Atlas does not need reflection or broad access to obfuscated internals,
- each later hook can remain a tiny call site,
- trace calls can early-return immediately while tracing is disabled.

Planned ownership:

```text
obfuscated/runtime client class
    -> game.AtlasRuntimeBridge
        -> game.atlas.AtlasTraceRecorder
            -> bounded in-memory session
                -> atomic save to .client-atlas/traces/
```

`DevDefinitionBridge` remains separate developer-tool ownership. Atlas should use the same narrow-observer pattern rather than hijacking DevDefinitionBridge state.

### Trace-session safety contract

3A.1 and all later hooks must follow these rules:

- tracing is opt-in and inactive by default,
- inactive hook cost is a fast early return,
- no disk I/O on packet/input/interface/definition hot paths,
- event storage is bounded and exposes overflow/dropped-event count,
- trace failures must never alter or interrupt normal client behavior,
- do not record packet payload byte arrays by default,
- do not record credentials, arbitrary chat/text payloads, arbitrary object dumps, or stack traces by default,
- store compact neutral fields only,
- stop freezes a session; saving may perform disk I/O after the hot path,
- original obfuscated names and Atlas stable IDs remain correlation authority.

A trace event should stay compact: monotonic sequence, timestamp, category/event type, optional source Atlas symbol ID, optional thread metadata, and a small ordered set of primitive/string fields.

### Hook map

#### Input - verified-static

`Class549_Sub1.method8081(int,char,int,int)` is the central normalized keyboard/focus event queue point.

`keyPressed`, `keyReleased`, `keyTyped`, and focus loss flow into this method before events are queued. This is the preferred keyboard hook for **3A.2** rather than instrumenting the base game loop.

**UNKNOWN:** the final high-level mouse/menu action dispatcher was not established within the narrow 3A.0 inspection. Resolve that specific dispatcher during 3A.2; do not guess or broaden the current scan. This does not block 3A.1 lifecycle work.

#### Outgoing packets - verified-static

`Class195.method2929(Class572_Sub25,byte)` is the central outgoing packet enqueue point before network flush.

The queued node carries an `OutgoingPacket` and encoded length. The future package bridge can expose neutral fields such as packet/opcode ID, declared packet length, and encoded/enqueued length without copying packet payload bytes.

Do not instrument the repeated flush methods when the enqueue point already represents the logical send event.

#### Incoming packets - verified-static

`MaterialInformation.method1605(...)` delegates into `PacketsDecoder.method3031(Class195,byte)`.

`PacketsDecoder.method3031` resolves `Class195.currentPacket`, handles variable packet length, receives bytes, and then dispatches named packet cases. `IncomingPacket` exposes public `id` and `length` fields.

The preferred **3A.3** hook is after packet identity/final length are known and before per-packet handling. Record metadata only, not payload bytes.

#### Interface/component activity - verified-static

The incoming decoder already exposes high-value named interface events including:

- `ROOT_INTERFACE`
- `SET_INTERFACE`
- `CLOSE_INTERFACE`
- `MOVE_INTERFACE`
- `HIDE_INTERFACE_COMPONENT`
- `INTERFACE_SETTINGS`
- `ANIMATION_ON_INTERFACE`
- `SET_NPC_INTERFACE`
- `SET_PLAYER_INTERFACE`
- `SET_OBJECT_INTERFACE`
- model/item/NPC-on-component events where present.

`Class512.method6083(...)` is a generic/lazy InterfaceDefinitions lookup and is too hot/noisy to serve as the blanket interface activity hook.

For **3A.4**, use the packet metadata stream first; add tiny calls inside only named interface branches when component-specific values are needed.

#### Definition/cache activity - verified-static

`Class639.getDefinition(...)` checks its cache. On a miss, `Class639.method7568(int,...)` loads/decode/finalizes the definition.

`method7568` already calls:

```text
DevDefinitionBridge.observeDefinitionLoader(this, interface17)
```

This proves a narrow observer seam already works at the cache-miss definition boundary. For **3A.5**, a separate Atlas bridge call beside that observer is the preferred generic definition-load hook.

Record compact facts such as definition ID, concrete definition/loader category, and cache-miss/load event. Do not serialize entire definitions.

`Class639_Sub15` is a verified Class639-backed NPC definition loader path. Model/animation/GFX categories that are not Class639-backed should receive their own targeted loader hook only when 3A.5 reaches them; do not assume all asset types share this loader.

### Files inspected for 3A.0

- `Client/src/main/java/game/Class195.java`
- `Client/src/main/java/game/Class572_Sub25.java`
- `Client/src/main/java/game/OutgoingPacket.java`
- `Client/src/main/java/game/IncomingPacket.java`
- `Client/src/main/java/game/PacketsDecoder.java`
- `Client/src/main/java/game/MaterialInformation.java`
- `Client/src/main/java/game/Class549.java`
- `Client/src/main/java/game/Class549_Sub1.java`
- `Client/src/main/java/game/Class584.java`
- `Client/src/main/java/game/Class512.java`
- `Client/src/main/java/game/Class639.java`
- `Client/src/main/java/game/Class639_Sub15.java`
- `Client/src/main/java/game/DevDefinitionBridge.java`
- `Client/src/main/java/game/atlas/AtlasWorkspace.java`

Do not re-scan these ownership paths in 3A.1 unless implementation reveals contradictory evidence.

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status: DONE**

- [x] Foundation discovery/schema/scanner/query/control/runtime gate.

## Phase 2 - Static Relationship and Investigation Map

**Status: DONE**

- [x] Structural relationships and runtime gate.
- [x] Investigation index/search/relationship neighborhoods.
- [x] Assistant export.
- [x] Safe initial domain correlation.
- [x] Final combined runtime gate.

## Phase 3 - Runtime Evidence and Knowledge

**Status: ACTIVE**

### Bundle 3A - Targeted runtime tracing

**Status: ACTIVE**

- [x] **3A.0 Targeted runtime-tracing architecture discovery** - verified-static; architecture/hook map above.
- [ ] **3A.1 Trace-session lifecycle** - start/stop/name/save, bounded buffers, atomic trace persistence, status/snapshot API, failure isolation.
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
- Phase 2: runtime-verified.
- 3A.0 is static architecture discovery only; **no local runtime test is required**.
- Do not request another Phase 2 structural/search gate without contradictory evidence or a relevant Phase 2 implementation change.
- Phase 3 runtime tests must be short, explicit, action-scoped, and consolidated after useful implementation slices.

# Carryover / blockers

## CARRYOVER

- Resolve the exact high-level menu/mouse action dispatcher during 3A.2; current status `UNKNOWN`.
- On a natural client source change + rebuild boundary, confirm cached/streaming queries refuse stale generated data until rebuilt.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- None for 3A.1.

# Resume Here

**Last completed checkpoint:**

- **Phase 3 / Bundle 3A / 3A.0 Targeted runtime-tracing architecture discovery - DONE / verified-static.**

**Current phase:**

- **Phase 3 - Runtime Evidence and Knowledge / ACTIVE**

**Active bundle:**

- **Bundle 3A - Targeted runtime tracing / ACTIVE**

**Current/next checklist item:**

- **3A.1 Trace-session lifecycle.**

**3A.1 established implementation direction:**

- Create a single process-wide recorder under `game.atlas`.
- Create/use a one-way `game.AtlasRuntimeBridge` seam for later obfuscated runtime hooks.
- Implement start / stop / name / status-or-snapshot / save lifecycle.
- Use a bounded in-memory event buffer with explicit dropped-event count.
- Save atomically under `AtlasWorkspace.tracesDirectory()`.
- Keep recorder inactive by default and failure-isolated.
- Do not add input/packet/interface/definition hook calls yet unless required by the lifecycle itself; those remain their ordered checklist slices.

**Verified dataset baseline:**

- 1221 class files
- 33742 symbols
- 325826 relationships
- ~8.5 MiB symbols JSONL
- ~74.4 MiB relationships JSONL
- ~1.28 s full structural scan
- ~946.649 ms investigation-index load
- ~181.5 MiB approximate load memory delta

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- unrelated server/gameplay systems,
- Phase 2 scanner/search architecture,
- the 3A.0 packet/input/interface/definition ownership paths listed above,
- Client Console internals before Phase 4.

**Pending runtime verification:**

- None for 3A.0.
- Runtime verification begins after a useful tracing implementation slice exists; consolidate rather than testing every hook separately.

# Next recommended work

**3A.1 Trace-session lifecycle.**
