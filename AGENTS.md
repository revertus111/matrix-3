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
- After every completed patch, include a concise status update in the chat showing the current subject/project progression. For phased tools or features, include an Area/Status table using `✅ Complete`, `🟡 Foundation`, `🔵 In Progress`, `⚠️ Needs runtime verification`, and `❌ Not started` where applicable. Keep the status aligned with the repository's actual implementation state.

## Priority discipline

- Stability before expansion.
- Content drives tooling, not the reverse.
- A cool tool is not automatically a priority.
- The first major content milestone is one complete custom boss proving the end-to-end content pipeline.
- Avoid speculative fixes. Evidence -> classification -> minimal patch -> test.
