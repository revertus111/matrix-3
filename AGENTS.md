# Matrix3 Project Rules

Read `docs/rs3/PROJECT.md` before changing code. Also read the relevant `docs/<subject>/patchnotes.txt` and test documentation for the current subject when they exist.

## Repository

- Repository: `revertus111/matrix-3`
- Branch: `main`
- Patch the current branch directly. Do not create or switch branches unless explicitly requested or a real GitHub problem requires it.
- Development target: Eclipse + Java 8. Keep Gradle/build metadata compatible, but do not redesign the project around another IDE or Java version without explicit approval.

## AAA workflow

- No code or documentation changes until the user gives explicit `AAA` approval for the current task.
- `SAP AAA` means scan and patch are approved for that exact task.
- Before AAA, inspect only the smallest likely file set and report findings, likely files, implementation, and important uncertainty.
- After AAA, patch directly and keep the change minimal, isolated, and strictly limited to the requested task.

## Repository scan discipline

- Start with the smallest likely file set for the requested task.
- Prefer known file paths, direct file reads, and exact-reference searches over broad repository searches.
- Do not recursively scan, enumerate, or fetch the entire repository unless the task genuinely requires it.
- Do not repeatedly search for the same class, method, file, symbol, or concept using slightly different queries.
- If the first targeted search fails, use one narrow fallback. Do not continually broaden the search.
- Do not inspect sibling systems, unrelated packages, adjacent tooling, or broad dependency trees unless current evidence shows they are required.
- Stop scanning as soon as the implementation path is established.
- Do not keep searching merely to increase confidence after enough evidence exists to make the requested change.
- Do not repeatedly reread `AGENTS.md`, project documentation, branch state, or files already inspected unless they changed or a specific unread section is required.
- Verify repository and branch state once at the beginning. Recheck only when a GitHub operation fails or there is evidence that repository state changed.
- After `AAA`, patch the established files directly. Do not restart discovery or rescan the repository before making the approved change.
- After patching, verify only the changed files and their immediate dependencies or relevant tests. Do not perform a full-repository review.
- If a GitHub, search, or repository tool fails, make one reasonable targeted retry or fallback. If it still cannot be resolved, report the uncertainty instead of entering a repeated tool-call loop.
- If broader investigation genuinely becomes necessary, state what new evidence requires expanding the scan before expanding it.

## Matrix3 architecture rules

1. Matrix3 is the authoritative game architecture.
2. The 718 project is reference material only. Never port 718 behavior merely because it already exists there.
3. Revision-830 cache/data is data authority where applicable; it does not automatically replace Matrix3 engine ownership.
4. One major system should have one documented owner. Update `docs/rs3/SYSTEM_OWNERSHIP.md` when ownership actually changes.
5. Do not rewrite, replace, or refactor a working Matrix3 system without evidence that the requested feature requires it.
6. Separate work into Matrix3 core, gameplay/content, and developer tooling. Tools should call stable APIs rather than quietly taking ownership of engine behavior.
7. Build features in small vertical slices that can be tested and reverted independently.
8. Do not bundle unrelated fixes or cleanup.

## Reverse engineering

- Preserve original class, field, and method names unless the user explicitly approves renaming.
- Trace only references needed for the requested behavior.
- Stop once there is enough evidence to classify the finding.
- Use these labels accurately:
  - `VERIFIED`: runtime-confirmed behavior.
  - `verified-static`: directly established from source/data but not runtime-confirmed.
  - `HYPOTHESIS`: plausible but not proven.
- Never present guessed semantics as verified.

## Documentation and testing

- Every code change must update or create `docs/<subject>/patchnotes.txt`.
- Keep patchnotes short and technical: what changed and why.
- Add/update a subject test list when runtime behavior changes.
- Run or request the relevant portion of `docs/rs3/SMOKE_TEST.md` after meaningful core changes. Cache/loading, object, networking, persistence, or broad engine changes require the full smoke test unless clearly unnecessary.
- `docs/rs3/BASELINE.md` is the known-good reference point. Do not silently redefine it after regressions.

## Goal-anchored status updates

Status updates are a navigation aid for the user's original/main goal, not a changelog for the latest subtask.

- Every status update in chat, including every required post-patch status, must stay anchored to the original/main goal of the active project or workstream.
- Keep a stable set of top-level milestone rows for that main goal. Reuse those same rows across updates instead of replacing the table with whatever small feature, bug, tool, animation, or research task was just touched.
- A subtask belongs in the main table only when it is itself one of the established top-level milestones. Otherwise summarize it separately as `Just completed:` or `Current focus:`.
- The table must show enough of the remaining main path that the user can return after a side track and immediately see what the real next step is.
- If work temporarily moves to a side task, keep the main-goal table unchanged and mention the side task separately. Do not let tooling or incidental fixes become the apparent project goal.
- End status updates with `Next main step:` using the next meaningful milestone or checkpoint from the original plan, not merely the next convenient subtask.
- Only change the status anchor or stable milestone rows when the user explicitly changes the main goal, starts a separate workstream, or approves a revised main roadmap.
- Do not invent percentage-complete estimates unless they are grounded in an explicit checklist or measurable scope.
- Use `✅ Complete`, `🟡 Foundation`, `🔵 In Progress`, `⚠️ Needs runtime verification` (or a concise audit note when the area has not yet been inspected), and `❌ Not started` where applicable.

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
