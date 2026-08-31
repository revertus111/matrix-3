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

## Priority discipline

- Stability before expansion.
- Content drives tooling, not the reverse.
- A cool tool is not automatically a priority.
- The first major content milestone is one complete custom boss proving the end-to-end content pipeline.
- Avoid speculative fixes. Evidence -> classification -> minimal patch -> test.
