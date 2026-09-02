# The Barrows: Rise of the Six

Target: reproduce the classic RuneScape 3 Rise of the Six encounter as completely as practical on Matrix3 while preserving Matrix3 combat, NPC, world, instance, drop, and persistence ownership.

This document is the encounter fidelity checklist. A mechanic is not considered complete merely because the fight is killable.

## Classification

- VERIFIED: confirmed by runtime testing in this Matrix3 project.
- verified-static: confirmed from Matrix3 source/cache structure or strong donor/authoritative encounter evidence, but not yet runtime-proven here.
- HYPOTHESIS: implementation detail that still requires runtime evidence before it can be treated as correct.

## Fidelity evidence ladder

RoTS must not be reconstructed from one RSPS donor and assumed accurate. Use this order of evidence:

1. **Behavior authority - RuneScape encounter evidence**
   - Prefer RuneScape/Jagex-era mechanic descriptions, the Beasts transcript, strategy documentation, and period-correct gameplay footage for what an attack actually does.
   - Use old 2013-2015 RoTS footage when code/text cannot establish exact movement, timing, facing, wall interaction, throw trajectories, Deathcopter behavior, or escape sequencing.
2. **Asset evidence - Matrix-family donor implementations**
   - Nocturne and other Matrix-family RoTS implementations are useful for candidate NPC ids, animation ids, graphics, projectiles, object ids, coordinates, transformations, and sequencing clues.
   - Donor gameplay logic is reference only. Known donor bugs/placeholders must never become Matrix3 behavior merely because the code exists.
3. **Data verification - revision-830 cache**
   - Candidate NPC/animation/GFX/projectile/object ids must exist and resolve correctly in the active cache before being treated as project evidence.
   - Alternate NPC forms, object animations, map coordinates and visual assets should be checked against cache/runtime rather than guessed.
4. **Final authority - Matrix3 runtime**
   - The mechanic is only VERIFIED after it behaves correctly in this project at runtime.
   - Screenshots/video plus server behavior are preferred for movement-heavy mechanics. Runtime evidence decides whether a donor/cache interpretation was actually correct.

Normal workflow:

**Encounter behavior evidence -> donor asset candidate -> revision-830 data check -> smallest Matrix3-native implementation -> targeted runtime test -> VERIFIED or revise.**

## Brother toolkit map

This table is the working high-level mechanic authority for the six brothers. Exact cadence, animation ids, damage rolls and edge cases may remain HYPOTHESIS until verified, but the mechanic ownership itself should stay consistent.

| Brother | Classic RoTS toolkit to reproduce | Wall Slam? | Notes |
| --- | --- | --- | --- |
| Dharok | Hurricane -> Greatest Axe -> Wall Slam | Yes | Greatest Axe absorbs incoming damage during the charge and returns it through his next attack. |
| Torag | Hurricane -> Whack -> Wall Slam, plus conditional Throw interactions | Yes | Whack pins a victim until enough teammate damage is dealt to Torag. |
| Guthan | Hurricane -> Impale, plus conditional Throw interactions | No | His spear/Impale state is his major unique movement-pressure mechanic; he does not normally Wall Slam. |
| Verac | Soulspot, Deathcopter, Wall Slam, plus conditional Throw interactions | Yes | **Normal Verac should not use Hurricane as a regular rotation special.** A revived melee brother may still be forced into Hurricane by the shared revival rule. |
| Ahrim | Turret of Fire, Shadow Pits, Flight | No | Flight uses his alternate NPC state and changes his attack/defence behavior. |
| Karil | Lightning Conductor, Bombard, Portal Dash | No | His specials are ranged/shadow/arena-control mechanics rather than melee Wall Slam/Hurricane behavior. |

### Important current implementation mismatch

The current Matrix3 checkpoint allows Verac to use Hurricane as part of his normal temporary implemented-special subset. That is **not the intended final RoTS behavior**. Verac's normal tracking/spinning identity is Deathcopter; normal Hurricane should be removed from his ordinary rotation when the next melee-brother fidelity patch is made. The shared rule that a revived melee brother can force Hurricane remains valid and is separate from Verac's normal rotation.

## Throw interaction model

Throw is not being treated as an isolated generic attack owned by every brother. It is a conditional cross-brother interaction used by parts of the melee-brother toolkit and later feeds mechanics such as Verac Deathcopter.

Current fidelity rule:

