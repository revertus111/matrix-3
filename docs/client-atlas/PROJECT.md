# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations start from known evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. Human UI consumes the same Atlas engine/data; it is not a second source of truth.

## Scope

### In scope

- Persistent class/field/method/constructor symbol index.
- Inheritance and implemented-interface mapping.
- Static caller/callee, field read/write, type-reference, constant, and evidence-backed literal-ID relationships.
- Search by original symbol, ID, alias, evidence note, domain, or related symbol.
- Targeted runtime trace sessions after the static foundation is proven.
- Evidence records using `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN`.
- External semantic aliases/notes without renaming original client symbols.
- Machine-readable exports for future assistant/code investigation.
- Incremental/stale-index detection.
- Small standalone developer control UI and later Client Console browser.

### Out of scope

- Renaming obfuscated classes, fields, or methods.
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
- UI code must call Atlas APIs rather than duplicate scanner/query ownership.

# Phase 1 implementation

## Offline ownership

Source package:

```text
Client/src/main/java/game/atlas/
```

Main CLI/control entry:

```text
game.atlas.ClientAtlasMain
```

Standalone control class:

```text
game.atlas.ClientAtlasControl
```

Normal client startup remains:

```text
game.RS3Applet
```

Running `ClientAtlasMain` with **no arguments** now opens the standalone Atlas control window. Explicit CLI commands remain available for automation.

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

- ASM core dependency: `org.ow2.asm:asm:9.7.1`.
- Eclipse may require **Gradle -> Refresh Gradle Project** after first pulling the ASM dependency; this was runtime-confirmed to resolve the missing-ASM classpath errors.
- Phase 1 uses `ClassReader.SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES` so method bodies remain Phase 2 ownership.

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

- `.client-atlas/` is ignored by Git and survives normal `build/` cleaning.
- Metadata stores schema/fingerprint/scan root/timestamp/counts.
- Generated symbol/relationship files are deterministic JSONL.
- Fresh scans replace generated symbol/relationship data but preserve evidence/traces.
- Scanner publishes through temporary files and rejects class changes during a scan.
- SQLite/native persistence stays deferred until measurements prove a need.

## Phase 1 indexed coverage

- classes / interfaces / enums / annotations
- fields / methods / constructors
- descriptors / generic signatures / access flags
- compiled class paths
- `EXTENDS`
- `IMPLEMENTS`
- `DECLARES`

Stable IDs preserve original internal names, for example:

```text
CLASS:game/Class387
METHOD:game/Class387#method4844(II)V
FIELD:game/Class540#anInt7134:I
```

## Query/export

CLI remains available:

```text
game.atlas.ClientAtlasMain query "CLASS:game/Class1"
game.atlas.ClientAtlasMain query "METHOD:game/Class387#method4844(II)V"
game.atlas.ClientAtlasMain export "CLASS:game/Class1" <file>
```

Rules:

- Phase 1 canonical-ID lookup is exact; ranked/fuzzy search belongs to Phase 2.
- Query/export validates schema and current fingerprint before returning data.
- Immediate relationships are capped at 200 while preserving full count/truncation state.
- Export is atomic UTF-8 JSON.

## Standalone Client Atlas Control

Purpose: remove manual Eclipse run-argument churn from normal developer use.

Current controls:

- **Run Phase 1 Check**
- **Scan / Rebuild Index**
- **Refresh Status**
- **Search**
- **Export Last Result**
- **Open Workspace**

UI behavior:

- Auto-finds Matrix3 Client root and compiled class root.
- Shows current/stale/no-index state, symbol count, relationship count, and fingerprint.
- Uses background workers so scans/checks do not freeze the Swing UI.
- Accepts class shorthand such as `Class1` / `game.Class1` plus exact canonical Atlas IDs.
- Pretty-prints query output for humans while reusing the same `AtlasQueryEngine` results.
- Exports normal UI results under `.client-atlas/exports/`.
- Uses a dark theme suitable for the existing developer-tool workflow.

`Run Phase 1 Check` automates the high-value gate work without changing program arguments:

1. Scan compiled classes.
2. Require non-zero class/symbol/relationship counts.
3. Reopen metadata and verify the current fingerprint.
4. Verify CLASS/FIELD/METHOD/CONSTRUCTOR coverage.
5. Verify DECLARES/EXTENDS/IMPLEMENTS coverage.
6. Verify `game/atlas/**` is excluded.
7. Run an exact class query.
8. Run an exact declared method/constructor query where available.
9. Write `.client-atlas/phase1-check.json` and verify it exists.

The UI intentionally does **not** mutate compiled client classes merely to test stale-index rejection. That guard remains a later intentional-change verification.

# Runtime evidence

## VERIFIED

2026-09-05 local Eclipse/Java 8 Phase 1 evidence:

