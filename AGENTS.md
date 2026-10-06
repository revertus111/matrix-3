# Matrix3 Project Rules

Read this file once at the start of a Matrix3 task or new chat. Do not reread it unless it changed.

## Repository
- Repo: `revertus111/matrix-3`
- Branch: `main`
- Environment: Eclipse + Java 8.
- Do not redesign around Maven, Gradle, Kotlin, another IDE, or another Java version unless explicitly requested.
- Patch `main` directly. Do not create/switch branches unless the user explicitly requests it or GitHub forces it.

## Specialized agents are opt-in only
- Do **not** read `agents/*.md` automatically.
- Do **not** route into specialized agents because a task touches combat, client, cache, interfaces, construction, reverse engineering, tooling, or another domain.
- Read a specialized agent file only when the user explicitly asks for that exact agent/file or explicitly requests a deep-agent scan.
- Existing `agents/` files may remain in the repository as dormant reference material; they are not part of the default workflow.

## Fast default workflow
- Use current conversation context, accepted runtime results, and established project checkpoints as authority.
- For established work, read only the exact source/docs needed for the requested change.
- Do not reopen settled architecture, ownership, protocols, transforms, or accepted runtime findings unless new evidence contradicts them.
- One narrow lookup/search per genuine unknown is the default. If that fails, use one narrow fallback.
- Stop scanning as soon as the implementation seam is known.
- Never broad-scan or recursively inspect the repo just to increase confidence.
- After `AAA`, patch immediately. Do not restart discovery unless new evidence makes the known path unsafe.
- Batch coherent compatible changes so the user gets one pull/build/test session where practical.

## AAA
- No code or documentation changes without explicit `AAA`, `AAAA`, or `SAP AAA` for the current task.
- `SAP AAA` means scan + patch are approved for that task.
- Once approved, continue implementation -> required docs -> narrow static verification without asking for another AAA unless scope materially changes.
- Runtime-verification bookkeeping for an already-approved patch may be updated without another AAA; new behavior still requires approval.

## Engineering rules
- Matrix3 is authoritative. The old 718 project is reference material only.
- Preserve working Matrix3 ownership and behavior unless the requested feature requires a change.
- Prefer the smallest clean implementation over duplicate systems or speculative abstractions.
- Character/tool adapters may feed shared systems; they must not duplicate an existing shared owner without an explicit architecture decision.
- Never guess obfuscated semantics, protocol layouts, cache formats, transforms, or persistence behavior when the guess could affect correctness.

## Evidence
- `VERIFIED` = runtime-confirmed.
- `verified-static` = directly established from source/data, not runtime-confirmed.
- `HYPOTHESIS` = plausible, not proven.
- `UNKNOWN` = insufficient evidence.
- Never claim compile/runtime success without actual evidence.

## Documentation
- Every code change must update/create `docs/<subject>/patchnotes.txt`.
- Update a subject test list when runtime behavior changes.
- Update `PROJECT.md` only when the workstream/checkpoint itself changes; do not read it merely as ceremony.
- Keep patchnotes concise: what changed and why.

## Communication
- Lead with the finding/result.
- Keep summaries compact and practical.
- Report modified files, testing/evidence, risks, commit SHA, and exact `git pull origin main` after a patch.
- Do not spend the user's runtime/testing time on repeated verification that can be done statically first.
