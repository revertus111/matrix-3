# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations start from known evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. Human UI consumes the same Atlas engine/data; it is not a second source of truth.

## Scope

### In scope

- Persistent class/field/method/constructor symbol index.
- Inheritance/interface mapping.
- Static caller/callee, field read/write, type-reference, constant, and later evidence-backed literal-ID relationships.
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

Phase 1 is therefore **DONE**.

## verified-static

- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as normal main.
- Atlas remains isolated under `game.atlas`.
- `AtlasFingerprint` excludes Atlas classes.
- `AtlasWorkspace` owns local persistence/schema/current checks.
- `AtlasScanner` owns bytecode scanning.
- `AtlasQueryEngine` owns exact query/export.
- `ClientAtlasControl` is only a human control surface over those APIs.
- Phase 2 schema v2/source-locator implementation is complete.
- Phase 2 method-body call/field scanning is complete.
- Phase 2 type-reference and typed-constant scanning is complete.
- Local schema-v2 rebuild/relationship/size verification is intentionally consolidated into 2A.4 to minimize user PC time.

## UNKNOWN / measurement needed

- Exact schema-v2 relationship count and generated JSONL sizes.
- Actual scan/query timings on the user's machine.
- Completeness of source line/debug data.
- Whether meaningful `invokedynamic` usage exists in this client.
- Whether a database is ever needed after measured Phase 2 data.
- Safest high-level runtime hooks for Phase 3.

# Schema v2

`AtlasWorkspace.SCHEMA_VERSION = 2`.

Old schema-v1 generated data is treated as non-current and must be rebuilt. Evidence/traces are preserved.

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

Java source paths resolve from original JVM internal owners. For inner/anonymous classes, `$...` is stripped only for locating the owning `.java` source file. `sourcePath` stays null when the source file cannot be established.

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
- `occurrenceCount` is positive.
- Repeated same-method/type/target edges aggregate into one record.
- If an aggregated edge uses different opcodes, stored opcode becomes null instead of claiming one instruction represents all occurrences.
- `detail` remains optional and must not replace typed fields.

# Phase 2 structural scanner

## Core evidence rule

Record what bytecode proves, not guessed semantics.

- direct method invocation -> `CALLS`
- `invokedynamic` -> `DYNAMIC_CALL`
- `GETFIELD` / `GETSTATIC` -> `READS_FIELD`
- `PUTFIELD` / `PUTSTATIC` -> `WRITES_FIELD`
- reliable JVM type evidence -> `REFERENCES_TYPE`
- numeric/string literal -> typed `CONSTANT`

A raw integer such as `762` is **not** automatically an interface, animation, opcode, model, etc. `LITERAL_ID` remains reserved for later evidence-backed domain correlation.

## Method-body scanning

Uses:

```text
ClassReader.SKIP_FRAMES
```

This keeps code and debug-line information while avoiding frame processing.

Direct call targets use exact stable IDs:

```text
METHOD:<owner>#<name><descriptor>
CONSTRUCTOR:<owner>#<init><descriptor>
```

Field access targets use exact stable IDs:

```text
FIELD:<owner>#<name><descriptor>
```

`invokedynamic` uses `DYNAMIC_CALL` with dynamic name/descriptor + bootstrap owner/name/descriptor. Bootstrap tag/interface facts remain in `detail`.

## Type references

Type references use neutral factual targets:

```text
TYPE:<internal-jvm-name>
```

This avoids guessing whether the target should semantically be treated as a class, interface, definition type, domain object, etc.

`REFERENCES_TYPE` is collected from:

- field descriptors
- method argument/return descriptors
- declared exceptions
- class/field/method generic signatures
- generic inner-class signatures using exact `$` JVM names
- `NEW`, `CHECKCAST`, `INSTANCEOF`, `ANEWARRAY`, `MULTIANEWARRAY`
- method/field instruction owners and object descriptors
- LDC `Type` values
- bootstrap `Handle` owners/descriptors
- `ConstantDynamic` descriptors/bootstrap structures

Object arrays resolve to their object element type. Primitive-only descriptors do not create fake object references.

## Constants

Typed targets include:

```text
int:762
long:1234
float:1.0
double:1.0
string:Attack
```