- Gradle refresh resolved `org.objectweb.asm` / `ClassReader` / `ClassVisitor` / `Opcodes` Eclipse classpath errors.
- Atlas scanner completed successfully against the compiled Matrix3 client.
- Class files: **1221**.
- Symbols: **33742**.
- Relationships: **34053**.
- Client fingerprint: `41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26`.
- `status` reopened persisted metadata successfully with the same counts/fingerprint.
- `Current fingerprint: true` was runtime-confirmed.

## verified-static

- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as normal main.
- Atlas remains isolated under `game.atlas`.
- `AtlasFingerprint` excludes Atlas classes.
- `AtlasWorkspace` owns local persistence and stale/current checks.
- `AtlasScanner` owns declaration scanning.
- `AtlasQueryEngine` owns exact query/export.
- `ClientAtlasControl` is only a human control surface over those APIs.
- `ClientAtlasMain` with no args opens the UI; explicit CLI commands remain intact.
- The new UI source passed a Java-8 language-level static compilation check against the Atlas API signatures before patching.

## HYPOTHESIS

- Streaming exact lookup is sufficient for Phase 1; an in-memory ranked index will likely help Phase 2.
- Per-method relationship aggregation should keep Phase 2 JSONL reasonably compact.

## UNKNOWN

- Exact generated JSONL file sizes on the verified local scan.
- Exact scan/query timings on the user's machine.
- How much Phase 2 method-body relationships increase index size.
- Quality/completeness of compiled debug/source-line data.
- Whether meaningful `invokedynamic` usage exists.
- Safest high-level runtime hooks for Phase 3 tracing.

# Phase 2 prepared architecture

Phase 2 implementation remains gated until Phase 1 closes. Architecture discovery is complete enough that it should **not** be redone unless new test evidence contradicts it.

## Core rule: record facts, not guessed semantics

- method invocation -> `CALLS`
- `GETFIELD` / `GETSTATIC` -> `READS_FIELD`
- `PUTFIELD` / `PUTSTATIC` -> `WRITES_FIELD`
- reliable descriptor/type instruction -> `REFERENCES_TYPE`
- numeric/string literal -> typed `CONSTANT`

A raw integer such as `762` is **not** automatically an interface, animation, opcode, model, etc. `LITERAL_ID` requires domain/context evidence.

## Scanner direction

Extend the existing `AtlasScanner`; do not create a competing scanner.

Phase 2 reader target:

```text
ClassReader.SKIP_FRAMES
```

Per-method relationships should aggregate repeated identical edges using a key like:

```text
fromId + relationshipType + target
```

Preserve occurrence count and bounded source-line evidence.

## Relationship schema v2 target

```text
fromId
type
target
sourcePath
sourceLine
opcode
occurrenceCount
detail (optional)
```

Schema version increments when this lands so old Phase 1 generated data is explicitly rejected/rescanned.

Add a distinct dynamic-call relationship type for `invokedynamic`; never mislabel it as a proven direct call.

## Type/constant/source targets

Collect type references from descriptors, signatures, exceptions, relevant type instructions, instruction owners/descriptors, `Type` LDC values, bootstrap handles/dynamic constants where reliable.

Capture constants with typed targets such as:

```text
int:762
long:1234
float:1.0
string:Attack
```

Use `visitSource` and `visitLineNumber` where debug data exists. Resolve source ownership to `Client/src/main/java/<owner>.java`, stripping `$...` for inner/anonymous classes when locating the Java source file. Do not claim a source location when evidence is absent.

## Phase 2 query/index direction

Do not add SQLite before measurement.

Likely in-memory investigation maps:

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

Bounded neighborhood starting limits:

- default depth 1
- optional depth 2
- node cap 100
- edge cap 500
- always report schema/fingerprint/truncation

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status:** NEEDS TEST

### Bundle 1A - Foundation + usable developer workflow

- [x] **1A.1 Targeted implementation discovery**.
- [x] **1A.2 Atlas schema + persistence skeleton**.
- [x] **1A.3 Bytecode scanner MVP**.
- [x] **1A.4 Basic query/export CLI**.
- [x] **1A.5 Standalone Client Atlas Control implementation** - no-argument UI, scan/status/search/export/open-workspace controls, one-click Phase 1 automated check. **Implementation complete; UI/runtime verification pending.**

### Phase 1 gate state

Already runtime-confirmed:

- [x] Atlas compiles/runs in the user's Eclipse + Java 8 setup after Gradle project refresh.
- [x] Offline scan completes with non-zero counts.
- [x] Metadata reopens without rebuild.
- [x] Persisted fingerprint reports current.

Still required to close Phase 1:

- [ ] Pull/run the new no-argument `ClientAtlasMain` UI.
- [ ] Click **Run Phase 1 Check** and receive `PHASE 1 AUTOMATED CHECK: PASS`.
- [ ] Confirm search works from the UI.
- [ ] Confirm `Export Last Result` writes under `.client-atlas/exports/`.
- [ ] Later, when a real compiled-client change naturally occurs, verify stale-index rejection before rescanning. This is a guard regression check and does not require intentionally corrupting/mutating the client just to unblock Phase 2 if all automated gate checks pass.

