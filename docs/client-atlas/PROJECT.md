# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations can start from known evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. A Client Console browser may consume the same data later, but the Atlas data/search layer is the product.

The finished system should answer questions such as:

- What references this class, field, method, interface/component ID, packet/opcode, model, animation, GFX, cache index, item, NPC, object, varp, or varbit?
- Who calls this method and what does it call?
- Which fields does a method read or write?
- What code path actually executed when a specific action was performed in the running client?
- What has already been verified about an obfuscated symbol?
- What evidence supports a semantic alias or behavior classification?

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
- Pretending original Jagex names can be recovered when they are absent.
- Promoting guessed semantics to verified status.
- Replacing client runtime/system ownership with tooling.
- Always-on method logging that materially hurts client performance.
- Building the visual graph/UI before the static Atlas/search workflow proves useful.
- Scanning unrelated server systems unless a client finding specifically requires correlation.

## Architecture / ownership

- Atlas is developer/reverse-engineering tooling, not gameplay authority.
- Existing client runtime, cache, networking, renderer, interface, input, and definition systems remain authoritative.
- Atlas owns generated reverse-engineering metadata, search indexes, aliases, evidence, and trace-session records.
- Original obfuscated names remain the permanent primary identifiers.
- Runtime instrumentation must be explicit, bounded, and switchable.
- UI code must consume Atlas data/APIs rather than duplicate discovery logic.

## Phase 1 implementation architecture

Targeted implementation discovery is complete.

### Scanner ownership

The static Atlas starts as an **offline client-side CLI/tool**, isolated from Client Console and normal game startup.

Proposed source package:

```text
Client/src/main/java/game/atlas/
```

Proposed entry point:

```text
game.atlas.ClientAtlasMain
```

The static scanner must not require `RS3Applet` or a logged-in client.

### Scan input

Primary scan input:

```text
Client/build/classes/java/main/
```

This is the normal compiled-class output of the existing Java Gradle project and avoids scanning classes expanded from `clientlibs.jar` into the fat application jar.

The Atlas package itself must be excluded from scans:

```text
game/atlas/**
```

The first implementation test must confirm the actual local class-output path rather than silently assuming it.

### Build/dependency direction

- Keep Java 8 source/target compatibility.
- Keep the existing `game.RS3Applet` application main unchanged.
- Add Atlas as a separate runnable main/tool; do not turn normal client launch into Atlas launch.
- Prefer a mature bytecode library rather than writing a custom JVM class-file parser.
- **ASM is the preferred parser direction.** Pin an explicit Java-8-compatible version during the implementation patch after compatibility verification.
- Use a small JSON library only if needed for robust JSONL persistence; do not add a database/native dependency for the MVP.
- Do not assume `clientlibs.jar` provides bytecode/indexing support. No such dependency is declared by the client build.

### Persistence direction

Use simple portable text records first rather than a binary database.

Preferred MVP layout:

```text
Client/.client-atlas/
    metadata.json
    symbols.jsonl
    relationships.jsonl
    evidence.jsonl
    traces/
```

`Client/.client-atlas/` should be ignored by Git and survive normal `build/` cleaning.

Search can load compact indexes into memory on demand. Do not introduce SQLite until measured Atlas size/search behavior proves it is necessary.

### Assistant-visible knowledge

A purely local ignored database would not fully satisfy the main goal because future repository investigations need reusable evidence.

Use two layers:

1. **Local working Atlas** - full generated/indexed data under `Client/.client-atlas/`.
2. **Repository-visible knowledge/export** - explicit compact text exports under `docs/client-atlas/` for durable verified knowledge and targeted investigation snapshots.

Candidate durable paths:

```text
docs/client-atlas/knowledge/
docs/client-atlas/snapshots/
```

Do not commit a massive generated dump by default. Measure the first real index before deciding whether a full static snapshot is small enough to keep in Git. Targeted neighborhood exports must remain available regardless.

### Revision/staleness identity

The static scan should calculate a deterministic fingerprint from the scanned class inputs, preferably SHA-256 over sorted relative class paths plus class bytes.

Evidence/index metadata must record that fingerprint so stale discoveries can be detected after client changes.

### Later Client Console integration

The existing human tooling lives under:

```text
Client/src/main/java/game/console/
```

`game.RS3Applet` already integrates `game.console.ClientConsoleShell` and `ConsolePreferences`.

