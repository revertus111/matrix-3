# Client Atlas

## Goal

Build a persistent, searchable reverse-engineering map of the obfuscated **718+ Client** so future investigations start from saved structural/runtime evidence instead of repeatedly searching, tracing, and guessing through decompiled source.

Client Atlas is developer/reverse-engineering tooling. Client runtime behavior remains authoritative; Atlas records evidence without renaming obfuscated symbols or inventing semantics.

## Canonical Main-Goal Status

This table is the authority for user-facing Client Atlas status across chats.

| Main-goal area | Status |
| --- | --- |
| Static client knowledge foundation | ✅ Complete |
| Static relationship mapping | ✅ Complete |
| Fast investigation/search | ✅ Complete |
| Runtime evidence/tracing | ✅ Complete |
| Client Console Atlas browser | 🔵 In Progress |
| Advanced correlation/knowledge | 🔵 In Progress |

Checklist state below is the execution map. Do not derive replacement milestone rows from it.

## Verified baseline

- Phase 1 runtime gate: PASS.
- Phase 2 structural gate: `PHASE 2 STRUCTURAL CHECK: PASS`.
- Phase 2 final investigation gate: `PHASE 2 INVESTIGATION CHECK: PASS`.
- 1221 compiled client classes.
- 33742 symbols.
- 325826 relationships.
- `symbols.jsonl` ~8.5 MiB / `relationships.jsonl` ~74.4 MiB.
- Structural scan ~1.28 s.
- Investigation-index load ~946.649 ms / ~181.5 MiB approximate memory delta.
- Exact search ~0.588 ms / friendly search ~0.416 ms.
- Depth-2 verifier neighborhood 28 nodes / 40 relationships.
- Domain queries remain hints; semantic status stays `UNKNOWN` and never auto-promotes `LITERAL_ID`.

Last Phase 2 runtime-confirmed fingerprint:

```text
41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26
```

Phase 3 changed client runtime source. The corrected Bundle 3A gate rebuilt Atlas against the current compiled client and accepted the saved trace as `CURRENT`.

# Architecture / ownership

- Existing Matrix3 client runtime/cache/network/interface/input/definition systems remain authoritative.
- Atlas owns generated metadata, search/correlation APIs, exports, traces, aliases, and evidence records.
- Original obfuscated identifiers remain primary IDs.
- JSONL remains static persistence authority; no database is justified by current measurements.
- `.client-atlas/` remains Git-ignored and survives normal build cleaning.
- Runtime tracing is opt-in, bounded, switchable, payload-minimal, and failure-isolated.
- Normal client startup remains `game.RS3Applet`.
- Java 8 / Eclipse remains the protected target.

Runtime observation/correlation path:

```text
Matrix3 runtime / small existing bridge
    -> game.AtlasRuntimeBridge
        -> game.atlas.AtlasTraceRecorder
            -> bounded .trace.jsonl
                -> current AtlasInvestigationIndex
                    -> AtlasTraceCorrelationEngine
                        -> bounded assistant correlation JSON
```

Curated knowledge path:

```text
exact current Atlas symbol ID
    -> AtlasEvidenceStore
        -> persistent .client-atlas/evidence.jsonl
            -> EvidenceView freshness/orphan evaluation
                -> curated search / Client Console Atlas editor
```

Client Console browser path:

```text
ClientConsoleShell lazy Atlas panel
    -> game.console.AtlasPanel (UI consumer only)
        -> AtlasWorkspace / AtlasInvestigationIndex / AtlasSearchEngine
        -> AtlasEvidenceStore
```

The Client Console does not own Atlas scanning, ranking, relationship semantics, fingerprint rules, or evidence persistence. `game.atlas` remains the engine/data authority.

Curated knowledge rules:

- exact obfuscated Atlas IDs remain primary; aliases never rename generated symbols,
- one curated record per exact subject ID,
- new/updated records require a symbol that exists in a current `AtlasInvestigationIndex`,
- each record stores the current Atlas fingerprint at edit time,
- classifications are only `VERIFIED`, `verified-static`, `HYPOTHESIS`, or `UNKNOWN`,
- supporting references are external evidence pointers/notes and never create generated relationships,
- stale fingerprints and missing subject IDs produce warnings instead of deletion or automatic reclassification,
- curated search is separate from generated symbol ranking so notes/aliases cannot distort structural evidence,
- `evidence.jsonl` is curated state and is never reset by normal static rescans.

