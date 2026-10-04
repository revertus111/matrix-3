# Matrix3 Project Rules

This root file is the mandatory entry point for every Matrix3 repository task. Read it once at the start of the task/workstream continuation; do not repeatedly reread it unless it changes.

## Context-aware rule routing

Choose the execution lane **before** loading extra process documentation.

### Established continuation / known implementation path

When the current conversation, authoritative project docs, or already-verified source work has established the owner, files, architecture, transform/protocol conventions, and intended implementation seam:

- Use this root `AGENTS.md` as the default rule authority.
- Do **not** automatically reread `docs/rs3/PROJECT.md`, subject project docs, specialized agent files, protocol notes, ownership maps, or evidence checkpoints merely because the task touches those domains.
- Read only the exact source/docs required to make or document the current change.
- Treat existing `VERIFIED`, `verified-static`, accepted transform/protocol conventions, and `Resume Here` state as inputs, not questions to rediscover.
- Do not re-trace settled ownership, re-prove known protocols, or repeat architecture discovery unless new evidence contradicts the established path or the requested change actually alters that contract.
- A user instruction such as “root/main agent only” explicitly disables specialized-agent routing for that approved task unless a new unknown makes the task unsafe to continue without it.
- Once AAA covers the coherent bundle, move directly through implementation -> documentation -> targeted static verification -> one consolidated runtime test whenever practical.

**Process exists to prevent bad guesses, not to add ceremony to work that is already understood.**

### New / unknown / materially changed work

When starting a genuinely new subsystem, unfamiliar obfuscated/core behavior, uncertain ownership path, new protocol/format, or work where the existing architecture does not establish what owns the behavior:

- Slow down before patching.
- Read `docs/rs3/PROJECT.md` and the authoritative `docs/<subject>/PROJECT.md`, patchnotes, and test documentation when they exist.
- Route to only the specialized agent files that materially apply.
- Trace the real owner with the smallest useful source set.
- Establish `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN` accurately.
- Do not guess semantics, signs, protocol layouts, cache formats, renderer ownership, or persistence behavior.
- Once the implementation seam is established, **immediately transition back to the established-continuation Fast lane** rather than carrying Deep-lane ceremony through the rest of the feature.

## Specialized agent routing for new/unknown work

Use these only when the task is new/unknown, the implementation path materially changes, or current evidence specifically requires the domain rules:

- Persistent multi-patch workstream / phase / bundle / resume / canonical status work -> `agents/workstreams.md`
- RuneScape interfaces / HUD / overlays / interface input -> `agents/interfaces.md`
- Client engine / rendering / scene / camera / minimap / compass / input / client runtime behavior -> `agents/client-engine.md`
- Server gameplay / combat / NPCs / world logic / persistence-backed gameplay -> `agents/server-gameplay.md`
- Construction / settlement / RTS / workers / storage / logistics / processing -> `agents/construction.md`
- Cache / revision-830 data / models / animations / sprites / textures / maps / GFX / particles / definitions -> `agents/cache-assets.md`
- ForgeLabs / Live Editor / inspectors / Owner Console tooling / developer editors -> `agents/tools-editors.md`
- Obfuscated/decompiled client, engine, protocol, cache, renderer, or other unfamiliar internals -> `agents/reverse-engineering.md`

If a task crosses domains, read only the additional files required by the unresolved implementation path. Do not load unrelated domain rules merely because they exist.

For unfamiliar client work, use `docs/client/CLIENT_MAP.md` as a searchable knowledge base according to `agents/client-engine.md`. Do not read the entire map during normal development.

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
- Exception for runtime verification bookkeeping: when the user explicitly runtime-verifies an already-AAA-approved patch/checkpoint, immediately update only that exact work's verification/status documentation (for example `PROJECT.md`, testlist, patchnotes, and `Resume Here`) without asking for another AAA. This exception does not authorize new code behavior, fixes, refactors, unrelated documentation changes, or scope expansion; those still require normal AAA approval.
- Specialized agent files may define equally narrow documentation-bookkeeping exceptions for knowledge discovered inside an already-AAA-approved investigation. Those exceptions never authorize new runtime behavior or scope expansion.

## User-time optimization

- Treat the user's PC/runtime testing time as scarce.
- Do as much safe inspection, organization, patching, documentation, and static verification as possible before requiring the user to run the client/server.
- Consolidate compatible runtime verification into short test sessions; prefer one clear pull/start/test session over repeated minor restart cycles.
- Keep runtime instructions ordered, concise, and grouped by required startup state. Distinguish quick checks from deeper tests when practical.
- Never trade stability, correct ownership, evidence, or revertibility for raw patch count.
- When several approved changes are one coherent feature and ownership is already established, batch them into one implementation/test cycle rather than serializing them into artificial slices.

