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

Client Console browser/runtime path:

```text
ClientConsoleShell lazy Atlas destination
    -> game.console.AtlasWorkspacePanel
        -> Browser: game.console.AtlasPanel
            -> AtlasWorkspace / AtlasInvestigationIndex / AtlasSearchEngine
            -> AtlasEvidenceStore
        -> Runtime evidence: game.console.AtlasRuntimeEvidencePanel
            -> AtlasTraceCatalog
            -> existing ClientAtlasTraceControl
            -> current AtlasInvestigationIndex / AtlasTraceCorrelationEngine
```

The Client Console does not own Atlas scanning, ranking, relationship semantics, trace recording/control semantics, correlation acceptance, fingerprint rules, or evidence persistence. `game.atlas` remains the engine/data authority.

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
- Runtime evidence trace browsing exposes at most the newest 100 saved traces,
- Runtime evidence trace listing/correlation runs off the Swing EDT,
- Runtime evidence correlation reuses the existing current-index/10000-event/1000-preview acceptance path unchanged,
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

**Status: IMPLEMENTED / RUNTIME GATE DEFERRED INTO COMBINED PHASE 4 GATE**

The full compatible Browser Foundation implementation is patched. At the user's explicit request, its runtime gate is deferred while the independent compatible Bundle 4B implementation proceeds so both can be verified in one Client Console session.

- [x] Register/lazy-load a dedicated Client Console Atlas panel without moving Atlas engine ownership into Client Console.
  - persistent panel ID remains `atlas`,
  - lazy Atlas workspace construction through the established `ClientConsoleShell` panel seam,
  - panel creation failures remain isolated by the existing shell error boundary,
  - active-panel persistence automatically reuses `ConsolePreferences`.
- [x] Reuse the existing Atlas search/index APIs for a fast search panel.
  - `AtlasWorkspace.findClientRoot(...)` + current-only `AtlasInvestigationIndex.load(...)`,
  - `AtlasSearchEngine` remains the structural search authority,
  - first index load and searches run in `SwingWorker` rather than on the Swing EDT,
  - visible structural results are capped at 50,
  - Reload explicitly discards the cached browser index and reopens the current Atlas snapshot.
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

Deferred verification does not block 4B because 4B consumes the same established Atlas panel destination and existing `game.atlas` APIs without changing 4A browser semantics, evidence persistence, or runtime hooks. Bundle 4A cannot be marked DONE until the combined gate passes.

## Bundle 4B - Runtime evidence workflow

**Status: IMPLEMENTED / CONSOLIDATED RUNTIME GATE REQUIRED**

- [x] Browse saved traces/correlation summaries from the Atlas panel.
  - `AtlasTraceCatalog` owns metadata-only trace discovery in `game.atlas`,
  - newest 100 `.trace.jsonl` files maximum,
  - newest-first deterministic ordering,
  - Runtime evidence view lists trace filename/size and correlates a selected trace by button or double-click,
  - Correlate latest uses the existing `AtlasTraceCorrelationEngine.latestTrace(...)` authority,
  - correlation summary exposes trace path, status, accepted flag, total/dropped events, bounded preview count, and trace/current Atlas fingerprints.
- [x] Surface existing trace controls without duplicating runtime-trace ownership.
  - Browser and Runtime evidence live under one `AtlasWorkspacePanel` using the same persistent `atlas` Client Console destination,
  - Browser remains the default view,
  - Runtime evidence is lazy-created only when opened,
  - Runtime Trace Control launches the existing `ClientAtlasTraceControl`,
  - Client Console does not implement a second START/STOP/SAVE protocol.
- [x] Keep trace/correlation safety and bounded-output rules unchanged.
  - saved trace listing/correlation runs in `SwingWorker`,
  - correlation loads through current-only `AtlasInvestigationIndex.load(...)`,
  - `AtlasTraceCorrelationEngine` is reused unchanged,
  - 10000-event trace read cap and 1000-event correlated preview cap remain unchanged,
  - fingerprint/event-count/source-owner acceptance remains unchanged,
  - no runtime trace hook, packet hook, recorder format, or game/server behavior changed.

## Bundle 4A + 4B consolidated runtime gate - NEXT

Run this once, preferably inside the already-pending Client Console V2 acceptance launch:

