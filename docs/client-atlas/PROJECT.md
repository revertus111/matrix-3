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

Normal client startup remains `game.RS3Applet`.

Running `ClientAtlasMain` with no arguments opens the standalone Atlas control window. CLI commands remain available for automation.

## Scan input

```text
Client/build/classes/java/main/
```

Atlas excludes `game/atlas/**` from fingerprinting/scanning.

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
    phase2-structural-check.txt
    phase2-structural-query.json
    exports/
    traces/
```

Rules:

- `.client-atlas/` is ignored by Git and survives normal build cleaning.
- Fresh scans replace generated symbol/relationship data but preserve evidence/traces.
- Scanner publishes generated data through temporary files.
- Fingerprints are checked before/after scan so changed compiled classes reject the scan.
- Query/export rejects stale or incompatible-schema generated data.
- SQLite/native persistence remains deferred; verified Bundle 2A measurements do not justify it.

# Evidence

## VERIFIED

### Phase 1 - 2026-09-05

- ASM resolved after Gradle project refresh.
- Atlas scanner completed successfully.
- Class files: **1221**.
- Symbols: **33742**.
- Phase-1 structural relationships: **34053**.
- Fingerprint: `41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26`.
- Persisted metadata reopened with `Current fingerprint: true`.
- Standalone Client Atlas Control opened successfully.
- One-click Phase 1 check passed.
- UI search/export worked for `CLASS:game/Class1`.
- Phase-1 Class1 export reported 15 immediate relationships with no truncation.

Phase 1 is **DONE**.

### Phase 2 / Bundle 2A - 2026-09-05

Local **Run Phase 2 Check** completed with:

```text
PHASE 2 STRUCTURAL CHECK: PASS
```

Verified measurements visible in the local report:

- Pre-scan index: schema 2 / current.
- Schema version: **2**.
- Class files: **1221**.
- Symbols: **33742**.
- Relationships: **325826**.
- Scan time: **1282.0 ms**.
- Exact query: `CLASS:game/Class1`.
- Exact-query time: **362.0 ms**.
- `symbols.jsonl`: **8,980,282 bytes (~8.5 MiB)**.
- `relationships.jsonl`: **78,016,586 bytes (~74.4 MiB)**.
- Fingerprint remained current.

The verifier PASS means its mandatory assertions succeeded, including schema-v2 record shape, generated `CALLS`, `READS_FIELD`, `WRITES_FIELD`, `REFERENCES_TYPE`, typed `CONSTANT` relationships, zero automatic `LITERAL_ID` promotion, exact-query regression, compact export regression, and preservation of evidence/trace generated-state boundaries.

**Decision from measurements:** keep portable JSONL + planned in-memory investigation indexes. The current ~83 MiB generated static dataset and ~1.3 second rebuild do not justify SQLite/native persistence.

Bundle 2A is **DONE**.

## verified-static

- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as normal main.
- Atlas remains isolated under `game.atlas`.
- `AtlasFingerprint` excludes Atlas classes.
- `AtlasWorkspace` owns local persistence/schema/current checks.
- `AtlasScanner` owns bytecode scanning.
- `AtlasQueryEngine` owns exact query/export.
- `ClientAtlasControl` is a human control surface over Atlas APIs.
- `AtlasStructuralVerifier` owns Bundle 2A structural verification/measurement.
- UI and CLI use the same Atlas engine/verifier rather than duplicating ownership.

# Schema v2

`AtlasWorkspace.SCHEMA_VERSION = 2`.

Old schema-v1 generated data is non-current and requires rebuild. Evidence/traces are preserved.

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

Java source paths resolve from original JVM internal owners. Inner/anonymous classes strip `$...` only when locating the owning `.java` file. `sourcePath` stays null when source cannot be established.

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

Method-body scanning uses `ClassReader.SKIP_FRAMES`, retaining code/debug-line information without frame processing.

Direct call targets:

```text
METHOD:<owner>#<name><descriptor>
CONSTRUCTOR:<owner>#<init><descriptor>
```

Field access targets:

```text
FIELD:<owner>#<name><descriptor>
```

Type references use neutral factual targets:

```text
TYPE:<internal-jvm-name>
```

Typed constants include:

```text
int:762
long:1234
float:1.0
double:1.0
string:Attack
```

No automatic domain meaning is assigned to generic constants.

# Search/index direction

Bundle 2A measurements support the planned on-demand/in-memory search layer.

Initial maps for 2B.1:

- symbol ID -> symbol
- owner/name -> candidate symbols
- outgoing relationships
- incoming relationships
- typed constant -> referencing symbols

Friendly searches later in 2B:

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
- [x] **Phase 1 local verification gate**.

## Phase 2 - Static Relationship and Investigation Map

**Status: ACTIVE**

### Bundle 2A - Structural relationships

**Status: DONE**

- [x] **2A.0 Targeted relationship architecture discovery**.
- [x] **2A.1 Relationship schema v2 + source locator**.
- [x] **2A.2 Calls + field access scanner**.
- [x] **2A.3 Type references + constants**.
- [x] **2A.4 Structural verification + size metrics** - local one-click verifier passed; measurements recorded above.

### Bundle 2B - Investigation search

**Status: ACTIVE**

- [ ] **2B.1 In-memory investigation index** - current execution target.
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

Bundle 2A structural gate is complete. Do not request another structural rescan unless new evidence requires it.

Future Bundle 2B tests should reuse the existing current schema-v2 index where possible and validate:

- index construction against the verified ~325k relationship dataset,
- exact and shorthand candidate resolution,
- incoming/outgoing relationship lookup,
- typed constant lookup,
- ambiguity surfaced instead of silently resolved,
- bounded result sizes and truncation state,
- acceptable index-build/query time and memory use.

# Carryover / blockers

## CARRYOVER

- Stale-index rejection on a natural future compiled-client change.
- >200 immediate-relationship truncation regression when a naturally suitable symbol is available.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- None for Bundle 2B.

# Resume Here

**Last completed checkpoint:**

- Phase 2 / Bundle 2A / **2A.4 Structural verification + size metrics** - `PHASE 2 STRUCTURAL CHECK: PASS`.

**Current phase:**

- **Phase 2 - Static Relationship and Investigation Map**

**Active bundle:**

- **Bundle 2B - Investigation search / ACTIVE**

**Current/next checklist item:**

- **2B.1 In-memory investigation index**.

**Verified dataset baseline:**

- 1221 class files
- 33742 symbols
- 325826 relationships
- 8.5 MiB symbols JSONL
- 74.4 MiB relationships JSONL
- ~1.28 s full structural scan
- ~362 ms current streaming exact Class1 query

**Implementation state:**

- Schema v2/source locator complete and verified.
- CALLS/DYNAMIC_CALL/read/write scanning complete and verified by consolidated gate.
- Type references + typed constants complete and verified by consolidated gate.
- No automatic literal/domain-ID semantics.
- JSONL remains the persistence authority; no database is currently justified.

**Files/systems already inspected for Phase 2:**

- `Client/src/main/java/game/atlas/AtlasSchema.java`
- `Client/src/main/java/game/atlas/AtlasJson.java`
- `Client/src/main/java/game/atlas/AtlasWorkspace.java`
- `Client/src/main/java/game/atlas/AtlasScanner.java`
- `Client/src/main/java/game/atlas/AtlasQueryEngine.java`
- `Client/src/main/java/game/atlas/AtlasStructuralVerifier.java`
- `Client/src/main/java/game/atlas/ClientAtlasControl.java`
- `Client/src/main/java/game/atlas/ClientAtlasMain.java`
- `docs/client-atlas/PROJECT.md`
- `docs/client-atlas/patchnotes.txt`
- `docs/client-atlas/testlist.txt`
- `docs/rs3/WORKSTREAMS.md`

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree
- unrelated server/gameplay systems
- Bundle 2A relationship architecture/scanner path
- runtime tracing hooks before Phase 3
- Client Console internals before Phase 4

**Pending verification:**

- None for Bundle 2A.

**Next implementation:**

- Build **2B.1 In-memory investigation index** over the verified schema-v2 JSONL dataset. Preserve JSONL as persistence; load bounded/indexed maps on demand rather than introducing a second database.

# Next recommended work

**2B.1 In-memory investigation index.**