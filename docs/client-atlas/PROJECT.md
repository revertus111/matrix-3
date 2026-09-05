# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations start from known evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. Human UI consumes the same Atlas engine/data; it is not a second source of truth.

## Scope

### In scope

- Persistent class/field/method/constructor symbol index.
- Inheritance/interface mapping.
- Static caller/callee, field read/write, type-reference, constant, and evidence-backed literal-ID relationships.
- Search by original symbol, ID, alias, evidence note, domain, or related symbol.
- Targeted runtime trace sessions after the static map is proven.
- Evidence records using `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN`.
- External semantic aliases/notes without renaming original client symbols.
- Machine-readable exports for future assistant/code investigation.
- Incremental/stale-index detection.
- Standalone developer control UI and later Client Console browser.

### Out of scope

- Renaming obfuscated classes/fields/methods.
- Pretending stripped Jagex names can be recovered.
- Promoting guessed semantics to verified status.
- Replacing client runtime/system ownership with tooling.
- Always-on tracing that materially hurts client performance.
- Unbounded graph dumps.
- Scanning unrelated server systems without a concrete correlation need.

## Architecture / ownership

- Atlas is developer/reverse-engineering tooling, not gameplay authority.
- Existing client runtime/cache/network/render/interface/input/definition systems remain authoritative.
- Atlas owns generated metadata, indexes, aliases, evidence, exports, and trace-session records.
- Original obfuscated names remain permanent primary identifiers.
- Runtime instrumentation must be explicit, bounded, and switchable.
- UI code calls Atlas APIs rather than duplicating scanner/query ownership.

# Current implementation

## Offline package / entry points

```text
Client/src/main/java/game/atlas/
```

Primary entry:

```text
game.atlas.ClientAtlasMain
```

Standalone control:

```text
game.atlas.ClientAtlasControl
```

Normal client startup remains:

```text
game.RS3Applet
```

Running `ClientAtlasMain` with no arguments opens the standalone Atlas control window. CLI commands remain available for automation.

## Scan input

```text
Client/build/classes/java/main/
```

Atlas excludes its own compiled classes from fingerprinting/scanning:

```text
game/atlas/**
```

ASM dependency:

```text
org.ow2.asm:asm:9.7.1
```

Eclipse may require **Gradle -> Refresh Gradle Project** after first pulling ASM; this was runtime-confirmed to resolve the missing ASM classpath errors.

## Persistence

```text
Client/.client-atlas/
    metadata.properties
    symbols.jsonl
    relationships.jsonl
    evidence.jsonl
    phase1-check.json
    exports/
    traces/
```

Rules:

- `.client-atlas/` is ignored by Git and survives normal build cleaning.
- Fresh scans replace generated symbol/relationship data but preserve evidence/traces.
- Scanner publishes generated data through temporary files.
- Fingerprints are checked before/after scan so changed compiled classes reject the scan.
- Query/export rejects stale or incompatible-schema generated data.
- SQLite/native persistence stays deferred until measurements prove a need.

# Evidence

## VERIFIED

2026-09-05 local Eclipse/Java 8 Phase 1 verification:

- ASM resolved after Gradle project refresh.
- Atlas scanner completed successfully.
- Class files: **1221**.
- Symbols: **33742**.
- Relationships: **34053**.
- Fingerprint: `41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26`.
- Persisted metadata reopened with `Current fingerprint: true`.
- Standalone Client Atlas Control opened successfully.
- One-click `Run Phase 1 Check` ended with `PHASE 1 AUTOMATED CHECK: PASS`.
- UI search worked for `Class1`.
- UI export worked for `CLASS:game/Class1`.
- The verified Phase-1 export reported a current index, 15 immediate relationships, no truncation, the `EXTENDS java/lang/Object` edge, fields, constructor, and methods owned by `Class1`.

Phase 1 is therefore **DONE**. The stale-index guard remains a carryover regression check for the next natural compiled-client change; it is not a Phase 2 blocker.

## verified-static

- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as normal main.
- Atlas remains isolated under `game.atlas`.
- `AtlasFingerprint` excludes Atlas classes.
- `AtlasWorkspace` owns local persistence/schema/current checks.
- `AtlasScanner` owns bytecode scanning.
- `AtlasQueryEngine` owns exact query/export.
- `ClientAtlasControl` is only a human control surface over those APIs.
- Phase 2 schema v2 is implemented as described below.
- Phase 2 method-body scanning now records direct calls, dynamic calls, field reads, and field writes with per-method aggregation and source-line/opcode evidence where available.
- Local schema-v2 rebuild/scan verification remains intentionally batched with the completed 2A.2 work and upcoming 2A.3/2A.4 structural verification to minimize user PC time.

## UNKNOWN / measurement needed

- Exact generated JSONL file sizes and timings on schema v2.
- Measured relationship growth from method-body scanning.
- Completeness of source line/debug data.
- Whether meaningful `invokedynamic` usage exists in this client.
- Whether a database is ever needed after measured Phase 2 data.
- Safest high-level runtime hooks for Phase 3.

# Schema v2

