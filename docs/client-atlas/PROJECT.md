# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations can start from known evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. A Client Console browser may consume the same data later, but the Atlas data/search layer is the product.

## Scope

### In scope

- Persistent class/field/method/constructor symbol index.
- Inheritance and implemented-interface mapping.
- Static caller/callee, field read/write, type-reference, constant, and literal-ID relationships where reliably derivable.
- Search by original symbol, ID, alias, evidence note, domain, or related symbol.
- Targeted runtime trace sessions after the static foundation is proven.
- Evidence records using `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN`.
- External semantic aliases/notes without renaming original client symbols.
- Machine-readable exports intended for future assistant/code investigation.
- Incremental/stale-index detection.
- Later Client Console viewer over the same Atlas data.

### Out of scope

- Renaming obfuscated classes, fields, or methods.
- Pretending original Jagex names can be recovered when absent.
- Promoting guessed semantics to verified status.
- Replacing client runtime/system ownership with tooling.
- Always-on tracing that materially hurts client performance.
- Building the visual graph/UI before the static Atlas/search workflow proves useful.
- Scanning unrelated server systems unless a client finding specifically requires correlation.

## Architecture / ownership

- Atlas is developer/reverse-engineering tooling, not gameplay authority.
- Existing client runtime, cache, networking, renderer, interface, input, and definition systems remain authoritative.
- Atlas owns generated reverse-engineering metadata, search indexes, aliases, evidence, and trace-session records.
- Original obfuscated names remain permanent primary identifiers.
- Runtime instrumentation must be explicit, bounded, and switchable.
- UI code must consume Atlas data/APIs rather than duplicate discovery logic.

# Phase 1 implementation architecture

## Offline ownership

Source package:

```text
Client/src/main/java/game/atlas/
```

Entry point:

```text
game.atlas.ClientAtlasMain
```

Normal `game.RS3Applet` startup remains unchanged.

## Scan input

Primary input:

```text
Client/build/classes/java/main/
```

Atlas excludes its own compiled classes from fingerprinting/scanning:

```text
game/atlas/**
```

## Parser

- ASM core is used for bytecode declaration scanning.
- Pinned dependency: `org.ow2.asm:asm:9.7.1`.
- Phase 1 uses `ClassReader.SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES` so method bodies are deliberately not analyzed yet.
- Phase 2 owns callers/callees, field reads/writes, constants, literal IDs, and deeper structural relationships.

## Persistence

Local generated workspace:

```text
Client/.client-atlas/
    metadata.properties
    symbols.jsonl
    relationships.jsonl
    evidence.jsonl
    traces/
```

Rules:

- `.client-atlas/` is ignored by Git and survives normal `build/` cleaning.
- `metadata.properties` stores schema version, client fingerprint, scan root, timestamp, and record counts.
- `symbols.jsonl` stores one deterministic JSON object per symbol.
- `relationships.jsonl` stores one deterministic JSON object per relationship.
- Fresh scanning replaces generated symbol/relationship files but preserves evidence/traces.
- Scanner writes temporary files first and only publishes completed generated files.
- A before/after fingerprint check rejects a scan if compiled client classes change during the scan.
- SQLite/native persistence remains deferred until measured size/performance proves a need.

## Indexed symbol coverage

Phase 1 scanner records:

- classes,
- interfaces,
- enums,
- annotations,
- fields,
- methods,
- constructors,
- JVM descriptors/signatures,
- access flags,
- compiled source path,
- `EXTENDS`,
- `IMPLEMENTS`,
- `DECLARES` relationships.

Original JVM/internal names are preserved exactly.

## Exact query/export

Phase 1 provides exact canonical-ID investigation without a UI:

```text
game.atlas.ClientAtlasMain query "CLASS:game/Class1"
game.atlas.ClientAtlasMain query "METHOD:game/Class387#method4844(II)V"
game.atlas.ClientAtlasMain export "CLASS:game/Class1" docs/client-atlas/snapshots/class1.json
```

Rules:

