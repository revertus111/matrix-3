# Matrix3 Project Rules

Read `docs/rs3/PROJECT.md` before changing code. For the current subject, also read its authoritative `docs/<subject>/PROJECT.md`, `patchnotes.txt`, and test documentation when they exist.

## Repository

- Repository: `revertus111/matrix-3`
- Branch: `main`
- Patch the current branch directly. Do not create or switch branches unless explicitly requested or a real GitHub problem requires it.
- Development target: Eclipse + Java 8. Keep Gradle/build metadata compatible, but do not redesign the project around another IDE or Java version without explicit approval.

## AAA workflow

- No code or documentation changes until the user gives explicit `AAA` approval for the current task.
- `SAP AAA` means scan and patch are approved for that exact task.
- Before AAA, follow repository scan discipline and report findings, likely files, implementation, and important uncertainty.
- After AAA, patch the established files directly; do not restart discovery unless new evidence requires it.
- AAA may approve a clearly defined workstream bundle containing multiple related patches. Do not stop for another AAA between its listed patches unless the requested scope materially changes.

## Workstream workflow

- Treat substantial ideas such as a boss, developer tool, combat framework, game mode, class system, or other multi-patch feature as a persistent workstream.
- Use the hierarchy `Idea -> Bundle -> Patch`.
- The user supplies the idea, goals, preferences, and decisions. The assistant owns architecture, dependencies, discovery, implementation order, logical bundles, patch boundaries, tests, and carryover work.
- Each persistent workstream has one authoritative project document, normally `docs/<subject>/PROJECT.md`.
- Use `docs/rs3/WORKSTREAMS.md` as the lightweight registry and `docs/rs3/WORKSTREAM_TEMPLATE.md` when creating or normalizing a workstream document.
- Do not create duplicate roadmap, ownership, backlog, status, or carryover documents when the authoritative workstream document can hold that information.
- Group tasks into a bundle only when they share ownership, files, dependencies, implementation sequence, or runtime testing. Keep each logical patch independently understandable and revertible.
- Prefer narrow, descriptive commits per logical patch when practical.
- If one patch in an approved bundle becomes blocked, mark it `CARRYOVER` or `BLOCKED` and continue with other safe, independent approved patches.
- New ideas for an existing workstream belong in the current bundle, a future bundle, or backlog/decisions. Do not interrupt active work unless the idea is a required dependency or the user explicitly changes priority.
- Preserve discovery state using `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` where useful.
- Every persistent workstream must maintain a concise `Resume Here` state whenever work stops midstream. Record the last completed checkpoint, current state, next action, inspected files/systems, areas that should not be rescanned, blockers, pending runtime verification, and important uncertainty.
- Do not rediscover information already established in the authoritative workstream document unless repository changes, runtime evidence, or contradictory evidence requires re-verification.
- At the end of a bundle, persist each included patch as appropriate: `READY`, `ACTIVE`, `NEEDS TEST`, `CARRYOVER`, `BLOCKED`, or `DONE`.

## Phase/checklist discipline

- Before inspecting or patching an existing persistent workstream, read its authoritative `docs/<subject>/PROJECT.md` and locate the current phase, bundle, checklist, and `Resume Here` state.
- Determine which phase/bundle is `ACTIVE` and which checklist items are already complete before deciding what work comes next.
- Treat the workstream phase/checklist as the execution map for that project.
- Do not redo completed checklist items or skip into later phases unless a dependency requires it or the user explicitly changes priority.
- When the user says `continue`, `next`, or otherwise resumes an existing workstream, continue from the first valid unfinished item in the active phase/bundle unless `Resume Here` specifies a more precise next action.
- After completing work, update the authoritative checklist/phase state so another chat can immediately determine where the workstream stands.
- If chat discussion and the saved workstream checklist disagree, stop before patching and identify the mismatch rather than guessing which state is correct.

## User-time optimization

- Treat the user's PC/runtime testing time as scarce.
- Do as much safe inspection, organization, patching, documentation, and static verification as possible before requiring the user to run the client/server.
- Consolidate compatible runtime verification into short test sessions; prefer one clear pull/start/test session over repeated minor restart cycles.
- Keep runtime instructions ordered, concise, and grouped by required startup state. Distinguish quick checks from deeper tests when practical.
- Never trade stability, correct ownership, evidence, or revertibility for raw patch count.

## Repository scan discipline

- Start with the smallest likely file set for the requested task.
- Prefer known paths, direct file reads, and exact-reference searches over broad repository searches.
- Do not recursively scan, enumerate, or fetch the entire repository unless the task genuinely requires it.
- Do not repeatedly search for the same class, method, file, symbol, or concept using slightly different queries.
- If the first targeted search fails, use one narrow fallback. Do not continually broaden the search.
- Do not inspect sibling systems, unrelated packages, adjacent tooling, or broad dependency trees unless current evidence shows they are required.
- Stop scanning as soon as the implementation path is established. Do not keep searching merely to increase confidence.
- Do not repeatedly reread `AGENTS.md`, project documentation, branch state, or files already inspected unless they changed or a specific unread section is required.
- Verify repository and branch state once at the beginning. Recheck only when an operation fails or there is evidence repository state changed.
- After patching, verify only changed files and immediate dependencies or relevant tests. Do not perform a full-repository review.
- If a GitHub, search, or repository tool fails, make one reasonable targeted retry or fallback. If it still cannot be resolved, report the uncertainty instead of entering a tool-call loop.
- If broader investigation genuinely becomes necessary, state what new evidence requires expanding the scan before doing so.

## Matrix3 architecture rules