`AtlasWorkspace.SCHEMA_VERSION = 2`.

Old generated schema-v1 data is treated as non-current and must be rebuilt. Evidence/traces are preserved.

## Symbol record

Schema v2 separates compiled and Java-source locations:

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

Declaration scanning resolves Java source from the original JVM internal owner:

```text
src/main/java/<owner>.java
```

For inner/anonymous classes, `$...` is stripped when locating the owning source file. `sourcePath` remains null when a matching source file cannot be established.

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

Rules:

- `sourceLine` and `opcode` are nullable.
- `occurrenceCount` is positive and defaults to 1 for structural declaration relationships.
- Existing `EXTENDS`, `IMPLEMENTS`, and `DECLARES` records carry Java source-path evidence when available.
- Method-body relationships populate line/opcode evidence when compiled debug/instruction evidence supports it.
- Repeated same-method/type/target edges are aggregated into one record with an incremented `occurrenceCount`.
- If repeated occurrences of one aggregated edge use different opcodes, the stored opcode becomes null rather than claiming one opcode represents all occurrences.
- `detail` remains optional and must not replace fields that deserve typed representation.

Relationship types include a distinct:

```text
DYNAMIC_CALL
```

This ensures `invokedynamic` evidence is never mislabeled as a proven direct `CALLS` edge.

# Phase 2 architecture

## Core evidence rule

Record what bytecode proves, not guessed semantics.

- ordinary direct method invocation -> `CALLS`
- `invokedynamic` -> `DYNAMIC_CALL`
- `GETFIELD` / `GETSTATIC` -> `READS_FIELD`
- `PUTFIELD` / `PUTSTATIC` -> `WRITES_FIELD`
- reliable descriptor/type instruction -> `REFERENCES_TYPE`
- numeric/string literal -> typed `CONSTANT`

A raw integer such as `762` is **not** automatically an interface, animation, opcode, model, etc. `LITERAL_ID` requires domain/context evidence.

## Scanner direction

Extend the existing `AtlasScanner`; do not create a competing scanner.

Phase 2 method-body scanning uses:

```text
ClassReader.SKIP_FRAMES
```

This keeps code and debug line information while avoiding frame-processing overhead.

Per-method repeated relationships aggregate by relationship type + exact target. The method ID is the `fromId` for every method-body edge.

## Calls

Direct method instruction targets use exact stable IDs:

```text
METHOD:<owner>#<name><descriptor>
CONSTRUCTOR:<owner>#<init><descriptor>
```

External targets may still be recorded as bytecode facts even when the target symbol is outside the scanned client.

`invokedynamic` uses `DYNAMIC_CALL` with a stable factual target containing the dynamic name/descriptor and bootstrap owner/name/descriptor. Bootstrap tag/interface facts are preserved in `detail`; bootstrap arguments remain 2A.3 structural/type/constant work.

## Field access

```text
GETFIELD / GETSTATIC -> READS_FIELD
PUTFIELD / PUTSTATIC -> WRITES_FIELD
```

Target form matches the existing stable field ID:

```text
FIELD:<owner>#<name><descriptor>
```

## Type references

Collect reliable `REFERENCES_TYPE` evidence from:

- field/method descriptors
- argument/return types
- declared exceptions
- generic signatures where ASM parses them reliably
- `NEW`, `CHECKCAST`, `INSTANCEOF`, `ANEWARRAY`, `MULTIANEWARRAY`
- method/field owners and descriptors
- `Type` values loaded through `LDC`
- bootstrap handles/dynamic constants where reliable

Array descriptors should resolve object element types rather than become semantic aliases.

## Constants

Capture typed values, for example:

```text
int:762
long:1234
float:1.0
string:Attack
```

High-value sources:

- static/final field constant values
- `LDC`
- `ICONST_*`, `BIPUSH`, `SIPUSH`
- `LCONST_*`, `FCONST_*`, `DCONST_*`

Skip noisy local arithmetic such as ordinary `IINC` unless later investigation proves it useful.

## Search/index direction

Do not add SQLite before measurement.

Likely Phase 2 in-memory maps:

- symbol ID -> symbol
- owner/name -> candidates
- outgoing relationships
- incoming relationships
- typed constant -> referencing symbols

Later friendly searches:

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

Ambiguous shorthand returns candidates; it never silently chooses an overload.

Bounded neighborhood starting limits:

- default depth 1
- optional depth 2
- node cap 100
- edge cap 500
- always report schema/fingerprint/truncation

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status: DONE**

### Bundle 1A

- [x] **1A.1 Targeted implementation discovery**.
- [x] **1A.2 Atlas schema + persistence skeleton**.
- [x] **1A.3 Bytecode scanner MVP**.
- [x] **1A.4 Basic query/export CLI**.
- [x] **1A.5 Standalone Client Atlas Control**.
- [x] **Phase 1 local verification gate** - Eclipse/Java 8 scan/status/control/check/search/export passed.

## Phase 2 - Static Relationship and Investigation Map

**Status: ACTIVE**

### Bundle 2A - Structural relationships

**Status: ACTIVE**