- Do not invent a universal Throw action or assume every brother can throw every other brother.
- Record eligible thrower/target pairings only when encounter evidence or cache/donor behavior establishes them.
- Keep Throw coordination in the RoTS encounter/brother state, not in generic Matrix3 NPC combat.
- A Throw sequence must cleanly handle target death, thrower death/subdual, side changes, Shadow Realm transitions, and instance cleanup.
- Deathcopter must be able to intercept/replace the relevant Verac throw interaction rather than existing as an unrelated animation-only special.

Exact eligible pairings, trigger windows, trajectory, impact area, damage and animation ids remain fidelity items until stronger evidence is tied to the classic target behavior.

## Guthan Impale lifecycle

Guthan's Impale is more than a single projectile hit and should be implemented as a temporary spear-ownership state:

1. Guthan selects the appropriate victim according to the classic target-selection rule; exact selection priority is still to be verified.
2. Guthan throws/impales the victim with his warspear.
3. Guthan changes into his spear-absent state using alternate NPC id **18542** when the cache/runtime presentation matches the expected state.
4. While the spear is away, Guthan should fight without the spear and should not behave as though he still owns the full spear-special toolkit.
5. The impaled victim receives repeated hard-typeless bleed pressure while separated from Guthan. Strategy evidence describes roughly **400-500** repeated damage in the documented encounter; exact classic timing/value must still be matched to the target fidelity window.
6. The victim must return close to Guthan to end the Impale state normally.
7. Guthan retrieves/rips the spear back out, restores his normal spear state, and applies the retrieval hit. Strategy evidence describes roughly **1,000** hard-typeless damage; exact classic value/timing remains to verify.
8. If Guthan is subdued, the victim dies/leaves, the encounter changes realm/state, or the instance ends, the spear/bleed state must clean up safely and cannot remain stuck on either entity.

The alternate-state transition, bleed presentation, projectile/animation/GFX ids, exact victim-selection rule, retrieval range and tick timings must be established from donor/cache/video evidence before this mechanic is marked VERIFIED.

## Current runtime checkpoint

VERIFIED from 2026-09-01 user runtime test
- The revision-830 cache contains the RoTS arena at the donor-backed source map; the copied arena renders correctly rather than voiding.
- All six empowered brothers spawn visibly on valid arena terrain.
- Empowered brother identity/model data resolves correctly; Guthan was runtime-confirmed as `Guthan the Infested (level 650)`.
- Brothers acquire/attack the player in the copied arena.
- At least one subdued brother returns through the current shadow-bond revival path.

verified-static
- Rise of the Six is a 1-4 player encounter; the original intended group size is four.
- Empowered brothers use combat level 650 and 50,000 life points.
- Empowered NPC ids: Ahrim 18538/18539, Dharok 18540, Guthan 18541/18542, Karil 18543, Torag 18544, Verac 18545.
- Defeating a brother heals each active brother by 5,000 life points.
- The shared shadow-bond revive timer is 30 seconds and resets whenever another brother is defeated.
- If the timer expires, all currently defeated brothers return with 25,000 life points.
- The fight ends only when all six brothers are defeated at the same time.
- Fight music is The Price is Wight, music-track id 1208.
- Nocturne's RS3 RoTS implementation copies source chunk 290,753 as an 8x8-chunk map and starts the host at source offset +10,+1 on plane 1.
- Matrix3 MapInstance ratio 1x1 copies exactly 8x8 chunks, matching that donor map shape.
- Source coordinate 2328,6036,1 lies inside that copied map and is documented as a Shadow Realm location.
- Dharok's donor Greatest Axe state uses `Give me everything!`, GFX 4406, animation 21940, absorbs incoming damage for roughly eleven seconds, then adds all stored damage to his next outgoing hit.
- Torag's donor Whack state uses animations 21933 (Torag start), 21934 (victim pin), 21935 (hammering), and 21938 (victim release); 2,500 incoming damage while hammering breaks the victim free without damaging Torag.
- Donor base combat values: Ahrim 3,000 max / projectile 559 / impact GFX 377 / 5 ticks; Karil 3,000 max / projectile 955 / animation 18232 / 7 ticks; Guthan/Torag/Verac 3,500 max; Dharok scales 2,000 through 7,000 by HP band.
- Hurricane is a shared melee-brother special for Dharok, Guthan and Torag during normal fighting; the brother tracks the target while spinning at walking speed for ten damage pulses, damaging nearby players with escalating hard-typeless hits that can reach roughly 2,500.
- Verac is a melee brother for shared revival behavior, but his normal tracking/spinning special is Deathcopter rather than ordinary Hurricane.
- Wall Slam belongs to Dharok, Torag and Verac: the brother runs to a nearby wall, then attacks the location captured when the special began; the impact covers a 5x5 area and can deal roughly 3,000 hard-typeless damage.
- For Dharok and Torag, the classic shared rotation is Hurricane -> brother-specific special -> Wall Slam after the initial special selection, with normal auto-attacks separating specials.
- A revived melee brother should force Hurricane as its next special, including Verac when the shared revival behavior applies.