- Phase 1 accepts the exact stable Atlas symbol ID; ranked/fuzzy/shorthand search belongs to Phase 2.
- Query/export reopens the generated index and validates the saved schema version.
- Query/export recomputes the current client fingerprint and refuses to return results from a stale index.
- Exact lookup streams `symbols.jsonl`; it does not build a second database or duplicate scanner ownership.
- Immediate stored relationships are included when `fromId` or `target` exactly matches the queried symbol ID.
- Relationship output is bounded to 200 records; the result includes the full matching count and `relationshipsTruncated` state.
- `query` writes compact machine-readable JSON to stdout.
- `export` atomically writes the same compact UTF-8 JSON result to the requested file.
- No extra JSON/database dependency is added for Phase 1 query/export.

## Assistant-visible knowledge

Use two layers:

1. **Local working Atlas** - full generated/indexed data under `Client/.client-atlas/`.
2. **Repository-visible knowledge/export** - compact durable exports under `docs/client-atlas/`.

Candidate durable paths:

```text
docs/client-atlas/knowledge/
docs/client-atlas/snapshots/
```

Do not commit a massive generated dump by default. Measure the first real index first.

# Phase 2 prepared architecture

Phase 2 implementation remains gated on the Phase 1 local verification. The design below was completed while that gate is pending so development can resume immediately once the foundation passes.

## Core rule: record facts, not guessed semantics

Phase 2 records what the JVM bytecode proves.

Examples:

- A method invocation can produce a `CALLS` edge.
- `GETFIELD`/`GETSTATIC` can produce `READS_FIELD`.
- `PUTFIELD`/`PUTSTATIC` can produce `WRITES_FIELD`.
- A numeric/string literal can produce a typed `CONSTANT`.
- A type instruction or descriptor can produce `REFERENCES_TYPE`.

A raw number such as `762` must **not** automatically become `interface 762`, `animation 762`, `opcode 762`, or any other semantic ID. `LITERAL_ID` remains reserved for later evidence-backed/domain-aware correlation.

## Scanner strategy

Do not create a second scanner subsystem. Extend `AtlasScanner` after the Phase 1 gate passes.

Phase 2 reader direction:

```text
ClassReader.SKIP_FRAMES
```

This keeps method code and debug/source information available while still avoiding frame-processing overhead.

Each visited method owns a small in-memory relationship accumulator. Repeated identical relationships inside one method should be compacted before writing rather than flooding JSONL with duplicate edges.

Recommended unique-edge key:

```text
fromId + relationshipType + target
```

Preserve an occurrence count and bounded source-line evidence for repeated bytecode occurrences.

## Relationship schema v2

Before the first Phase 2 generated index, evolve the relationship record rather than hiding machine-readable evidence in the old free-form `detail` string.

Planned relationship fields:

```text
fromId
type
target
sourcePath
sourceLine
opcode
occurrenceCount
detail (optional only for data that does not deserve a dedicated field)
```

Schema version should increment when this lands so old Phase 1 generated data is rejected and explicitly rescanned.

For symbols, separate the compiled class path from the real Java source path instead of using one ambiguous location field once source enrichment is added.

## Method calls

`visitMethodInsn` produces exact target IDs:

```text
CALLS -> METHOD:<owner>#<name><descriptor>
CALLS -> CONSTRUCTOR:<owner>#<init><descriptor>
```

The bytecode target owner/name/descriptor are facts and should be preserved exactly even when the target class is external to the scanned client.

`invokedynamic` must not be mislabeled as an ordinary direct call. If encountered, use an explicit dynamic-call relationship type and preserve its name/descriptor/bootstrap handle information separately.

## Field access

Opcode mapping:

```text
GETFIELD / GETSTATIC -> READS_FIELD
PUTFIELD / PUTSTATIC -> WRITES_FIELD
```

Targets use exact stable field IDs:

```text
FIELD:<owner>#<name>:<descriptor>
```

## Type references

Collect `REFERENCES_TYPE` from reliable bytecode/static structures including:

