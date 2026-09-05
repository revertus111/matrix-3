# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future client investigations can start from known evidence instead of repeatedly searching, tracing, and guessing through obfuscated source.

Client Atlas is primarily an engineering/research index for fast machine-assisted investigation. A Client Console UI may browse the same data later, but the database/export is the authoritative product.

The finished system should answer questions such as:

- What references this class, field, method, interface/component ID, packet/opcode, model, animation, GFX, cache index, item, NPC, object, varp, or varbit?
- Who calls this method and what does it call?
- Which fields does a method read or write?
- What code path actually executed when a specific action was performed in the running client?
- What has already been verified about an obfuscated symbol?
- What evidence supports a semantic alias or behavior classification?

## Scope

### In scope

- Persistent symbol index for client classes, fields, methods, constructors, inheritance, and implemented interfaces.
- Static relationship mapping:
  - callers/callees where determinable,
  - field reads/writes,
  - class/type references,
  - constants and literal IDs,
  - inheritance/interface relationships.
- Domain correlation where evidence permits:
  - interfaces/components,
  - menu actions,
  - packets/opcodes,
  - NPC/item/object/player definitions,
  - cache indexes,
  - models,
  - animations,
  - GFX,
  - sprites,
  - projectiles,
  - particles,
  - varps/varbits,
  - containers/inventory/equipment,
  - camera/input/rendering references.
- Search by exact obfuscated symbol, ID, alias, evidence note, domain, or related symbol.
- Runtime trace sessions that can record targeted execution/state evidence while the user performs one action in the client.
- Evidence records using `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN`.
- Human semantic aliases and notes stored externally without renaming original client symbols.
- Machine-readable exports designed for future assistant/code investigation.
- Incremental refresh so unchanged client data does not need to be rebuilt unnecessarily.
- A later human-friendly Client Console browser over the same Atlas data.

### Out of scope

- Renaming obfuscated classes, fields, or methods in source.
- Pretending original Jagex symbol names can be recovered when the information is not present.
- Automatically classifying guessed semantics as verified.
- Replacing Matrix3/client runtime ownership with Atlas tooling.
- Broad always-on tracing that materially degrades normal client performance.
- Building the visual graph/UI before the underlying index/search workflow proves useful.
- Scanning unrelated server systems unless a client finding specifically requires correlation.

## Architecture / ownership

- Matrix3 authority involved: none for gameplay ownership; Atlas observes/indexes the **718+ Client** and must not become an alternate gameplay authority.
- Tool ownership: `Client Atlas` owns generated reverse-engineering metadata, aliases, evidence, search indexes, and trace-session records.
- Existing systems that must remain authoritative: client source/runtime behavior, cache/data loaders, networking, rendering, interface handling, input handling, and other existing client systems.
- Important boundaries:
  - Original obfuscated names remain unchanged.
  - Atlas metadata is descriptive, not authoritative runtime behavior.
  - Runtime instrumentation should be targeted and switchable.
  - Generated data should live outside hand-maintained source logic.
  - A UI must consume Atlas APIs/data rather than duplicate discovery logic.

### Preferred data architecture

Use a persistent indexed store plus portable exports. Exact implementation is intentionally deferred until the current client/build dependencies are inspected.

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
- static/runtime evidence source

Evidence
- subject
- status: VERIFIED | verified-static | HYPOTHESIS | UNKNOWN
- semantic alias
- concise claim
- supporting references/trace ids
- last verified revision/hash