HYPOTHESIS pending targeted runtime tests
- Exact daily formation/facing and west/east side ownership still need runtime/map verification.
- Current 3-5 normal-auto gate between implemented melee specials is a temporary timing approximation until exact classic attack-count timing is established.
- Hurricane currently uses a one-tile Chebyshev damage radius and a deterministic 250-to-2,500 pulse ramp; exact empowered-brother radius/damage distribution still needs runtime/source evidence.
- Exact Hurricane spin animation/GFX are not yet established in the active cache. The implementation deliberately reuses the brother's normal attack emote as a temporary visual instead of inventing an animation id.
- Wall Slam's captured 5x5 impact and 3,000 cap are verified-static, but exact arena wall anchors, run-up/fling animation ids and damage distribution remain HYPOTHESIS. The current implementation discovers a reachable collision edge with Matrix3 pathing instead of hardcoding unverified wall coordinates.
- Guthan's normal rotation remains incomplete until Impale/Throw are implemented.
- Verac's current normal Hurricane allowance is a known implementation mismatch; final normal rotation should use Soulspot/Deathcopter/Wall Slam/Throw interactions as established rather than ordinary Hurricane.
- Torag's temporary 18-tick safety release is a Matrix3 runtime guard for solo testing; authentic natural timeout/escape behavior still needs verification.
- The temporary Matrix3 boss-instance exit/grave tile is deliberately a known Barrows surface tile until object 87997 and the real RoTS graveyard path are wired.

## Implemented now

- Native Matrix3 BossInstance registration with a four-player cap.
- Private instance creation through the existing BossInstanceD flow.
- Admin/developer launch through `bosslabs rots` or `bosslabs riseofthesix` without changing Commands.java.
- Dedicated RoTS brother NPC class; normal BarrowsBrother remains untouched.
- Dedicated CombatScript keys for all empowered/alternate RoTS NPC ids.
- Donor-backed base max-hit bands and Ahrim/Karil projectile/animation assets through Matrix3 CombatScript.
- Guthan life-steal baseline at the documented 1-in-8 roll.
- Ahrim Strength-drain baseline using the documented 1-in-8 chance.
- Shared defeat state: brothers become incapacitated rather than using normal NPC drop/respawn death.
- Survivor heal of 5,000 HP after each brother is subdued.
- 30-second revive generation reset after every new subdued brother.
- Revive of every currently subdued brother at 25,000 HP when the timer expires.
- Victory lockout when all six are subdued before the timer expires.
- Dharok Greatest Axe first faithful slice: force-talk, charge GFX/animation, incoming damage absorption/storage, no autos during charge, and stored damage returned through his next normal hit.
- Torag Whack first faithful slice: victim pin/lock, start/hammer/release animations, incoming teammate damage redirected into the 2,500 release threshold, and cleanup-safe victim unlock.
- Shared Hurricane runtime currently exists for Dharok, Guthan, Torag and Verac: ten escalating hard-typeless pulses, nearby-player damage, active target pursuit at walking speed, normal-auto lockout while spinning, and cleanup-safe target restoration. Verac's normal use is a known temporary mismatch documented above.
- Wall Slam runtime for Dharok/Torag/Verac: captures the target's starting tile, uses Matrix3 collision/pathing to run toward an arena edge and rush back, then resolves a 5x5 hard-typeless impact around the captured tile.
- Dharok/Torag now cycle through all three currently implemented rotation slots; Guthan/Verac use only the shared specials currently implemented for them.
- Revived melee brothers force Hurricane as their next special.
- Instance cleanup finishes all custom RoTS NPCs when the encounter is destroyed.

## Required for 100% encounter fidelity

