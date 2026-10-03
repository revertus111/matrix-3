# Matrix3 Reverse Engineering Rules

Read root `AGENTS.md` first. This file applies to obfuscated/decompiled client, engine, protocol, cache, renderer, scene, or other unfamiliar internals where semantics are not already established.

## Investigation rules

- Preserve original class, field, and method names unless the user explicitly approves renaming.
- Trace only references needed for the requested behavior and stop once enough evidence exists to classify the finding.
- Before expanding an unfamiliar trace, check the smallest relevant file set for existing developer/decompiler breadcrumbs such as `System.out`, `print`/`println`, `printStackTrace`, debug strings, comments, exception messages, temporary semantic labels, or similar diagnostics.
- Treat debug/decompiler breadcrumbs as leads, not authority. Prior labels may reflect what an earlier developer believed a method did and must be checked against surrounding source/data and runtime behavior.
- Use relevant breadcrumbs to narrow symbol/reference tracing and avoid unnecessary broad scans.
- Promote breadcrumb-derived semantics to `verified-static` only when source/data supports them, and to `VERIFIED` only after runtime confirmation. Unsupported or contradictory labels remain `HYPOTHESIS` or `UNKNOWN`.
- Never present guessed semantics as verified.
- Follow `Evidence -> classification -> minimal patch -> targeted test`.

## Evidence labels

- `VERIFIED`: runtime-confirmed behavior.
- `verified-static`: directly established from source/data but not runtime-confirmed.
- `HYPOTHESIS`: plausible but not proven.
- `UNKNOWN`: not established enough to classify further.

## Client map persistence

For client-side reverse engineering, `docs/client/CLIENT_MAP.md` is the persistent searchable knowledge base.

- Search the client map before tracing an unfamiliar class, field, method, subsystem, interface ID, opcode, renderer, camera path, input path, or cache-related behavior.
- Do not read the whole client map. Search for the exact symbol, subsystem, ID, or semantic term and read only the relevant entry/context.
- Do not retrace an existing `VERIFIED` or `verified-static` mapping unless repository changes, runtime evidence, or contradictory evidence requires re-verification.
- When reverse engineering establishes a stable reusable client mapping, update `docs/client/CLIENT_MAP.md` before considering the investigation complete.
- Update an existing entry instead of creating duplicate mappings.
- Preserve exact original class/field/method names and useful relationships/call ownership.
- Record uncertainty explicitly with the correct evidence label.
- When later evidence improves a mapping, upgrade the existing entry rather than adding a competing entry.
- Do not add guesses as facts, temporary debugging noise, or trivial one-off implementation details.

A client-map documentation update that records knowledge discovered during an already-AAA-approved client investigation is part of that approved investigation and does not require a second AAA. This exception authorizes only the directly discovered mapping/documentation, not new behavior, refactors, unrelated documentation, or scope expansion.