- [x] **2A.0 Targeted relationship architecture discovery**.
- [x] **2A.1 Relationship schema v2 + source locator** - schema bump, typed source/occurrence fields, compiled/source path separation, dynamic-call type, old-schema stale guard. Rebuild/record verification is batched with 2A.2-2A.4 checks.
- [x] **2A.2 Calls + field access scanner** - method-body `CALLS`, `DYNAMIC_CALL`, `READS_FIELD`, `WRITES_FIELD`, line/opcode evidence, and per-method occurrence aggregation. Runtime verification is batched with 2A.3/2A.4.
- [ ] **2A.3 Type references + constants** - descriptors/signatures/type instructions + typed constants; no automatic semantic IDs. **NEXT.**
- [ ] **2A.4 Structural verification + size metrics** - rebuild schema v2, verify known relationships/source evidence, measure counts/files/timing, tune only from evidence.

### Bundle 2B - Investigation search

**Status: PLANNED**

- [ ] **2B.1 In-memory investigation index**.
- [ ] **2B.2 Ranked/friendly search**.
- [ ] **2B.3 Relationship queries + bounded neighborhoods**.
- [ ] **2B.4 Assistant-oriented export**.
- [ ] **2B.5 Safe initial domain correlation**.

## Phase 3 - Runtime Evidence and Knowledge

**Status: PLANNED**

### Bundle 3A

- [ ] Start/stop/name/save trace lifecycle.
- [ ] Menu/input hooks.
- [ ] Packet metadata hooks.
- [ ] Interface/component hooks.
- [ ] Definition/cache/model/animation/GFX hooks.
- [ ] Correlate runtime events back to Atlas symbols.

### Bundle 3B

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

## Current batching strategy

Do not spend a separate PC session solely on 2A.1 or 2A.2.

When 2A.3 is implemented, run the structural verification bundle once:

1. Clean/build Client in Eclipse/Java 8.
2. Open Client Atlas Control.
3. Confirm old schema-v1 index is non-current until rebuilt.
4. Rebuild index once.
5. Confirm metadata schema version 2/current.
6. Verify `compiledPath` + `sourcePath` on known symbols.
7. Verify typed relationship source/line/opcode/occurrence fields.
8. Verify known CALLS/read/write/type/constant paths.
9. Measure relationship/file-size/query-time growth.
10. Re-run exact search/export regression.

See `docs/client-atlas/testlist.txt` for the exact checks.

# Carryover / blockers

## CARRYOVER

- Stale-index rejection on a natural future compiled-client change.
- >200 immediate-relationship truncation regression when a naturally suitable symbol is available.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- None.

# Resume Here

**Last completed:**

- Phase 1 local verification gate.
- Phase 2 / Bundle 2A / **2A.2 Calls + field access scanner** implementation.

**Current phase:**

- **Phase 2 - Static Relationship and Investigation Map**

**Active bundle:**

- **Bundle 2A - Structural relationships**

**Next checklist item:**

- **2A.3 Type references + constants**.

**Current implementation state:**

- Schema version is 2.
- Symbol records separate `compiledPath` and `sourcePath`.
- Relationship records include source path, nullable source line/opcode, occurrence count, and detail.
- Declaration scan populates source path and occurrence count for structural records.
- Method bodies are now visited with frames skipped but code/debug line information retained.
- Direct method/constructor instructions produce exact `CALLS` edges.
- `invokedynamic` produces distinct `DYNAMIC_CALL` edges with bootstrap identity rather than false direct-call semantics.
- GETFIELD/GETSTATIC produce `READS_FIELD`; PUTFIELD/PUTSTATIC produce `WRITES_FIELD`.
- Repeated same-method/type/target edges are aggregated and counted.
- Old schema data is treated as non-current; one rebuild migrates generated data while preserving evidence/traces.
- Type-reference and constant indexing have **not** started yet.

**Files already inspected/changed for this phase:**

- `Client/src/main/java/game/atlas/AtlasSchema.java`
- `Client/src/main/java/game/atlas/AtlasJson.java`
- `Client/src/main/java/game/atlas/AtlasWorkspace.java`
- `Client/src/main/java/game/atlas/AtlasScanner.java`
- `Client/src/main/java/game/atlas/AtlasQueryEngine.java`
- `Client/src/main/java/game/atlas/ClientAtlasControl.java`
- `docs/client-atlas/PROJECT.md`
- `docs/client-atlas/patchnotes.txt`
- `docs/client-atlas/testlist.txt`
- `docs/rs3/WORKSTREAMS.md`

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree
- unrelated server/gameplay systems
- Phase 2 relationship architecture
- runtime tracing hooks before Phase 3
- Client Console internals before Phase 4

**Pending verification:**

- schema-v2 local rebuild/record shape plus 2A.2 call/read/write aggregation is batched with 2A.3-2A.4 structural verification.

**Blockers:**

- None.

# Next recommended work

**2A.3 Type references + constants.** Extend the existing method-body/declaration scanner with reliable structural type references and typed constants while keeping raw numeric values semantically neutral until later evidence-backed correlation.
