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

Runtime observation seam:

```text
Matrix3 runtime / small existing bridge
    -> game.AtlasRuntimeBridge
        -> game.atlas.AtlasTraceRecorder
            -> bounded memory session
                -> atomic .client-atlas/traces/*.trace.jsonl
```

Safety contract:

- tracing OFF by default,
- no event-path disk writes,
- maximum 10000 stored events per session,
- explicit dropped-event count after the cap,
- no packet payload byte arrays,
- no credentials,
- no arbitrary chat/text strings,
- no arbitrary object dumps or stack traces,
- observation failures cannot interrupt normal client behavior,
- saved traces carry the compiled-client fingerprint when available.

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

The numbered responsibilities are internal checklist items, not separate user patch/test sessions.

- [x] **3A.0 Targeted architecture discovery - verified-static.**
- [x] **3A.1 Trace-session lifecycle - implementation complete / verified-static.**
  - process-wide bounded recorder,
  - start / stop / named session / save,
  - dropped-event accounting,
  - atomic JSONL trace persistence,
  - fingerprint capture on save,
  - cross-process file-backed trace commands/status,
  - standalone dark trace-control UI + CLI.
- [x] **3A.2 Menu/input coverage - safe Core implementation complete / verified-static.**
  - high-level menu path resolved as `Class25.method728(...) -> Class319.method4094(...) -> DevModeBridge.handleMenuAction(...)`,
  - menu trace uses the existing small Dev Mode seam before normal Matrix3 action handling,
  - records normalized action/local coordinates only; no menu text,
  - `AtlasKeyboardObserver` mirrors the verified `Class549_Sub1` key map/filter behavior without consuming events or rewriting the large decompiled input class,
  - keyboard events carry the verified `Class549_Sub1.method8081(...)` owner symbol as a correlation hint.
- [x] **3A.3 Packet metadata coverage - implementation complete / verified-static.**
  - outgoing hook is `Class195.method2929(...)` after encoded/enqueued length is known,
  - incoming hook is the small `MaterialInformation.method1605(...)` wrapper immediately after successful `PacketsDecoder.method3031(...)` processing,
  - no packet payload capture.
- [x] **3A.4 Initial interface activity coverage - implementation complete / verified-static.**
  - named incoming interface/component packet classes are identified by `AtlasRuntimeBridge`,
  - blanket `Class512.method6083(...)` instrumentation remains intentionally rejected,
  - decoded component payload fields are CARRYOVER only if the initial packet stream proves insufficient.
- [x] **3A.5 Safe definition/cache/GFX coverage - implementation complete / verified-static.**
  - ID-bearing `Class639.method7568(...)` hook runs after decode/finalization,
  - generic Class639 definition misses record definition ID + loader/definition class,
  - GFX is confirmed Class639-backed when `Class452_Sub1` registers the graphics definition loader,
  - exact model and animation loader hooks remain CARRYOVER until ownership is established; no guessed hook added.

### Hook audit

Decompiled runtime edits were accepted only when their diff was surgical:

- `Class195.java`: +1 line / 0 deletions.
- `MaterialInformation.java`: +2 lines / 0 deletions.
- `Class639.java`: +1 line / 0 deletions.
- A direct `Class549_Sub1` rewrite was rejected because audit exposed unrelated array-line churn; main was rolled back before the safe `AtlasKeyboardObserver` implementation was used.

Resolved verified-static hook ownership:

```text
keyboard semantics owner: Class549_Sub1.method8081(int,char,int,int)
menu dispatcher:          Class319.method4094(Class572_Sub12_Sub10,int,int,byte)
outgoing enqueue:         Class195.method2929(Class572_Sub25,byte)
incoming decoder owner:   PacketsDecoder.method3031(Class195,byte)
incoming safe wrapper:    MaterialInformation.method1605(Class195,int)
definition cache miss:    Class639.method7568(int,int)
```