A later Atlas browser belongs with that existing console UI, but the console must consume Atlas output rather than own scanning/indexing.

## Verified foundation

### VERIFIED

- None yet. No Client Atlas runtime/source implementation exists.

### verified-static

- Repository rules require original obfuscated class/field/method names to be preserved unless explicit renaming is approved.
- Repository rules define `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` as the reverse-engineering evidence vocabulary.
- Client project root is `Client/`.
- Client source root is `Client/src/main/java/`.
- Source roots currently include `game`, `com`, `jaclib`, `jagdx`, `jaggl`, and `vartracker`.
- The `game` package contains the large obfuscated/decompiled client class set plus named/custom client code.
- `Client/build.gradle` applies Java/application plugins, targets Java 8, uses `game.RS3Applet` as the normal main class, and declares only `lib:clientlibs` as its implementation dependency.
- `Client/settings.gradle` names the project `Matrix3-Client` and enables Maven Central plus the local `lib` directory.
- `Client/lib/` currently contains one `clientlibs.jar`.
- The Gradle wrapper is 8.7.
- `Client/.gitignore` currently ignores `bin/`, `build/`, and `.gradle/`.
- Client Console is integrated from `game.RS3Applet` through `game.console.ClientConsoleShell` / `ConsolePreferences`.
- Existing developer UI/tool classes live in `Client/src/main/java/game/console/`.

## Unknown / research needed

### HYPOTHESIS

- ASM-based bytecode indexing plus JSONL persistence is the smallest professional implementation for the static Atlas MVP.
- Loading symbols/relationships into in-memory maps will be fast enough for this client without SQLite.
- A compact committed snapshot may be practical, but its size must be measured after the first scan.

### UNKNOWN

- Exact ASM/JSON library versions to pin while preserving Java 8 compatibility.
- First real Atlas symbol/relationship counts and generated file sizes.
- Whether full Git-visible static snapshots are small enough to commit cleanly.
- Which high-level runtime hooks are safest for Phase 3 tracing; intentionally deferred until static Atlas is useful.

## Evidence model

Conceptual records:

```text
Symbol
- stable Atlas id
- original owner/class name
- original member name
- descriptor/signature
- symbol kind
- source/class location

Relationship
- from symbol
- relation type
- to symbol/value
- evidence source

Evidence
- subject
- status: VERIFIED | verified-static | HYPOTHESIS | UNKNOWN
- optional semantic alias
- concise claim
- supporting symbol/trace references
- client fingerprint

Trace Session
- session id/name/time
- target/action description
- observed events/values
- correlation back to Atlas symbols
```

Aliases never replace original names.

## Search requirements

Atlas should eventually support concise queries such as:

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

Results should return a small ranked neighborhood rather than force consumers to inspect the entire index.

## Performance / safety rules

- Normal client startup/gameplay must not require an Atlas rebuild.
- Static indexing runs explicitly against compiled client inputs.
- Atlas failure must not prevent normal client operation.
- Runtime tracing is opt-in and bounded.
- Expensive graph expansion is query-driven.
- Generated data must not silently pollute Git or normal build outputs.
- Static scanner work should require no client login/game runtime.

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status:** ACTIVE

**Goal:** Produce the smallest useful offline machine-readable Atlas that can index the compiled client, reopen its data, search an exact symbol, and export a compact result.

### Bundle 1A - Implementation foundation

**Status:** ACTIVE

Checklist:

- [x] **1A.1 Targeted implementation discovery** - locate client build/source/tooling ownership and choose the implementation path.
- [ ] **1A.2 Atlas schema + persistence skeleton** - create isolated Atlas package, record models, metadata/fingerprint, and local data directory handling.
- [ ] **1A.3 Bytecode scanner MVP** - index classes, fields, methods, constructors, descriptors, inheritance, and implemented interfaces.
- [ ] **1A.4 Basic query/export CLI** - exact symbol lookup plus compact machine-readable export.

**Phase 1 gate:**

- Java 8/Eclipse-compatible build.
- Atlas runs without launching/logging into the client.
- Compiled client classes can be indexed.
- Index can be reopened without rebuild.
- One known obfuscated class/method can be found exactly.
- Compact export works.
- Normal `game.RS3Applet` launch ownership is unchanged.

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
- [ ] Optional bounded graph for the selected neighborhood only.

## Phase 5 - Advanced Correlation

**Status:** BACKLOG

