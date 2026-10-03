# Matrix3 Client Engine Rules

Read root `AGENTS.md` first. This file applies to client engine work such as rendering, camera, scene ownership, input, minimap, compass, movement presentation, animation presentation, runtime client state, and other client-side engine behavior.

## Client ownership

- Preserve Matrix3 client ownership unless concrete evidence requires a change.
- Preserve correct client/server authority boundaries. Client presentation must not silently become gameplay authority.
- Prefer extending the established client owner over adding a parallel implementation.
- Treat game feel as part of correctness where applicable: responsiveness, acceleration/deceleration, vectors, interpolation, smoothing, frame-rate independence, state transitions, timing, input conflicts, and visual feedback.
- Reuse proven vanilla RuneScape/Matrix3 client paths where they already solve the required engine problem cleanly.
- Do not duplicate renderer, camera, scene, minimap, or input ownership merely to make a feature easier to patch.

## Client map

`docs/client/CLIENT_MAP.md` is the persistent searchable map of the Matrix3 client.

Before unfamiliar client work:

- Search `CLIENT_MAP.md` for the exact subsystem, class, method, field, interface ID, opcode, cache index, renderer, camera behavior, input behavior, or semantic term involved.
- Do not read the entire file.
- Read only the relevant mapping/context returned by the search.
- Do not retrace information already recorded as `VERIFIED` or `verified-static` unless repository changes, runtime evidence, or contradictory evidence requires it.

After client investigation:

- Add newly established reusable client knowledge to `CLIENT_MAP.md`.
- Update an existing entry instead of creating duplicate mappings.
- Record exact original class, field, and method names where applicable.
- Record subsystem ownership, important relationships, call flow, IDs, and useful search terms when they materially help future work.
- Explain what the established code actually does, not only what semantic label was assigned to it.
- Preserve uncertainty explicitly with `VERIFIED`, `verified-static`, `HYPOTHESIS`, or `UNKNOWN`.
- Upgrade an existing entry when stronger evidence becomes available instead of creating a competing entry.
- Do not add guesses presented as facts, temporary debugging noise, or trivial one-off patch details.

A client-map update that records knowledge discovered during an already-AAA-approved client investigation is part of that approved task and does not require another AAA. This exception does not authorize new code behavior, fixes, refactors, unrelated documentation, or scope expansion.

## Semantic source comments

The long-term goal is a readable maintained Matrix3 client source tree, not a permanently opaque decompiler dump.

When client investigation establishes reusable semantics for existing source, preserve that knowledge in the source itself as well as in `CLIENT_MAP.md`.

For an established class, field, method, branch, constant, or important block, add concise developer comments/Javadocs that explain the behavior a future developer needs to understand. Depending on the symbol, include the useful subset of:

- what the class/method/field represents;
- what the code does in plain technical language;
- important inputs and how they are interpreted;
- important state read or mutated;
- important outputs, side effects, or downstream systems affected;
- ownership/lifecycle information where relevant;
- non-obvious call flow or ordering requirements;
- special coordinate/unit/bit-domain meanings;
- known rejected assumptions or traps when they prevent future regressions;
- evidence status when the semantic statement is not runtime-verified.

Comments must explain intent/behavior rather than narrating obvious Java syntax. Avoid noisy comments on trivial getters, assignments, loops, or self-explanatory code.

Do not write a confident semantic comment for an `UNKNOWN` fact. A useful `HYPOTHESIS` may be documented only when clearly labeled as such. `verified-static` and `VERIFIED` knowledge should normally be preserved in source comments when it materially improves readability or prevents rediscovery.

When a semantic comment becomes stale because stronger evidence changes the mapping, update or remove the old comment in the same approved work.

## Semantic renaming

Readable semantic names are an approved long-term direction for the Matrix3 client, but renaming must follow evidence rather than guesswork.

- `UNKNOWN`: keep the original obfuscated/decompiled name.
- `HYPOTHESIS`: keep the original name; a clearly labeled comment may record the hypothesis when useful.
- `verified-static`: eligible for semantic renaming when the meaning and rename scope are sufficiently specific and low-risk.
- `VERIFIED`: strong semantic rename candidate.
- Do not give a broad or misleading name to a multi-purpose class/method merely to make it look cleaner.
- Rename the smallest safe semantic unit and keep the project compiling through each logical rename patch.
- Renaming a class means renaming its `.java` source file consistently with the Java type.
- Approved renames must preserve the original identifier in a nearby class/member comment when useful for donor/decompiled-source correlation.
- Every approved semantic rename must be recorded in `docs/client/RENAMES.md` with original symbol, current symbol, evidence classification, short reason/behavior summary, and commit when available.
- When an existing renamed symbol gains a better proven name, update the existing rename record rather than creating ambiguous competing mappings.

The source comment answers **what this code does**. `RENAMES.md` answers **what this used to be called**. `CLIENT_MAP.md` answers **how this part of the client fits into the larger system**.

## When reverse engineering is required

If the task enters unfamiliar obfuscated/decompiled internals, also read `agents/reverse-engineering.md` before expanding the trace.
