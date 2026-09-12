# Matrix3 Workstream Registry

This file is the lightweight index of persistent Matrix3 development workstreams.

It is not a backlog and should not duplicate the detailed roadmap inside each workstream's authoritative `PROJECT.md`.

## Purpose

Use this registry to answer:

- What substantial ideas/workstreams exist?
- Where is the authoritative project document?
- What is the current high-level state?
- What should a new chat read before continuing that workstream?

## Workstream rules

- One substantial idea should have one authoritative workstream project document, normally `docs/<subject>/PROJECT.md`.
- Register a workstream here when its authoritative project document is created or normalized.
- Keep detailed phase/bundle/checklist state, discovery notes, blockers, testing, and `Resume Here` information inside the workstream project document.
- Do not create competing roadmap/status files for the same workstream.
- Existing subject documentation does not need to be reorganized immediately. Normalize it when that workstream is actively resumed.
- Shared architecture maps or discovery references may be listed when they materially reduce repeat scanning, but they do not replace feature/workstream ownership.
- After the 2026-09-12 runtime reset, pre-reset workstream documents remain reusable design/history references even when their former implementation is no longer present in the active Client/Server trees.

## Status values

- `PLANNED` - idea captured but not actively developed.
- `ACTIVE` - current implementation/research is underway.
- `NEEDS TEST` - current implementation is waiting on user runtime verification.
- `PAUSED` - valid workstream intentionally not current priority.
- `BLOCKED` - cannot safely continue until a documented dependency/blocker is resolved.
- `COMPLETE` - current defined workstream goal is complete.

## Registered workstreams

| Workstream | Authority document | Status | Notes |
| --- | --- | --- | --- |
| Steam Deck Runtime | `docs/steam-deck/PROJECT.md` | NEEDS TEST | Current enabling priority. Portable Java 8 + one-launch local server/client support is implemented statically; physical Deck Desktop Mode runtime is the next gate before Gaming Mode and phone Remote Play. |
| Client Atlas | `docs/client-atlas/PROJECT.md` | PAUSED | Preserved as pre-reset design/research/reuse history. Its post-baseline runtime implementation is not part of the active Client tree after the 2026-09-12 reset. |
| BossLabs | `docs/bosslabs/PROJECT.md` | PAUSED | Preserved for selective reuse. Its former post-baseline implementation is no longer assumed present after the reset. |
| Rambler's Backpack | `docs/backpack/PROJECT.md` | PAUSED | Preserved for selective reuse. Its former post-baseline implementation is no longer assumed present after the reset. |
| Client Console | `docs/client-console/PROJECT.md` | PAUSED | Preserved for selective reuse. Its former post-baseline implementation is no longer assumed present after the reset. |
| CacheEditor | `docs/cache-editor/PROJECT.md` | PAUSED | Preserved for selective reuse. Its former post-baseline implementation is no longer assumed present after the reset. |
| Interface Editor | `docs/interface-editor/PROJECT.md` | PAUSED | Preserved for selective reuse. Its former post-baseline implementation is no longer assumed present after the reset. |
| Dev Mode | `docs/dev-mode/PROJECT.md` | PAUSED | Preserved for selective reuse. Its former post-baseline implementation is no longer assumed present after the reset. |
| _Register when a subject is next normalized_ | `docs/<subject>/PROJECT.md` | PLANNED | Existing feature docs remain valid reference material until their workstream is deliberately rebuilt/resumed. |

## Shared discovery / navigation references

| Reference | Document | Purpose |
| --- | --- | --- |
| Client Atlas | `docs/client-atlas/PROJECT.md` | Persistent client architecture/search/evidence map and reusable pre-reset semantic research. It may still reduce rescanning even though its former runtime implementation was reset. |
| Reset reuse index | `docs/rs3/RESET_REUSE_INDEX.md` | Records the protected baseline, pre-reset HEAD, and policy for selectively reusing old implementation rather than restoring the full post-reset tree. |

## Registration checklist

When adding a workstream:

1. Create or normalize its authoritative `docs/<subject>/PROJECT.md` using `WORKSTREAM_TEMPLATE.md` where useful.
2. Add one row above with the exact authority path and current high-level status.
3. Preserve existing verified findings and patch history; do not rewrite history just to fit the template.
4. Identify the current phase, active bundle, next checklist item, and `Resume Here` state before ending the session if work remains.