## Prefab/self-test discipline

- For substantial runtime-affecting systems, developer tools, and persistent workstreams, prefer a small deterministic prefab/self-test once the owning APIs are stable enough to support one safely.
- The ideal fast path is `pull -> clean/build -> launch -> select/load safe context -> click one test action -> read PASS/FAIL`, so routine verification costs the user seconds instead of a long manual checklist.
- A prefab/self-test must exercise the real owning APIs or runtime path; do not fake success by only toggling UI state or duplicating production logic inside the test.
- Keep prefab tests disposable and scoped. Prefer exact developer-owned test instances, temporary in-memory definitions/configuration, zero/harmless damage, no rewards, bounded work, deterministic cleanup, and explicit PASS/FAIL output.
- Prefab tests must not silently rewrite persistent content, global live state, rollback history, drops, player saves, cache data, or unrelated world state. If temporary mutation is unavoidable, snapshot and restore it exactly or do not automate that check.
- Rights/admin/developer gates remain authoritative. A self-test is never a bypass around normal permissions or engine ownership.
- A prefab/self-test is the first-line confidence check, not a replacement for focused manual acceptance, persistence/restart verification, multiplayer behavior, or `docs/rs3/SMOKE_TEST.md` when those are required.
- When a runtime regression is found repeatedly, prefer strengthening the relevant prefab/self-test so the same failure becomes cheap to detect in future patches.

## Repository scan discipline

- Start with the smallest likely file set for the requested task.
- Prefer known paths, direct file reads, and exact-reference searches over broad repository searches.
- Do not recursively scan, enumerate, or fetch the entire repository unless the task genuinely requires it.
- Do not repeatedly search for the same class, method, file, symbol, or concept using slightly different queries.
- If the first targeted search fails, use one narrow fallback. Do not continually broaden the search.
- Do not inspect sibling systems, unrelated packages, adjacent tooling, or broad dependency trees unless current evidence shows they are required.
- Stop scanning as soon as the implementation path is established. Do not keep searching merely to increase confidence.
- Do not repeatedly reread `AGENTS.md`, project documentation, branch state, protocols, coordinate conventions, or files already inspected unless they changed or a specific unread section is required.
- Verify repository and branch state once at the beginning. Recheck only when an operation fails or there is evidence repository state changed.
- After patching, verify only changed files and immediate dependencies or relevant tests. Do not perform a full-repository review.
- If a GitHub, search, or repository tool fails, make one reasonable targeted retry or fallback. If it still cannot be resolved, report the uncertainty instead of entering a tool-call loop.
- If broader investigation genuinely becomes necessary, state what new evidence requires expanding the scan before doing so.

## Bounded investigation and execution speed

Use two execution lanes so Matrix3 work stays rigorous without allowing reverse-engineering/tool chains to consume an entire session.

### Fast lane

Use the Fast lane when ownership and the implementation path are already known, including established workstream continuations, normal UI/content changes, documentation, known APIs, established developer-tool paths, and bug fixes with clear evidence.

- Root `AGENTS.md` is sufficient by default for an established continuation; do not automatically load specialized agents or protocol/architecture documentation again.
- Read only the smallest established file set and any exact authority/status file that actually needs modification.
- Do not reopen settled architecture, re-trace known ownership, or revalidate an accepted protocol/transform convention merely because the task touches the same subsystem.
- Once the patch path is established and AAA covers the task, move directly to implementation, documentation, and targeted verification.
- Prefer one coherent patch/test cycle over extra discovery passes or artificial sub-slices.
- If new contradictory evidence appears, pause only the affected assumption, investigate it narrowly, then return to the Fast lane.

### Deep lane

Use the Deep lane only when the task genuinely requires unfamiliar obfuscated/core behavior such as rendering, scene ownership, cache decoding, a new/changed protocol, or another engine path whose semantics are not yet established.

