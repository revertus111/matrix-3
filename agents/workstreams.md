# Matrix3 Persistent Workstream Rules

Read root `AGENTS.md` first. This file applies when the task belongs to an existing or new persistent multi-patch workstream.

## Workstream workflow

- Treat substantial ideas such as a boss, developer tool, combat framework, game mode, class system, or other multi-patch feature as a persistent workstream.
- Use the hierarchy `Idea -> Phase -> Bundle -> Patch/Checklist`.
- A phase is an ordered milestone of the workstream. A bundle is a related unit of work inside a phase. Patches/checklist items are the concrete implementation, discovery, documentation, or verification steps inside that bundle.
- The user supplies the idea, goals, preferences, and decisions. The assistant owns architecture, phase decomposition, dependencies, discovery, implementation order, logical bundles, patch/checklist boundaries, tests, and carryover work.
- Each persistent workstream has one authoritative project document, normally `docs/<subject>/PROJECT.md`.
- Each normalized persistent workstream must keep a `Canonical Main-Goal Status` table in its authoritative `PROJECT.md`. That table is the source of truth for user-facing milestone rows across chats; phase/bundle/checklist state is a separate execution map.
- Use `docs/rs3/WORKSTREAMS.md` as the lightweight registry and `docs/rs3/WORKSTREAM_TEMPLATE.md` when creating or normalizing a workstream document.
- Do not create duplicate roadmap, ownership, backlog, status, or carryover documents when the authoritative workstream document can hold that information.
- Group tasks into a bundle only when they share ownership, files, dependencies, implementation sequence, or runtime testing. Keep each logical patch independently understandable and revertible.
- Prefer narrow, descriptive commits per logical patch when practical.
- If one patch in an approved bundle becomes blocked, mark it `CARRYOVER` or `BLOCKED` and continue with other safe, independent approved patches.
- New ideas for an existing workstream belong in the current phase/bundle, a future phase/bundle, or backlog/decisions. Do not interrupt active work unless the idea is a required dependency or the user explicitly changes priority.
- Preserve discovery state using `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` where useful.
- Every persistent workstream must maintain a concise `Resume Here` state whenever work stops midstream. Record the last completed checkpoint, current phase/bundle/checklist item, next action, inspected files/systems, areas that should not be rescanned, blockers, pending runtime verification, and important uncertainty.
- Do not rediscover information already established in the authoritative workstream document unless repository changes, runtime evidence, or contradictory evidence requires re-verification.
- At the end of a bundle, persist each included patch/checklist item as appropriate: `READY`, `ACTIVE`, `NEEDS TEST`, `CARRYOVER`, `BLOCKED`, or `DONE`.

## Phase/checklist discipline

- Before inspecting or patching an existing persistent workstream, read its authoritative `docs/<subject>/PROJECT.md` and locate the current phase, bundle, checklist, `Canonical Main-Goal Status`, and `Resume Here` state.
- Determine which phase/bundle is `ACTIVE` and which checklist items are already complete before deciding what work comes next.
- Treat the workstream phase/checklist as the execution map for that project.
- Do not redo completed checklist items or skip into later phases unless a dependency requires it or the user explicitly changes priority.
- When the user says `continue`, `next`, or otherwise resumes an existing workstream, continue from the first valid unfinished checklist item in the active phase/bundle unless `Resume Here` specifies a more precise next action.
- After completing work, update the authoritative checklist/phase state so another chat can immediately determine where the workstream stands.
- If chat discussion and the saved workstream checklist disagree, stop before patching and identify the mismatch rather than guessing which state is correct.

## Phase completion gates

- A phase may be marked `DONE` only when all required checklist items for that phase are complete and any mandatory runtime verification has passed.
- If required runtime verification is still pending, keep the phase in `NEEDS TEST` or equivalent rather than marking it complete.
- Deferred verification must be explicitly recorded with the reason, affected checklist item(s), and what must be tested later.
- Do not advance to the next phase merely because implementation work is finished if the current phase still has required verification or unresolved blockers.
- A blocked or deferred item may allow later independent work only when the workstream explicitly records that it does not invalidate the phase gate or create a dependency risk.
- When a phase closes, update the authoritative `PROJECT.md` with the completed gate state and set the next phase/bundle/checklist item as the new execution target.