Safety contract:

- tracing OFF by default,
- no event-path disk writes,
- maximum 10000 stored/read trace events,
- definition category maximum 4000 stored events,
- repeated definition/load combinations recorded once per session,
- intentional filtering counted as `suppressedCount`,
- actual global-buffer loss counted as `droppedCount`,
- no packet payload byte arrays,
- no credentials,
- no arbitrary chat/text strings,
- no arbitrary object dumps or stack traces,
- observation failures cannot interrupt normal client behavior,
- saved traces carry the compiled-client fingerprint when available,
- correlation never creates semantic claims or auto-promotes `LITERAL_ID`,
- curated evidence is bounded and atomically rewritten,
- malformed/duplicate curated records fail explicitly instead of being silently accepted,
- Client Console Atlas index loading/search/evidence I/O runs off the Swing EDT,
- Client Console search output is bounded to 50 structural candidates,
- relationship display is bounded to 60 outgoing + 60 incoming relationships per selected symbol,
- exact obfuscated IDs remain visible throughout search, detail, relationship navigation, and evidence editing.

# Completed static capabilities

## Phase 1 - DONE

- Stable symbol IDs and metadata/fingerprint workspace.
- ASM bytecode declaration scanner.
- Exact query/export.
- Standalone Atlas Control.
- Runtime verification passed.

## Phase 2 - DONE

- CALLS / DYNAMIC_CALL / field reads+writes / type references / typed constants.
- In-memory investigation index.
- Ranked/friendly search with ambiguity safety.
- Bounded relationship and depth-1/2 neighborhood queries.
- Assistant export v2.
- Safe domain correlation such as `interface 762`, `component 7`, `762:7`, `animation 1234`, and `model 5678`.
- Final combined runtime verification passed.

# Phase 3 - Runtime Evidence and Knowledge

**Status: DONE**

## Bundle 3A - Targeted runtime tracing

**Status: DONE / RUNTIME VERIFIED**

### 3A-Core - DONE / RUNTIME VERIFIED

- [x] **3A.0 Targeted architecture discovery - verified-static.**
- [x] **3A.1 Trace-session lifecycle - runtime verified.**
- [x] **3A.2 Menu/input coverage - runtime verified.**
  - menu path: `Class25.method728(...) -> Class319.method4094(...) -> DevModeBridge.handleMenuAction(...)`,
  - no menu text captured,
  - runtime traces proved the hook is live,
  - two previously guessed coordinate fields are neutral `rawArg1/rawArg2` because runtime values did not support coordinate semantics,
  - `AtlasKeyboardObserver` mirrors verified `Class549_Sub1` normalization without consuming events or rewriting the large input class.
- [x] **3A.3 Packet metadata coverage - runtime verified.**
  - outgoing: `Class195.method2929(...)`,
  - incoming safe wrapper: `MaterialInformation.method1605(...)` after central decoder processing,
  - IDs/length metadata only; no payload bytes.
- [x] **3A.4 Initial interface activity coverage - runtime verified.**
  - named interface/component packet classification,
  - traces observed interface activity while normal client behavior remained intact,
  - no blanket `Class512.method6083(...)` hook.
- [x] **3A.5 Safe definition/cache/GFX coverage - runtime verified.**
  - ID-bearing `Class639.method7568(...)` cache-miss hook,
  - traces naturally captured AnimationDefinition, ItemDefinitions, ObjectDefinitions, and VarBitDefinition activity,
  - GFX remains confirmed Class639-backed verified-static,
  - model/animation-specific loader hooks remain carryover until ownership is established.

### Hook audit

Accepted decompiled runtime edits remain surgical:

- `Class195.java`: +1 line / 0 deletions.
- `MaterialInformation.java`: +2 lines / 0 deletions.
- `Class639.java`: +1 line / 0 deletions.
- Direct `Class549_Sub1` rewrite was rejected/rolled back after unrelated array-line churn appeared.

Resolved verified-static ownership:

```text
keyboard semantics owner: Class549_Sub1.method8081(int,char,int,int)
menu dispatcher:          Class319.method4094(Class572_Sub12_Sub10,int,int,byte)
outgoing enqueue:         Class195.method2929(Class572_Sub25,byte)
incoming decoder owner:   PacketsDecoder.method3031(Class195,byte)
incoming safe wrapper:    MaterialInformation.method1605(Class195,int)
definition cache miss:    Class639.method7568(int,int)
```

### First Bundle 3A runtime-gate attempt - FAILED USEFULLY / VERIFIED EVIDENCE

The first controlled trace proved the hooks work but exposed a recorder-design problem:

- stored events: 10000,
- hard dropped events: 63578,
- definition events among stored events: 9934,
- non-definition stored events: 66,
- observed non-definition categories still included input, network, and interface,
- ~2961 unique definition/load combinations existed among the 9934 stored definition events.

Classification:

- **VERIFIED:** input/menu/network/interface/definition runtime hooks emit events.
- **VERIFIED:** repeated definition/cache activity saturated the global trace buffer and caused useful later events to be dropped.
- **UNKNOWN:** semantic meaning of the menu numeric arguments beyond their raw values; do not call them coordinates.

### 3A trace-noise correction - RUNTIME VERIFIED / PASS

- [x] Definition tracing is first-occurrence-per-session by definition ID + loader class + definition class.
- [x] Definition category has a 4000 stored-event ceiling, preserving at least 6000 global slots for other categories.
- [x] Duplicate/category-capped events increment `suppressedCount` instead of `droppedCount`.
- [x] `droppedCount` remains reserved for true global 10000-event-buffer overflow.
- [x] Runtime Trace Control displays Events / Dropped / Suppressed separately.
- [x] Saved trace header persists `suppressedCount` while retaining trace format version 1 compatibility.
- [x] Menu numeric fields renamed to neutral `rawArg1/rawArg2`.

Corrected runtime evidence:

- stored events: 6050,
- hard dropped events: 0,
- intentionally suppressed events: 6617488,
- definition events: 4000 (category cap reached without exhausting the global buffer),
- network events: 1267,
- interface events: 631,
- input events: 152,
- keyboard events: 86,
- menu events: 66.

The corrected trace remained useful after the definition cap was reached and finalized normally through `Stop + Save`.

### 3A.6 Runtime-to-Atlas correlation - DONE / RUNTIME VERIFIED

- [x] Added `AtlasTraceCorrelationEngine` - verified-static.
- [x] Correlation loads only through `AtlasInvestigationIndex.load(...)`, so stale generated Atlas data is rejected before correlation.
- [x] Saved trace fingerprint is compared against the current loaded Atlas fingerprint.
- [x] Exact event `sourceSymbol` and optional `ownerSymbol` IDs resolve with `index.getSymbol(...)`.
- [x] Correlation is accepted as current only when fingerprint, trace event count, source IDs, and owner IDs all validate.
- [x] Unknown/mismatched fingerprints and unresolved IDs remain explicit diagnostic states.
- [x] Input remains bounded at 10000 trace events; assistant event output is capped at 1000 and unresolved-ID lists at 100.
- [x] Atomic correlation export + `latest` trace resolution.
- [x] CLI correlation commands remain available.
- [x] Main `ClientAtlasControl` exposes `Runtime Trace Control` and `Correlate Latest Trace`.
- [x] Corrected trace correlated against the rebuilt current index with `Status: CURRENT` and `Accepted: true`.

### Bundle 3A corrected consolidated runtime gate - PASS

Completed in one Eclipse/Java 8 client session:

1. Clean/build current Client source.
2. Rebuild Atlas against the current compiled client.
3. Start client/login normally.
4. Start one controlled runtime trace.
5. Exercise keyboard/menu/network/interface/definition activity.
6. `Stop + Save` finalized the trace.
7. Runtime trace result: `Dropped = 0`, suppression active, stored event count below 10000.
8. `Correlate Latest Trace` returned `Status: CURRENT` and `Accepted: true`.
9. Correlation acceptance proves trace/current fingerprints matched, header event count matched parsed event count, and emitted source/owner symbol IDs resolved.
10. User-reported same-launch Matrix3 smoke passed; client behavior remained normal.

Bundle 3A is closed. Do not request another 3A runtime gate without a relevant implementation change or contradictory evidence.