1. Eclipse Java 8 clean/build Client once.
2. Rebuild the static Atlas index once after compiling so its fingerprint matches the current compiled Client.
3. Start Matrix3 normally and open the Client Atlas globe icon; Browser should be the default Atlas view.
4. Search `Class1`, choose/use an exact canonical Atlas ID, verify exact detail, and open one symbol-backed relationship. Confirm a constant/type/value relationship stays visible but non-navigable.
5. Save/reopen/delete one temporary HYPOTHESIS evidence record and require `CURRENT` while saved.
6. Switch to Runtime evidence and confirm saved traces populate without freezing the game/UI.
7. Open the existing Runtime Trace Control, create one short named trace against this newly compiled client, exercise a few normal keyboard/menu/interface actions, then Stop + Save.
8. Refresh traces; confirm the new trace appears near the top.
9. Correlate that selected trace and require `Status: CURRENT`, `Accepted: true`, and `Dropped = 0`.
10. Correlate latest and require the same newest trace to return `CURRENT` + `Accepted: true`.
11. Resize to the narrow supported console width and switch Browser/Runtime evidence repeatedly; controls remain reachable and no visible render/input hitch or Swing/client-thread exception appears.
12. Leave Atlas selected, clean-close/relaunch once, and confirm the Atlas rail destination restores. Browser may reopen as the default Atlas subview; subview persistence is not required.

If this passes, mark Bundles 4A and 4B DONE. No Phase 1/2/3 regression gate is required unless contradictory evidence appears because Phase 4 consumes those authorities rather than modifying their runtime semantics.

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
Client/src/main/java/game/atlas/AtlasTraceCatalog.java
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

Client Console browser/runtime consumer:

```text
Client/src/main/java/game/console/AtlasPanel.java
Client/src/main/java/game/console/AtlasWorkspacePanel.java
Client/src/main/java/game/console/AtlasRuntimeEvidencePanel.java
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
- Bundle 4A implementation: **complete / verified-static; runtime verification intentionally deferred into the combined Phase 4 gate**.
- Bundle 4B implementation: **complete / verified-static; combined Phase 4 gate pending**.
- Bundle 4B adds only metadata trace browsing/Client Console invocation of existing Atlas APIs; no runtime trace/packet/game semantics changed.
- The combined Phase 4 gate may be run inside the pending Client Console V2 acceptance launch to avoid another restart.
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

- Phase 3 Runtime Evidence and Knowledge is DONE.
- Full compatible Phase 4 / Bundle 4A Browser Foundation implementation is patched / verified-static.
- Full compatible Phase 4 / Bundle 4B Runtime Evidence workflow implementation is patched / verified-static.
- The user explicitly chose to skip the standalone 4A test and continue 4B so both can share one runtime gate.

**Current phase:**

- **Phase 4 - Client Console Atlas Browser / ACTIVE**

**Active/next bundle:**

- **Bundle 4A + 4B consolidated runtime gate / NEXT**

**Current/next work:**

- Do not split Atlas verification into per-control cycles.
- Pull + Eclipse Java 8 clean/build once when the Client Console V2 implementation bundle is also ready for runtime acceptance.
- Rebuild Atlas once for the new compiled fingerprint.
- Run the combined Browser + Runtime evidence gate from `docs/client-atlas/testlist.txt` in that same client launch.
- On PASS, mark Bundles 4A and 4B DONE and decide whether 4C polish is justified by actual browser use before entering Phase 5.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- Phase 1/2 scanner/search architecture,
- resolved keyboard/menu/network/interface/Class639 ownership paths,
- Bundle 3A runtime tracing/correlation gate,
- Bundle 3B curated evidence architecture,
- established Client Console shell/panel lazy-load seam,
- existing trace-control/correlation ownership,
- unrelated server/gameplay systems.

**Pending runtime/offline verification:**

- Eclipse Java 8 compile of the full Phase 4A + 4B implementation.
- One combined Client Console Atlas Browser + Runtime evidence gate.
- This may share the pending Client Console V2 acceptance launch.

# Next recommended work

**Run the combined Phase 4 / Bundle 4A + 4B Atlas runtime gate once the Client Console V2 implementation bundle is ready for its own consolidated acceptance session.**