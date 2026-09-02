# The Barrows: Rise of the Six

Target: reproduce the classic RuneScape 3 Rise of the Six encounter as completely as practical on Matrix3 while preserving Matrix3 combat, NPC, world, instance, drop, and persistence ownership.

A mechanic is not complete merely because the fight is killable.

## Classification

- VERIFIED: confirmed by runtime testing in this Matrix3 project.
- verified-static: directly established from Matrix3 source/cache data, donor assets, or strong RuneScape encounter evidence, but not yet runtime-confirmed here.
- HYPOTHESIS: plausible implementation detail still requiring evidence/runtime validation.

## Fidelity evidence ladder

RoTS must not be reconstructed from one RSPS donor and assumed accurate.

1. **Behavior authority - RuneScape encounter evidence**
   - Prefer Jagex-era mechanic descriptions, the Beasts transcript, strategy documentation, and period-correct gameplay footage for what an attack actually does.
   - Use old 2013-2015 RoTS footage when text/code cannot establish movement, timing, facing, throw trajectories, wall interaction, Deathcopter behavior, or escape sequencing.
2. **Asset evidence - Matrix-family donor implementations**
   - Use donor code for candidate NPC ids, animations, graphics, projectiles, objects, transformations, coordinates, and sequencing clues.
   - Donor gameplay logic is reference only. Known bugs/placeholders do not automatically become Matrix3 behavior.
3. **Data verification - revision-830 cache**
   - Candidate ids must resolve correctly in the active cache before being treated as project evidence.
4. **Final authority - Matrix3 runtime**
   - A mechanic becomes VERIFIED only after it behaves correctly in this project.

Normal workflow:

**Encounter behavior evidence -> donor asset candidate -> revision-830 data check -> smallest Matrix3-native implementation -> targeted runtime test -> VERIFIED or revise.**

## Current runtime checkpoint

VERIFIED from user runtime testing:

- The donor-backed RoTS arena renders from the copied revision-830 map rather than loading void/black terrain.
- All six empowered brothers spawn visibly on valid arena terrain and engage the player.
- Guthan resolves as `Guthan the Infested (level 650)`.
- At least one subdued brother has been observed returning through the current shadow-bond revival path.

Still requiring targeted runtime verification:

- exact 30-second mass revival timing / simultaneous-six victory
- every current special and cleanup path
- Guthan Impale behavior
- new daily west/east formation ownership and physical side split

## Brother toolkit map

| Brother | Classic toolkit to reproduce | Wall Slam? | Current state |
| --- | --- | --- | --- |
| Dharok | Hurricane -> Greatest Axe -> Wall Slam | Yes | All three mechanically present; exact shared visuals/timing still need runtime fidelity work. |
| Torag | Hurricane -> Whack -> Wall Slam + conditional Throw | Yes | Core three mechanically present; Throw pairing is now side-gated but flight/landing execution is still pending exact assets. |
| Guthan | Hurricane -> Impale + conditional Throw | No | Hurricane + Impale mechanically present; Throw pairing is now side-gated but execution remains pending. |
| Verac | Soulspot, Deathcopter, Wall Slam + conditional Throw | Yes | Normal Hurricane mismatch fixed; Wall Slam present; Soulspot/Deathcopter/Throw execution pending. |
| Ahrim | Turret of Fire, Shadow Pits, Flight | No | Base combat only. |
| Karil | Lightning Conductor, Bombard, Portal Dash | No | Base combat only. |

## Verified-static encounter fundamentals

- Rise of the Six supports 1-4 players; classic intended group size is four.
- Empowered brothers are level 650 with 50,000 life points.
- Empowered NPC ids: Ahrim 18538/18539, Dharok 18540, Guthan 18541/18542, Karil 18543, Torag 18544, Verac 18545.
- Defeating a brother heals each active brother by 5,000 life points.
- Shared shadow-bond revive timer is 30 seconds and resets whenever another brother is defeated.
- If that timer expires, currently defeated brothers return at 25,000 life points.
- The fight ends only when all six are simultaneously subdued.
- Fight music is The Price is Wight, track id 1208.
- Nocturne's RoTS donor copies source chunk 290,753 as an 8x8-chunk map; Matrix3's 1x1 MapInstance ratio matches that shape.
- RuneScape uses a repeating twenty-day west/east brother formation cycle.
- A dated RuneScape formation record for 2025-06-11 is West Ahrim/Torag/Guthan and East Karil/Dharok/Verac; this is rotation 11 in the documented twenty-rotation sequence and is the Matrix3 UTC cycle anchor.