- field descriptors,
- method argument/return descriptors,
- declared exception types,
- class/method generic signatures where ASM can parse them reliably,
- `NEW`, `CHECKCAST`, `INSTANCEOF`, `ANEWARRAY`, `MULTIANEWARRAY`,
- method/field instruction owner and descriptors,
- `Type` values loaded through `LDC`,
- bootstrap handles/dynamic constants when present.

Array/descriptor parsing must resolve referenced object element types rather than treating raw JVM descriptor text as a semantic alias.

## Constants

Capture constants as typed values, not guessed IDs.

High-value sources:

- static/final field constant values,
- `LDC`,
- `ICONST_*`, `BIPUSH`, `SIPUSH`,
- `LCONST_*`, `FCONST_*`, `DCONST_*`.

Suggested target encoding:

```text
int:762
long:1234
float:1.0
string:Attack
```

Skip noisy local arithmetic such as ordinary `IINC` unless later investigations prove it useful.

`Type`, `Handle`, and dynamic-constant values should feed structural references rather than being flattened into misleading strings.

## Source-location enrichment

Use compiled debug/source data when available:

- `ClassVisitor.visitSource` for source file metadata.
- `MethodVisitor.visitLineNumber` for current Java line evidence.

Map internal owners back to the client source tree:

```text
Client/src/main/java/<owner>.java
```

For inner/anonymous classes, strip the `$...` suffix when resolving the owning Java source file.

Do not claim a source location is reliable if no matching source file/debug evidence exists; retain the compiled class path as fallback evidence.

## Phase 2 query/index direction

Do not commit to SQLite before the first real Phase 1 index is measured.

If generated size is reasonable, Phase 2 should build an on-demand in-memory `AtlasIndex` over JSONL with maps for:

- symbol ID -> symbol,
- owner/name -> candidate symbols,
- outgoing relationships,
- incoming relationships,
- typed constants -> referencing symbols.

Friendly searches may then support:

```text
Class387
Class387.method4844
calls <symbol>
called-by <symbol>
reads <field>
written-by <field>
references <type>
constant 762
```

Ambiguous shorthand must return/rank candidates; it must never silently choose the wrong overload.

## Bounded relationship neighborhoods

Assistant-oriented neighborhood export should be query-driven, not an uncontrolled graph dump.

Initial safety targets:

- default depth: 1,
- optional depth: 2,
- node cap: 100,
- edge cap: 500,
- always include fingerprint/schema/truncation state.

These caps are starting safeguards and may be adjusted only after real index measurements.

# Verified foundation

## VERIFIED

- None yet; Client Atlas has not received the consolidated local Java 8/Eclipse Phase 1 verification.

## verified-static

- Repository rules require original obfuscated names to be preserved unless explicit renaming is approved.
- Repository rules define `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` as reverse-engineering evidence labels.
- Client project root is `Client/` and source root is `Client/src/main/java/`.
- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as the normal main.
- Client Console remains separate under `game.console`.
- Atlas source exists under `game.atlas` and is offline-only.
- `AtlasSchema` already defines relationship categories for references, calls, field reads/writes, constants, and literal IDs.
- `AtlasFingerprint` provides deterministic client-class SHA-256 fingerprinting and excludes Atlas classes.
- `AtlasWorkspace` owns `.client-atlas` paths, metadata persistence/reopen, layout initialization, and stale/current checks.
- `AtlasScanner` currently scans compiled declarations while intentionally skipping method bodies.
- `AtlasQueryEngine` performs exact canonical-ID lookup, stale/schema validation, bounded immediate-relationship collection, and atomic compact export.
- `ClientAtlasMain` exposes `init`, `status`, `scan`, `query`, and `export` without changing normal client startup ownership.
- ASM visitor APIs already used by `AtlasScanner` provide the extension path required for method-instruction, field-instruction, type, constant, source-file, and line-number evidence without touching gameplay/runtime ownership.

# Unknown / research needed

## HYPOTHESIS