## Bundle 3B - Evidence/knowledge

**Status: DONE / OFFLINE VERIFIED**

The complete compatible Bundle 3B implementation was patched before requesting another user test.

- [x] **3B.1 External aliases/notes.**
  - `AtlasEvidenceStore` persists curated records in `.client-atlas/evidence.jsonl`.
  - exact obfuscated `subjectId` remains primary; alias is external metadata only,
  - note/claim text is stored outside generated scanner data,
  - one curated record per exact subject ID; same-subject upsert replaces deterministically,
  - curated search covers subject ID, alias, note/claim, status, and references without affecting static symbol ranking.
- [x] **3B.2 Evidence classification/supporting references.**
  - statuses persist as `VERIFIED`, `verified-static`, `HYPOTHESIS`, or `UNKNOWN`,
  - supporting references are bounded and de-duplicated,
  - new/updated evidence must attach to an exact symbol in a current `AtlasInvestigationIndex`,
  - the record stores that current Atlas fingerprint,
  - JSONL serialization is deterministic; writes replace atomically.
- [x] **3B.3 Fingerprint stale-evidence warnings.**
  - current fingerprint + present subject -> `CURRENT`,
  - fingerprint mismatch -> `STALE_FINGERPRINT`,
  - absent exact subject -> `SUBJECT_NOT_PRESENT`,
  - stale + absent subject -> combined explicit state,
  - stale/orphan records remain visible for human review; no automatic semantic promotion or deletion.
- [x] **3B.4 Preserve curated knowledge across rescans.**
  - `AtlasWorkspace.initialize()` continues to reset only generated `symbols.jsonl` and `relationships.jsonl`,
  - `evidence.jsonl` remains curated persistent state,
  - evidence load validates duplicate IDs/malformed records explicitly,
  - store limits protect against accidentally unbounded curated data.
- [x] **3B.5 Consolidated verifier.**
  - `AtlasEvidenceVerifier` tests the whole bundle in one run,
  - verifier writes only to an isolated temporary evidence workspace and does not touch the developer's real `evidence.jsonl`,
  - covers JSON escape round-trip, alias/note/classification/references, freshness warnings, orphan retention, rescan preservation, deterministic upsert/search, and delete.

### Bundle 3B consolidated offline gate - PASS

Completed under Eclipse / Java 8 on 2026-09-06:

- `AtlasEvidenceVerifier` passed alias + note/claim + classification + supporting-reference JSONL round-trip.
- Current-fingerprint evidence evaluated `CURRENT`.
- Fingerprint mismatch produced an explicit stale-evidence warning.
- Missing exact subjects were retained and flagged for review.
- Curated evidence survived generated Atlas initialize/rescan.
- Same-subject upsert and curated knowledge search passed deterministically.
- Curated record delete persisted atomically.
- Final result: `BUNDLE 3B KNOWLEDGE CHECK: PASS`.
- Report: `Client/.client-atlas/knowledge-check.txt`.

Bundle 3B is closed. Phase 3 Runtime Evidence and Knowledge is DONE. No Matrix3 client launch/smoke was required because Bundle 3B changed only offline `game.atlas` tooling.

# Phase 4 - Client Console Atlas Browser

**Status: ACTIVE**

## Bundle 4A - Browser foundation

**Status: IMPLEMENTED / CONSOLIDATED RUNTIME GATE REQUIRED**

The full compatible Browser Foundation implementation is patched before requesting runtime verification.

- [x] Register/lazy-load a dedicated Client Console Atlas panel without moving Atlas engine ownership into Client Console.
  - new persistent panel ID: `atlas`,
  - lazy `AtlasPanel` construction through the established `ClientConsoleShell` panel seam,
  - panel creation failures remain isolated by the existing shell error boundary,
  - active-panel persistence automatically reuses `ConsolePreferences`.
- [x] Reuse the existing Atlas search/index APIs for a fast search panel.
  - `AtlasWorkspace.findClientRoot(...)` + current-only `AtlasInvestigationIndex.load(...)`,
  - `AtlasSearchEngine` remains the structural search authority,
  - first index load and searches run in `SwingWorker` rather than on the Swing EDT,
  - visible structural results are capped at 50,
  - Reload explicitly discards the cached panel index and reopens the current Atlas snapshot.