## Implemented now

### Native encounter foundation

- Matrix3 `BossInstance` registration with four-player cap.
- Admin/developer launch through `bosslabs rots` / `bosslabs riseofthesix` without modifying Commands.java.
- Dedicated RoTS brother NPC class; normal `BarrowsBrother` remains untouched.
- Dedicated CombatScript keys for all empowered/alternate ids.
- Admin/owner Start creates a fresh private boss instance instead of reusing the old hour-long session.

### Daily formation / side ownership

The encounter now has an explicit west/east formation authority instead of treating all six brothers as one undifferentiated group.

- All twenty documented brother rotations are encoded in `RiseOfTheSixInstance`.
- Rotation advances by UTC calendar day using 2025-06-11 rotation 11 as the dated anchor.
- Each loaded encounter records one active rotation and assigns exactly three brothers WEST and three EAST.
- The six already runtime-proven spawn tiles are deliberately reused as two groups of three: source X 2326/2328/2330 for WEST and 2332/2334/2336 for EAST, all on source Y 6034 plane 1.
- Source X 2331 is the temporary Matrix3 side-classification midpoint for player-position queries.
- `getBrotherSide`, `getPlayerSide`, `isPlayerOnBrotherSide`, and `areBrothersOnSameSide` now provide one encounter-owned side API for future mechanics.
- The verified Throw pairing matrix now rejects otherwise-valid targets that are on the opposite active side.
- `findVerifiedThrowTarget` therefore returns only active same-side candidates.

**Important fidelity boundary:** the daily lineup ownership is evidence-backed, but the exact final north/middle/south physical brother tiles and exact Jagex arena side bounds are not yet runtime-verified. The current implementation intentionally preserves known-good terrain instead of moving the fight onto guessed coordinates.

For the current project date 2026-09-02, the anchored twenty-day cycle resolves to rotation 19:

- WEST: Karil, Torag, Dharok
- EAST: Ahrim, Guthan, Verac

This date-specific expectation is a useful runtime acceptance check, not a permanent hardcoded lineup.

### Shadow bond

- brothers become incapacitated instead of using normal NPC drop/respawn death
- surviving brothers heal 5,000 HP after each subdual
- new subdual resets the pending revival generation
- subdued brothers revive at 25,000 HP when the revival task wins
- all-six-subdued state cancels further revival

### Base combat

- Ahrim 3,000 max, donor-backed projectile 559 / impact GFX 377 / 5-tick baseline
- Karil 3,000 max, donor-backed projectile 955 / animation 18232 / 7-tick baseline
- Guthan/Torag/Verac 3,500 melee baseline
- Dharok 2,000 -> 7,000 max-hit bands by remaining HP
- Guthan life-steal baseline at 1-in-8
- Ahrim Strength drain baseline at 1-in-8

### Dharok - Greatest Axe

Donor-backed first faithful slice:

- `Give me everything!`
- GFX 4406
- animation 21940
- absorbs/stores incoming damage during the charge
- normal autos pause during the charge
- stored damage is added once to Dharok's next normal outgoing hit

Exact timing/visual behavior still requires runtime verification.

### Torag - Whack

Donor-backed first faithful slice:

- Torag start animation 21933
- victim pin animation 21934
- hammering animation 21935
- release animation 21938
- victim is locked/pinned
- incoming teammate damage is redirected into the 2,500 release threshold instead of Torag HP
- temporary solo safety release remains until authentic termination behavior is fully verified

### Hurricane

Current Matrix3 mechanical implementation:

- normal Hurricane belongs to Dharok, Guthan and Torag
- brother pursues at walking speed
- ten escalating hard-typeless pulses
- nearby players can also be hit
- normal autos are suppressed while Hurricane owns the brother
- exact spin animation/GFX and final damage/radius distribution remain HYPOTHESIS

