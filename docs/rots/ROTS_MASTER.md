# Rise of the Six - Revision 830 1:1 Reconstruction

## Goal

Reconstruct The Barrows: Rise of the Six as closely as possible to the **revision-830 late-2014 RuneScape encounter** using revision-830 cache data plus period/runtime/video/documentary evidence.

The current Matrix3-native RoTS implementation is a useful gameplay foundation, but it is **not** the authenticity authority. Existing behavior must be kept, corrected, or replaced only after comparison against the reconstruction specification.

The revision-830 cache is authoritative for what assets and map data are available. It does not by itself prove the gameplay meaning of an animation, GFX, model, projectile, object, sound, coordinate, timing, or mechanic.

## Core reconstruction rule

**Research/specification first. Implementation second.**

Do not continue adding or polishing specials while basic encounter facts are still uncertain.

Before a mechanic is treated as final, establish as much of the following as evidence allows:

- arena/map/plane and relevant coordinates,
- legal targets and side ownership,
- trigger conditions,
- normal attacks before/after the mechanic,
- animation,
- GFX/projectile,
- movement/pathing,
- damage/effects,
- AoE/range,
- timing/duration,
- recovery/return-to-combat behavior,
- multiplayer interaction,
- evidence source and confidence label.

Unknown values stay unknown. Do not fill gaps with donor behavior merely to make the encounter look complete.

## Evidence hierarchy

Prefer evidence in this order:

1. **Revision-830 cache/runtime evidence** - map data, definitions, assets and behavior directly verified in this project.
2. **Jagex / contemporary RuneScape evidence** - official material and period encounter behavior.
3. **Period late-2014 guides/videos** - especially material matching the revision-830 era.
4. **Later RuneScape documentation** - useful when the mechanic is known not to have changed.
5. **RSPS donor implementations** - leads/reference only; never authenticity proof by themselves.

Evidence labels:

- `VERIFIED` - confirmed at runtime.
- `verified-static` - directly established from source/cache/data without runtime confirmation.
- `HYPOTHESIS` - plausible interpretation that still needs proof.

Never promote a cache correlation, donor implementation, current Matrix3 behavior, or visual resemblance to authentic `VERIFIED` behavior without evidence.

## Main-goal status plan

Permanent status anchor:

**100% / 1:1 revision-830 late-2014 Rise of the Six.**

| Main RoTS milestone | Current status | Meaning |
| --- | --- | --- |
| Evidence/specification pack | 🔵 In Progress | Build the authoritative encounter specification before further broad implementation. |
| Correct main arena / map / spawn geometry | ⚠️ Needs verification | Current copied map and spawn layout are not trusted as authentic. Correct revision-830 arena must be proven first. |
| Encounter / side foundation | 🟡 Foundation | Instance, rotation and logical side state exist, but exact arena bounds, target rules and transitions depend on the correct map/spec. |
| Shadow bond / subdue / revive / all-six completion | 🔵 In Progress | Core logical flow works in runtime; exact late-2014 presentation/timing still requires parity evidence. |
| Normal combat and attack cadence | 🟡 Foundation | Normal attacks exist for all six; authentic animations, hit timing and cadence still need a full evidence pass. |
| Melee brother specials | 🔵 In Progress | Substantial prototypes exist, but rotations/assets/timing must be checked against the specification before being treated as authentic. |
| Ahrim special rotation | ❌ Not started | Normal magic/flying foundations exist; authentic late-2014 rotation is not implemented. |
| Karil special rotation | ❌ Not started | Normal ranged combat exists; authentic late-2014 special rotation is not implemented. |
| Shared side/team mechanics | 🟡 Foundation | Some empowerment/Throw foundations exist; authentic cross-brother and side behavior is incomplete. |
| Portal / barriers / Shadow Realm / arena transitions | ⚠️ Needs verification | Correct maps, planes, objects, boundaries and transition behavior must be proven before implementation is trusted. |
| Completion / chest / escape / rewards / cleanup | ⚠️ Needs focused audit | Full late-2014 end-of-fight flow still requires specification and implementation audit. |
| Final 1:1 parity and full-fight regression | ❌ Not started | Run only after all major encounter systems are evidence-backed and implemented. |
| **100% / 1:1 Rise of the Six** | 🔵 **In Progress** | Permanent overall goal. |

## Roadmap order

Use this order unless new evidence proves a prerequisite must move earlier:

