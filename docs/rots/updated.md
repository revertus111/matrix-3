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

## Runtime evidence checkpoint

Source fight log:
- `rots_2026-09-03_21-04-08-967_4336fb0e.log`
- Rotation 1: West Dharok/Torag/Verac, East Karil/Ahrim/Guthan.
- Recorder cadence was approximately one entry tick every 600 ms.
- All-six completion occurred around recorder tick 316.

## Immediate runtime-correctness issues

| Issue | Status | Evidence / implementation note | Next verification |
| --- | --- | --- | --- |
| Revive bar progressed at half the actual revive timer rate | PATCHED - VERIFY | `startReviveBarTask` used Matrix3 `period=1`, which skips one process pass. Changed to `period=0` so the bar progresses every server task tick alongside the 50-tick revive window. | Subdue one or more brothers and confirm `REVIVE_PROGRESS` reaches 255/255 at the revival boundary and stops for the expired generation. |
| Empty-side warning effectively took ~48s instead of intended ~24s | PATCHED - VERIFY | `startSideEmpowermentMonitor` used `period=1`. Changed to `period=0` so `EMPTY_SIDE_WARNING_TICKS=40` means ~40 x 600 ms. | Keep one side empty with no subdued brothers for ~24s and confirm `SIDE_HOP_WARNING`, then the delayed move/completion path. |
| Subdued 1-HP shells naturally regenerated +1 HP every 10 restore cycles | PATCHED - VERIFY | Matrix3 `Entity.restoreHitPoints()` is the verified-static source. RoTS brother now suppresses ordinary hitpoint restoration only while `subdued=true`, preserving global NPC regeneration. | Subdue a brother for >60s and confirm every snapshot stays at exactly 1 HP until revive/all-six completion. |

## Next runtime-correctness issues

| Issue | Status | Evidence / risk | Planned direction |
| --- | --- | --- | --- |
| Brothers target players across the west/east encounter split | OPEN | Rotation-1 log showed East Karil/Ahrim/Guthan targeting the only West-side player. This also prevented Guthan's same-side Impale selection from executing. | Add RoTS-specific same-side targeting validation without changing generic Matrix3 NPC targeting. Verify target acquisition, target loss on side crossing, and ranged/mage behavior. |
| Guthan Impale was not exercised in the first recorded fight | OPEN / BLOCKED | Zero `GUTHAN_IMPALE_*` events while Guthan was East and the player was West. | Fix side-aware targeting first, then run a same-side Guthan test and verify the full Impale sequence. |
| Greatest Axe charge-state timing and animation duration do not line up | NEEDS EVIDENCE | Recorded charge state lasted about 11.4s from start while animation 21940 is ~4.8s from cache data; animation started after the current delayed schedule. Functional stored-damage release worked. | Runtime/video compare the 21940 motion and identify whether animation should repeat, start earlier, or be paired with a separate visual. Do not guess-loop yet. |
| Greatest Axe documented blue-lightning/axe-energy visual is missing | NEEDS EVIDENCE | Donor GFX 4406 is runtime-rejected. Deep Scan GFX loader path was repaired but candidate identification still needs runtime/cache evidence. | Run repaired Deep Scan and test only evidence-supported candidate GFX. |
| Post-revive melee brothers can begin Hurricanes close together | NEEDS EVIDENCE | Recorder showed multiple melee brothers entering forced post-revive Hurricane in a short window. | Compare against authentic 2014 behavior before changing shared revive-special sequencing. |
| Torag can emit a cleanup `TORAG_RELEASE victim=null` recorder line after the real release | OPEN - LOW PRIORITY | Scheduled cleanup can observe an already-cleared victim. Gameplay did not show a second real release. | Clean recorder/event noise only after higher-value encounter issues are fixed. |
| Fight Recorder normal close/drain path still needs explicit runtime acceptance | OPEN TEST | First uploaded file ended without proving `INSTANCE_FINISH` + `RECORDER END` because the instance was not normally closed before capture ended. | Leave/finish one instance normally and confirm queued lines drain with no second recorder file and no `DROPPED_LINES`. |

## Encounter parity work still open

These are not necessarily regressions; they remain part of the original 1:1 roadmap.

- Exact west/east arena bounds and portal/barrier collision/transition behavior.
- Authentic side-empowerment landing positions/assets/blockers beyond the corrected timer cadence.
- Normal attack cadence verification for all six brothers.
- Hurricane authentic pulse/damage/movement parity after the current 21941 integration.
- Wall Slam authentic animation/pathing/AoE/timing.
- Torag Whack/Pummel exact 1:1 timing/asset confirmation.
- Guthan Impale exact damage/bleed/retrieval timing after it can be exercised correctly.
- Dharok Greatest Axe visual/timing/GFX fidelity.
- Verac authentic complete special rotation, keeping Helicopter/Deathcopter separate from shared Hurricane.
- Ahrim authentic special rotation.
- Karil authentic special rotation / Shadow Dash.
- Brother Throw execution/assets/timing.
- Portal/barriers/shadow realm/arena transitions.
- Completion/escape/rewards/cleanup focused audit.
- Final full-fight 1:1 regression pass.

## Fixed/working runtime evidence from the first Fight Recorder log

- Fight Recorder itself produced useful per-tick/event telemetry.
- Hurricane executed 10 pulses with the current 250 -> 2500 ramp and animation 21941 path.
- Greatest Axe stored incoming damage and delivered the stored amount on the later outgoing hit.
- Torag Whack/Pummel/rescue release flow executed.
- Wall Slam executed hit and miss cases.
- Logical subdue/revive/all-six completion flow executed, exposing the three timing/state bugs above.

## Current continuation order

1. Runtime-verify the three `PATCHED - VERIFY` fixes in one new Fight Recorder log.
2. Fix RoTS same-side targeting so brothers cannot attack through the portal split.
3. Re-test Guthan Impale on the correct side.
4. Return to the melee-special milestone: Greatest Axe fidelity, Torag/Guthan exact timing, Wall Slam, Verac rotation.
5. Continue the stable roadmap in `ROTS_MASTER.md`: Ahrim -> Karil -> shared/team mechanics -> portal/transitions -> completion/rewards -> final 1:1 regression.

## Rule for future chats

Before continuing RoTS runtime fixes, read:
- `AGENTS.md`
- `docs/rs3/PROJECT.md`
- `docs/rots/ROTS_MASTER.md`
- `docs/rots/updated.md`
- `docs/rots/patchnotes.txt`
- `docs/rots/testlist.txt`

Update this file whenever a Fight Recorder log discovers a new persistent issue, an issue is patched, or a later runtime test promotes `PATCHED - VERIFY` to `VERIFIED FIXED`.