**Verac correction:** ordinary Hurricane has been removed from Verac's normal implemented rotation. Verac can still be forced into Hurricane through the shared post-revival melee rule. This matches the documented rare post-revival Verac spin behavior rather than treating Hurricane as one of his normal specials.

### Wall Slam

Current Matrix3 mechanical implementation for Dharok, Torag and Verac:

- captures the target's tile when the special begins
- runs toward a reachable arena collision edge
- rushes back toward the captured location
- resolves a 5x5 hard-typeless impact around the captured tile
- current damage range 500-3,000

Exact wall anchors, run-up/fling animations, impact animation/GFX and final damage distribution remain HYPOTHESIS.

## Guthan Impale

### Behavior authority

Classic behavior:

1. Guthan prefers a player he is not currently attacking on his side.
2. If no secondary target is available, he can throw the spear at his current target.
3. The spear impales the victim and Guthan loses access to spear specials while it is away.
4. Guthan continues normal melee attacks by punching with the same combat effectiveness.
5. The victim takes repeated 400-500 hard-typeless bleed damage.
6. Returning adjacent to Guthan causes him to rip the spear back out.
7. Retrieval deals about 1,000 hard-typeless damage.
8. Victim death, Guthan death/subdual, or Torag pummelling the impaled player returns the spear automatically.

### verified-static asset/data evidence

- normal Guthan id 18541; alternate spear-absent Guthan id 18542
- cache attack animation: 18541 -> 18223, 18542 -> 18224
- donor spear projectile: 4411
- donor throw animation candidate: 21944
- donor victim/impaled animation: 21945
- donor spear-retrieval animation: 21947
- donor bleed graphics: 4411 and 4407
- donor bleed values: 400-500
- donor retrieval hit: 1,000

### Current Matrix3 implementation

- Impale is now the second normal Guthan special after Hurricane in the implemented rotation.
- The target selector currently prefers another eligible instance player instead of Guthan's primary target; solo/no-secondary fallback uses the primary target.
- West/east ownership now exists at encounter level, but the current Impale selector still needs a small follow-up to consume the side API before same-side victim selection can be marked complete.
- Guthan sends projectile 4411 and transforms to alternate NPC 18542 while the spear is away.
- The victim receives animation 21945 and repeated 400-500 hard-typeless bleed ticks with GFX 4411/4407.
- Guthan resumes ordinary melee combat while spearless; the alternate NPC's cache attack animation 18224 is therefore used naturally by Matrix3 combat.
- Guthan cannot start another special while the spear is away.
- When the victim becomes adjacent, Guthan briefly owns the retrieval sequence, plays animation 21947, restores NPC 18541, and deals a 1,000 hard-typeless retrieval hit.
- Generation tokens stop old projectile/bleed/retrieval tasks after reset/death/new-instance cleanup.
- Torag beginning Whack on the impaled victim automatically returns Guthan's spear through the encounter's cross-brother coordinator.
- Guthan subdual/finish and invalid/dead/leaving victims also restore the spear safely.

Still HYPOTHESIS / pending runtime:

- exact same-side Impale victim filtering against the new side API
- exact throw/retrieval tick timing
- whether donor animation 21944 is visually the exact launch animation in this cache
- exact defensive-ability clearing/cooldown behavior applied by each bleed tick
- exact Shadow Realm Impale multipliers/interaction

## Shared Throw interaction model

Throw is a cross-brother encounter interaction, not a generic NPC attack.

### verified-static thrower/target matrix

- Guthan -> Ahrim, Karil, Verac
- Torag -> Ahrim, Karil, Verac
- Verac -> Ahrim, Karil
- Dharok is not listed as a Throw brother in the Beasts descriptions used for this fidelity target.

The encounter instance owns this matrix and now applies active daily side ownership before returning an eligible Throw target.

### Important current boundary

**Actual Throw launches are intentionally not active yet.**

Side ownership is now authoritative enough to reject cross-side pairing, but the exact brother Throw launch/flight/landing animation, projectile/trajectory, timing and impact damage are not yet established strongly enough to activate the attack without guessing.

The shared coordinator should continue to own:

- same-side eligibility
- thrower/target reservation
- Surge/run-to-target sequence
- thrown brother flight/landing state
- landing/arc hard-typeless damage
- cleanup on subdual/death/realm transition
- Verac Deathcopter interception when Verac has signaled and another brother attempts to Throw him

Do not implement Throw separately in each brother's CombatScript.

## HYPOTHESIS items still pending

- exact physical north/middle/south brother spawn coordinates/facing inside each daily side
- exact player west/east sub-area bounds beyond the current source-X midpoint classification
- exact special cadence / number of autos between specials; current gate is 3-5 autos
- exact Hurricane visuals/radius/damage distribution
- exact Wall Slam anchors/animations/path timing
- Torag authentic Whack natural termination behavior
- exact Throw launch/flight/landing assets/timing/damage
- exact Shadow Realm interaction for every special
- real entrance/graveyard/tunnel object routing

## Required for 100% encounter fidelity

### Entry / staging

- wire real Rise of the Six well object 87997
- consume one Barrows totem (item 30004) where appropriate
- reproduce join/rejoin rules
- reproduce staging tunnel / random tunnel layouts / bridge counts
- reproduce four pressure pads and fight lock
- replace temporary exit/grave tile with verified RoTS route

### Formation / arena

- runtime-verify the twenty-day UTC formation cycle against the current RuneScape rotation expectation
- runtime-verify the current three-west/three-east known-good tile split
- replace temporary within-side slot coordinates with exact classic north/middle/south positions only when evidence establishes them
- verify exact brother facing
- central barrier/portal rules
- empty-side empowerment / side-hop rules
- prevent late joins after combat begins

### Shared shadow mechanics

- Shadow Drag selection/thresholds
- full-team Shadow Realm move in/out
- Shadow Realm accuracy/damage changes
- visible 30-second incapacitation/revival presentation

### Shared melee fidelity

- exact Hurricane spin animation/GFX, pulse timing/range/damage
- exact Wall Slam wall anchors/run-up/fling/impact assets
- replace temporary 3-5 auto cadence with classic cadence when verified
- activate shared Throw flight/landing only after exact assets/timing are established

### Ahrim

- blood-spell self healing
- Flight transformation using alternate NPC id 18539
- airborne melee immunity / magic reduction as appropriate to target era
- purple/red Shadow Pits
- Turret of Fire
- exact rotation/assets/messages

### Dharok

- runtime-verify Greatest Axe charge and returned damage
- runtime-verify Hurricane/Wall Slam fidelity

### Guthan

- runtime-verify full Impale lifecycle from this checkpoint
- consume the new side API in Impale target selection and runtime-verify same-side preference
- implement defensive-ability clearing/cooldown behavior if target-era evidence confirms it
- activate verified Throw interactions after exact Throw assets are established
- exact healing/resistance/set-effect behavior

### Karil

- Lightning Conductor
- Bombard pillar/line lightning
- Portal/Shadow Dash with portal explosions/closure behavior
- ranged stat-drain effect
- exact rotation/assets/messages

### Torag

- adrenaline-drain set effect
- activate verified Throw interactions after exact Throw assets are established
- replace temporary Whack solo safety timeout with authentic termination rules
- runtime-verify Whack/Hurricane/Wall Slam

### Verac

- armour/prayer-ignore set effect
- Soulspot/prayer drain
- Deathcopter and its Throw interception relationship
- activate verified Throw to Ahrim/Karil after exact Throw assets are established
- runtime-verify Wall Slam

### Victory / rewards / escape

- authentic victory/collapse sequence
- reward chest eligibility
- real RoTS reward rolls / level-90 shields / Malevolent energy
- unstable Malevolent energy item 30026
- 30-second collapsing escape
- collapsing walls/pads/bridges/alternate crossings
- destroy unstable energy on invalid escape/death/logout/teleport
- convert to stable Malevolent energy item 30027 only after valid escape
- no real loot in practice mode

## Non-goals

- Do not modify normal Barrows or `BarrowsBrother` to make RoTS work.
- Do not create a second combat engine/timer/world/NPC lifecycle inside BossLabs.
- Do not activate guessed Throw flight/landing behavior merely because side ownership now exists.
- Do not mark HYPOTHESIS behavior VERIFIED without runtime evidence.
