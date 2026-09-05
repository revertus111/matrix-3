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
| Client Atlas | `docs/client-atlas/PROJECT.md` | ACTIVE | Phase 1 and Phase 2 Bundle 2A are runtime-verified. Bundle 2B search/investigation through 2B.3 is also runtime-verified on 33742 symbols / 325826 relationships (`PHASE 2 INVESTIGATION CHECK: PASS`). 2B.4 Assistant-oriented export is the next execution target. |
| BossLabs | `docs/bosslabs/PROJECT.md` | NEEDS TEST | V2.1 runtime gate is current; accumulated shell/phases/testing/pattern/Drops work needs one consolidated verification session before V2.3 Asset Workflow. |
| Rambler's Backpack | `docs/backpack/PROJECT.md` | NEEDS TEST | Player-owned storage and contextual/bank integration are implemented statically. Phase 1 Bundle 1.2 consolidated runtime verification is the next execution target. |
| _Register when a subject is next normalized_ | `docs/<subject>/PROJECT.md` | PLANNED | Existing feature docs remain valid until their workstream is actively normalized. |

## Shared discovery / navigation references

| Reference | Document | Purpose |
| --- | --- | --- |
| Client Atlas | `docs/client-atlas/PROJECT.md` | Persistent client architecture/search/evidence map so client work can reuse known paths instead of repeatedly rescanning obfuscated code. |

## Registration checklist

When adding a workstream:

1. Create or normalize its authoritative `docs/<subject>/PROJECT.md` using `WORKSTREAM_TEMPLATE.md` where useful.
2. Add one row above with the exact authority path and current high-level status.
3. Preserve existing verified findings and patch history; do not rewrite history just to fit the template.
4. Identify the current phase, active bundle, next checklist item, and `Resume Here` state before ending the session if work remains.