- Streaming exact lookup is sufficient for Phase 1; an in-memory ranked index will likely be useful for Phase 2 search/neighborhood work.
- Per-method relationship aggregation will keep Phase 2 JSONL compact enough without a database.
- A compact committed snapshot may be practical, but size must be measured after the first scan.

## UNKNOWN

- First real class/symbol/relationship counts and generated file sizes.
- Exact scan/query performance on the user's local client build.
- How much Phase 2 method-body relationships increase index size.
- How complete compiled debug/source-line data is for this decompiled client build.
- Whether full Git-visible static snapshots are small enough to commit cleanly.
- Whether meaningful `invokedynamic` usage exists in the compiled client.
- Safest high-level runtime hooks for Phase 3 tracing; intentionally deferred.

# Evidence model

Stable IDs retain original names, for example:

```text
CLASS:game/Class387
METHOD:game/Class387#method4844(II)V
FIELD:game/Class540#anInt7134:I
```

Relationship records contain a source symbol, relationship type, target and evidence location/occurrence metadata once schema v2 lands.

Aliases never replace original names.

# Search requirements

## Phase 1

Exact canonical IDs only:

```text
CLASS:game/Class387
METHOD:game/Class387#method4844(II)V
FIELD:game/Class540#anInt7134:I
```

This is intentional so the first query layer cannot silently choose the wrong overloaded/ambiguous symbol.

## Phase 2 target

Atlas should later support concise ranked searches such as:

```text
Class387
Class387.method4844
interface 762
component 7
762:7
packet NPC_OP1
opcode 9
animation 1234
model 5678
npc menu
projectile
writes Class540.anInt7134
calls <symbol>
called-by <symbol>
status VERIFIED
alias "NPC menu builder"
```

Semantic/domain searches such as `interface 762` must only become authoritative when evidence/domain correlation can distinguish them from a generic constant `762`.

Results should return a small ranked neighborhood rather than force inspection of the full index.

# Performance / safety rules

- Normal client startup/gameplay must not require an Atlas rebuild.
- Static indexing runs explicitly against compiled client inputs.
- Atlas failure must not prevent normal client operation.
- Query/export must refuse known-stale index data rather than presenting it as current evidence.
- Runtime tracing is opt-in and bounded.
- Expensive graph expansion is query-driven.
- Generated data must not pollute Git or normal build outputs.
- Static scanner work requires no login/game runtime.
- Phase 2 must aggregate/restrict relationship data enough to remain useful rather than generating an unreadable raw instruction dump.

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status:** NEEDS TEST

**Goal:** Produce the smallest useful offline machine-readable Atlas that can index the compiled client, reopen its data, search an exact symbol, and export a compact result.

### Bundle 1A - Implementation foundation

**Status:** NEEDS TEST

Checklist:

- [x] **1A.1 Targeted implementation discovery** - locate client build/source/tooling ownership and choose implementation path.
- [x] **1A.2 Atlas schema + persistence skeleton** - isolated Atlas package, stable records, fingerprint, local workspace, and metadata reopen/staleness foundation.
- [x] **1A.3 Bytecode scanner MVP** - ASM declaration scanner writes class/field/method/constructor symbols plus inheritance/interface/declaration relationships to JSONL.
- [x] **1A.4 Basic query/export CLI** - exact canonical-ID lookup plus compact bounded machine-readable query/export output with stale-index protection.

### Phase 1 gate

Implementation is complete. Phase 1 must not advance until the consolidated local verification passes:

- Java 8/Eclipse-compatible clean/build.
- Atlas runs without launching/logging into the client.
- Compiled client classes are indexed with non-zero counts.
- Index reopens without rebuild and reports a current fingerprint.
- Known class/field/method/constructor symbols exist.
- `EXTENDS`, `IMPLEMENTS` where applicable, and `DECLARES` relationships exist.
- Exact canonical class and method queries work.
- Compact export works and reopens without rescanning.
- Stale index is rejected after compiled client classes change.
- Normal `game.RS3Applet` launch ownership remains unchanged.