- [x] Add symbol detail plus bounded relationship navigation.
  - exact ID, kind, owner, name, descriptor, signature/source when present,
  - combined outgoing/incoming relationship list,
  - each direction capped at 60 entries,
  - exact symbol-backed relationships can be opened by button or double-click,
  - constant/type/value targets remain visible but intentionally non-navigable rather than being guessed into symbol semantics.
- [x] Add curated evidence/alias view + editor over `AtlasEvidenceStore` with explicit freshness warnings.
  - classification picker uses Atlas `EvidenceStatus`,
  - alias, required note/claim, and one-reference-per-line editing,
  - save/upsert and delete use `AtlasEvidenceStore` off the EDT,
  - `CURRENT`, stale fingerprint, missing subject, and combined warnings come directly from `EvidenceView`,
  - Client Console does not duplicate evidence persistence or fingerprint rules.
- [x] Keep exact obfuscated Atlas IDs visible and primary throughout the UI.
  - result selection, symbol detail, relationship navigation, save/delete targets, and status text all retain the exact Atlas subject ID.
- [x] Add an original Java2D Atlas/globe rail icon through the existing `ConsoleIcons` authority; no asset/dependency added.

### Bundle 4A consolidated runtime gate - NEXT

One short Eclipse/client session after pulling/building:

1. Eclipse Java 8 clean/build Client once.
2. Start Matrix3 normally and click the new **Client Atlas** globe icon.
3. Confirm the panel opens lazily and the game remains responsive while the first Atlas index loads.
4. Search `Class1`, then search an exact canonical ID from the results. Confirm exact ID/kind/owner/name/descriptor detail appears.
5. Select a relationship that points to another exact Atlas symbol and use **Open selected relation** (or double-click). Confirm navigation opens that exact symbol. Select a constant/type/value relationship and confirm it remains visible but is not falsely opened as a symbol.
6. On one harmless test symbol, save evidence as `HYPOTHESIS` with alias `Atlas UI test`, note `Phase 4A runtime UI test`, and reference `runtime:phase4a-ui`. Confirm freshness shows `CURRENT` and the saved values reopen after switching symbols and returning.
7. Delete that temporary evidence record and confirm the editor returns to `No curated evidence`.
8. Resize the Client Console to its narrow supported width and confirm search/results/detail/relationships/evidence controls remain reachable through the panel split/scroll areas.
9. Move/interact in game while searching/navigating Atlas. Confirm no visible render/input hitch and no Swing/client-thread exception.
10. Leave Atlas selected, close Matrix3 normally, relaunch once, and confirm the Atlas rail selection restores through the existing active-panel preference path.

If these pass, close Bundle 4A and advance directly to Bundle 4B. No Phase 1/2/3 regression gate is required unless contradictory evidence appears; this bundle consumes those APIs rather than modifying their engine behavior.

## Bundle 4B - Runtime evidence workflow - PLANNED

- [ ] Browse saved traces/correlation summaries from the Atlas panel.
- [ ] Surface existing trace controls without duplicating runtime-trace ownership.
- [ ] Keep trace/correlation safety and bounded-output rules unchanged.

## Bundle 4C - Browser polish - BACKLOG

- [ ] Optional bounded relationship graph only if it improves investigation speed without adding heavy dependencies.
- [ ] Navigation/history/filter polish driven by actual browser use.

# Phase 5 - Advanced Correlation - BACKLOG

- [ ] Repeated-path clustering.
- [ ] Suggested aliases remain `HYPOTHESIS` until proven.
- [ ] Reliable cache/definition crosslinks.
- [ ] Revision/fingerprint diffs.
- [ ] Investigation report generation.

# Current Atlas files

Core/runtime tracing:

```text
Client/src/main/java/game/AtlasRuntimeBridge.java
Client/src/main/java/game/AtlasKeyboardObserver.java
Client/src/main/java/game/atlas/AtlasTraceControl.java
Client/src/main/java/game/atlas/AtlasTraceRecorder.java
Client/src/main/java/game/atlas/ClientAtlasTraceControl.java
```

Correlation/offline tooling:

```text
Client/src/main/java/game/atlas/AtlasTraceCorrelationEngine.java
Client/src/main/java/game/atlas/ClientAtlasMain.java
Client/src/main/java/game/atlas/ClientAtlasControl.java
```

