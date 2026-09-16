# Matrix3 Workstream Registry

This file is the lightweight index of persistent Matrix3 development workstreams.

The repository runtime has been returned to the protected Matrix3 baseline. Historical workstream documents remain useful reference material, but they must not be treated as proof that their old runtime implementations are still present.

## Status values

- `PLANNED` - idea captured but not actively developed.
- `ACTIVE` - current implementation/research is underway.
- `NEEDS TEST` - current implementation is waiting on user runtime verification.
- `PAUSED` - valid workstream intentionally not current priority or runtime implementation was reset.
- `BLOCKED` - cannot safely continue until a documented dependency/blocker is resolved.
- `COMPLETE` - current defined workstream goal is complete.

## Registered workstreams

| Workstream | Authority document | Status | Notes |
| --- | --- | --- | --- |
| Construction Revamp | `docs/construction_revamp/PROJECT.md` | ACTIVE | Current main workstream. Build Matrix3-native freeform settlement Construction from the protected runtime baseline; legacy 718 implementation is reference-only. |
| Client Atlas | `docs/client-atlas/PROJECT.md` | PAUSED | Historical design/research retained; runtime state was reset and must be revalidated before any implementation claim is reused. |
| BossLabs | `docs/bosslabs/PROJECT.md` | PAUSED | Historical design/research retained; runtime state was reset and must be revalidated before resuming. |
| Rambler's Backpack | `docs/backpack/PROJECT.md` | PAUSED | Historical design retained; runtime implementation claims are not current after the baseline reset. |
| Client Console | `docs/client-console/PROJECT.md` | PAUSED | Historical design retained; runtime implementation claims are not current after the baseline reset. |
| CacheEditor | `docs/cache-editor/PROJECT.md` | PAUSED | Historical design retained; runtime implementation claims are not current after the baseline reset. |
| Interface Editor | `docs/interface-editor/PROJECT.md` | PAUSED | Historical design retained; runtime implementation claims are not current after the baseline reset. |
| Dev Mode | `docs/dev-mode/PROJECT.md` | PAUSED | Historical design retained; runtime implementation claims are not current after the baseline reset. |

## Rules

- One substantial idea should have one authoritative `docs/<subject>/PROJECT.md`.
- Detailed phase/bundle/checklist state belongs in that workstream document.
- Do not infer that preserved documentation means the related runtime code still exists.
- Resume a paused workstream only after validating its current Matrix3 ownership and runtime state.