Phase 1 must remain `NEEDS TEST` until the standalone UI/check is runtime-confirmed.

## Phase 2 - Static Relationship and Investigation Map

**Status:** PREPARED - IMPLEMENTATION GATED ON PHASE 1 CHECK

### Bundle 2A - Structural relationships

- [x] **2A.0 Targeted relationship architecture discovery**.
- [ ] **2A.1 Relationship schema v2 + source locator** - typed occurrence/source fields, compiled/source path separation, dynamic-call relationship support, schema bump/rescan guard.
- [ ] **2A.2 Calls + field access scanner** - `CALLS`, `READS_FIELD`, `WRITES_FIELD`, per-method aggregation.
- [ ] **2A.3 Type references + constants** - descriptors/signatures/type instructions + typed constants; no automatic semantic IDs.
- [ ] **2A.4 Structural verification + size metrics**.

### Bundle 2B - Investigation search

- [ ] **2B.1 In-memory investigation index**.
- [ ] **2B.2 Ranked/friendly search**.
- [ ] **2B.3 Relationship queries + bounded neighborhoods**.
- [ ] **2B.4 Assistant-oriented export**.
- [ ] **2B.5 Safe initial domain correlation**.

## Phase 3 - Runtime Evidence and Knowledge

**Status:** PLANNED

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

**Status:** PLANNED

- [ ] Search panel.
- [ ] Symbol detail/relationship navigation.
- [ ] Evidence/alias editor.
- [ ] Trace browser/controls.
- [ ] Optional bounded relationship graph.

## Phase 5 - Advanced Correlation

**Status:** BACKLOG

- [ ] Repeated-path clustering.
- [ ] Suggested aliases remain `HYPOTHESIS` until proven.
- [ ] Reliable cache/definition crosslinks.
- [ ] Revision/fingerprint diffs.
- [ ] Investigation report generation.

# Testing

## Current shortest Phase 1 session

1. `git pull origin main`.
2. If ASM ever becomes unresolved again, Eclipse -> **Gradle -> Refresh Gradle Project**.
3. Run `game.atlas.ClientAtlasMain` with **no program arguments**.
4. Confirm the **Client Atlas Control** window opens and shows the existing current index.
5. Click **Run Phase 1 Check**.
6. Confirm the output ends with `PHASE 1 AUTOMATED CHECK: PASS`.
7. Search `Class1`.
8. Click **Export Last Result** and confirm the output reports the file path.

No repeated run-configuration argument editing is required anymore.

## Phase 2 checks later

- Known outgoing `CALLS` and corresponding incoming/called-by path.
- Known field GET/PUT read/write direction.
- Type references without semantic renaming.
- Typed constants without automatic domain IDs.
- Deduplicated occurrence aggregation.
- Reliable source path/line evidence where debug data exists.
- Measure symbol/relationship/file-size/query-time growth before changing persistence/caps.

# Carryover / blockers

## CARRYOVER

- Advanced automatic correlation remains usage-driven backlog.

## GATED

- Phase 2 code stays gated until the new standalone UI/one-click Phase 1 check passes once locally.

# Resume Here

**Last completed implementation:**

- Phase 1 / Bundle 1A / **1A.5 Standalone Client Atlas Control implementation**.
- Phase 2 prep / Bundle 2A / **2A.0 Targeted relationship architecture discovery**.

**Runtime evidence already confirmed:**

- Eclipse ASM dependency resolves after Gradle project refresh.
- Scan: 1221 class files / 33742 symbols / 34053 relationships.
- Fingerprint: `41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26`.
- Persisted metadata reopens with `Current fingerprint: true`.

**Current state:**

- Atlas engine/CLI exists and scanner/status have passed locally.
- `ClientAtlasMain` with no args now opens `ClientAtlasControl`.
- Control UI wraps scan/status/search/export/open-workspace and one-click Phase 1 automated verification.
- Normal `game.RS3Applet` ownership is unchanged.
- Phase 2 architecture is prepared but no Phase 2 scanner code has been added.

**Next required action:**

- Pull once, run `ClientAtlasMain` with no arguments, click **Run Phase 1 Check**, then search/export once from the UI.

**Next implementation after the gate passes:**

- **2A.1 Relationship schema v2 + source locator**.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree
- unrelated server/gameplay systems
- Phase 2 relationship architecture
- runtime tracing hooks before Phase 3
- Client Console internals before Phase 4

**Pending verification:**

- standalone UI opens under Eclipse/Java 8
- one-click Phase 1 check passes
- UI search/export works
- stale-index rejection on a later natural compiled-client change

**Blockers:**

- None technical; only the short UI runtime gate remains.

# Next recommended work

**Run the standalone Client Atlas Control once.** If `Run Phase 1 Check` passes, close Phase 1 and immediately begin **2A.1 Relationship schema v2 + source locator** without another discovery cycle.