- Read `agents/reverse-engineering.md` only when this lane is actually needed for decompiled/obfuscated internals and the user has not explicitly limited the task to root rules.
- After required authority/workstream/domain reads, default to one bounded discovery pass of roughly 4-6 targeted source files plus at most one narrow fallback search/read when needed.
- Fetch/search only the relevant symbols or line ranges from very large decompiled files where the tooling supports it; avoid repeated full-file retrieval.
- Stop immediately when the ownership/implementation seam is sufficiently established for `verified-static`, `VERIFIED`, `HYPOTHESIS`, or `UNKNOWN` classification.
- Transition back to the Fast lane as soon as the seam is established; do not keep Deep-lane protocol/evidence ceremony active for routine continuation patches.
- Do not perform additional confidence scans after the implementation path is established.
- If new evidence genuinely requires more than the bounded pass, state the reason before expanding and keep the expansion narrowly tied to that evidence.
- If the required seam is still unresolved after the bounded pass, persist the exact inspected files, findings, blocker, uncertainty, and next trace target in the authoritative workstream `Resume Here` when applicable instead of continuing an open-ended tool chain.
- Break difficult unknown work into `trace -> patch -> targeted test` checkpoints. Do not combine unrelated reverse-engineering questions into the same deep pass.

### Tool-latency and timeout discipline

- Avoid long serial chains of repository/search calls when the next action can already be determined from existing evidence.
- A slow/failed repository call gets one targeted retry or one narrower fallback, not repeated variants of the same request.
- If tool latency or response size starts dominating the task, preserve the current evidence/checkpoint first, then continue from that exact point rather than restarting discovery.
- Large prior work does not justify a larger current scan; use saved workstream/client-map state to skip already-settled discovery.
- Speed rules never justify guessing. When the evidence is insufficient after the bounded pass, classify the uncertainty and preserve it rather than forcing a speculative patch.

## Matrix3 architecture rules

1. Matrix3 is the authoritative game architecture.
2. The 718 project is reference material only. Never port 718 behavior merely because it already exists there.
3. Revision-830 cache/data is data authority where applicable; it does not automatically replace Matrix3 engine ownership.
4. One major system should have one documented owner. Update `docs/rs3/SYSTEM_OWNERSHIP.md` when ownership actually changes.
5. Preserve working Matrix3 systems unless concrete evidence shows the requested feature requires changing them.
6. Separate Matrix3 core, gameplay/content, and developer tooling. Tools should call stable APIs rather than quietly taking ownership of engine behavior.
7. Build features in small vertical slices that can be tested and reverted independently when the architecture is uncertain; once a coherent path is established, batch compatible changes to minimize user test cycles.

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

## Evidence classification

Use these labels accurately wherever discovery state matters:

- `VERIFIED`: runtime-confirmed behavior.
- `verified-static`: directly established from source/data but not runtime-confirmed.
- `HYPOTHESIS`: plausible but not proven.
- `UNKNOWN`: not established enough to classify further.

Never present guessed semantics as verified. Follow `Evidence -> classification -> minimal patch -> targeted test` for unknown behavior; do not force that ceremony onto already-established continuation work unless new evidence requires it.

## Communication standard

- Give the maximum useful detail in the fewest words possible.
- Lead with the recommendation, finding, or decision; explain only what materially helps the user act.
- Prefer short bullets, compact examples, and plain English over long paragraphs.
- Avoid unnecessary jargon. Briefly explain technical terms that matter.
- Do not repeat the same point in multiple ways.
- For complex subjects, compress the explanation without removing important risks, decisions, uncertainties, or test steps.
- Keep optional deep technical detail separate from the main actionable answer.

## Documentation and testing

- Every code change must update or create `docs/<subject>/patchnotes.txt`.
- Keep patchnotes short and technical: what changed and why.
- Add/update a subject test list when runtime behavior changes.
- Run or request the relevant portion of `docs/rs3/SMOKE_TEST.md` after meaningful core changes. Cache/loading, object, networking, persistence, or broad engine changes require the full smoke test unless clearly unnecessary.
- `docs/rs3/BASELINE.md` is the known-good reference point. Do not silently redefine it after regressions.
- When workstream phase, checklist, status, backlog, or carryover changes, update the authoritative workstream `PROJECT.md` when that file is actually part of the established task state. Do not load `agents/workstreams.md` solely to repeat already-settled process for a known continuation.
- When client investigation establishes reusable client knowledge, maintain `docs/client/CLIENT_MAP.md` when it is materially relevant to the discovered behavior; do not reopen that map for established continuation work without need.

## Priority discipline

- Stability before expansion.
- Content drives tooling, not the reverse.
- A cool tool is not automatically a priority.
- The first major content milestone is one complete custom boss proving the end-to-end content pipeline.
- Avoid speculative fixes. For unknown behavior: evidence -> classification -> minimal patch -> test. For established behavior: implement the known path, verify narrowly, and move on.
