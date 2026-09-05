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

Fingerprint from the last Phase 2 runtime-confirmed build:

```text
41be330f2baa1044db8da56ddc160447b1cc3db7e7bdcd4c1c5cfc955973fc26
```

A natural client rebuild after Phase 3 runtime-source changes is expected to change the current fingerprint; stale Atlas data must be rebuilt before correlation is accepted as current.

# Architecture / ownership

- Existing client runtime/cache/network/interface/input/definition systems remain authoritative.
- Atlas owns generated metadata, search/correlation APIs, exports, traces, aliases, and evidence records.
- Original obfuscated identifiers remain primary IDs.
- JSONL remains static persistence authority; no database is justified by current measurements.
- `.client-atlas/` remains Git-ignored and survives normal build cleaning.
- Runtime tracing is opt-in, bounded, switchable, payload-minimal, and failure-isolated.
- Normal client startup remains `game.RS3Applet`.
- Java 8 / Eclipse remains the protected target.

Current workspace:

```text
Client/.client-atlas/
    metadata.properties
    symbols.jsonl
    relationships.jsonl
    evidence.jsonl
    trace-control.properties
    trace-status.properties
    exports/
    traces/
```

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
- Safe domain correlation such as `interface 762`, `component 7`, `762:7`, `animation 1234`, `model 5678`.
- Final combined runtime verification passed.

# Phase 3 - Runtime Evidence and Knowledge

**Status: ACTIVE**

## Runtime tracing architecture - verified-static

Runtime hooks use one narrow seam:

```text
obfuscated/runtime class
    -> game.AtlasRuntimeBridge
        -> game.atlas.AtlasTraceRecorder
            -> bounded memory session
                -> atomic .client-atlas/traces/*.trace.jsonl
```

Safety contract:

- tracing OFF by default,
- inactive hooks return immediately,
- no event-path disk writes,
- maximum **10000** stored events per session,
- explicit dropped-event counter after the cap,
- no packet payload byte arrays,
- no credentials,
- no arbitrary chat/text capture,
- no arbitrary object dumps or stack traces,
- runtime trace failures cannot interrupt normal client behavior,
- saved traces carry the current compiled-client fingerprint when available.

## Verified hook ownership map

### Keyboard/input

Preferred normalized hook:

```text
game.Class549_Sub1.method8081(int,char,int,int)
```

`keyPressed`, `keyReleased`, `keyTyped`, and focus loss flow through it.

**UNKNOWN:** exact final high-level mouse/menu action dispatcher. Resolve only that dispatcher during the bundled hook patch; do not broaden into another client scan.

### Outgoing packets

Preferred logical-send hook:

```text
game.Class195.method2929(Class572_Sub25,byte)
```

Record packet ID, declared length, encoded/enqueued length only. Do not hook repeated flush methods.

### Incoming packets

Preferred hook:

```text
game.PacketsDecoder.method3031(Class195,byte)
```

Record identity/final length after packet resolution and before named branch handling. No payload capture.

### Interface activity

Use incoming packet classification first, including known named interface/component packets such as `ROOT_INTERFACE`, `SET_INTERFACE`, `CLOSE_INTERFACE`, `MOVE_INTERFACE`, `HIDE_INTERFACE_COMPONENT`, `INTERFACE_SETTINGS`, model/item/NPC/player component packets, and related interface events.

Do not blanket-hook `Class512.method6083(...)`; it is a generic/lazy definition lookup and would be noisy.

### Definition/cache activity

`Class639.method7568(...)` is the generic cache-miss decode path and already calls `DevDefinitionBridge.observeDefinitionLoader(...)`.

The first live Atlas seam now rides beside that existing developer observer through `AtlasRuntimeBridge` without taking Dev Mode ownership.

Model/animation/GFX loaders that are not `Class639`-backed must receive targeted hooks only when their real ownership is established.

# Bundle 3A - Targeted runtime tracing

## Execution optimization

The numbered 3A.x entries are **internal checklist responsibilities, not separate user patch sessions**.

For speed and limited PC time, execute them as:

### 3A-Core - ACTIVE

One compatible implementation bundle covering 3A.1 through the safe parts of 3A.5:

- [x] **3A.1 Trace-session lifecycle - implementation complete / verified-static.**
  - bounded process-wide recorder,
  - start / stop / name / save,
  - dropped-event accounting,
  - atomic JSONL trace save,
  - current-build fingerprint on save,
  - file-backed cross-process command/status protocol,
  - fresh runtime heartbeat/stale-runtime detection,
  - standalone dark trace-control UI,
  - CLI trace control/status commands,
  - failures isolated from client behavior.
- [ ] **3A.2 Menu/input hooks - ACTIVE inside 3A-Core.**
  - bridge API ready,
  - normalized keyboard callsite still needs insertion,
  - exact high-level menu/mouse dispatcher remains `UNKNOWN` and must be resolved narrowly.