Trace Session
- session id/name/time
- optional target/action description
- methods/events observed
- fields/values observed
- packets/menu/interface/cache events when instrumented
- correlation back to indexed symbols
```

Portable exports should be query-friendly rather than one huge human-readable dump. JSON/JSONL or another simple Java-8-compatible representation is preferred for interchange; the internal searchable store may differ.

### Static-analysis direction

Prefer a bytecode-first or otherwise structurally reliable index for exact symbols/references, with source-location enrichment where practical. Do not commit to ASM or any new parser dependency until the existing client dependency/build environment is verified.

Reason: decompiled source can be awkward, while compiled structure provides a stronger foundation for exact class/member/reference relationships.

### Runtime-analysis direction

Use narrow trace categories and explicit recording sessions rather than globally logging every call.

Example workflow:

```text
Start Trace: NPC menu action
Perform one right-click/selection
Stop Trace
Save session
Correlate observed events with Atlas symbols
```

Runtime tracing should favor useful high-level hooks first (menu actions, packets, interfaces, input, definitions, cache/model/animation/GFX activity) and only add deeper method/field tracing when a real investigation requires it.

## Verified foundation

### VERIFIED

- None yet. No Client Atlas runtime implementation exists yet.

### verified-static

- Repository development rules require original obfuscated class/field/method names to be preserved unless explicit renaming is approved.
- Repository rules define `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` as the evidence vocabulary for reverse-engineering work.
- `docs/rs3/WORKSTREAMS.md` already identified a client architecture map as a desired shared discovery/navigation reference.
- Client Atlas is a developer/reverse-engineering tool and must remain separate from gameplay/system ownership.

## Unknown / research needed

### HYPOTHESIS

- A bytecode-first static scanner will likely provide the most reliable base for symbols and relationships while source parsing can enrich navigation.
- Existing client libraries may already provide enough bytecode/reflection/instrumentation support to avoid adding a new dependency.
- A compact local indexed store plus JSON/JSONL exports will likely give better search performance and assistant usability than flat text dumps.

### UNKNOWN

- Exact client project/package location and build/output layout relevant to Atlas implementation.
- Whether a suitable bytecode library already exists in the client dependencies.
- Best Java-8-compatible persistent index format given the existing project dependencies.
- Which existing Client Console/tool framework should host later Atlas controls.
- How much runtime instrumentation can be added cleanly without invasive changes to obfuscated client code.
- Which high-level client systems already expose hooks that Atlas can reuse.

## Dependencies

- Required systems/features:
  - **718+ Client** source/build output.
  - Java 8 / Eclipse-compatible tooling.
  - Stable way to identify client revision/build state for index invalidation.
- Optional supporting tools:
  - Existing Client Console for later human UI.
  - Existing developer logging/tool infrastructure if it has suitable reusable hooks.
- Runtime/data dependencies:
  - Client runtime only for `VERIFIED` trace evidence; static indexing must work without launching the game where practical.

## Product priorities

1. **Fast investigation for future code work.**
2. **Persistent evidence so discoveries are not repeated.**
3. **Accurate relationships over guessed semantics.**
4. **Targeted runtime proof when static evidence is insufficient.**
5. **Human UI only after the underlying data/search flow is useful.**

## Search requirements

Atlas search should eventually support:

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

Results should return a small ranked neighborhood rather than forcing consumers to inspect an entire generated dump.

A result should be able to expose:

- original symbol,
- semantic alias if one exists,
- evidence status,
- location,
- callers,
- callees,
- fields read/written,
- referenced constants/IDs,
- related domain records,
- relevant runtime traces,
- evidence notes.

## Evidence rules

Aliases never replace original names.

Example:

```text
Original: Class387.method4844
Alias: NPC menu builder
Status: verified-static
Evidence:
- references NPC definitions
- constructs menu entries
Runtime evidence: none yet
```

After runtime confirmation:

```text
Original: Class387.method4844
Alias: NPC menu builder
Status: VERIFIED
Evidence:
- static references
- runtime trace atlas-trace-00184
```

Atlas must preserve the distinction between structural fact and semantic interpretation.

## Performance / safety rules

- Normal client startup/gameplay should not require a full Atlas rebuild.
- Static indexing should run explicitly or incrementally against changed build/source inputs.
- Runtime tracing must be opt-in and category/target scoped.
- Trace buffers require sane limits so accidental sessions cannot consume unbounded memory/disk.
- Expensive graph expansion must be query-driven, not eagerly rendered for the entire client.
- Failures in Atlas tooling must not prevent normal client operation unless running a dedicated offline scanner where failure is expected to stop that scan.

## Development plan

Use `Idea -> Bundle -> Patch`.

### Bundle 1 - Atlas Foundation

**Purpose:** Establish the smallest useful machine-readable Atlas pipeline and the implementation ownership path.

**Status:** READY

**Dependencies:**

- Targeted inspection of the **718+ Client** project/build/tooling only.
- No broad repository scan.

**Patches:**

1. Targeted implementation discovery
   - Goal: identify the smallest client/tool files, build output, dependency options, and safe generated-data location needed for Atlas.
   - Likely files/systems: client build metadata, existing developer-tool entry point, directly relevant class-output/dependency configuration.
   - Verification: document exact implementation path and unresolved dependency choices before source modification.
2. Atlas schema + persistent index foundation
   - Goal: create stable symbol/relationship/evidence/trace record models and index metadata/versioning.
   - Likely files/systems: new isolated Client Atlas tool package plus generated-data directory/configuration.
   - Verification: create/open/rebuild an empty/minimal index under Java 8.
3. Static symbol scanner MVP
   - Goal: index classes, fields, methods, descriptors, inheritance, and implemented interfaces from the client.
   - Verification: exact symbol lookup returns known obfuscated symbols and stable locations.
4. Basic query/export path
   - Goal: provide a machine-friendly way to query symbols and export relevant neighborhoods without a UI.
   - Verification: exact class/method queries and portable export work from a generated index.

**Runtime tests:**

- Prefer no game launch until the static foundation needs integration validation.
- One consolidated test should prove index generation, reopen, exact search, and export.

### Bundle 2 - Static Relationship Mapping

**Purpose:** Turn the symbol catalog into a navigable client map.

**Status:** PLANNED

**Dependencies:**

- Bundle 1 stable symbol identities/index.

**Patches:**

1. Class/type/member reference edges.
2. Method caller/callee edges where reliably derivable.
3. Field read/write edges.
4. Constants/literal-ID indexing.
5. Source-location enrichment where reliable.
6. Domain correlation rules for obvious interfaces/components/packets/cache IDs without inventing semantics.

**Runtime tests:**

- Static-only validation against a few known source relationships where possible.

### Bundle 3 - Search and Investigation API

**Purpose:** Make Atlas immediately useful for targeted code investigations.

**Status:** PLANNED

**Dependencies:**

- Bundle 2 relationships.

**Patches:**

1. Ranked exact/text/ID search.
2. Relationship-neighborhood queries (`calls`, `called-by`, reads/writes, related IDs).
3. Evidence/alias search.
4. Compact investigation export suitable for future assistant analysis.

**Runtime tests:**

- Query known symbols/IDs and verify relevant small neighborhoods are returned without full-database dumps.

### Bundle 4 - Runtime Trace Sessions

**Purpose:** Add targeted runtime evidence for behavior static analysis cannot prove.

**Status:** PLANNED

**Dependencies:**

- Stable Atlas symbol identities and search.
- Targeted discovery of reusable runtime hooks.

**Patches:**

1. Trace-session lifecycle: start/stop/name/save.
2. High-value event hooks: menu actions and input path.
3. Packet send/receive metadata hooks where safe.
4. Interface/component activity hooks.
5. Definition/cache/model/animation/GFX hooks where safe and useful.
6. Correlation of recorded events back to Atlas symbols.

**Runtime tests:**

- Short controlled sessions only; validate one action at a time and confirm tracing can be completely disabled.

### Bundle 5 - Evidence and Knowledge Layer

**Purpose:** Preserve what we learn so future chats do not rediscover the same client behavior.

**Status:** PLANNED

**Dependencies:**

- Searchable Atlas and trace records.

**Patches:**

1. External aliases/notes.
2. Evidence status and supporting-reference records.
3. Revision/hash invalidation warnings for stale evidence.
4. Merge/update workflow that preserves manually verified knowledge across rescans.

**Runtime tests:**

- Promote one known symbol from `verified-static` to `VERIFIED` using a saved trace without altering its original obfuscated name.

### Bundle 6 - Client Console Atlas Browser

**Purpose:** Add a fast human-facing viewer over the proven Atlas APIs/data.

**Status:** PLANNED

**Dependencies:**

- Bundles 1-5 useful without UI.

**Patches:**

1. Atlas search panel.
2. Symbol detail + callers/callees/reads/writes navigation.
3. Evidence/alias editor.
4. Trace-session controls/browser.
5. Optional bounded relationship graph for the selected neighborhood only.

**Runtime tests:**

- Search/navigate a known symbol and record a short trace without blocking the client UI.

### Bundle 7 - Advanced Correlation

**Purpose:** Reduce manual reverse-engineering work after the core system has proven itself.

**Status:** BACKLOG

**Dependencies:**

- Real usage evidence from earlier bundles.

**Candidate patches:**

1. Automatic clustering of repeated runtime/static paths.
2. Suggested semantic aliases marked strictly as `HYPOTHESIS` until verified.
3. Cross-link IDs to cache/definition metadata where reliable.
4. Diff Atlas indexes between client revisions/commits.
5. Investigation reports that capture only the relevant symbol neighborhood and evidence.

## Current bundle

- Bundle: **Bundle 1 - Atlas Foundation**
- Approval state: **Documentation foundation approved. Client/source implementation not yet approved.**
- Current patch: **1. Targeted implementation discovery**
- Current objective: establish the smallest safe implementation path before touching client source.

## Patch status

| Patch | Bundle | Status | Notes |
| --- | --- | --- | --- |
| Targeted implementation discovery | 1 | READY | Next action; no client source inspected yet. |
| Atlas schema + persistent index foundation | 1 | READY | Architecture outlined; exact storage/dependency choice awaits discovery. |
| Static symbol scanner MVP | 1 | READY | Bytecode-first preferred, exact parser dependency still UNKNOWN. |
| Basic query/export path | 1 | READY | Must work without UI first. |
| Static relationship mapping | 2 | READY | Begins after stable symbol identities. |
| Search/investigation API | 3 | READY | Machine/assistant workflow is priority. |
| Runtime trace sessions | 4 | READY | Targeted/opt-in only. |
| Evidence/knowledge layer | 5 | READY | Preserve aliases and verification evidence externally. |
| Client Console browser | 6 | READY | Deliberately after data/search foundation. |
| Advanced correlation | 7 | CARRYOVER | Optional after real-world usage proves needs. |

## Decisions / new ideas

### Decision log

- **Client Atlas is primarily for future assistant/code investigation.** Human UI is secondary.
- The searchable/indexed data layer is authoritative; do not make the Client Console panel the owner of discovery data.
- Preserve original obfuscated names permanently unless the user separately approves renaming.
- Do not produce one giant text dump as the main interface. Support targeted search and compact neighborhood exports.
- Prefer static evidence first; use runtime tracing to prove claims static analysis cannot establish.
- Runtime tracing should be explicit, short-lived, and scoped rather than always-on method logging.
- Any automatically suggested semantic meaning remains `HYPOTHESIS` until evidence supports promotion.
- Client Atlas replaces the vague future "Client architecture map" concept with a concrete persistent workstream and generated navigation/evidence system.

## Testing

Keep runtime testing concise and optimize around limited PC time.

### Quick/high-value checks

1. Generate/rebuild Atlas against the client.
2. Search one exact obfuscated class and method.
3. Query its immediate relationships.
4. Export only that investigation neighborhood.
5. Reopen Atlas without rebuilding and repeat the search.

### Deeper checks

1. Modify/rebuild one small client input and confirm incremental/stale-index handling.
2. Record one targeted runtime trace.
3. Correlate the trace to indexed symbols.
4. Save an alias/evidence classification and confirm it survives static re-indexing.

### Smoke/regression checks

- Static/offline scanner work should not affect Matrix3 runtime smoke coverage.
- Any runtime client instrumentation must include a normal-client launch check with Atlas tracing disabled.
- Broader smoke requirements will be determined from the exact implementation ownership discovered in Bundle 1.

## Carryover / blockers

### CARRYOVER

- Advanced automatic correlation is intentionally deferred until the core Atlas has real usage evidence.

### BLOCKED

- None.

## Resume Here

**Last completed:**

- Client Atlas workstream architecture and bundle plan created.

**Current state:**

- Design/documentation foundation exists.
- No Client Atlas source has been created.
- No **718+ Client** implementation files have been inspected for this workstream yet.

**Next action:**

- After implementation/discovery approval, inspect only the smallest relevant **718+ Client** build/tool/dependency files to establish the Atlas implementation path for Bundle 1 Patch 1.

**Files/systems already inspected:**

- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/rs3/WORKSTREAM_TEMPLATE.md`
- `docs/rs3/WORKSTREAMS.md`

**Do not re-scan without new evidence:**

- The above project-rule/workstream files unless they changed.
- Unrelated server/gameplay systems.

**Pending runtime verification:**

- None; implementation has not started.

**Blockers:**

- None.

**Important remaining uncertainty:**

- Exact client implementation/package/build path.
- Existing bytecode/instrumentation dependencies.
- Exact persistent index format.
- Best existing runtime hooks for later targeted traces.

## Next recommended work

**Bundle 1, Patch 1 - Targeted implementation discovery.** Establish the smallest safe implementation path for the static Atlas foundation, then stop and report before source modification unless that implementation bundle has explicit AAA approval.
