# Conveyor V2 — Scoped Implementation Notes

This file is a narrow Conveyor V2 implementation/resume document. The canonical Construction phase, main-goal status table and overall roadmap remain in `docs/construction_revamp/PROJECT.md`.

## Goal

Keep one server-owned persistent `SettlementConveyorRun` per player-authored straight segment while letting the client compose professional factory-style visuals from reusable one-tile authored modules.

Transport ownership remains server-side: payload identity, speed, spacing, backpressure, physical inventory provenance, belt-to-belt transfers, chest/machine endpoints and persistence must never move into the client visual generator.

## Current state

**Conveyor V2.0 Straight Runs + Belt Connections:** IMPLEMENTED / NEEDS RUNTIME TEST.

Current client/player-facing corrections:
- Continuous chain authoring retains committed B as the next A.
- Each current segment locks E/W or N/S from its first meaningful movement so the preview does not flip when the mouse crosses the diagonal threshold.
- Right-click/Escape ends the active chain and clears the segment-axis lock.

Current straight visual architecture:
- `STRAIGHT_1T` is the canonical visual primitive.
- The complete visible authored conveyor assembly is normalized to exactly 512 model units along its long axis.
- A logical N-tile straight run repeats that intact module N times at exact 512-unit spacing.
- Repeated raw modules are merged into one generated raw / cached renderer `Model` per straight segment.
- The logical run is still one server-owned ConveyorRun; repeated modules are not world objects, persistent build pieces or collision owners.
- Matrix3 `Class159(Class159[], int)` is the verified-static clone/merge path used so source face/texture/UV structures survive repetition.
- The old straight renderer strategy that stretched BELT_SURFACE while separately spreading caps/details/supports is superseded.
- Live Model Editor roles currently decide which authored source parts participate in the repeated straight module; `IGNORE` and untouched/unassigned original sawmill parts remain excluded.
- The fresh-client fallback authored set remains source parts 4,5,8,16,17,18,19,21 and goes through the same 1T module pipeline.

## Visual kit direction

Planned client visual primitives:
1. `STRAIGHT_1T` — current active acceptance target.
2. `CORNER_1T` — the user's authored 90-degree corner, selected/rotated from source/receiver heading difference.
3. `SPLITTER_1T` — explicit future graph-node visual.
4. `MERGER_1T` — optional explicit merge presentation when needed.
5. Endpoint/machine connector/support polish only after the primitive kit is stable.

The server connection graph remains authoritative regardless of which client junction visual is chosen.

## Runtime acceptance order

1. Pull latest `main`; Eclipse Clean/Build **Client**. Server rebuild is not required for the 1T visual-only renderer patch if current V2.0 server code is already running.
2. Show Conveyor A->B Demo and inspect 2/5/9-tile runs.
3. Verify every repeated tile preserves identical belt/roller/rail alignment with no progressive drift, gaps, growing overlap or texture loss.
4. Verify E/W and N/S runs rotate the complete module correctly.
5. Verify Build Palette preview uses the same repeated module at several lengths.
6. Recheck per-segment axis lock and continuous A->B->C chaining.
7. Recheck a real payload moving/transferring across connected runs; visual repetition must not change transport speed, spacing or ownership.
8. Observe one reasonably long run in RTS view for model-build/render performance.

## Resume Here

**Resume Here — Conveyor:** runtime-test `STRAIGHT_1T` first. If the 2/5/9-tile visual gate passes, mark it accepted and implement `CORNER_1T`: detect a 90-degree source-output -> receiver heading change from the existing synchronized V2.0 connection metadata, suppress/trim the conflicting straight-module seam as needed, and render/rotate the authored corner visual without changing server transport ownership.

Do not return to stretch-based straight-belt rendering unless new runtime evidence specifically requires it.