## Phase 2 - Static Relationship and Investigation Map

**Status:** PLANNED - ARCHITECTURE PREPARED, IMPLEMENTATION GATED ON PHASE 1 TEST

### Bundle 2A - Structural relationships

- [x] **2A.0 Targeted relationship architecture discovery** - inspected only `AtlasScanner`/`AtlasSchema`, defined evidence-safe bytecode mapping and schema/index direction; no Phase 2 source changes.
- [ ] **2A.1 Relationship schema v2 + source locator** - add typed occurrence/source fields, compiled/source path separation, dynamic-call relationship support, schema bump/rescan guard. **READY AFTER PHASE 1 GATE.**
- [ ] **2A.2 Calls + field access scanner** - method-body `CALLS`, `READS_FIELD`, and `WRITES_FIELD` with per-method dedup/occurrence aggregation.
- [ ] **2A.3 Type references + constants** - descriptors/signatures/type instructions plus typed constants; no automatic semantic ID classification.
- [ ] **2A.4 Structural verification + size metrics** - validate known call/field paths, measure generated counts/files, tune caps only from evidence.

### Bundle 2B - Investigation search

- [ ] **2B.1 In-memory investigation index** - exact/shorthand candidate maps plus incoming/outgoing relationship maps when Phase 1/2 measurements support it.
- [ ] **2B.2 Ranked/friendly search** - owner/member/text/constant resolution with ambiguity surfaced rather than silently selected.
- [ ] **2B.3 Relationship queries + bounded neighborhoods** - `calls`, `called-by`, `reads`, `written-by`, `references`, depth/cap controls.
- [ ] **2B.4 Assistant-oriented export** - compact static investigation package including fingerprint/schema/truncation and related symbols.
- [ ] **2B.5 Safe initial domain correlation** - only promote obvious interface/packet/cache/etc. semantics when context/evidence supports them; generic constants stay generic.

## Phase 3 - Runtime Evidence and Knowledge

**Status:** PLANNED

### Bundle 3A - Targeted trace sessions

- [ ] Start/stop/name/save trace lifecycle.
- [ ] High-value menu/input hooks.
- [ ] Packet metadata hooks where safe.
- [ ] Interface/component activity hooks.
- [ ] Definition/cache/model/animation/GFX hooks where useful.
- [ ] Correlate events back to Atlas symbols.

### Bundle 3B - Durable evidence

- [ ] External aliases/notes.
- [ ] Evidence classification/supporting references.
- [ ] Client-fingerprint stale-evidence warnings.
- [ ] Preserve curated knowledge across rescans.

## Phase 4 - Client Console Atlas Browser

**Status:** PLANNED

### Bundle 4A - Human browser

- [ ] Search panel.
- [ ] Symbol detail and relationship navigation.
- [ ] Evidence/alias editor.
- [ ] Trace-session controls/browser.
- [ ] Optional bounded graph for selected neighborhood only.

## Phase 5 - Advanced Correlation

**Status:** BACKLOG

### Bundle 5A - Usage-driven automation

- [ ] Repeated-path clustering.
- [ ] Suggested aliases kept as `HYPOTHESIS` until proven.
- [ ] Cross-link reliable cache/definition metadata.
- [ ] Atlas diff between client revisions/fingerprints.
- [ ] Investigation report generation.

# Current execution state

- Current gated phase: **Phase 1 - Static Atlas Foundation**
- Phase 1 bundle: **Bundle 1A - Implementation foundation**
- Phase 1 status: **NEEDS TEST**
- Last completed Phase 1 checklist item: **1A.4 Basic query/export CLI**
- Next required checkpoint: **Phase 1 consolidated verification gate**
- Approved side/prep work completed: **Phase 2 / Bundle 2A / 2A.0 Targeted relationship architecture discovery**
- Prepared post-gate implementation target: **2A.1 Relationship schema v2 + source locator**

## Status table