1. Build the revision-830 RoTS evidence/specification pack.
2. Prove the correct main combat arena, planes, spawn geometry, split, ledges, portal/chasm, barriers and related map objects directly against revision-830 data/runtime.
3. Prove entrance/tunnel, Shadow Realm and escape areas as distinct encounter locations where applicable.
4. Define exact side ownership, legal targeting/damage and cross-side mechanics on the verified arena.
5. Lock down all six normal attacks: animation, projectile/GFX, damage rules, attack speed and hit delay.
6. Define the authentic special-rotation controller and normal-auto spacing.
7. Finish brothers one at a time against the specification: Dharok -> Torag -> Guthan -> Verac -> Ahrim -> Karil.
8. Finish shared/team mechanics: empowerment, Brother Throw, ledge interactions and side transfers.
9. Finish Shadow Realm / portal / barrier / arena-transition fidelity.
10. Audit and finish completion, chest, escape, rewards and cleanup.
11. Run the final full-fight recorder/parity regression pass.

Do not return to broad animation/GFX hunting unless a specific evidence gap requires it.

## Current highest-priority finding: arena is not trusted

The current implementation copies the source map beginning from Matrix3 source ratio/chunk values corresponding to the area around `2320,6024`, then places the six brothers across one horizontal row near `2326-2336,6034`.

This is **current implementation geometry only**, not authenticated RoTS geometry.

External historical coordinate evidence identifies `2328,6036,1` as **Shadow Realm**. Because that coordinate lies inside the currently copied source area, the current map being the Shadow Realm rather than the main two-sided combat arena is a **strong HYPOTHESIS** that must be proven against revision-830 map/runtime data before any map change.

A historical RoTS coordinate around `3358,9433` is a **candidate lead only** for the main combat arena. Do not patch to it until revision-830 data/runtime confirms the correct region, plane, structure and two-sided geometry.

### Map verification requirements

Before changing the encounter map constants, establish:

- correct main combat arena region/chunks,
- correct plane(s),
- two combat sides and exact playable bounds,
- central portal/chasm geometry,
- Ahrim/Karil ledges or pillar positions,
- brother spawn slots,
- player entry/start positions,
- barriers/collision/crossing points,
- pressure plates/entrance elements if applicable to this build,
- Shadow Realm region as a separate map/location if confirmed,
- post-kill chest/escape tunnel region and transitions.

Do not implement a side-isolation fix around approximate X-coordinate splitting if the underlying arena is still unverified.

## Current implementation authority

RoTS gameplay remains owned by the existing Matrix3 classes:

- `Server/src/main/java/com/rs/game/map/bossInstance/impl/RiseOfTheSixInstance.java`
  - encounter-wide state, daily rotation, logical side ownership, subdue/revive bond, survivor healing, completion and current empowerment foundation.
- `Server/src/main/java/com/rs/game/npc/rots/RiseOfTheSixBrother.java`
  - brother logical state and current special prototypes.
- `Server/src/main/java/com/rs/game/npc/combat/impl/RiseOfTheSixCombat.java`
  - current normal attacks and special dispatch.
- `Server/src/main/java/com/rs/game/npc/rots/RiseOfTheSixReviveBar.java`
  - revive-progress hit bar.

Do not replace these with a second RoTS controller. Once the specification is strong enough, improve the existing implementation in small verified slices.

## Current implementation status

### Encounter foundation

- Six empowered brothers: implemented.
- 20-day west/east brother rotation: implemented; authenticity still belongs to the evidence/spec pass.
- Current side ownership/player-side classification: implemented but based on approximate current map geometry.
- Logical subdue instead of ordinary NPC death: working in runtime.
- Survivor heal on brother subdue: implemented at 5,000.
- Revive countdown: implemented at 50 server task ticks; recorded runtime is approximately 30 seconds, but authentic build timing remains an evidence item.
- Revive hitpoints: implemented at 25,000.
- Custom revive bar: implemented and runtime-synchronized with current revival logic.
- All-six-subdued fight-complete state: working in runtime.
- Empty-side warning/hop: prototype foundation exists; authentic geometry, landing positions, blockers and timing remain unproven.
- Brother Throw eligibility/pairing rules: prototype exists; authentic execution is incomplete.

### Brother mechanics

Treat these as **current prototypes/evidence**, not completed authenticity claims:

- Dharok: 21941 shared spin/Hurricane presentation, Greatest Axe storage/release prototype and Wall Slam prototype exist.
- Torag: 21941 shared spin, Whack/Pummel prototype and Wall Slam prototype exist.
- Guthan: 21941 shared spin plus substantial Impale throw/spearless/retrieve prototype exists.
- Verac: 21941 shared spin runtime visual is known; authentic full special rotation remains incomplete.
- Ahrim: normal magic exists; flying NPC state `18539` is `VERIFIED`; authentic special rotation remains unimplemented.
- Karil: normal ranged exists; authentic special rotation remains unimplemented.

## Working runtime evidence already established

Preserve these findings while the broader reconstruction is redone:

