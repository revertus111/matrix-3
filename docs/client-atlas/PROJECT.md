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

Phase 1 now provides exact canonical-ID investigation without a UI:

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
- `AtlasSchema` defines stable symbol/relationship/evidence/metadata models.
- `AtlasFingerprint` provides deterministic client-class SHA-256 fingerprinting and excludes Atlas classes.
- `AtlasWorkspace` owns `.client-atlas` paths, metadata persistence/reopen, layout initialization, and stale/current checks.
- `AtlasScanner` uses ASM to scan compiled declarations while intentionally skipping method bodies.
- `AtlasQueryEngine` performs exact canonical-ID lookup, stale/schema validation, bounded immediate-relationship collection, and atomic compact export.
- `ClientAtlasMain` exposes `init`, `status`, `scan`, `query`, and `export` without changing normal client startup ownership.

# Unknown / research needed

## HYPOTHESIS

- Streaming exact lookup is sufficient for Phase 1; an in-memory ranked index will likely be useful for Phase 2 search/neighborhood work.
- A compact committed snapshot may be practical, but size must be measured after the first scan.

## UNKNOWN

- First real class/symbol/relationship counts and generated file sizes.
- Exact scan/query performance on the user's local client build.
- Whether full Git-visible static snapshots are small enough to commit cleanly.
- Safest high-level runtime hooks for Phase 3 tracing; intentionally deferred.

# Evidence model

Stable IDs retain original names, for example:

```text
CLASS:game/Class387
METHOD:game/Class387#method4844(II)V
FIELD:game/Class540#anInt7134:I
```

Relationship records contain:

- source symbol id,
- relationship type,
- target symbol/internal type/value,
- optional detail.

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

**Status:** PLANNED

### Bundle 2A - Structural relationships

- [ ] Class/type/member reference edges.
- [ ] Method caller/callee edges where reliably derivable.
- [ ] Field read/write edges.
- [ ] Constants/literal-ID indexing.
- [ ] Reliable source-location enrichment.

### Bundle 2B - Investigation search

- [ ] Ranked exact/text/ID search.
- [ ] Relationship-neighborhood queries.
- [ ] Compact assistant-oriented investigation exports.
- [ ] Initial obvious domain correlation without invented semantics.

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

- Phase: **Phase 1 - Static Atlas Foundation**
- Active bundle: **Bundle 1A - Implementation foundation**
- Approval state: **SAP AAA completed for checklist 1A.4.**
- Last completed checklist item: **1A.4 Basic query/export CLI**
- Next checklist item: **Phase 1 consolidated verification gate**

## Status table

| Area | Status | Notes |
| --- | --- | --- |
| Targeted implementation discovery | DONE | Client/build/tooling path established. |
| Atlas schema + persistence skeleton | DONE | Offline foundation implemented. |
| Bytecode scanner MVP | DONE | ASM declaration scanner implemented. |
| Basic query/export CLI | DONE | Exact canonical-ID query/export with stale-index guard implemented. |
| Phase 1 verification gate | NEEDS TEST | One consolidated Java 8 build/scan/status/query/export session required. |
| Static relationship map | PLANNED | Phase 2; do not start before Phase 1 gate passes. |
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

## Smoke/regression

- Offline Phase 1 work should not affect normal gameplay smoke coverage.
- Any later runtime instrumentation must include a normal client launch with Atlas tracing disabled.

# Carryover / blockers

## CARRYOVER

- Advanced automatic correlation remains deferred until real Atlas usage identifies high-value automation.

## BLOCKED

- Phase 2 is intentionally gated on the consolidated Phase 1 verification, not technically blocked.

# Resume Here

**Last completed:**

- Phase 1 / Bundle 1A / checklist **1A.4 Basic query/export CLI**.

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

**Next action:**

- Run the **Phase 1 consolidated verification gate** from `docs/client-atlas/testlist.txt`. Do not begin Phase 2 until the required checks pass or failures are documented and patched.

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

**Pending verification:**

- Consolidated Phase 1 local Java 8/Eclipse build + scan/status/query/export/stale-index checks.

**Blockers:**

- None beyond the required Phase 1 verification gate.

**Important remaining uncertainty:**

- Real index counts/size/performance until the first local scan.
- Whether the generated index is small enough for any broader repository-visible snapshot strategy.

# Next recommended work

**Phase 1 verification gate.** Pull once, clean/build once, run the short Atlas scan/status/query/export checks, and record any failures. If the gate passes, Phase 2 / Bundle 2A becomes the next implementation target.