| Area | Status | Notes |
| --- | --- | --- |
| Targeted implementation discovery | DONE | Client/build/tooling path established. |
| Atlas schema + persistence skeleton | DONE | Offline foundation implemented. |
| Bytecode scanner MVP | DONE | ASM declaration scanner implemented. |
| Basic query/export CLI | DONE | Exact canonical-ID query/export with stale-index guard implemented. |
| Phase 1 verification gate | NEEDS TEST | One consolidated Java 8 build/scan/status/query/export session required. |
| Static relationship map | PREPARED | Phase 2 architecture/checklist is ready; implementation remains gated on Phase 1 verification. |
| Runtime traces/evidence | PLANNED | Phase 3. |
| Client Console browser | PLANNED | Phase 4. |
| Advanced correlation | CARRYOVER | Phase 5 usage-driven backlog. |

# Decisions / new ideas

- Client Atlas is primarily for future assistant/code investigation; human UI is secondary.
- The searchable data layer is authoritative for Atlas; Client Console is only a viewer/controller later.
- Preserve original obfuscated names permanently unless separately approved.
- Do not create one giant human text dump as the primary interface.
- Bytecode-first static evidence precedes runtime tracing.
- Runtime tracing remains short-lived and scoped.
- Automatically suggested semantics remain `HYPOTHESIS` until evidence supports promotion.
- Start with portable text persistence; do not add SQLite before measurement proves a need.
- Keep full local generated data separate from curated repository-visible knowledge/snapshots.
- ASM 9.7.1 is pinned for Phase 1 declaration scanning.
- Phase 1 deliberately skips method bodies; deeper relationships remain Phase 2 ownership.
- Phase 1 query uses canonical stable IDs only; friendly/fuzzy/ranked resolution is Phase 2 work.
- Exact query/export refuses stale client fingerprints.
- Immediate relationship output is capped at 200 while retaining the full matching count/truncation signal.
- No extra JSON dependency was added for query/export because deterministic Atlas JSONL can be streamed directly.
- Phase 2 static analysis records raw bytecode evidence before semantics.
- `LITERAL_ID` is not emitted just because an integer exists; domain meaning requires contextual evidence.
- Phase 2 should extend the existing scanner, not create a competing bytecode-analysis owner.
- Phase 2 relationship records should carry typed source/occurrence evidence instead of overloading free-form `detail`.
- Repeated method-level edges should aggregate occurrence information to control index noise.
- `invokedynamic` must remain distinct from proven direct method calls.

# Testing

## Consolidated Phase 1 quick/high-value checks

1. Clean/build the Client with the Java 8/Eclipse target.
2. Run `game.atlas.ClientAtlasMain scan` against the compiled class directory.
3. Confirm non-zero class, symbol, and relationship counts.
4. Run `game.atlas.ClientAtlasMain status` and confirm metadata reopens and fingerprint is current.
5. Confirm `symbols.jsonl` contains a known obfuscated class, method, field, and constructor.
6. Confirm `relationships.jsonl` contains `EXTENDS`, `IMPLEMENTS` where applicable, and `DECLARES` records.
7. Run `game.atlas.ClientAtlasMain query "CLASS:game/Class1"` and confirm compact JSON plus immediate relationships.
8. Copy one exact METHOD/CONSTRUCTOR ID from a class query's `DECLARES` records and query that exact ID.
9. Export a compact result and reopen/query again without rescanning.
10. Confirm stale client classes cause query/export to refuse the old index until rescanned.
11. Confirm normal `game.RS3Applet` main/configuration remains unchanged.

## Prepared Phase 2 checks

After the Phase 1 gate passes and each Phase 2 patch lands:

- Query one known method and verify a bytecode-proven outgoing `CALLS` edge.
- Query the target method and verify the corresponding incoming/called-by neighborhood.
- Verify one known `GETFIELD`/`PUTFIELD` path produces read/write edges in the expected direction.
- Verify descriptor/type-instruction references appear as `REFERENCES_TYPE` without semantic renaming.
- Verify numeric/string constants preserve value/type and are not automatically mislabeled as domain IDs.
- Verify repeated same-method edges aggregate occurrences instead of creating uncontrolled duplicates.
- Verify source line/path evidence points to a real matching source location when debug/source data is available.
- Measure symbols/relationships/file sizes and query time before adding a database or widening graph caps.