Captured from:

- classfile/static field constant values when present
- LDC literal values
- `ICONST_*`
- `BIPUSH` / `SIPUSH`
- `LCONST_*`
- `FCONST_*`
- `DCONST_*`
- reliable bootstrap literal arguments

`Type`, `Handle`, and `ConstantDynamic` values feed structural references instead of being flattened into misleading string constants. Ordinary `IINC` remains intentionally ignored.

# Search/index direction

Do not add SQLite before measurement.

Likely Phase 2 in-memory maps after Bundle 2A verifies:

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

Initial bounded-neighborhood targets:

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

**Status: NEEDS TEST**

- [x] **2A.0 Targeted relationship architecture discovery**.
- [x] **2A.1 Relationship schema v2 + source locator** - schema bump, typed source/occurrence fields, compiled/source path separation, dynamic-call type, old-schema stale guard.
- [x] **2A.2 Calls + field access scanner** - `CALLS`, `DYNAMIC_CALL`, `READS_FIELD`, `WRITES_FIELD`, line/opcode evidence, per-method aggregation.
- [x] **2A.3 Type references + constants** - descriptors/signatures/exceptions/type instructions/bootstrap structures + typed constants; no automatic semantic IDs.
- [ ] **2A.4 Structural verification + size metrics** - rebuild schema v2 once, verify known relationships/source/type/constant evidence, measure counts/files/timing, and tune only from measured evidence. **NEXT.**

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

## 2A.4 consolidated structural verification

Do one local session rather than testing 2A.1/2A.2/2A.3 separately:

1. `git pull origin main`.
2. Eclipse/Java 8 clean/build Client.
3. Open `game.atlas.ClientAtlasMain` with no arguments.
4. Confirm old schema-v1 index is non-current before rebuild if it still exists.
5. Click **Scan / Rebuild Index** once.
6. Confirm schema version 2/current.
7. Verify source-path + relationship record shape.
8. Verify one known `CALLS`, `READS_FIELD`, `WRITES_FIELD`, `REFERENCES_TYPE`, and typed `CONSTANT` relationship.
9. Confirm numeric constants remain generic and no automatic `LITERAL_ID`/domain semantics appear.
10. Record class/symbol/relationship counts, generated file sizes, approximate scan time, and one exact query timing if practical.
11. Re-run exact search/export regression.

See `docs/client-atlas/testlist.txt` for the detailed checks.

# Carryover / blockers

## CARRYOVER

- Stale-index rejection on a natural future compiled-client change.
- >200 immediate-relationship truncation regression when a naturally suitable symbol is available.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- No implementation blocker.
- Bundle 2A is now waiting only on the consolidated 2A.4 local verification/measurement gate.

# Resume Here

**Last completed:**

- Phase 2 / Bundle 2A / **2A.3 Type references + constants** implementation.

**Current phase:**

- **Phase 2 - Static Relationship and Investigation Map**

**Active bundle:**

- **Bundle 2A - Structural relationships**

**Current/next checklist item:**

- **2A.4 Structural verification + size metrics**.

**Current implementation state:**

- Schema version is 2.
- Symbols separate `compiledPath` / `sourcePath`.
- Relationships carry source path, nullable line/opcode, occurrence count, and detail.
- Direct calls/dynamic calls/field reads/field writes are indexed from method bodies.
- Repeated same-method edges aggregate occurrence counts.
- Type references cover descriptors, exceptions, signatures, type instructions, instruction owners/descriptors, LDC types, handles, and constant-dynamic structures.
- Type targets remain neutral `TYPE:<internal-name>` facts.
- Typed constants cover field constants, bytecode literal instructions, LDC literals, and reliable bootstrap literals.
- Numeric constants never auto-promote to `LITERAL_ID` or domain semantics.
- Old schema data is treated as non-current; one rebuild migrates generated data while preserving evidence/traces.

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

- Consolidated schema-v2 structural scan + relationship evidence + size/timing measurements.

**Next implementation after Bundle 2A passes:**

- **2B.1 In-memory investigation index**.

# Next recommended work

**2A.4 Structural verification + size metrics.** This is the one consolidated local test session for all Phase 2 structural scanner work completed so far.
