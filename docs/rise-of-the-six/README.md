# The Barrows: Rise of the Six

Target: reproduce the classic RuneScape 3 Rise of the Six encounter as completely as practical on Matrix3 while preserving Matrix3 combat, NPC, world, instance, drop, and persistence ownership.

This document is the encounter fidelity checklist. A mechanic is not considered complete merely because the fight is killable.

## Classification

- VERIFIED: confirmed by runtime testing in this Matrix3 project.
- verified-static: confirmed from Matrix3 source/cache structure or authoritative external encounter data, but not yet runtime-proven here.
- HYPOTHESIS: implementation detail that still requires runtime evidence before it can be treated as correct.

## Current runtime checkpoint

verified-static
- Rise of the Six is a 1-4 player encounter; the original intended group size is four.
- Empowered brothers use combat level 650 and 50,000 life points.
- Empowered NPC ids: Ahrim 18538/18539, Dharok 18540, Guthan 18541/18542, Karil 18543, Torag 18544, Verac 18545.
- Defeating a brother heals each active brother by 5,000 life points.
- The shared shadow-bond revive timer is 30 seconds and resets whenever another brother is defeated.
- If the timer expires, all currently defeated brothers return with 25,000 life points.
- The fight ends only when all six brothers are defeated at the same time.
- Fight music is The Price is Wight, music-track id 1208.
- Known source-map points include 2326,5910,0 for the battle shadow portal and 2328,6036,1 for the Shadow Realm.

HYPOTHESIS pending first runtime map test
- The copied source rectangle beginning at chunk 286,732 with size 2x3 contains the complete staging/fight/Shadow Realm area required by the encounter.
- The initial six provisional spawn tiles around the known shadow-portal coordinate land on the intended walkable west/east battle floors.
- The temporary Matrix3 boss-instance exit/grave tile is deliberately a known Barrows surface tile until object 87997 and the real RoTS graveyard path are wired.

## Implemented now

- Native Matrix3 BossInstance registration with a four-player cap.
- Private instance creation through the existing BossInstanceD flow.
- Admin/developer launch through `bosslabs rots` or `bosslabs riseofthesix` without changing Commands.java.
- Dedicated RoTS brother NPC class; normal BarrowsBrother remains untouched.
- Dedicated CombatScript keys for all empowered/alternate RoTS NPC ids.
- Basic magic/ranged/melee auto attacks through Matrix3 CombatScript.
- Dharok missing-health damage scaling baseline.
- Guthan life-steal baseline.
- Ahrim Strength-drain baseline using the documented 1-in-8 chance.
- Shared defeat state: brothers become incapacitated rather than using normal NPC drop/respawn death.
- Survivor heal of 5,000 HP after each brother is subdued.
- 30-second revive generation reset after every new subdued brother.
- Revive of every currently subdued brother at 25,000 HP when the timer expires.
- Victory lockout when all six are subdued before the timer expires.
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
- Greatest Axe damage-storage/return mechanic.
- Exact low-health scaling, animations, timings and messages.

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
- Hammerhead/Whack player pin and teammate damage-release threshold.
- Exact rotations, animations, timings and messages.

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
