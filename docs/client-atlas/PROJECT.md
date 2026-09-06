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
| Runtime evidence/tracing | 🔵 In Progress |
| Client Console Atlas browser | ❌ Not started |
| Advanced correlation/knowledge | ❌ Not started |

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

Phase 3 changes client runtime source, so the final corrected gate must rebuild Atlas against the current compiled client before correlation is accepted.

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
- correlation never creates semantic claims or auto-promotes `LITERAL_ID`.

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

**Status: ACTIVE**

## Bundle 3A - Targeted runtime tracing

### 3A-Core - IMPLEMENTED / CORRECTED GATE RERUN REQUIRED

- [x] **3A.0 Targeted architecture discovery - verified-static.**
- [x] **3A.1 Trace-session lifecycle - implementation complete / verified-static.**
- [x] **3A.2 Menu/input coverage - implementation complete; first runtime evidence captured.**
  - menu path: `Class25.method728(...) -> Class319.method4094(...) -> DevModeBridge.handleMenuAction(...)`,
  - no menu text captured,
  - first runtime trace proved the hook is live,
  - two previously guessed coordinate fields are now neutral `rawArg1/rawArg2` because runtime values did not support coordinate semantics,
  - `AtlasKeyboardObserver` mirrors verified `Class549_Sub1` normalization without consuming events or rewriting the large input class.
- [x] **3A.3 Packet metadata coverage - implementation complete; first runtime evidence captured.**
  - outgoing: `Class195.method2929(...)`,
  - incoming safe wrapper: `MaterialInformation.method1605(...)` after central decoder processing,
  - IDs/length metadata only; no payload bytes.
- [x] **3A.4 Initial interface activity coverage - implementation complete; first runtime evidence captured.**
  - named interface/component packet classification,
  - first trace observed `ROOT_INTERFACE`, `SET_INTERFACE`, and `HIDE_INTERFACE_COMPONENT`,
  - no blanket `Class512.method6083(...)` hook.
- [x] **3A.5 Safe definition/cache/GFX coverage - implementation complete; first runtime evidence captured.**
  - ID-bearing `Class639.method7568(...)` cache-miss hook,
  - first trace naturally captured AnimationDefinition, ItemDefinitions, ObjectDefinitions, and VarBitDefinition activity,
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

### 3A trace-noise correction - IMPLEMENTED / VERIFIED-STATIC / NEEDS RERUN

- [x] Definition tracing is first-occurrence-per-session by definition ID + loader class + definition class.
- [x] Definition category has a 4000 stored-event ceiling, preserving at least 6000 global slots for other categories.
- [x] Duplicate/category-capped events increment `suppressedCount` instead of `droppedCount`.
- [x] `droppedCount` remains reserved for true global 10000-event-buffer overflow.
- [x] Runtime Trace Control displays Events / Dropped / Suppressed separately.
- [x] Saved trace header persists `suppressedCount` while retaining trace format version 1 compatibility.
- [x] Menu numeric fields renamed to neutral `rawArg1/rawArg2`.

### 3A.6 Runtime-to-Atlas correlation - IMPLEMENTED / NEEDS CONSOLIDATED RUNTIME VALIDATION

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
- [ ] Runtime proof against the rebuilt current index remains part of the corrected final gate rerun.

### Bundle 3A corrected consolidated runtime gate - NEXT

One pull/build/start session only:

1. Eclipse Java 8 clean/build.
2. Open main Atlas Control and click `Scan / Rebuild Index` once against the newly compiled client.
3. Start client/login normally.
4. Open `Runtime Trace Control` and start one named trace.
5. Perform a few keyboard/menu/network/interface/definition actions.
6. Click `Stop + Save`.
7. Require in Runtime Trace Control:
   - `Dropped = 0`,
   - `Suppressed` may be high and is expected,
   - stored event count remains below 10000 for this short controlled trace.
8. Back in main Atlas Control click `Correlate Latest Trace`.
9. Require:
   - `Status: CURRENT`,
   - `Accepted: true`,
   - trace fingerprint = rebuilt Atlas fingerprint,
   - source/owner IDs resolve.
10. Confirm stopped tracing no longer grows event count.
11. Complete the permanent Matrix3 smoke checklist during this same launch because central packet enqueue/decode seams changed.

Do not request per-hook runtime launches.

## Bundle 3B - Evidence/knowledge - PLANNED

- [ ] External aliases/notes.
- [ ] Evidence classification/supporting references.
- [ ] Fingerprint stale-evidence warnings for curated knowledge.
- [ ] Preserve curated knowledge across rescans.

# Phase 4 - Client Console Atlas Browser - PLANNED

- [ ] Search panel.
- [ ] Symbol/relationship navigation.
- [ ] Evidence/alias editor.
- [ ] Trace browser/controls.
- [ ] Optional bounded relationship graph.

# Phase 5 - Advanced Correlation - BACKLOG

- [ ] Repeated-path clustering.
- [ ] Suggested aliases remain `HYPOTHESIS` until proven.
- [ ] Reliable cache/definition crosslinks.
- [ ] Revision/fingerprint diffs.
- [ ] Investigation report generation.

# Current Phase 3 files

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
- 3A-Core hooks: first runtime evidence confirms they emit useful events.
- First Bundle 3A gate: **FAILED because definition noise saturated the recorder; failure is understood and corrected.**
- Trace-noise correction: implementation complete / verified-static / one corrected gate rerun required.
- 3A.6: implementation complete / verified-static / final correlation validation still pending.
- No Phase 2 retest unless contradictory evidence or a Phase 2 implementation change appears.

# Carryover / blockers

## CARRYOVER

- Component-specific decoded interface payload values only if the current interface packet stream proves insufficient.
- Exact animation loader instrumentation after ownership is established.
- Exact model/cache loader instrumentation after ownership is established.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.

These do not block the corrected Bundle 3A gate.

## BLOCKERS

- None after the trace-noise correction; runtime rerun is the active gate.

# Resume Here

**Last completed checkpoint:**

- **Phase 3 / Bundle 3A / first runtime gate attempted.**
- Hook emission is runtime-proven.
- First gate failed from definition-event flooding.
- Definition dedupe/category cap + separate suppression accounting + neutral menu raw arguments are now implemented / verified-static.

**Current phase:**

- **Phase 3 - Runtime Evidence and Knowledge / ACTIVE**

**Active bundle:**

- **Bundle 3A corrected consolidated runtime gate / NEXT**

**Current/next work:**

- Pull/build once, rebuild Atlas once, rerun one controlled trace, require `Dropped = 0`, then run `Correlate Latest Trace` and finish the same-session smoke verification.
- High `Suppressed` is expected and is not a gate failure; it proves noisy duplicate definition activity is being filtered instead of consuming the global buffer.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- Phase 1/2 scanner/search architecture,
- resolved keyboard/menu/network/interface/Class639 ownership paths,
- unrelated server/gameplay systems,
- Client Console internals before Phase 4.

**Pending runtime verification:**

- corrected definition dedupe/category cap behavior,
- zero hard drops in a normal short trace,
- 3A.6 exact source/owner correlation against rebuilt current Atlas index,
- stop/disabled behavior,
- fingerprint match,
- normal startup/ownership and permanent smoke checks.

# Next recommended work

**Bundle 3A corrected consolidated runtime gate rerun.**
