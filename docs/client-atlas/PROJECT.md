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
- One-click Phase 1 check passed.
- UI search/export worked for `CLASS:game/Class1`.
- The verified Phase-1 export reported 15 immediate relationships with no truncation.

Phase 1 is **DONE**.

## verified-static

- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as normal main.
- Atlas remains isolated under `game.atlas`.
- `AtlasFingerprint` excludes Atlas classes.
- `AtlasWorkspace` owns local persistence/schema/current checks.
- `AtlasScanner` owns bytecode scanning.
- `AtlasQueryEngine` owns exact query/export.
- `ClientAtlasControl` is a human control surface over Atlas APIs.
- Schema v2/source-locator implementation is complete.
- Method-body call/field scanning is complete.
- Type-reference and typed-constant scanning is complete.
- `AtlasStructuralVerifier` now owns the consolidated Bundle 2A verification/measurement pass.
- The standalone UI and CLI both call the same verifier rather than duplicating gate logic.

## UNKNOWN / measurement needed

These are exactly what 2A.4 will measure locally:

- Schema-v2 relationship count.
- Generated `symbols.jsonl` / `relationships.jsonl` sizes.
- Actual scan time on the user's machine.
- Exact-query time on the user's machine.
- Amount of usable source-line/opcode evidence.
- Whether this compiled client contains meaningful `invokedynamic` sites.
- Whether measured data ever justifies a database or different graph caps.

# Schema v2

`AtlasWorkspace.SCHEMA_VERSION = 2`.

Old schema-v1 generated data is non-current and requires rebuild. Atlas Control reports this as:

```text
REBUILD REQUIRED (schema 1 -> 2)
```

Evidence/traces are preserved.

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

Direct call targets:

```text
METHOD:<owner>#<name><descriptor>
CONSTRUCTOR:<owner>#<init><descriptor>
```

Field access targets:

```text
FIELD:<owner>#<name><descriptor>
```

`invokedynamic` uses `DYNAMIC_CALL` with dynamic name/descriptor plus bootstrap owner/name/descriptor. Bootstrap tag/interface facts stay in `detail`.

## Type references

Neutral factual target:

```text
TYPE:<internal-jvm-name>
```

`REFERENCES_TYPE` is collected from:

- field descriptors
- method arguments/return descriptors
- declared exceptions
- class/field/method generic signatures
- generic inner-class signatures with exact `$` JVM names
- `NEW`, `CHECKCAST`, `INSTANCEOF`, `ANEWARRAY`, `MULTIANEWARRAY`
- method/field instruction owners and object descriptors
- LDC `Type` values
- bootstrap `Handle` owners/descriptors
- `ConstantDynamic` descriptors/bootstrap structures

Object arrays resolve to object element type. Primitive-only descriptors do not create fake object references.

## Constants

Typed targets include:

```text
int:762
long:1234
float:1.0
double:1.0
string:Attack
```

Captured from field constant values, LDC, ICONST/BIPUSH/SIPUSH/LCONST/FCONST/DCONST, and reliable bootstrap literal arguments.

`Type`, `Handle`, and `ConstantDynamic` values feed structural references instead of being flattened into misleading string constants. Ordinary `IINC` remains ignored.

# 2A.4 consolidated verifier

`AtlasStructuralVerifier` performs the Bundle 2A gate in one pass.

It automatically:

1. Captures pre-scan schema/current state plus evidence/trace size state.
2. Rebuilds generated Atlas data using the current scanner.
3. Requires schema 2 + current fingerprint.
4. Requires relationship growth above the Phase 1 structural-only baseline.
5. Verifies symbol location fields and Class1 source/compiled paths where available.
6. Verifies schema-v2 relationship field shape.
7. Requires generated `CALLS`, `READS_FIELD`, `WRITES_FIELD`, `REFERENCES_TYPE`, and `CONSTANT` families.
8. Requires zero automatic `LITERAL_ID` promotions.
9. Exercises an internal incoming `CALLS` exact-query path.
10. Re-runs exact class query + compact export regression.
11. Requires evidence/trace files to remain unchanged across rebuild.
12. Counts every relationship type.
13. Records source-line/opcode/aggregation counts.
14. Records class/symbol/relationship totals, JSONL byte sizes, scan time, and query time.
15. Writes `.client-atlas/phase2-structural-check.txt`.