## Smoke/regression

- Offline Phase 1/2 static work should not affect normal gameplay smoke coverage.
- Any later runtime instrumentation must include a normal client launch with Atlas tracing disabled.

# Carryover / blockers

## CARRYOVER

- Advanced automatic correlation remains deferred until real Atlas usage identifies high-value automation.

## BLOCKED / GATED

- Phase 2 implementation is intentionally gated on the consolidated Phase 1 verification.
- Phase 2 architecture/discovery is complete enough to avoid further broad scanning once the gate passes.

# Resume Here

**Last completed:**

- Phase 1 / Bundle 1A / checklist **1A.4 Basic query/export CLI**.
- Side/prep checkpoint: Phase 2 / Bundle 2A / **2A.0 Targeted relationship architecture discovery**.

**Current state:**

- Offline Atlas foundation exists under `Client/src/main/java/game/atlas/`.
- ASM 9.7.1 is pinned in the Client build.
- Scanner indexes declarations and structural ownership into deterministic JSONL.
- Client fingerprinting excludes Atlas classes and detects scan-time client-class changes.
- Exact canonical-ID query/export exists through `AtlasQueryEngine` and `ClientAtlasMain`.
- Query/export refuses a stale or unsupported-schema index.
- Compact query/export includes immediate relationships with a 200-record cap plus full count/truncation state.
- Normal Client Console/game runtime ownership remains untouched.
- Phase 1 implementation is complete but the mandatory consolidated local verification has not run yet.
- Phase 2 relationship architecture is now prepared from the existing `AtlasScanner`/`AtlasSchema` path; no Phase 2 source code has been added.

**Next required action:**

- Run the **Phase 1 consolidated verification gate** from `docs/client-atlas/testlist.txt`.

**Next implementation after that gate passes:**

- **2A.1 Relationship schema v2 + source locator.** Do not redo relationship architecture discovery unless the Phase 1 test exposes a contradictory foundation problem.

**Files/systems already inspected or changed:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/client-atlas/PROJECT.md`
- `docs/rs3/WORKSTREAMS.md`
- `Client/build.gradle`
- `Client/src/main/java/game/atlas/AtlasSchema.java`
- `Client/src/main/java/game/atlas/AtlasFingerprint.java`
- `Client/src/main/java/game/atlas/AtlasWorkspace.java`
- `Client/src/main/java/game/atlas/AtlasJson.java`
- `Client/src/main/java/game/atlas/AtlasScanner.java`
- `Client/src/main/java/game/atlas/AtlasQueryEngine.java`
- `Client/src/main/java/game/atlas/ClientAtlasMain.java`
- `docs/client-atlas/patchnotes.txt`
- `docs/client-atlas/testlist.txt`

**Do not re-scan without new evidence:**

- Broad `game` package/source tree.
- Unrelated server/gameplay systems.
- Runtime tracing hooks until Phase 3.
- Client Console internals until Phase 4.
- Phase 2 relationship architecture unless Phase 1 verification contradicts the prepared design.

**Pending verification:**

- Consolidated Phase 1 local Java 8/Eclipse build + scan/status/query/export/stale-index checks.

**Blockers:**

- No technical blocker. Phase 2 implementation is held only by the required Phase 1 verification gate.

**Important remaining uncertainty:**

- Real Phase 1 index counts/size/performance until the first local scan.
- How much method-body analysis grows the relationship index.
- Availability/quality of compiled source-line debug data.
- Whether the generated index is small enough for any broader repository-visible snapshot strategy.

# Next recommended work

**Phase 1 verification gate.** Development prep no longer needs to stop there: Phase 2 architecture is ready, so once the test passes the next code patch is already defined as **2A.1 Relationship schema v2 + source locator**.