### Entry / staging
- Wire the real Rise of the Six well object 87997.
- Consume exactly one Barrows totem (item 30004) when creating a fight as appropriate.
- Reproduce join/rejoin rules and the original staging tunnel.
- Reproduce random tunnel layouts/bridge counts.
- Reproduce four pressure pads and fight-lock once combat begins.
- Replace the temporary developer exit/grave tile with the verified RoTS graveyard route.

### Formation / arena
- Implement the complete daily brother-formation rotation.
- Verify exact brother spawn coordinates and facing.
- Verify arena barriers/portal traversal and side ownership.
- Implement empty-side empowerment and brother side-hop rules.
- Prevent late joins after the fight starts.

### Shared shadow mechanics
- Implement Shadow Drag trigger selection/thresholds.
- Move the full team into/out of the Shadow Realm correctly.
- Apply Shadow Realm accuracy/damage changes.
- Add the visible 30-second incapacitation/revival progress presentation.

### Shared melee fidelity
- Runtime-verify Hurricane pursuit, pulse timing/range/damage and replace the temporary normal-attack visual with the exact spin animation/GFX once established.
- Runtime-verify Wall Slam's collision-edge selection, run-away/rush-back timing and 5x5 impact; replace path approximations with exact arena wall anchors/animations only when evidence establishes them.
- Replace the temporary 3-5 auto gate with the exact classic special cadence once verified.
- Remove ordinary Hurricane from Verac's normal temporary rotation while preserving the shared post-revival forced-Hurricane rule.
- Build the verified cross-brother Throw interaction instead of implementing Throw as unrelated per-NPC attacks.

### Ahrim
- Blood-spell self healing.
- Flight transformation/state using the alternate NPC id.
- Melee immunity and reduced magic/necromancy damage while airborne.
- Purple/red Shadow Pits and their player-damage/brother-heal behavior.
- Turret of Fire.
- Exact attack/special rotation, animations, graphics, projectiles, timings and messages.

### Dharok
- Runtime-verify Greatest Axe timing/GFX/animation and returned-damage behavior.

### Guthan
- Implement the full Impale spear-ownership lifecycle described above.
- Use alternate NPC id 18542 for the spear-absent state when verified visually/runtime-correct.
- Implement repeated victim bleed and return-to-Guthan spear retrieval.
- Implement conditional Throw interactions only after eligible pairing/trigger evidence is established.
- Exact healing/set-effect behavior, animations, timings and messages.

### Karil
- Lightning Conductor.
- Bombard pillar/line lightning paths.
- Shadow/Portal Dash and bomb placement/explosions.
- Exact ranged set effect, rotations, animations, projectiles, timings and messages.

### Torag
- Correct adrenaline-drain set effect.
- Implement conditional Throw interactions using the shared RoTS Throw state once verified.
- Replace the temporary solo Whack safety timeout with the exact classic termination rules once verified.
- Runtime-verify Whack's 2,500 teammate damage release and animations.

### Verac
- Correct armour/prayer-ignoring set effect.
- Remove ordinary Hurricane from his normal rotation; keep only shared forced Hurricane after revival where applicable.
- Deathcopter, including its relationship with the relevant Throw interaction rather than a disconnected standalone animation.
- Soulspot/prayer-drain mechanic.
- Wall Slam runtime fidelity verification.
- Conditional Throw behavior/pairings.
- Exact rotations, animations, graphics, timings and messages.

### Victory / rewards / escape
- Play the authentic victory/collapse sequence.
- Spawn and lock the reward chest to eligible surviving participants.
- Reproduce RoTS reward rolls, including level-90 shields, Barrows amulets and Malevolent energy.
- Award unstable Malevolent energy as item 30026.
- Start the 30-second collapse when the escape sequence is triggered.
- Reproduce collapsing walls, damaging pads, destroyed bridges and alternate crossings.
- Destroy unstable energy on death, logout, teleport or invalid escape.
- Convert unstable Malevolent energy to stable item 30027 only after a valid tunnel escape.
- Verify practice-mode behavior does not award real loot.

## Non-goals

- Do not modify the existing normal Barrows controller or BarrowsBrother to make RoTS work.
- Do not create a second combat engine, timer engine, world state, NPC lifecycle or drop authority inside BossLabs.
- Do not mark HYPOTHESIS mechanics as VERIFIED until runtime evidence exists.
