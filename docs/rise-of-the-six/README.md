# The Barrows: Rise of the Six

Target: reproduce the classic RuneScape 3 Rise of the Six encounter as completely as practical on Matrix3 while preserving Matrix3 combat, NPC, world, instance, drop, and persistence ownership.

This document is the encounter fidelity checklist. A mechanic is not considered complete merely because the fight is killable.

## Classification

- VERIFIED: confirmed by runtime testing in this Matrix3 project.
- verified-static: confirmed from Matrix3 source/cache structure or strong donor/authoritative encounter evidence, but not yet runtime-proven here.
- HYPOTHESIS: implementation detail that still requires runtime evidence before it can be treated as correct.

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

HYPOTHESIS pending targeted runtime tests
- Exact daily formation/facing and west/east side ownership still need runtime/map verification.
- Exact RoTS special trigger rotations/frequency need replacement of donor random triggers once stronger rotation evidence is available.
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
- Dharok Greatest Axe first faithful slice: force-talk, charge GFX/animation, incoming damage absorption/storage, no autos during charge, and stored damage returned through his next hit.
- Torag Whack first faithful slice: victim pin/lock, start/hammer/release animations, incoming teammate damage redirected into the 2,500 release threshold, and cleanup-safe victim unlock.
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
- Make revived melee brothers force Hurricane as their next attack.

### Ahrim
- Blood-spell self healing.
- Flight transformation/state using the alternate NPC id.
- Melee immunity and reduced magic/necromancy damage while airborne.
- Purple/red Shadow Pits and their player-damage/brother-heal behavior.
- Turret of Fire.
- Exact attack/special rotation, animations, graphics, projectiles, timings and messages.

### Dharok
- Hurricane.
- Wall Slam.
- Replace donor-random Greatest Axe trigger with the exact live/classic special rotation once verified.
- Runtime-verify Greatest Axe timing/GFX/animation and returned-damage behavior.

### Guthan
- Hurricane.
- Wall Slam.
- Throw/Impale spear state using the alternate NPC id.
- Bleed until the victim returns near Guthan.
- Exact healing/set-effect behavior, animations, timings and messages.

### Karil
- Lightning Conductor.
- Bombard pillar/line lightning paths.
- Shadow/Portal Dash and bomb placement/explosions.
- Exact ranged set effect, rotations, animations, projectiles, timings and messages.

### Torag
- Correct adrenaline-drain set effect.
- Hurricane.
- Wall Slam.
- Replace donor-random Whack trigger and temporary solo safety timeout with the exact classic attack rotation/termination rules once verified.
- Runtime-verify Whack's 2,500 teammate damage release and animations.

### Verac
- Correct armour/prayer-ignoring set effect.
- Hurricane and Deathcopter.
- Wall Slam.
- Soulspot/prayer-drain mechanic.
- Throw behavior.
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
