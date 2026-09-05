# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations can start from known evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. A Client Console browser may consume the same data later, but the Atlas data/search layer is the product.

The finished system should answer questions such as:

- What references this class, field, method, interface/component ID, packet/opcode, model, animation, GFX, cache index, item, NPC, object, varp, or varbit?
- Who calls this method and what does it call?
- Which fields does a method read or write?
- What code path actually executed for a targeted runtime action?
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

## Phase 1 implementation architecture

### Scanner ownership

The static Atlas is an **offline client-side CLI/tool**, isolated from Client Console and normal game startup.

Source package:

```text
Client/src/main/java/game/atlas/
```

Entry point:

```text
game.atlas.ClientAtlasMain
```

Normal `game.RS3Applet` startup remains unchanged.

### Scan input

Primary input:

```text
Client/build/classes/java/main/
```

This avoids scanning classes expanded from `clientlibs.jar` into the fat application jar.

Atlas excludes its own classes from the client fingerprint/scan:

```text
game/atlas/**
```

### Build/dependency direction

- Keep Java 8 source/target compatibility.
- Keep `game.RS3Applet` as the normal client main.
- Add Atlas as a separate runnable tool only.
- Prefer a mature bytecode library rather than a custom JVM class-file parser.
- **ASM remains the preferred scanner parser direction.** Pin an explicit Java-8-compatible version in checklist 1A.3 after compatibility verification.
- Do not add SQLite/native persistence before measured size/performance requires it.
- Checklist 1A.2 intentionally adds no new external dependency.

### Persistence direction

The JDK-only foundation uses:

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
- `metadata.properties` is used for dependency-free schema/fingerprint/count persistence and reopen checks.
- Generated `symbols.jsonl` and `relationships.jsonl` are reset by a fresh Atlas initialization.
- Curated/future `evidence.jsonl` and trace storage are preserved by initialization.
- JSONL record serialization is added with the scanner/query work; no home-grown JSON parser is introduced just for 1A.2.
- Search should load compact indexes into memory on demand before considering a database.

### Assistant-visible knowledge

Use two layers:

1. **Local working Atlas** - full generated/indexed data under `Client/.client-atlas/`.
2. **Repository-visible knowledge/export** - compact durable exports under `docs/client-atlas/`.

Candidate durable paths:

```text
docs/client-atlas/knowledge/
docs/client-atlas/snapshots/
```

Do not commit a massive generated dump by default. Measure the first real index first.

### Revision/staleness identity

`AtlasFingerprint` calculates deterministic SHA-256 over sorted compiled client `.class` relative paths, file sizes, and bytes while excluding `game/atlas/**`.

This lets Atlas distinguish client changes from Atlas-tool-only changes and flag stale local evidence/index data later.

### Later Client Console integration

Existing human tooling remains under:

```text
Client/src/main/java/game/console/
```

A later Atlas browser belongs there, but Client Console must consume Atlas output rather than own scanning/indexing.

## Verified foundation

### VERIFIED

- None yet; runtime Atlas behavior has not been user-tested.

### verified-static

- Repository rules require original obfuscated names to be preserved unless explicit renaming is approved.
- Repository rules define `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` as reverse-engineering evidence labels.
- Client project root is `Client/` and source root is `Client/src/main/java/`.
- Source roots include `game`, `com`, `jaclib`, `jagdx`, `jaggl`, and `vartracker`.
- `Client/build.gradle` targets Java 8, uses `game.RS3Applet` as normal main, and declares `lib:clientlibs` as its implementation dependency.
- `Client/settings.gradle` names the project `Matrix3-Client` and enables Maven Central plus local `lib`.
- `Client/lib/` contains `clientlibs.jar`.
- Gradle wrapper is 8.7.
- Client Console integrates through `game.console.ClientConsoleShell` / `ConsolePreferences`.
- Atlas source foundation now exists under `Client/src/main/java/game/atlas/`.
- `AtlasSchema` defines stable symbol, relationship, evidence, and metadata models without semantic renaming.
- `AtlasFingerprint` provides deterministic client-class SHA-256 fingerprinting and excludes Atlas classes.
- `AtlasWorkspace` owns `.client-atlas` paths, metadata persistence/reopen, workspace initialization, and stale/current fingerprint comparison.
- `ClientAtlasMain` is a separate offline `init`/`status` entry point and does not change normal client startup ownership.

## Unknown / research needed

### HYPOTHESIS

- ASM-based bytecode indexing plus JSONL persistence remains the smallest professional scanner MVP.
- In-memory maps will likely be sufficient before SQLite is needed.
- A compact committed snapshot may be practical, but size must be measured after the first scan.

### UNKNOWN

- Exact Java-8-compatible ASM version to pin for 1A.3.
- First real symbol/relationship counts and generated file sizes.
- Whether full Git-visible static snapshots are small enough to commit cleanly.
- Safest high-level runtime hooks for Phase 3 tracing; intentionally deferred.

## Evidence model

### Symbol

- stable Atlas id
- original owner/class name
- original member name
- descriptor/signature
- symbol kind
- source/class location
- access flags

Stable IDs keep original names visible, for example:

```text
CLASS:game/Class387
METHOD:game/Class387#method4844(II)V
FIELD:game/Class540#anInt7134:I
```

### Relationship

