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
- Preserve uncertainty explicitly with `VERIFIED`, `verified-static`, `HYPOTHESIS`, or `UNKNOWN`.
- Upgrade an existing entry when stronger evidence becomes available instead of creating a competing entry.
- Do not add guesses presented as facts, temporary debugging noise, or trivial one-off patch details.

A client-map update that records knowledge discovered during an already-AAA-approved client investigation is part of that approved task and does not require another AAA. This exception does not authorize new code behavior, fixes, refactors, unrelated documentation, or scope expansion.

## When reverse engineering is required

If the task enters unfamiliar obfuscated/decompiled internals, also read `agents/reverse-engineering.md` before expanding the trace.
