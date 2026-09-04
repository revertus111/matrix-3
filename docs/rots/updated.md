# RoTS Runtime Continuation / Updated Issues

## Purpose

This file is the persistent handoff/checkpoint for runtime issues discovered while refining Matrix3 Rise of the Six toward the main goal:

**100% / 1:1 2014-era Rise of the Six.**

Use this file when work continues in another chat. It is a continuity tracker, not a replacement for `ROTS_MASTER.md` or the stable main-goal status table.

Status labels:
- `OPEN` - known issue still needs implementation/research.
- `PATCHED - VERIFY` - code change is on `main`, but runtime confirmation is still required.
- `VERIFIED FIXED` - confirmed by a later runtime Fight Recorder log/test.
- `NEEDS EVIDENCE` - behavior/asset/timing is not yet proven enough to patch safely.

## Runtime evidence checkpoints

First source fight log:
- `rots_2026-09-03_21-04-08-967_4336fb0e.log`
- Rotation 1: West Dharok/Torag/Verac, East Karil/Ahrim/Guthan.
- Recorder cadence was approximately one entry tick every 600 ms.
- This log exposed the original half-speed revive bar, half-speed empty-side monitor, and subdued HP regeneration defects.

Second source fight log after the first three runtime fixes:
- `rots_2026-09-03_21-42-48-058_1861806.log`
- Same Rotation 1 formation.
- `SIDE_HOP_WARNING` occurred at tick 41 and the East -> West move completed at tick 47.
- Revive generation 6 reached `elapsedTicks=50 bar=255/255` at tick 182 and the five currently subdued brothers revived on that same recorder tick at 25,000 HP using animation 21914.
- 1,494 `SNAPSHOT_BROTHER` entries were observed with `subdued=true`; every one remained exactly `hp=1/50000`, with no subdued +1 regeneration creep.
- All-six completion occurred at tick 328.
- No `DROPPED_LINES` entry was present, but normal instance close/drain was still not exercised before the uploaded capture ended.

Focused acceptance checklist for recorder-found fixes:
- `docs/rots/runtime-fixes-testlist.txt`

## Immediate runtime-correctness issues

| Issue | Status | Evidence / implementation note | Next verification |
| --- | --- | --- | --- |
| Revive bar progressed at half the actual revive timer rate | VERIFIED FIXED | `startReviveBarTask` now uses `period=0`. In the second recorder log, generation 6 progresses once per recorder tick through `elapsedTicks=50 bar=255/255` at tick 182, and revival occurs on that same tick. Superseded generations stop producing progress after a newer subdue increments `reviveGeneration`. | Keep the separate one-shot 50-tick revive boundary timing question under `NEEDS EVIDENCE`; the bar-vs-revive mismatch itself is fixed. |
| Empty-side warning effectively took ~48s instead of intended ~24s | VERIFIED FIXED | `startSideEmpowermentMonitor` now uses `period=0`. The player becomes WEST at tick 2; `SIDE_HOP_WARNING` fires at tick 41 (~24.2s after recorder start / ~23.4s after WEST classification), not ~48s. | Keep exact post-warning hop-delay timing separate; warning cadence itself is fixed. |
| Subdued 1-HP shells naturally regenerated +1 HP every 10 restore cycles | VERIFIED FIXED | RoTS brother suppresses ordinary Matrix3 natural HP restoration only while `subdued=true`. The second log contained 1,494 subdued snapshots and all remained exactly 1 HP. | Preserve a normal non-RoTS NPC regeneration regression check when doing the next local Eclipse test. |

## Next runtime-correctness issues

