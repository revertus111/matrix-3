# Matrix3 Reverse Engineering Rules

Read root `AGENTS.md` first. This file applies to obfuscated/decompiled client, engine, protocol, cache, renderer, scene, or other unfamiliar internals where semantics are not already established.

## Investigation rules

- Preserve original class, field, and method names until semantics are sufficiently established for an approved rename.
- Trace only references needed for the requested behavior and stop once enough evidence exists to classify the finding.
- Before expanding an unfamiliar trace, check the smallest relevant file set for existing developer/decompiler breadcrumbs such as `System.out`, `print`/`println`, `printStackTrace`, debug strings, comments, exception messages, temporary semantic labels, or similar diagnostics.
- Treat debug/decompiler breadcrumbs as leads, not authority. Prior labels may reflect what an earlier developer believed a method did and must be checked against surrounding source/data and runtime behavior.
- Use relevant breadcrumbs to narrow symbol/reference tracing and avoid unnecessary broad scans.
- Promote breadcrumb-derived semantics to `verified-static` only when source/data supports them, and to `VERIFIED` only after runtime confirmation. Unsupported or contradictory labels remain `HYPOTHESIS` or `UNKNOWN`.
- Never present guessed semantics as verified.
- Follow `Evidence -> classification -> semantic comment/map update -> minimal patch -> targeted test`.

## Evidence labels

- `VERIFIED`: runtime-confirmed behavior.
- `verified-static`: directly established from source/data but not runtime-confirmed.
- `HYPOTHESIS`: plausible but not proven.
- `UNKNOWN`: not established enough to classify further.

## Semantic source documentation

When reverse engineering establishes reusable meaning, preserve that understanding in the source itself instead of leaving the next developer with only obfuscated names.

For materially useful established semantics, add concise comments/Javadocs that explain what the code does and why it matters. Record the useful subset of:

- semantic responsibility of the class/method/field;
- actual behavior or algorithm in plain technical language;
- inputs/arguments and any non-obvious interpretation;
- state/fields read or mutated;
- returned values, side effects, dispatches, render/network/cache effects, or other downstream impact;
- lifecycle/owner/caller relationship;
- important ordering, coordinate systems, units, bit ranges, cache/index meanings, or invariants;
- known rejected interpretations that would otherwise cause repeat mistakes;
- evidence label when not `VERIFIED`.

Do not comment obvious syntax line-by-line. The goal is understanding, not comment volume.

`UNKNOWN` semantics must not receive confident descriptive comments. `HYPOTHESIS` comments must literally identify themselves as hypotheses. Stable `verified-static` or `VERIFIED` semantics should normally be written back to source when the information is reusable.

If stronger evidence later changes the meaning, update/remove the old semantic comment in the same work so source comments never become stale folklore.

## Semantic renaming rules

The long-term target is clean readable Matrix3 client source, including meaningful `.java` class names, method names, and field names.

- Do not rename `UNKNOWN` or merely `HYPOTHESIS` symbols.
- `verified-static` symbols may be renamed when the meaning is specific enough and the affected references are understood.
- `VERIFIED` symbols are strong rename candidates.
- Avoid semantic overclaiming: a class with several responsibilities must not be renamed to one narrow responsibility unless evidence proves that ownership.
- Prefer small coherent rename batches that can compile/test independently.
- Preserve the original obfuscated/decompiled symbol in source comments where it materially helps correlate old docs, donor source, old commits, or another revision.
- Record every approved semantic rename in `docs/client/RENAMES.md`.
- A rename record must include original symbol, current symbol, evidence classification, a short behavior/reason summary, and commit when available.
- If a prior rename becomes inaccurate, update the existing record and source comment rather than adding a second conflicting mapping.

## Client map persistence

For client-side reverse engineering, `docs/client/CLIENT_MAP.md` is the persistent searchable knowledge base.

- Search the client map before tracing an unfamiliar class, field, method, subsystem, interface ID, opcode, renderer, camera path, input path, or cache-related behavior.
- Do not read the whole client map. Search for the exact symbol, subsystem, ID, or semantic term and read only the relevant entry/context.
- Do not retrace an existing `VERIFIED` or `verified-static` mapping unless repository changes, runtime evidence, or contradictory evidence requires re-verification.
- When reverse engineering establishes a stable reusable client mapping, update `docs/client/CLIENT_MAP.md` before considering the investigation complete.
- Update an existing entry instead of creating duplicate mappings.
- Preserve exact original class/field/method names and useful relationships/call ownership.
- Explain the established code behavior, not just a semantic nickname.
- Record uncertainty explicitly with the correct evidence label.
- When later evidence improves a mapping, upgrade the existing entry rather than adding a competing entry.
- Do not add guesses as facts, temporary debugging noise, or trivial one-off implementation details.

A client-map/source-comment documentation update that records knowledge discovered during an already-AAA-approved client investigation is part of that approved investigation and does not require a second AAA. This exception authorizes only the directly discovered mapping/documentation, not new behavior, refactors, unrelated documentation, or semantic renames not covered by the approved scope.