### Bundle 5A - Usage-driven automation

- [ ] Repeated-path clustering.
- [ ] Suggested aliases kept as `HYPOTHESIS` until proven.
- [ ] Cross-link reliable cache/definition metadata.
- [ ] Atlas diff between client revisions/fingerprints.
- [ ] Investigation report generation.

## Current execution state

- Phase: **Phase 1 - Static Atlas Foundation**
- Active bundle: **Bundle 1A - Implementation foundation**
- Approval state: **SAP AAA completed for checklist 1A.1 discovery/documentation only.**
- Last completed checklist item: **1A.1 Targeted implementation discovery**
- Next checklist item: **1A.2 Atlas schema + persistence skeleton**

## Status table

| Area | Status | Notes |
| --- | --- | --- |
| Targeted implementation discovery | DONE | Client/build/tooling path established. |
| Atlas schema + persistence skeleton | READY | Next implementation patch. |
| Bytecode scanner MVP | READY | Follows stable schema/fingerprint. |
| Basic query/export CLI | READY | Completes Phase 1 static foundation. |
| Static relationship map | PLANNED | Phase 2. |
| Runtime traces/evidence | PLANNED | Phase 3. |
| Client Console browser | PLANNED | Phase 4. |
| Advanced correlation | CARRYOVER | Phase 5 usage-driven backlog. |

## Decisions / new ideas

- Client Atlas is primarily for future assistant/code investigation; human UI is secondary.
- The searchable data layer is authoritative for Atlas; Client Console is only a viewer/controller later.
- Preserve original obfuscated names permanently unless separately approved.
- Do not create one giant human text dump as the primary interface.
- Bytecode-first static evidence precedes runtime tracing.
- Runtime tracing remains short-lived and scoped.
- Automatically suggested semantics remain `HYPOTHESIS` until evidence supports promotion.
- Start with portable text persistence; do not add SQLite before measurement proves a need.
- Keep full local generated data separate from curated repository-visible knowledge/snapshots.

## Testing

### Phase 1 quick/high-value checks

1. Compile the Client under Java 8-compatible source/target settings.
2. Run Atlas against the compiled class directory.
3. Reopen the generated Atlas without rebuilding it.
4. Search one exact obfuscated class and method.
5. Export that symbol's compact record.
6. Confirm normal client main class/configuration remains unchanged.

### Later deeper checks

1. Change/rebuild one client input and confirm fingerprint/staleness handling.
2. Record one targeted runtime trace.
3. Correlate the trace to indexed symbols.
4. Save an alias/evidence classification and confirm it survives re-indexing.

### Smoke/regression

- Offline Phase 1 work should not affect normal gameplay smoke coverage.
- Any later runtime instrumentation must include a normal client launch with Atlas tracing disabled.

## Carryover / blockers

### CARRYOVER

- Advanced automatic correlation remains intentionally deferred until real Atlas usage identifies high-value automation.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Phase 1 / Bundle 1A / checklist **1A.1 Targeted implementation discovery**.

**Current state:**

- Client Atlas architecture is documented.
- Exact client project/build/tooling path is established.
- No Atlas source code exists yet.
- Normal Client Console/runtime source has not been modified for Atlas.

**Next action:**

- Execute **1A.2 Atlas schema + persistence skeleton** only after AAA/SAP AAA for that implementation step.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/client-atlas/PROJECT.md`
- `docs/rs3/WORKSTREAMS.md`
- `Client/build.gradle`
- `Client/settings.gradle`
- `Client/gradle/wrapper/gradle-wrapper.properties`
- `Client/.gitignore`
- `Client/lib/` listing
- `Client/src/main/java/` root listing
- `Client/src/main/java/game/` direct listing only
- `Client/src/main/java/game/RS3Applet.java` beginning/integration imports
- `Client/src/main/java/game/console/` direct listing only
- `docs/client-console/patchnotes.txt`

**Do not re-scan without new evidence:**

- The above build/layout paths.
- Broad `game` package/source tree.
- Unrelated server/gameplay systems.
- Runtime tracing hooks until Phase 3.

**Pending runtime verification:**

- None for discovery.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Exact Java-8-compatible dependency versions to pin.
- Real index size/performance until the first scan exists.

## Next recommended work

**Phase 1 -> Bundle 1A -> 1A.2 Atlas schema + persistence skeleton.** Create the isolated offline Atlas foundation without changing normal client runtime ownership.