### 3A-Correlation + Gate - NEXT

- [ ] **3A.6 Correlate runtime events to Atlas symbols.**
  - validate event `sourceSymbol` / keyboard `ownerSymbol` against the rebuilt current index,
  - add bounded trace investigation/assistant export only as needed,
  - keep UNKNOWN semantics UNKNOWN unless evidence proves them.
- [ ] **Bundle 3A consolidated runtime gate.**
  - one client start,
  - start one named trace,
  - perform a few controlled keyboard/menu/network/interface/definition actions,
  - stop + save,
  - confirm categories/source IDs/fingerprint/dropped count,
  - confirm stop fully disables event growth,
  - rebuild static Atlas once at this natural source-change boundary and confirm stale pre-rebuild data is refused.

No per-hook runtime launches should be requested before this gate unless compilation specifically fails.

## Bundle 3B - Evidence/knowledge - PLANNED

- [ ] External aliases/notes.
- [ ] Evidence classification/supporting references.
- [ ] Fingerprint stale-evidence warnings.
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

Core tracing:

```text
Client/src/main/java/game/AtlasRuntimeBridge.java
Client/src/main/java/game/AtlasKeyboardObserver.java
Client/src/main/java/game/atlas/AtlasTraceControl.java
Client/src/main/java/game/atlas/AtlasTraceRecorder.java
Client/src/main/java/game/atlas/ClientAtlasTraceControl.java
```

Small/runtime seams:

```text
Client/src/main/java/game/DevDefinitionBridge.java
Client/src/main/java/game/DevModeBridge.java
Client/src/main/java/game/Class195.java
Client/src/main/java/game/MaterialInformation.java
Client/src/main/java/game/Class639.java
Client/src/main/java/game/atlas/ClientAtlasMain.java
```

# Testing

- Phase 1: runtime-verified.
- Phase 2: runtime-verified.
- 3A.0: verified-static discovery.
- 3A-Core: **implementation complete / verified-static / runtime gate deferred intentionally.**
- Do not request another Phase 2 gate unless contradictory evidence or a Phase 2 implementation change appears.
- Do not test individual Phase 3 hooks separately unless one specifically fails compilation/runtime behavior.

# Carryover / blockers

## CARRYOVER

- Component-specific decoded interface payload values only if the initial interface packet stream is insufficient.
- Exact animation loader instrumentation after ownership is established.
- Exact model/cache loader instrumentation after ownership is established.
- On the natural Phase 3 rebuild boundary, confirm stale generated data is refused until rebuilt.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.

These carryover items do not block 3A.6 correlation or the initial consolidated runtime gate.

## BLOCKERS

- None.

# Resume Here

**Last completed checkpoint:**

- **Phase 3 / Bundle 3A / 3A-Core runtime hooks implemented - verified-static.**
- Menu dispatcher UNKNOWN is resolved.
- Packet/interface and Class639/GFX Core hooks are inserted.
- Risky direct keyboard source rewrite was rejected and replaced with the isolated normalized observer.

**Current phase:**

- **Phase 3 - Runtime Evidence and Knowledge / ACTIVE**

**Active bundle:**

- **3A-Correlation + Gate / ACTIVE NEXT**

**Current/next work:**

- **3A.6 runtime-to-Atlas correlation.**
- Validate event source/owner IDs against current Atlas records and add the smallest bounded trace-investigation/export layer needed for assistant use.
- Do not ask the user to launch/test yet; keep the runtime verification consolidated into the final Bundle 3A gate.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- Phase 1/2 scanner/search architecture,
- resolved 3A-Core keyboard/menu/network/interface/Class639 ownership paths,
- unrelated server/gameplay systems,
- Client Console internals before Phase 4.

**Pending runtime verification:**

- Entire 3A-Core implementation plus 3A.6 correlation should be verified in one short controlled trace session.

# Next recommended work

**3A.6 runtime-to-Atlas correlation.**