Curated evidence/knowledge:

```text
Client/src/main/java/game/atlas/AtlasSchema.java
Client/src/main/java/game/atlas/AtlasJson.java
Client/src/main/java/game/atlas/AtlasEvidenceStore.java
Client/src/main/java/game/atlas/AtlasEvidenceVerifier.java
Client/src/main/java/game/atlas/AtlasWorkspace.java
```

Client Console browser consumer:

```text
Client/src/main/java/game/console/AtlasPanel.java
Client/src/main/java/game/console/ClientConsoleShell.java
Client/src/main/java/game/console/ConsoleIcons.java
```

Small runtime seams:

```text
Client/src/main/java/game/DevDefinitionBridge.java
Client/src/main/java/game/DevModeBridge.java
Client/src/main/java/game/Class195.java
Client/src/main/java/game/MaterialInformation.java
Client/src/main/java/game/Class639.java
```

# Testing

- Phase 1: runtime-verified.
- Phase 2: runtime-verified.
- 3A.0: verified-static discovery.
- 3A-Core runtime hooks: runtime-verified.
- First Bundle 3A gate: **FAILED because definition noise saturated the recorder; failure was understood and corrected.**
- Trace-noise correction: **runtime-verified / PASS** with 6050 stored, 0 dropped, and 6617488 intentionally suppressed events.
- 3A.6 correlation: **runtime-verified / PASS** with `Status: CURRENT` and `Accepted: true` against the rebuilt current Atlas index.
- Same-launch Matrix3 smoke: **PASS by user report 2026-09-06**.
- Bundle 3A: **DONE**.
- Bundle 3B implementation: **complete / verified-static**.
- Bundle 3B consolidated offline verifier: **PASS by user report 2026-09-06**.
- Bundle 3B: **DONE**.
- Phase 3: **DONE**.
- Bundle 4A implementation: **complete / verified-static; one consolidated Client Console runtime gate pending**.
- Bundle 4A does not modify Atlas engine semantics or game/server behavior; the runtime gate is for shell integration, UI workflow, responsiveness, persistence, and evidence-editor wiring.
- No Phase 2 / Bundle 3A / Bundle 3B retest unless contradictory evidence or a relevant implementation change appears.

# Carryover / blockers

## CARRYOVER

- Component-specific decoded interface payload values only if the current interface packet stream proves insufficient.
- Exact animation loader instrumentation after ownership is established.
- Exact model/cache loader instrumentation after ownership is established.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.

These do not block Phase 4.

## BLOCKERS

- None.

# Resume Here

**Last completed checkpoint:**

- **Phase 3 / Bundle 3B consolidated offline knowledge gate: PASS.**
- Phase 3 Runtime Evidence and Knowledge is DONE.
- Full compatible Phase 4 / Bundle 4A Browser Foundation implementation is patched.
- `game.console.AtlasPanel` is a UI consumer of existing `game.atlas` authorities; no Atlas engine ownership moved into Client Console.

**Current phase:**

- **Phase 4 - Client Console Atlas Browser / ACTIVE**

**Active bundle:**

- **Bundle 4A - Browser foundation / IMPLEMENTED / NEEDS CONSOLIDATED RUNTIME GATE**

**Current/next work:**

- Do not split Bundle 4A into per-control test cycles.
- Pull + Eclipse Java 8 clean/build once.
- Run the single Bundle 4A Client Console gate: lazy open, search, exact detail, bounded relationship navigation, evidence save/reopen/delete, narrow resize/responsiveness, and active-panel restart restore.
- On PASS, mark Bundle 4A DONE and continue to Bundle 4B runtime evidence workflow.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- Phase 1/2 scanner/search architecture,
- resolved keyboard/menu/network/interface/Class639 ownership paths,
- Bundle 3A runtime tracing/correlation gate,
- Bundle 3B curated evidence architecture,
- established Client Console shell/panel lazy-load seam,
- unrelated server/gameplay systems.

**Pending runtime/offline verification:**

- Eclipse Java 8 compile of the full Bundle 4A implementation.
- One consolidated Client Console Bundle 4A runtime gate.

# Next recommended work

**Run the Phase 4 / Bundle 4A consolidated Client Console runtime gate once.**