- from symbol id
- relation type
- target symbol/value
- optional detail/evidence source

### Evidence

- subject id
- status: `VERIFIED | verified-static | HYPOTHESIS | UNKNOWN`
- optional alias
- concise claim
- supporting references
- client fingerprint

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

Results should return a small ranked neighborhood rather than force inspection of the full index.

## Performance / safety rules

- Normal client startup/gameplay must not require an Atlas rebuild.
- Static indexing runs explicitly against compiled client inputs.
- Atlas failure must not prevent normal client operation.
- Runtime tracing is opt-in and bounded.
- Expensive graph expansion is query-driven.
- Generated data must not pollute Git or normal build outputs.
- Static scanner work requires no login/game runtime.

# Development plan

Use `Idea -> Phase -> Bundle -> Patch/Checklist`.

## Phase 1 - Static Atlas Foundation

**Status:** ACTIVE

**Goal:** Produce the smallest useful offline machine-readable Atlas that can index the compiled client, reopen its data, search an exact symbol, and export a compact result.

### Bundle 1A - Implementation foundation

**Status:** ACTIVE

Checklist:

- [x] **1A.1 Targeted implementation discovery** - locate client build/source/tooling ownership and choose implementation path.
- [x] **1A.2 Atlas schema + persistence skeleton** - isolated Atlas package, stable record models, deterministic fingerprint, local workspace, metadata reopen/staleness foundation, and ignored generated data.
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

Phase 1 remains `ACTIVE`; compile/run/query verification is consolidated after 1A.3/1A.4 rather than forcing repeated PC test sessions.

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

## Current execution state

- Phase: **Phase 1 - Static Atlas Foundation**
- Active bundle: **Bundle 1A - Implementation foundation**
- Approval state: **SAP AAA completed for checklist 1A.2.**
- Last completed checklist item: **1A.2 Atlas schema + persistence skeleton**
- Next checklist item: **1A.3 Bytecode scanner MVP**

## Status table

| Area | Status | Notes |
| --- | --- | --- |
| Targeted implementation discovery | DONE | Client/build/tooling path established. |
| Atlas schema + persistence skeleton | DONE | JDK-only offline foundation implemented; consolidated Phase 1 compile/run test still pending. |
| Bytecode scanner MVP | READY | Next implementation step; ASM compatibility/version choice belongs here. |
| Basic query/export CLI | READY | Follows scanner data generation. |
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
- Keep 1A.2 dependency-free; pin ASM only when the scanner is introduced.
- Metadata uses `metadata.properties` for robust Java-8/JDK-only reopen support; generated index records remain JSONL-oriented.

## Testing

### Phase 1 quick/high-value checks

1. Compile the Client under Java 8-compatible source/target settings.
2. Run `game.atlas.ClientAtlasMain init` against compiled classes.
3. Run `game.atlas.ClientAtlasMain status` and confirm persisted metadata reopens.
4. Confirm the fingerprint reports current before client-class changes and stale after a real client-class change/rebuild.
5. After 1A.3, search one exact obfuscated class and method.
6. After 1A.4, export that symbol's compact record.
7. Confirm normal `game.RS3Applet` main/configuration remains unchanged.

### Smoke/regression

- Offline Phase 1 work should not affect normal gameplay smoke coverage.
- Any later runtime instrumentation must include a normal client launch with Atlas tracing disabled.

## Carryover / blockers

### CARRYOVER

- Advanced automatic correlation remains deferred until real Atlas usage identifies high-value automation.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Phase 1 / Bundle 1A / checklist **1A.2 Atlas schema + persistence skeleton**.

**Current state:**

- Offline Atlas foundation exists under `Client/src/main/java/game/atlas/`.
- Stable schema/evidence vocabulary is encoded without renaming client symbols.
- Deterministic client-class fingerprinting exists and excludes Atlas classes.
- `.client-atlas` workspace/metadata initialization and reopen/staleness support exist.
- Normal Client Console/game runtime ownership remains untouched.
- No bytecode parser/scanner has been added yet.

**Next action:**

- Execute **1A.3 Bytecode scanner MVP**: pin a Java-8-compatible ASM dependency and write the smallest scanner for classes, fields, methods, constructors, descriptors, inheritance, and implemented interfaces.

**Files/systems already inspected or changed:**

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
- `Client/src/main/java/game/atlas/AtlasSchema.java`
- `Client/src/main/java/game/atlas/AtlasFingerprint.java`
- `Client/src/main/java/game/atlas/AtlasWorkspace.java`
- `Client/src/main/java/game/atlas/ClientAtlasMain.java`
- `docs/client-atlas/patchnotes.txt`

**Do not re-scan without new evidence:**

- Broad `game` package/source tree.
- Unrelated server/gameplay systems.
- Runtime tracing hooks until Phase 3.
- Client Console internals until Phase 4.

**Pending verification:**

- Consolidated Phase 1 local Java 8/Eclipse compile/run checks are pending; no game-runtime verification is needed for 1A.2 itself.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Exact Java-8-compatible ASM version to pin for 1A.3.
- Real index size/performance until the first scanner run exists.

## Next recommended work

**Phase 1 -> Bundle 1A -> 1A.3 Bytecode scanner MVP.** Add the bytecode parser and generate the first real symbol index without touching normal client runtime ownership.
