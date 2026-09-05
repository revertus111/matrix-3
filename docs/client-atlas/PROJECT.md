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
    phase2-investigation-check.txt
    exports/
    traces/
```

Rules:

- `.client-atlas/` is ignored by Git and survives normal build cleaning.
- Fresh scans replace generated symbol/relationship data but preserve evidence/traces.
- Scanner publishes generated data through temporary files.
- Fingerprints are checked before/after scan so changed compiled classes reject the scan.
- Query/export rejects stale or incompatible-schema generated data.
- JSONL remains the persistence authority.
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

Local structural verification completed with:

```text
PHASE 2 STRUCTURAL CHECK: PASS
```

Verified measurements:

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

The verifier PASS means mandatory assertions succeeded for schema-v2 record shape, generated `CALLS`, `READS_FIELD`, `WRITES_FIELD`, `REFERENCES_TYPE`, typed `CONSTANT`, zero automatic `LITERAL_ID` promotion, exact-query/export regression, and evidence/trace preservation.

**Decision from measurements:** keep portable JSONL + in-memory investigation indexes. The current ~83 MiB generated static dataset and ~1.3 second rebuild do not justify SQLite/native persistence.

Bundle 2A is **DONE**.

## verified-static

- `Client/build.gradle` targets Java 8 and keeps `game.RS3Applet` as normal main.
- Atlas remains isolated under `game.atlas`.
- `AtlasFingerprint` excludes Atlas classes.
- `AtlasWorkspace` owns local persistence/schema/current checks.
- `AtlasScanner` owns bytecode scanning.
- `AtlasQueryEngine` owns exact streaming query/export.
- `ClientAtlasControl` is the standalone human control surface over shared Atlas APIs.
- `AtlasStructuralVerifier` owns Bundle 2A structural verification/measurement.
- `AtlasInvestigationIndex` owns the Phase 2B in-memory acceleration layer; it does not scan or replace JSONL persistence.
- `AtlasSearchEngine` owns ranked/friendly symbol resolution; it never changes authoritative IDs or silently selects ambiguous candidates.
- `AtlasRelationshipQueryEngine` owns bounded relationship filtering/traversal over already-recorded bytecode facts.
- `AtlasInvestigationVerifier` owns the consolidated Bundle 2B local search/investigation gate and does not rescan compiled classes.
- UI/CLI consume the same investigation/search/relationship APIs instead of duplicating resolution logic.

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
- If an aggregated edge uses different opcodes, stored opcode becomes null rather than claiming one instruction represents all occurrences.
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

# Phase 2B investigation/search

## 2B.1 implementation

`AtlasInvestigationIndex` is an immutable, on-demand acceleration layer over the existing schema-v2 JSONL.

It intentionally does **not** rescan compiled classes, create another persistent database, rename symbols, infer semantic meaning, or replace scanner/query ownership.

Load safety:

1. Read persisted metadata.
2. Require current schema version.
3. Require current client fingerprint before load.
4. Require generated symbol/relationship files.
5. Parse current JSONL into compact entries.
6. Require loaded symbol/relationship totals to exactly match metadata.
7. Recheck client fingerprint after load so a concurrent rebuild cannot silently produce a mixed index.

Lookup maps:

- exact symbol ID -> symbol entry
- normalized owner -> candidate symbols
- normalized member/class name -> candidate symbols
- normalized owner+name -> candidate symbols
- source symbol ID -> outgoing relationships
- relationship target -> incoming relationships
- typed constant target -> referencing relationships

A temporary load-time string canonicalization pool reuses repeated IDs, targets, and source paths so the ~325k relationship dataset does not needlessly duplicate String objects.

## 2B.2 implementation

`AtlasSearchEngine` provides deterministic ranked/friendly symbol resolution over `AtlasInvestigationIndex`.

Supported forms include:

```text
CLASS:game/Class387
game/Class387
game.Class387
Class387
Class387.method4844
Class387.method4844(II)V
method4844
```

Rules:

- Canonical Atlas IDs remain the highest-authority lookup form.
- Exact class-owner paths resolve class/interface/enum/annotation symbols.
- Simple exact names resolve only when one candidate exists.
- Owner/member shorthand resolves a member only when one candidate/overload exists.
- Optional method descriptors can select an exact overload.
- Multiple exact-name or overload matches are returned as candidates; Atlas never silently chooses one.
- Prefix/contains fallback is deterministic and capped at 50 candidates.
- Fuzzy/partial matches never auto-resolve, even when only one fuzzy result remains.
- Match output preserves kind, exact stable ID, match reason, source path where available, full candidate count, truncation state, and search time.

CLI automation path:

```text
ClientAtlasMain search "Class387"
ClientAtlasMain search "Class387.method4844"
ClientAtlasMain search "method4844"
```

## 2B.3 implementation

`AtlasRelationshipQueryEngine` adds bounded investigation commands over the same in-memory index/search engine:

```text
calls <symbol>
called-by <symbol>
reads <field-or-symbol>
written-by <field>
references <type-or-class>
constant 762
constant int:762
neighbors <symbol> depth=1
neighbors <symbol> depth=2
```

Behavior:

- ambiguous/fuzzy operands return search candidates before traversal; no silent overload selection,
- `calls` / `called-by` filter `CALLS` in the correct direction,
- class-level call/read queries expand through declared methods so a class query is useful even though bytecode edges originate from methods,
- `reads <field>` returns incoming `READS_FIELD`; method/class reads return outgoing reads,
- `written-by` requires a field and returns incoming `WRITES_FIELD`,
- `references` accepts a class-like symbol or neutral `TYPE:<internal-name>` and returns incoming `REFERENCES_TYPE`,
- raw integer constants search both normalized `int:` and `long:` forms where valid; decimals search normalized float/double forms; typed targets remain authoritative,
- generic numeric constants remain structural facts and never become domain IDs automatically,
- normal relationship results cap at **500 edges**,
- neighborhoods support depth **1-2**, cap at **100 nodes / 500 edges**, and explicitly report truncation,
- relationship output preserves source path/line, opcode, and occurrence count where available.

Standalone control now uses one **Search / Investigate** box for friendly symbols and relationship commands. It caches a current investigation snapshot, invalidates it when generated data changes/stales, and reuses the same APIs as CLI automation.

The former **Run Phase 2 Check** label is now **Run Structural Check** because that action verifies Bundle 2A structural data rather than all of Phase 2.

## Bundle 2B verifier

`AtlasInvestigationVerifier` performs the consolidated local 2B.1-2B.3 gate **without rescanning** compiled classes.

It checks:

- current schema/fingerprint,
- investigation-index counts equal persisted metadata,
- exact + friendly Class1 resolution,
- exact owner/member shorthand with descriptor,
- ambiguous exact names remain unresolved candidates,
- fuzzy/prefix search remains bounded and non-resolved,
- one known internal `CALLS` edge in outgoing and incoming directions,
- `READS_FIELD` and `WRITES_FIELD` field directions,
- `REFERENCES_TYPE`,
- typed `CONSTANT`,
- depth-2 neighborhood caps,
- index-load/search timing and approximate used-memory delta.

Human workflow:

```text
Run ClientAtlasMain with no args -> Run Search Check
```

CLI automation:

```text
ClientAtlasMain verify-search
```

Report:

```text
.client-atlas/phase2-investigation-check.txt
```

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

**Status: NEEDS TEST**

- [x] **2B.1 In-memory investigation index implementation**.
- [x] **2B.2 Ranked/friendly search implementation**.
- [x] **2B.3 Relationship queries + bounded neighborhoods implementation** - includes standalone Search / Investigate integration and one-click Bundle 2B verification harness.
- [ ] **2B local verification gate** - `PHASE 2 INVESTIGATION CHECK: PASS` required before 2B.4.
- [ ] **2B.4 Assistant-oriented export** - gated on local investigation PASS.
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

## Current shortest Bundle 2B local session

1. `git pull origin main`.
2. Eclipse/Java 8 clean/build Client.
3. Run `game.atlas.ClientAtlasMain` with no program arguments.
4. Click **Run Search Check** once.
5. Confirm output contains:

```text
PHASE 2 INVESTIGATION CHECK: PASS
```

6. Send/copy `.client-atlas/phase2-investigation-check.txt`.

**Do not click Scan or Run Structural Check first.** The Bundle 2B verifier intentionally proves the current generated index can be loaded/searched without rescanning.

The verifier automatically covers the 2B.1-2B.3 checks listed above. Manual relationship commands are optional smoke checks, not required for the normal gate.

# Carryover / blockers

## CARRYOVER

- On a natural future client source change + rebuild boundary, confirm cached/streaming query paths refuse stale generated data before rebuild.
- >200 streaming exact-query truncation regression when a naturally suitable symbol is available.
- Destructive schema-mismatch simulation is not required for the normal Bundle 2B gate; static guards remain in place.
- Advanced automatic correlation remains usage-driven backlog.

## BLOCKERS

- No implementation blocker.
- Bundle 2B is waiting only on the one-click local investigation/search PASS.

# Resume Here

**Last completed implementation:**

- Phase 2 / Bundle 2B / **2B.3 Relationship queries + bounded neighborhoods + consolidated search verifier**.

**Current phase:**

- **Phase 2 - Static Relationship and Investigation Map**

**Active bundle:**

- **Bundle 2B - Investigation search / NEEDS TEST**

**Current/next checklist item:**

- **Bundle 2B local investigation/search verification gate**.

**Verified dataset baseline:**

- 1221 class files
- 33742 symbols
- 325826 relationships
- 8.5 MiB symbols JSONL
- 74.4 MiB relationships JSONL
- ~1.28 s full structural scan
- ~362 ms streaming exact Class1 query

**Current implementation state:**

- Schema v2/source locator complete and verified.
- CALLS/DYNAMIC_CALL/read/write scanning complete and verified.
- Type references + typed constants complete and verified.
- No automatic literal/domain-ID semantics.
- JSONL remains persistence authority; no database is justified.
- `AtlasInvestigationIndex` loads current JSONL into immutable investigation maps.
- `AtlasSearchEngine` handles exact/friendly/ranked candidate resolution safely.
- `AtlasRelationshipQueryEngine` handles bounded calls/read/write/type/constant/neighborhood investigation.
- Standalone Atlas Control exposes the shared Search / Investigate surface.
- `AtlasInvestigationVerifier` exposes one-click/CLI Bundle 2B verification without rescanning.

**Files/systems already inspected or changed for Phase 2:**

- `Client/src/main/java/game/atlas/AtlasSchema.java`
- `Client/src/main/java/game/atlas/AtlasJson.java`
- `Client/src/main/java/game/atlas/AtlasWorkspace.java`
- `Client/src/main/java/game/atlas/AtlasScanner.java`
- `Client/src/main/java/game/atlas/AtlasQueryEngine.java`
- `Client/src/main/java/game/atlas/AtlasStructuralVerifier.java`
- `Client/src/main/java/game/atlas/AtlasInvestigationIndex.java`
- `Client/src/main/java/game/atlas/AtlasSearchEngine.java`
- `Client/src/main/java/game/atlas/AtlasRelationshipQueryEngine.java`
- `Client/src/main/java/game/atlas/AtlasInvestigationVerifier.java`
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

- Pull/build once and click **Run Search Check**.
- Persist the actual investigation-index load time/memory/search evidence from the generated report.

**Next implementation after gate passes:**

- **2B.4 Assistant-oriented export**.

# Next recommended work

**Run the one-click Bundle 2B investigation/search gate.** If it reports PASS, close the gate and begin **2B.4 Assistant-oriented export**.