- [ ] **3A.3 Packet metadata hooks - ACTIVE inside 3A-Core.**
  - outgoing/incoming bridge APIs and neutral packet decoding ready,
  - final one-line callsites still need insertion.
- [ ] **3A.4 Interface/component hooks - ACTIVE inside 3A-Core.**
  - named incoming interface packet classifier is implemented in `AtlasRuntimeBridge`,
  - incoming decoder callsite still needs insertion,
  - component-specific branch hooks only if packet metadata proves insufficient.
- [ ] **3A.5 Definition/cache/model/animation/GFX hooks - ACTIVE inside 3A-Core.**
  - existing `DevDefinitionBridge` observer now starts the runtime control seam and can emit safe definition category events,
  - direct ID-bearing `Class639.method7568(...)` callsite remains to be inserted,
  - non-Class639 model/animation/GFX ownership remains targeted follow-up inside the same bundle.

**Do not request a runtime launch after each bullet.** Finish all safe 3A-Core callsites first.

### 3A-Correlation + Gate - PLANNED

- [ ] **3A.6 Correlate runtime events to Atlas symbols.**
  - runtime bridge already attaches exact source Atlas IDs for known hook methods,
  - validate those IDs against the rebuilt current index,
  - add assistant-readable trace investigation/export as needed.
- [ ] **Bundle 3A runtime gate.**
  - one client start,
  - start one named trace,
  - perform a few controlled input/interface/network/definition actions,
  - stop + save,
  - confirm event categories/source IDs/fingerprint/dropped count,
  - confirm tracing fully disables,
  - rebuild static Atlas once if the Phase 3 source changes made the saved Phase 2 index stale.

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

# Current Phase 3 implementation

Added in the 3A-Core foundation patch:

```text
Client/src/main/java/game/AtlasRuntimeBridge.java
Client/src/main/java/game/atlas/AtlasTraceControl.java
Client/src/main/java/game/atlas/AtlasTraceRecorder.java
Client/src/main/java/game/atlas/ClientAtlasTraceControl.java
```

Updated:

```text
Client/src/main/java/game/DevDefinitionBridge.java
Client/src/main/java/game/atlas/ClientAtlasMain.java
```

`AtlasRuntimeBridge` already exposes prepared neutral APIs for keyboard, outgoing packet, incoming packet/interface classification, and definition/cache observations. Those APIs intentionally exist before the remaining giant obfuscated callsites are touched so each future runtime edit is tiny and easy to audit.

# Testing

- Phase 1: runtime-verified.
- Phase 2: runtime-verified.
- 3A.0: verified-static discovery; no runtime test required.
- 3A-Core foundation: **NEEDS TEST later, deliberately batched with the remaining Core hooks.**
- Do not request another Phase 2 gate unless contradictory evidence or a Phase 2 implementation change appears.
- Do not test individual Phase 3 hooks separately unless one specifically fails compilation/runtime behavior.

# Carryover / blockers

## CARRYOVER

- Resolve the exact high-level menu/mouse dispatcher during the 3A-Core hook patch.
- Insert the prepared one-line keyboard/outgoing/incoming/direct-definition hooks without broad rescanning.
- Establish targeted non-Class639 model/animation/GFX ownership only if the generic definition seam does not cover the needed asset category.
- On the natural post-hook rebuild boundary, confirm stale generated Atlas data is refused until rebuilt.
- Verify >200 streaming exact-query truncation when a naturally suitable symbol appears.

## BLOCKERS

- No architectural blocker.
- Avoid whole-file rewrites of giant decompiled runtime classes merely to insert one trace call; use the smallest surgical mutation path available.

# Resume Here

**Last completed checkpoint:**

- **Phase 3 / Bundle 3A / 3A-Core foundation implemented.**
- **3A.1 implementation is complete / verified-static; runtime verification intentionally deferred to the consolidated Bundle 3A gate.**

**Current phase:**

- **Phase 3 - Runtime Evidence and Knowledge / ACTIVE**

**Active bundle:**

- **3A-Core - ACTIVE**

**Current/next work:**

- **3A-Core hook patch:** insert prepared bridge calls into the verified keyboard, outgoing packet, incoming packet, and direct definition-load callsites; narrowly resolve the menu/mouse dispatcher while touching input ownership.
- Continue through all safe compatible 3A.2-3A.5 work under the already-approved SAP AAA instead of stopping after each numbered checklist item.

**Do not re-scan/re-discover without new evidence:**

- broad `game` source tree,
- Phase 1/2 scanner/search architecture,
- verified 3A.0 keyboard/network/interface/definition ownership paths,
- unrelated server/gameplay systems,
- Client Console internals before Phase 4.

**Pending runtime verification:**

- One consolidated Phase 3A runtime session after the Core hooks + correlation are ready.
- No user runtime action is required for the current foundation patch yet.

# Next recommended work

**Finish the single 3A-Core hook patch, then do 3A-Correlation and one final Bundle 3A runtime gate.**