- Fight Recorder produces useful per-tick/event telemetry.
- Revive bar cadence now matches the active revive generation in current Matrix3 scheduling.
- Current logical subdued shells remain exactly 1 HP without global NPC-regeneration changes.
- Empty-side warning now executes at the intended current every-task-tick cadence.
- NPC `18539` is `VERIFIED` as the flying Ahrim state in current runtime.
- NPC `18542` is `VERIFIED` as the spearless Guthan state used by the current Impale implementation.
- NPCs `18546-18551` are body-only/null-name cache definitions and render invisibly in current revision-830 runtime testing; authentic purpose remains unproven.
- Current RoTS combat-family models render with Santa hats in this revision-830 cache/runtime.
- Animation `21941` is `VERIFIED` to produce the sustained shared spin presentation across Dharok, Guthan, Torag and Verac.
- Current Greatest Axe stored-damage release executes functionally.
- Current Torag Whack/Pummel flow executes functionally.
- Current Guthan single-player Impale throw/impact/retrieve flow executes functionally.
- Current Wall Slam prototype executes hit and miss cases.
- Current logical subdue/revive/all-six-completion flow executes.

These prove current code behavior. They do **not** automatically prove late-2014 authenticity.

## Known current runtime defects / uncertainties

- West/east combat isolation is not enforced in the current approximate arena: wrong-side brothers can target/follow/attack a player and player damage can land on wrong-side brothers.
- Because the main arena itself is now unverified, do not patch permanent side gating until correct map geometry and side rules are specified.
- Guthan Impale bleed cadence and multiplayer victim selection still need authentic evidence/runtime exercise.
- Greatest Axe animation duration, charge timing and missing visual remain unresolved.
- Post-revive melee special sequencing remains unverified.
- Current one-shot task delays describe Matrix3 scheduler behavior, not automatically authentic RuneScape timings.
- Current normal attack delays and special spacing are prototypes and require replacement or confirmation from evidence.

## Research workflow

For each encounter area/mechanic:

1. State the exact question being answered.
2. Gather the smallest relevant cache/runtime/period evidence set.
3. Record the evidence and classify it.
4. Write the expected revision-830 behavior as a compact specification.
5. Compare the current Matrix3 implementation against that specification.
6. Classify implementation pieces as `KEEP`, `FIX`, `REPLACE`, or `MISSING`.
7. Patch only after the behavior is evidence-backed enough to justify implementation.
8. Verify with Fight Recorder/runtime and update the subject evidence.

## Research tools

Primary cache workflow:

- Client Console -> Owner -> RoTS cache research
- `Scan RoTS` for focused NPC/animation definition evidence.
- `Deep Scan` for targeted render-set/BAS/GFX correlation.
- `Copy All` to preserve research output.

Runtime Fight Recorder:

- `Server/src/main/java/com/rs/game/npc/rots/RiseOfTheSixDebugLog.java`
- Creates one unique file per RoTS instance under `data/logs/rots/`.
- Records recorder ticks, elapsed milliseconds, players, brother states, targets, sides, HP, special state, normal attacks and mechanic events.
- Use it to verify implementation behavior after the specification says what should happen.

Optional fallback:

- `Server/src/main/java/com/rs/tools/RotsCacheScanner.java`
- Read-only; use only when a focused standalone cache dump is actually needed.

## Documentation areas

### Encounter

Use `mechanics/encounter.txt` for verified/spec evidence concerning arena setup, side ownership, rotation, subdue/revive, side transitions, completion, escape and cleanup.

### Brothers

Create/update brother-specific mechanic evidence only when enough evidence exists to justify the claim. Do not create empty documentation merely for symmetry.

### Assets

Use `assets/` for cache/runtime-supported IDs. Clearly distinguish current implementation mappings from authentic revision-830 mappings.

### Timings

Use `timings/` only for measured/documented attack cadence, animation duration, special windups, hit timing, cooldowns, revive timing and encounter timers. Never store guessed timing as fact.

## Current next main step

**Verify the correct revision-830 RoTS map/arena before changing more combat behavior.**

Required output of that research step:

1. Identify the main two-sided combat arena region/chunks and plane.
2. Confirm whether the currently copied `2320,6024` area is the Shadow Realm or another RoTS sub-area.
3. Validate or reject the `3358,9433` main-arena lead against revision-830 data/runtime.
4. Record exact combat-side geometry, central structures, ledges, brother spawn slots and entry points.
5. Identify the related Shadow Realm and escape/tunnel regions separately.
6. Only then update the encounter map implementation and proceed to exact side rules.

Until that is complete, do not spend implementation time polishing Hurricane, Greatest Axe, Guthan, Ahrim or Karil.