## Prefab/self-test discipline

- For substantial runtime-affecting systems, developer tools, and persistent workstreams, prefer a small deterministic prefab/self-test once the owning APIs are stable enough to support one safely.
- The ideal fast path is `pull -> clean/build -> launch -> select/load safe context -> click one test action -> read PASS/FAIL`.
- A prefab/self-test must exercise the real owning APIs or runtime path; do not fake success by only toggling UI state or duplicating production logic inside the test.
- Keep prefab tests disposable and scoped. Prefer exact developer-owned test instances, temporary in-memory definitions/configuration, zero/harmless damage, no rewards, bounded work, deterministic cleanup, and explicit PASS/FAIL output.
- Prefab tests must not silently rewrite persistent content, global live state, rollback history, drops, player saves, cache data, or unrelated world state. If temporary mutation is unavoidable, snapshot and restore it exactly or do not automate that check.
- Rights/admin/developer gates remain authoritative. A self-test is never a bypass around normal permissions or engine ownership.
- A prefab/self-test is the first-line confidence check, not a replacement for focused manual acceptance, persistence/restart verification, multiplayer behavior, or `docs/rs3/SMOKE_TEST.md` when those are required.
- When designing a new substantial workstream, include a prefab/self-test checkpoint when practical after the architecture becomes stable enough; reuse proven harness patterns instead of inventing bespoke testing infrastructure for every feature.
- When a runtime regression is found repeatedly, prefer strengthening the relevant prefab/self-test so the same failure becomes cheap to detect in future patches.

## Goal-anchored status updates

Status updates are a navigation aid for the user's original/main goal, not a changelog for the latest subtask.

- Every status update, including required post-patch status, must stay anchored to the original/main goal of the active project or workstream.
- For a normalized workstream, the authoritative `PROJECT.md` `Canonical Main-Goal Status` table is the only source of truth for the user-facing Area/Status milestone rows.
- On every new chat, resume, `continue`, or `next`, read that canonical table before reporting status and reproduce its row names, row order, and current status values exactly. Do not reconstruct the table from memory, recent chat, phases, bundles, checklists, tests, or `Resume Here`.
- Never derive, regenerate, rename, reorder, add, remove, or implicitly change canonical milestone rows from the active phase/bundle/checklist. The checklist is execution detail; the canonical table represents the main goal.
- A phase/bundle/checklist item being `NEEDS TEST`, `BLOCKED`, `DONE`, or otherwise changing state does not automatically change a canonical milestone status. Mention that local state under `Just completed:`, `Current focus:`, or the optional blocker/runtime-verification note.
- Change a canonical row/status only when the top-level milestone itself genuinely changes state or the user explicitly approves a revised main-goal roadmap. When that happens, update the canonical table in `PROJECT.md` in the same workstream-state patch so future chats inherit the change.
- If an older workstream does not yet contain a canonical table, do not invent a replacement table in the status response. Treat the workstream as needing status normalization and preserve existing roadmap/checklist facts until the canonical table is explicitly established.
- Put non-milestone work under `Just completed:` or `Current focus:` rather than turning it into a new main-goal row.
- Show enough of the remaining main path that the user can return after a side track and immediately see what comes next.
- If work moves temporarily to a side task, keep the canonical main-goal table unchanged and mention the side task separately.
- End status updates with `Next main step:` using the next meaningful milestone/checkpoint from the authoritative plan.
- Only change the status anchor or canonical milestone rows when the user explicitly changes the main goal, starts a separate workstream, approves a revised roadmap, or the saved top-level milestone itself reaches a new state.
- Do not invent percentage-complete estimates unless grounded in an explicit checklist or measurable scope.
- Use `✅ Complete`, `🟡 Foundation`, `🔵 In Progress`, `⚠️ Needs runtime verification` (or a concise audit note), and `❌ Not started` where applicable.

Required post-patch status shape:

1. `Main goal:` the original/main objective.
2. `Just completed:` the patch or subtask that changed.
3. The Area/Status table copied from the authoritative `Canonical Main-Goal Status` section without local reinterpretation.
4. `Next main step:` the next meaningful checkpoint that advances the original goal.
5. Optional blocker/runtime-verification note only when it materially affects that next step.
