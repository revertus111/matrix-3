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

Phase 3 changes client runtime source, so the next natural rebuild is expected to produce a new fingerprint. Stale generated Atlas data must be rebuilt before runtime correlation is accepted as current.

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
- explicit dropped-event count,
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

### 3A-Core - IMPLEMENTED / NEEDS CONSOLIDATED RUNTIME GATE

- [x] **3A.0 Targeted architecture discovery - verified-static.**
- [x] **3A.1 Trace-session lifecycle - implementation complete / verified-static.**
- [x] **3A.2 Menu/input coverage - safe Core implementation complete / verified-static.**
  - menu path: `Class25.method728(...) -> Class319.method4094(...) -> DevModeBridge.handleMenuAction(...)`,
  - normalized action/local coordinates only; no menu text,
  - `AtlasKeyboardObserver` mirrors verified `Class549_Sub1` normalization without consuming events or rewriting the large input class.
- [x] **3A.3 Packet metadata coverage - implementation complete / verified-static.**
  - outgoing: `Class195.method2929(...)`,
  - incoming safe wrapper: `MaterialInformation.method1605(...)` after central decoder processing,
  - IDs/length metadata only; no payload bytes.
- [x] **3A.4 Initial interface activity coverage - implementation complete / verified-static.**
  - named interface/component packet classification,
  - no blanket `Class512.method6083(...)` hook.
- [x] **3A.5 Safe definition/cache/GFX coverage - implementation complete / verified-static.**
  - ID-bearing `Class639.method7568(...)` cache-miss hook,
  - GFX confirmed Class639-backed,
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

### 3A.6 Runtime-to-Atlas correlation - IMPLEMENTED / NEEDS CONSOLIDATED RUNTIME VALIDATION

- [x] Added `AtlasTraceCorrelationEngine` - verified-static.
- [x] Correlation loads only through `AtlasInvestigationIndex.load(...)`, so a stale generated Atlas index is rejected before correlation.
- [x] Saved trace fingerprint is compared against the current loaded Atlas fingerprint.
- [x] Exact event `sourceSymbol` and optional `ownerSymbol` IDs are resolved with `index.getSymbol(...)`.
- [x] Correlation is accepted as current only when fingerprint, trace event count, source IDs, and owner IDs all validate.
- [x] Mismatched/unknown fingerprints and unresolved IDs remain explicit diagnostic states; they are never silently accepted.
- [x] Input remains bounded at 10000 trace events; assistant event output is capped at 1000 and unresolved-ID lists at 100.
- [x] Output includes category counts, dropped count, source/owner resolution totals, symbol metadata, original safe event fields, and truncation state.
- [x] Added atomic correlation export and `latest` trace resolution.
- [x] Added CLI commands:
  - `trace-correlate <trace|latest> [classes-dir]`
  - `trace-correlate-export <trace|latest> <file> [classes-dir]`
- [x] Main `ClientAtlasControl` now exposes `Runtime Trace Control` and `Correlate Latest Trace`, so the normal Phase 3 gate can be completed without CLI arguments.
- [ ] Runtime proof against the rebuilt current index is intentionally folded into the final Bundle 3A gate.

### Bundle 3A consolidated runtime gate - NEXT

One pull/build/start session only. The normal path is now GUI-first; CLI remains optional.

1. Eclipse Java 8 clean/build.
2. Open the main Atlas Control and confirm `Runtime Trace Control` + `Correlate Latest Trace` are visible.
3. Confirm the old Phase 2 Atlas index is stale after the Phase 3 source build.
4. Click `Scan / Rebuild Index` once.
5. Start client/login normally.
6. Click `Runtime Trace Control`, then `Start Trace` and name it `phase3-gate`.
7. Perform a few controlled keyboard/menu/network/interface/definition/GFX actions.
8. In Runtime Trace Control click `Stop + Save`.
9. Back in the main Atlas Control click `Correlate Latest Trace`.
10. Require:
   - `Status: CURRENT`,
   - `Accepted: true`,
   - trace fingerprint = rebuilt Atlas fingerprint,
   - source/owner IDs resolve,
   - expected categories exist,
   - dropped count visible and normal short trace below cap.
11. Confirm stopped tracing no longer grows event count.
12. Because central packet enqueue/decode seams changed, complete the permanent Matrix3 smoke checklist during this same launch before Phase 3/Bundle 3A is marked DONE.

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
- 3A-Core: implementation complete / verified-static / runtime gate deferred intentionally.
- 3A.6: implementation complete / verified-static / runtime validation deferred to the same consolidated Bundle 3A gate.
- Runtime-gate GUI wiring: implemented / verified-static; user-visible button/open behavior remains part of the same consolidated gate.
- No Phase 2 retest unless contradictory evidence or a Phase 2 implementation change appears.

# Carryover / blockers

## CARRYOVER

- Component-specific decoded interface payload values only if the initial interface packet stream is insufficient.
- Exact animation loader instrumentation after ownership is established.
- Exact model/cache loader instrumentation after ownership is established.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.

These do not block the initial Bundle 3A gate.

## BLOCKERS

- None.

# Resume Here

**Last completed checkpoint:**

- **Phase 3 / Bundle 3A / 3A.6 correlation implementation complete - verified-static.**
- Runtime hooks plus bounded exact symbol correlation/export are implemented.
- Main Atlas Control now exposes the runtime trace and latest-correlation actions directly.

**Current phase:**

- **Phase 3 - Runtime Evidence and Knowledge / ACTIVE**

**Active bundle:**

- **Bundle 3A consolidated runtime gate / NEXT**

**Current/next work:**

- Pull/build once, then complete the GUI-first Atlas rebuild -> Runtime Trace Control -> Stop + Save -> Correlate Latest Trace flow and required Matrix3 smoke verification.
- Do not add more trace features before this gate unless compilation/runtime evidence requires a fix.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- Phase 1/2 scanner/search architecture,
- resolved 3A-Core keyboard/menu/network/interface/Class639 ownership paths,
- unrelated server/gameplay systems,
- Client Console internals before Phase 4.

**Pending runtime verification:**

- Runtime gate buttons are visible/open correctly.
- 3A-Core hooks.
- 3A.6 exact source/owner correlation against the rebuilt current Atlas index.
- stop/disabled behavior, fingerprint match, dropped count, normal startup/ownership, and permanent smoke checks.

# Next recommended work

**Bundle 3A consolidated runtime gate.**
