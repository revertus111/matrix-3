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
| Client Atlas | `docs/client-atlas/PROJECT.md` | ACTIVE | Phases 1-3 are verified foundations. Bundle 5A semantic coverage/mapping queue is offline-verified across 33742 symbols / 1221 owners / 393 deterministic bundles. Phase 4 viewer acceptance remains deferred; Bundle 5B scalable assistant semantic writeback is next before real MAP-0001 mapping begins. |
| BossLabs | `docs/bosslabs/PROJECT.md` | NEEDS TEST | V2.1 runtime gate is current; accumulated shell/phases/testing/pattern/Drops work needs one consolidated verification session before V2.3 Asset Workflow. |
| Rambler's Backpack | `docs/backpack/PROJECT.md` | NEEDS TEST | BoB 671/665 presentation is superseded. Bank-interface 762 presentation is implemented statically with independent Backpack storage; Phase 1 Bundle 1.3 backend-isolation/runtime acceptance is next. |
| Client Console | `docs/client-console/PROJECT.md` | NEEDS TEST | V2 implementation bundles are complete statically. Bundle 2.3 consolidated runtime gate is next: icons/dashboard/Commands/Item Browser/Boss Research/Owner/Settings/Atlas coexistence, focus, DPI/layout, bridge authority, and persistence in one session. |
| Interface Editor | `docs/interface-editor/PROJECT.md` | NEEDS TEST | V1.3 adds Load Active Interface, live Open now browsing, and searchable all-interface cache browsing; Bundle 1.3 consolidated runtime acceptance is next. Wire Mesh remains carryover because its AWT paint path flashes under OpenGL. |
| Dev Mode | `docs/dev-mode/PROJECT.md` | NEEDS TEST | Phase 1 Bundles 1.2 World Manipulation and 1.3 Continuous/Paint are implemented statically; runtime is intentionally deferred in one accumulated test queue. Safe independent contextual-editor work may continue when explicitly requested. |
| _Register when a subject is next normalized_ | `docs/<subject>/PROJECT.md` | PLANNED | Existing feature docs remain valid until their workstream is actively normalized. |

## Shared discovery / navigation references

| Reference | Document | Purpose |
| --- | --- | --- |
| Client Atlas | `docs/client-atlas/PROJECT.md` | Persistent client architecture/search/evidence map plus semantic mapping queue so client work can reuse known paths and steadily convert unknown obfuscated code into durable evidence. |

## Registration checklist

When adding a workstream:

1. Create or normalize its authoritative `docs/<subject>/PROJECT.md` using `WORKSTREAM_TEMPLATE.md` where useful.
2. Add one row above with the exact authority path and current high-level status.
3. Preserve existing verified findings and patch history; do not rewrite history just to fit the template.
4. Identify the current phase, active bundle, next checklist item, and `Resume Here` state before ending the session if work remains.
