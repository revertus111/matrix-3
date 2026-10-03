# Matrix3 Construction and RTS Rules

Read root `AGENTS.md` first. For the active Construction revamp, also read `docs/construction_revamp/PROJECT.md` before inspecting or changing the system.

This file applies to settlement ownership, workers, RTS controls, jobs, needs, storage, reservations, processing, logistics, population, building, and related persistence.

## Construction architecture

- Treat the settlement as one persistent system with clearly owned subsystems rather than unrelated feature scripts.
- Preserve one owner for worker state, settlement state, storage/logistics state, and persistence boundaries.
- Worker commands/jobs must not silently bypass server gameplay authority.
- RTS selection/camera/input is a control layer over gameplay ownership, not a second implementation of worker logic.
- Storage, reservations, processing, and logistics must remain transactionally safe enough to avoid duplication/loss across interruptions, logout, relog, or competing workers.
- New processing/logistics features should use stable settlement APIs rather than direct cross-system mutation.
- Keep player-facing controls separate from developer/debug controls.
- Persist durable settlement state deliberately; do not persist transient selection/debug/editor state unless explicitly required.
- Prefer one complete vertical slice through player action -> worker/system action -> persistence/test over broad unfinished infrastructure.

## RTS/UI work

- For player-facing Construction interfaces, also read `agents/interfaces.md`.
- For RTS camera, minimap, compass, client input, or rendering, also read `agents/client-engine.md`.
- For server worker/jobs/persistence changes, also read `agents/server-gameplay.md`.
- For Construction developer tools/editors, also read `agents/tools-editors.md`.
- For unfamiliar obfuscated client internals, also read `agents/reverse-engineering.md`.

## Project state

The authoritative current phase, bundle, checklist, canonical main-goal status, test state, and `Resume Here` position remain in `docs/construction_revamp/PROJECT.md`. Do not duplicate that roadmap here.