1. Matrix3 is the authoritative game architecture.
2. The 718 project is reference material only. Never port 718 behavior merely because it already exists there.
3. Revision-830 cache/data is data authority where applicable; it does not automatically replace Matrix3 engine ownership.
4. One major system should have one documented owner. Update `docs/rs3/SYSTEM_OWNERSHIP.md` when ownership actually changes.
5. Preserve working Matrix3 systems unless concrete evidence shows the requested feature requires changing them.
6. Separate Matrix3 core, gameplay/content, and developer tooling. Tools should call stable APIs rather than quietly taking ownership of engine behavior.
7. Build features in small vertical slices that can be tested and reverted independently.

## RuneScape professional engineering standard

- For RuneScape, RSPS, Matrix3, client, server, cache, rendering, combat, movement, interfaces, tooling, and content work, operate at the quality bar of Jagex's best RuneScape developer/engineer. This is an engineering standard, not a literal identity.
- Treat RuneScape as a complete game platform, not a collection of scripts to patch.
- Aim beyond merely `working`: behavior should be maintainable, performant, polished, intentional, and correctly owned.
- For player-facing systems such as movement, camera, rendering, animation, combat, input, and interfaces, treat game feel as part of correctness: responsiveness, acceleration/deceleration, vectors, interpolation, smoothing, frame-rate independence, state transitions, timing, input conflicts, and visual feedback where applicable.
- For developer tools, build workflows a professional RuneScape content team would actually want to use: fast, clear, low-friction, persistent where useful, with sensible defaults and strong feedback.
- Adapt professional game-development techniques to Matrix3 instead of forcing unrelated modern-engine architecture onto it.
- Preserve correct client/server authority and ownership boundaries.
- Prefer the smallest clean professional solution over both quick hacks and unnecessary overengineering.
- Do not add abstractions, physics, dependencies, subsystems, or complexity merely to appear sophisticated.
- When the obvious solution works but a substantially better professional approach exists, identify the better approach and why it matters.
- Proactively suggest high-value improvements, polish, safeguards, or quality-of-life ideas directly relevant to the current task without turning them into unrelated feature creep.
- Clearly distinguish a temporary prototype/workaround from an implementation suitable for the real project.
- Consider future related systems enough to avoid obvious architectural dead ends, but never use that as a reason for unrelated refactoring.
- Stability, RuneScape correctness, maintainability, performance, and user experience take priority over clever code.

## Communication standard

- Give the maximum useful detail in the fewest words possible.
- Lead with the recommendation, finding, or decision; explain only what materially helps the user act.
- Prefer short bullets, compact examples, and plain English over long paragraphs.
- Avoid unnecessary jargon. Briefly explain technical terms that matter.
- Do not repeat the same point in multiple ways.
- For complex subjects, compress the explanation without removing important risks, decisions, uncertainties, or test steps.
- Keep optional deep technical detail separate from the main actionable answer.

## Reverse engineering

- Preserve original class, field, and method names unless the user explicitly approves renaming.
- Trace only references needed for the requested behavior and stop once enough evidence exists to classify the finding.
- Use these labels accurately:
  - `VERIFIED`: runtime-confirmed behavior.
  - `verified-static`: directly established from source/data but not runtime-confirmed.
  - `HYPOTHESIS`: plausible but not proven.
  - `UNKNOWN`: not established enough to classify further.
- Never present guessed semantics as verified.
- Follow `Evidence -> classification -> minimal patch -> targeted test`.

## Documentation and testing

- Every code change must update or create `docs/<subject>/patchnotes.txt`.
- Keep patchnotes short and technical: what changed and why.
- Add/update a subject test list when runtime behavior changes.
- Run or request the relevant portion of `docs/rs3/SMOKE_TEST.md` after meaningful core changes. Cache/loading, object, networking, persistence, or broad engine changes require the full smoke test unless clearly unnecessary.
- `docs/rs3/BASELINE.md` is the known-good reference point. Do not silently redefine it after regressions.
- When workstream phase, checklist, status, backlog, or carryover changes, update the authoritative workstream `PROJECT.md`.

## Goal-anchored status updates

Status updates are a navigation aid for the user's original/main goal, not a changelog for the latest subtask.

- Every status update, including required post-patch status, must stay anchored to the original/main goal of the active project or workstream.
- Keep a stable set of top-level milestone rows for that goal. Reuse them instead of replacing the table with the latest subtask.
- Put non-milestone work under `Just completed:` or `Current focus:` rather than turning it into a new main-goal row.
- Show enough of the remaining main path that the user can return after a side track and immediately see what comes next.
- If work moves temporarily to a side task, keep the main-goal table unchanged and mention the side task separately.
- End status updates with `Next main step:` using the next meaningful milestone/checkpoint from the authoritative plan.
- Only change the status anchor or stable milestone rows when the user explicitly changes the main goal, starts a separate workstream, or approves a revised roadmap.
- Do not invent percentage-complete estimates unless grounded in an explicit checklist or measurable scope.
- Use `✅ Complete`, `🟡 Foundation`, `🔵 In Progress`, `⚠️ Needs runtime verification` (or a concise audit note), and `❌ Not started` where applicable.

Required post-patch status shape:

1. `Main goal:` the original/main objective.
2. `Just completed:` the patch or subtask that changed.
3. A concise Area/Status table for the stable main-goal milestones.
4. `Next main step:` the next meaningful checkpoint that advances the original goal.
5. Optional blocker/runtime-verification note only when it materially affects that next step.

## Priority discipline

- Stability before expansion.
- Content drives tooling, not the reverse.
- A cool tool is not automatically a priority.
- The first major content milestone is one complete custom boss proving the end-to-end content pipeline.
- Avoid speculative fixes. Evidence -> classification -> minimal patch -> test.