Non-failing evidence notes:

- `DYNAMIC_CALL` may be zero when the client contains no invokedynamic sites.
- Source-line count may be zero if compiled debug line data is unavailable.
- Aggregated-occurrence count may be zero if no naturally repeated same-method edge occurs.

Human workflow:

```text
Run ClientAtlasMain with no args -> Run Phase 2 Check
```

Automation workflow:

```text
ClientAtlasMain verify-structural
```

# Search/index direction

Do not add SQLite before 2A.4 measurements.

Planned Phase 2 in-memory maps after Bundle 2A passes:

- symbol ID -> symbol
- owner/name -> candidates
- outgoing relationships
- incoming relationships
- typed constant -> referencing symbols

Friendly searches later:

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

**Status: NEEDS TEST**

- [x] **2A.0 Targeted relationship architecture discovery**.
- [x] **2A.1 Relationship schema v2 + source locator**.
- [x] **2A.2 Calls + field access scanner**.
- [x] **2A.3 Type references + constants**.
- [ ] **2A.4 Structural verification + size metrics** - one-click verifier/UI/CLI harness implemented; local `PHASE 2 STRUCTURAL CHECK: PASS` + measured report still required.

### Bundle 2B - Investigation search

**Status: PLANNED - GATED ON 2A.4 PASS**

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

## Current shortest 2A.4 local session

1. `git pull origin main`.
2. Eclipse/Java 8 clean/build Client.
3. Run `game.atlas.ClientAtlasMain` with no program arguments.
4. Click **Run Phase 2 Check** once.
5. Confirm output contains `PHASE 2 STRUCTURAL CHECK: PASS`.
6. Send/copy `.client-atlas/phase2-structural-check.txt`.

No manual JSONL inspection is required for the normal gate.

See `docs/client-atlas/testlist.txt` for every automated assertion.

# Carryover / blockers

## CARRYOVER

- Stale-index rejection on a natural future compiled-client change.
- >200 immediate-relationship truncation regression when a naturally suitable symbol is available.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- No implementation blocker.
- Bundle 2A is waiting only on the one-click 2A.4 local PASS/report.

# Resume Here

**Last completed implementation:**

- Phase 2 / Bundle 2A / **2A.4 verification harness implementation**.

**Current phase:**

- **Phase 2 - Static Relationship and Investigation Map**

**Active bundle:**

- **Bundle 2A - Structural relationships / NEEDS TEST**

**Current/next checklist item:**

- **2A.4 local structural verification + size metrics PASS**.

**Current implementation state:**

- Schema version 2 implemented.
- Symbols separate compiled/source paths.
- Relationship records carry source/line/opcode/count/detail.
- CALLS/DYNAMIC_CALL/read/write scanning implemented.
- Type references + typed constants implemented.
- No automatic literal/domain-ID semantics.
- One-click structural verifier is implemented in `AtlasStructuralVerifier` and exposed by Client Atlas Control.
- CLI automation uses the same verifier through `verify-structural`.
- The verifier rebuilds the index itself; the user does not need to click Scan separately.

**Files already inspected/changed for this phase:**

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
- Phase 2 relationship architecture
- runtime tracing hooks before Phase 3
- Client Console internals before Phase 4

**Pending verification:**

- Pull/build once and click **Run Phase 2 Check**.
- Persist the actual schema-v2 counts/sizes/timings from the generated report.

**Next implementation after gate passes:**

- **2B.1 In-memory investigation index**.

# Next recommended work

**Run the one-click 2A.4 structural gate.** If the report ends in PASS, close Bundle 2A and begin **2B.1** without another structural discovery cycle.
