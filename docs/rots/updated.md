# RoTS Runtime Continuation / Updated Issues

## Purpose

Persistent handoff/checkpoint for refining Matrix3 Rise of the Six toward:

**100% / 1:1 revision-830 late-2014 Rise of the Six.**

`ROTS_MASTER.md` is the authoritative roadmap. This file tracks runtime findings and the current continuation state.

Status labels:
- `OPEN` - known issue still needs work.
- `PATCHED - VERIFY` - code change exists but still needs runtime confirmation.
- `VERIFIED FIXED` - confirmed by later runtime evidence.
- `NEEDS EVIDENCE` - authenticity/behavior is not proven enough to patch safely.

## Major priority change

The next implementation step is **not** side-isolation or another brother special.

The current copied arena geometry is no longer trusted as authentic RoTS geometry. Historical coordinate evidence places `2328,6036,1` inside the current copied source area and identifies that coordinate as Shadow Realm. This makes the current map being a RoTS sub-area rather than the true two-sided combat arena a strong `HYPOTHESIS`.

A historical coordinate around `3358,9433` is a candidate main-arena lead only. It must be proven against revision-830 map/runtime data before any source-map constant changes.

Until the correct arena is verified:

- do not harden permanent side targeting around the current approximate X split,
- do not treat current brother spawn tiles as authentic,
- do not continue broad special polish,
- preserve already verified runtime fixes and recorder evidence.

## Runtime evidence checkpoints

First recorder fight:
- `rots_2026-09-03_21-04-08-967_4336fb0e.log`
- Rotation 1: West Dharok/Torag/Verac, East Karil/Ahrim/Guthan.
- Recorder cadence was approximately one entry tick every 600 ms.
- Exposed half-speed revive bar, half-speed empty-side monitor and subdued HP regeneration defects.

Second recorder fight after those fixes:
- `rots_2026-09-03_21-42-48-058_1861806.log`
- Same Rotation 1 formation.
- `SIDE_HOP_WARNING` at tick 41; East -> West movement completed at tick 47.
- Revive generation 6 reached `elapsedTicks=50 bar=255/255` at tick 182; five subdued brothers revived on that same recorder tick at 25,000 HP using animation 21914.
- 1,494 subdued brother snapshots remained exactly `hp=1/50000`.
- All-six completion occurred at tick 328.
- No `DROPPED_LINES` entry was present.

Focused acceptance checklist:
- `docs/rots/runtime-fixes-testlist.txt`

## Verified current-code fixes

| Issue | Status | Evidence |
| --- | --- | --- |
| Revive bar progressed at half the active revive cadence | VERIFIED FIXED | Bar now advances every task tick and reaches 255/255 on the same recorder tick as revival. |
| Empty-side warning effectively took about twice the intended current delay | VERIFIED FIXED | Monitor now runs every task tick; warning observed around the intended current ~24-second window. |
| Subdued 1-HP shells naturally regenerated | VERIFIED FIXED | 1,494 subdued snapshots stayed exactly at 1 HP after the RoTS-local restore suppression. |

These prove current Matrix3 behavior only. Exact revision-830 authenticity remains subject to the reconstruction specification.

## Open runtime issues / evidence gaps

| Issue | Status | Direction |
| --- | --- | --- |
| Correct main combat arena/map | NEEDS EVIDENCE - HIGHEST PRIORITY | Prove the revision-830 main arena, plane, chunks, side geometry, ledges, portal/chasm, barriers, spawn slots and entry points. |
| Current `2320,6024` copied area may be Shadow Realm | STRONG HYPOTHESIS | Verify directly against revision-830 map/runtime data. Do not change map constants from external coordinate evidence alone. |
| `3358,9433` main-arena coordinate lead | HYPOTHESIS | Validate/reject against revision-830 data/runtime before use. |
| West/east combat isolation currently fails | OPEN - VERIFIED RUNTIME | Wrong-side targeting/damage is real in the current implementation, but permanent gating must be designed against the verified arena/side rules rather than the approximate current split. |
| Guthan Impale exact behavior | PARTIALLY EXERCISED | Single-player throw/impact/retrieve works; bleed cadence and multiplayer target rules still need authentic evidence/testing. |
| Greatest Axe charge timing vs animation | NEEDS EVIDENCE | Current functional storage/release exists; authentic duration/animation sequence still unresolved. |
| Greatest Axe blue-energy visual | NEEDS EVIDENCE | Current donor GFX candidate is rejected; identify only from evidence-backed cache/runtime research. |
| Post-revive melee special sequencing | NEEDS EVIDENCE | Current simultaneous Hurricane behavior must be compared against authentic late-2014 behavior. |
| One-shot WorldTask delays | NEEDS EVIDENCE | Recorder proves Matrix3 scheduling results, not authentic RuneScape timing constants. |
| Torag cleanup recorder noise | OPEN - LOW PRIORITY | Clean only after encounter correctness work. |
| Fight Recorder normal close/drain acceptance | OPEN TEST | Finish/leave one instance normally and confirm complete drain/end markers. |

## Encounter parity work still open

- Correct main two-sided arena and exact brother spawn geometry.
- Shadow Realm map/location identity and late-2014 transition behavior.
- Exact west/east playable bounds and legal cross-side interactions.
- Central portal/chasm, ledges, barriers and collision behavior.
- Entrance/start/tunnel/pressure-plate behavior where applicable to revision 830.
- Normal attack animation/projectile/GFX/cadence verification for all six brothers.
- Authentic special-rotation controller and auto spacing.
- Dharok full special parity.
- Torag full special parity.
- Guthan full special parity.
- Verac full special parity.
- Ahrim authentic special rotation.
- Karil authentic special rotation.
- Brother Throw and shared/team mechanics.
- Completion/chest/escape/rewards/cleanup.
- Final full-fight 1:1 regression.

## Working runtime evidence to preserve

- Fight Recorder produces useful per-tick/event telemetry.
- Revive bar matches the current active revive-generation cadence.
- Current subdued shells remain exactly 1 HP.
- Current empty-side monitor executes at every task tick.
- Hurricane current 21941 path executes.
- Greatest Axe current stored-damage release executes.
- Torag Whack/Pummel current flow executes.
- Guthan single-player Impale throw/impact/retrieve executes.
- Wall Slam current prototype executes hit and miss cases.
- Logical subdue/revive/all-six-completion flow executes.

## Current continuation order

1. Verify the correct revision-830 main RoTS arena/map and plane.
2. Confirm whether the current `2320,6024` area is Shadow Realm or another RoTS sub-area.
3. Validate/reject the `3358,9433` main-arena lead.
4. Record exact side geometry, portal/chasm, ledges, barriers, brother spawn slots and player entry positions.
5. Identify Shadow Realm and escape/tunnel locations separately.
6. Build the encounter-side targeting/damage specification on the verified geometry.
7. Then fix side isolation and resume normal-combat/special reconstruction in the order defined by `ROTS_MASTER.md`.

## Rule for future chats

Before continuing RoTS work, read:
- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/rots/ROTS_MASTER.md`
- `docs/rots/updated.md`
- `docs/rots/patchnotes.txt`
- `docs/rots/testlist.txt`
- `docs/rots/runtime-fixes-testlist.txt`

Do not treat current prototype behavior as authentic just because it works. Keep `VERIFIED`, `verified-static`, and `HYPOTHESIS` distinctions intact.