| Issue | Status | Evidence / risk | Planned direction |
| --- | --- | --- | --- |
| West/east side isolation is not enforced for combat targeting/damage | OPEN - VERIFIED RUNTIME | Before the side hop, the player is WEST while East Karil/Ahrim/Guthan acquire that player; Karil/Ahrim fire normal attacks across the split and Guthan follows across it. The same log also shows WEST-side player damage landing on East Karil/Guthan before their side ownership changes. Guthan even begins a Hurricane while still logically EAST against the WEST player. | Add a RoTS-local same-side combat gate that rejects wrong-side NPC target acquisition/retention and wrong-side player damage without changing generic Matrix3 combat behavior. Re-test both directions and side crossing. |
| Guthan Impale exact behavior | PARTIALLY EXERCISED | The second log contains two complete single-player Impale/retrieve paths: 21944 throw/projectile 4411, 18541->18542, 21945 victim impact, retrieval start, 21947 retrieve, 18542->18541, and 1000 retrieve hit. No `GUTHAN_BLEED` event occurred because the only victim was already close enough for retrieval. | After side isolation is fixed, test Guthan with a valid same-side victim held outside retrieve range long enough to exercise bleed GFX/damage cadence, then test multi-player victim selection and Torag pummel auto-return. |
| Greatest Axe charge-state timing and animation duration do not line up | NEEDS EVIDENCE | Recorded charge state lasts much longer than animation 21940's ~4.8s cache duration; functional stored-damage release works. | Runtime/video compare the 21940 motion and identify whether animation should repeat, start earlier, or be paired with a separate visual. Do not guess-loop yet. |
| Greatest Axe documented blue-lightning/axe-energy visual is missing | NEEDS EVIDENCE | Donor GFX 4406 is runtime-rejected. Deep Scan GFX loader path was repaired but candidate identification still needs runtime/cache evidence. | Run repaired Deep Scan and test only evidence-supported candidate GFX. |
| Post-revive melee brothers can begin Hurricanes together | NEEDS EVIDENCE | At tick 183 after the five-brother revival, Dharok, Torag and Guthan all enter forced post-revive Hurricane together. | Compare against authentic 2014 behavior before changing shared revive-special sequencing. |
| One-shot WorldTask delays need mechanic-by-mechanic timing verification | NEEDS EVIDENCE | The second log adds precise evidence: side-hop warning at tick 41 reaches movement at tick 47, so the configured 5-tick one-shot delay spans about 3.6s / six recorder intervals. Revive generation 6 starts at tick 131 and revives at tick 182, about 30.65s later for the configured 50-tick one-shot. This does not prove the desired authentic values, only the actual Matrix3 schedule result. | Compare each mechanic against authentic timing before adjusting one-shot values. Prioritize revive, side hop, Torag, Guthan and Greatest Axe. |
| Torag can emit a cleanup `TORAG_RELEASE victim=null` recorder line after the real release | OPEN - LOW PRIORITY | Scheduled cleanup can observe an already-cleared victim. Gameplay did not show a second real release. | Clean recorder/event noise only after higher-value encounter issues are fixed. |
| Fight Recorder normal close/drain path still needs explicit runtime acceptance | OPEN TEST | The second uploaded file also ends without `INSTANCE_FINISH` + `RECORDER END`; it reaches fightComplete snapshots but the instance was not normally closed before capture ended. No `DROPPED_LINES` entry was observed. | Leave/finish one instance normally and confirm queued lines drain with no second recorder file and no `DROPPED_LINES`. |

## Encounter parity work still open

These are not necessarily regressions; they remain part of the original 1:1 roadmap.

- Exact west/east arena bounds and portal/barrier collision/transition behavior.
- Authentic side-empowerment landing positions/assets/blockers beyond the corrected warning cadence.
- Normal attack cadence verification for all six brothers.
- Hurricane authentic pulse/damage/movement parity after the current 21941 integration.
- Wall Slam authentic animation/pathing/AoE/timing.
- Torag Whack/Pummel exact 1:1 timing/asset confirmation.
- Guthan Impale exact bleed/retrieval timing and multiplayer selection.
- Dharok Greatest Axe visual/timing/GFX fidelity.
- Verac authentic complete special rotation, keeping Helicopter/Deathcopter separate from shared Hurricane.
- Ahrim authentic special rotation.
- Karil authentic special rotation / Shadow Dash.
- Brother Throw execution/assets/timing.
- Portal/barriers/shadow realm/arena transitions.
- Completion/escape/rewards/cleanup focused audit.
- Final full-fight 1:1 regression pass.

## Working runtime evidence now established

- Fight Recorder produces useful per-tick/event telemetry with no dropped-line evidence in the first two uploaded fights.
- Empty-side warning cadence now runs at the intended every-task-tick rate.
- Revive bar now runs at the same every-task-tick cadence as the active revive generation and reaches 255/255 at revival.
- Logical subdued shells now remain exactly 1 HP with natural regeneration suppressed only for subdued RoTS brothers.
- Hurricane executes the current 10-pulse 250 -> 2500 ramp and animation 21941 path.
- Greatest Axe stores incoming damage and delivers the stored amount on the later outgoing hit.
- Torag Whack/Pummel/rescue release flow executes.
- Guthan single-player Impale throw/impact/retrieve path executes.
- Wall Slam executes hit and miss cases.
- Logical subdue/revive/all-six completion flow executes.

## Current continuation order

1. Fix RoTS west/east side isolation in both directions: brother target acquisition/retention and player damage against wrong-side brothers.
2. Re-test side crossing plus East/West ranged, mage, melee and Hurricane behavior before the empowerment hop.
3. Exercise Guthan Impale bleed and multi-player selection on the correct side.
4. Resolve the highest-value melee-special fidelity gaps: Greatest Axe visual/timing, Torag/Guthan exact timing, Wall Slam and Verac rotation.
5. Continue the stable roadmap in `ROTS_MASTER.md`: Ahrim -> Karil -> shared/team mechanics -> portal/transitions -> completion/rewards -> final 1:1 regression.

## Rule for future chats

Before continuing RoTS runtime fixes, read:
- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/rots/ROTS_MASTER.md`
- `docs/rots/updated.md`
- `docs/rots/patchnotes.txt`
- `docs/rots/testlist.txt`
- `docs/rots/runtime-fixes-testlist.txt`

Update this file whenever a Fight Recorder log discovers a new persistent issue, an issue is patched, or a later runtime test promotes `PATCHED - VERIFY` to `VERIFIED FIXED`.
